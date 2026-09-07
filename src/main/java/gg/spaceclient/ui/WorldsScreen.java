package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.util.Reflect;
import gg.spaceclient.util.Screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The world list as a row of cards rather than a column of rows.
 *
 * A saved world is remembered as a picture long before it is remembered as a
 * name - "the one on the cliff" comes to mind faster than whatever it got
 * called at three in the morning. The game already renders a thumbnail for
 * every world and then shows it at thirty pixels wide next to a wall of text.
 * These cards give it the space to actually be recognised.
 *
 * <h2>Everything here is reflective</h2>
 *
 * Listing worlds, reading a summary and opening one are three separate APIs
 * that no part of this mod has compiled against, and all three have moved
 * around in recent versions. So the whole screen is built on Reflect and
 * degrades in one step: if the list cannot be read it says so and offers the
 * game's own screen, rather than presenting an empty grid that looks like you
 * have lost your saves.
 */
public class WorldsScreen extends Screen {

    private static final int CARD_W = 150;
    private static final int CARD_H = 118;
    private static final int CARD_GAP = 18;

    private final Screen parent;

    /** Built once when the screen opens; reading saves is disk work. */
    private java.util.List<Entry> entries = java.util.List.of();
    private String failure = null;

    private float scroll = 0f;
    private float scrollTarget = 0f;

    public WorldsScreen(Screen parent) {
        super(Component.literal("Singleplayer"));
        this.parent = parent;
    }

    /** One saved world, already reduced to what the card draws. */
    private record Entry(String id, String name, String detail, Path icon) {}

    @Override
    protected void init() {
        if (entries.isEmpty() && failure == null) load();

        int count = entries.size() + 1;
        int totalWidth = count * CARD_W + (count - 1) * CARD_GAP;
        int startX = Math.max(30, (this.width - totalWidth) / 2);
        int y = (this.height - CARD_H) / 2;

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int x = startX + i * (CARD_W + CARD_GAP);
            this.addRenderableWidget(new WorldCard(
                    x, y, CARD_W, CARD_H,
                    entry.name(), entry.detail(), texture(entry),
                    false, () -> open(entry.id())));
        }

        int newX = startX + entries.size() * (CARD_W + CARD_GAP);
        this.addRenderableWidget(new WorldCard(
                newX, y, CARD_W, CARD_H,
                "New world", "", null, true, this::createWorld));

        this.addRenderableWidget(new FlatButton(
                20, this.height - 36, 100, 22,
                () -> "Back", () -> false, this::onClose).asAction());
    }

    // --- reading the saves ---

    private void load() {
        try {
            Object source = Reflect.call(Minecraft.getInstance(), "getLevelSource");
            if (source == null) throw new IllegalStateException("no level source");

            Object candidates = Reflect.call(source, "findLevelCandidates");
            Object future = Reflect.callWith(source, "loadLevelSummaries", candidates);

            Object list = future == null ? null : Reflect.call(future, "join", "get");
            if (!(list instanceof java.util.List<?> summaries)) {
                throw new IllegalStateException("no summaries");
            }

            java.util.List<Entry> out = new java.util.ArrayList<>();
            for (Object summary : summaries) {
                Entry entry = toEntry(summary);
                if (entry != null) out.add(entry);
            }
            entries = out;

        } catch (Throwable t) {
            failure = "Could not read the world list on this version";
            SpaceClient.LOGGER.warn("World list unavailable: {}", String.valueOf(t));
        }
    }

    private Entry toEntry(Object summary) {
        try {
            Object id = Reflect.call(summary, "getLevelId", "getLevelName");
            Object name = Reflect.call(summary, "getLevelName", "getLevelId");
            if (!(id instanceof String levelId)) return null;

            String label = name instanceof String text && !text.isEmpty() ? text : levelId;

            Object played = Reflect.call(summary, "getLastPlayed");
            String detail = "";
            if (played instanceof Number millis && millis.longValue() > 0) {
                detail = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(millis.longValue()));
            }

            Object icon = Reflect.call(summary, "getIcon");
            Path iconPath = icon instanceof Path path && Files.isRegularFile(path) ? path : null;

            return new Entry(levelId, label, detail, iconPath);

        } catch (Throwable ignored) {
            return null;
        }
    }

    // --- thumbnails ---

    private static final java.util.Map<String, Identifier> TEXTURES = new java.util.HashMap<>();
    private static boolean textureWarned = false;

    /**
     * The world's own thumbnail, registered once and remembered.
     *
     * Keyed by world id rather than by image, because unlike a server icon
     * this file changes every time the world is saved - keying by content
     * would register a new texture on every visit.
     */
    private Identifier texture(Entry entry) {
        if (entry.icon() == null) return null;

        Identifier known = TEXTURES.get(entry.id());
        if (known != null) return known;

        try {
            byte[] bytes = Files.readAllBytes(entry.icon());

            Class<?> nativeImage = Class.forName("com.mojang.blaze3d.platform.NativeImage");
            Method read = nativeImage.getMethod("read", byte[].class);
            Object image = read.invoke(null, (Object) bytes);
            if (image == null) return null;

            Object texture = newDynamicTexture(image);
            if (texture == null) return null;

            Object manager = Reflect.call(Minecraft.getInstance(), "getTextureManager");
            if (manager == null) return null;

            Identifier id = Identifier.fromNamespaceAndPath(
                    SpaceClient.MOD_ID,
                    "world_icon/" + entry.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));

            Reflect.callWith(manager, "register", id, texture);
            TEXTURES.put(entry.id(), id);
            return id;

        } catch (Throwable ignored) {
            if (!textureWarned) {
                textureWarned = true;
                SpaceClient.LOGGER.warn("World thumbnails could not be decoded on this version");
            }
            return null;
        }
    }

    private Object newDynamicTexture(Object image) {
        try {
            Class<?> type = Class.forName("com.mojang.blaze3d.platform.DynamicTexture");
            for (Constructor<?> constructor : type.getConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                try {
                    if (params.length == 1 && params[0].isInstance(image)) {
                        return constructor.newInstance(image);
                    }
                    if (params.length == 2 && params[1].isInstance(image)) {
                        if (params[0] == String.class) {
                            return constructor.newInstance("space client world icon", image);
                        }
                        if (params[0] == java.util.function.Supplier.class) {
                            java.util.function.Supplier<String> label = () -> "space client world icon";
                            return constructor.newInstance(label, image);
                        }
                    }
                } catch (Throwable ignored) {
                    // Wrong shape, try the next
                }
            }
        } catch (Throwable ignored) {
            // Not present on this version
        }
        return null;
    }

    // --- actions ---

    private void open(String levelId) {
        try {
            Object flows = Reflect.call(Minecraft.getInstance(), "createWorldOpenFlows");
            if (flows == null) return;

            // The newer shape takes something to run if the world will not
            // open; the older one takes only the id
            Object done = Reflect.callWith(flows, "openWorld", levelId, (Runnable) () -> Screens.open(this));
            if (done == null) Reflect.callWith(flows, "openWorld", levelId);

        } catch (Throwable ignored) {
            SpaceClient.LOGGER.warn("Could not open the world on this version");
        }
    }

    private void createWorld() {
        try {
            Class<?> type = Class.forName(
                    "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen");
            for (Method method : type.getMethods()) {
                if (!method.getName().equals("openFresh")) continue;
                if (method.getParameterCount() != 2) continue;
                method.invoke(null, Minecraft.getInstance(), this);
                return;
            }
        } catch (Throwable ignored) {
            // Fall through to the game's own list, which can always create one
        }
        SpaceClient.LOGGER.warn("Could not open the world creator on this version");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!MenuWallpaper.draw(graphics, this.width, this.height, mouseX, mouseY, delta)) {
            Backdrop.draw(graphics, this.width, this.height);
        }
        graphics.fill(0, 0, this.width, this.height, 0x60000000);

        String title = "Singleplayer";
        boolean scaled = Scale.push(graphics, (this.width - this.font.width(title) * 2) / 2, 30, 2f);
        graphics.text(this.font, title,
                scaled ? 0 : (this.width - this.font.width(title) * 2) / 2,
                scaled ? 0 : 30, 0xFFFFFFFF, false);
        if (scaled) Scale.pop(graphics);

        String hint = failure != null ? failure : "Pick a world, or make a new one";
        graphics.text(this.font, hint,
                (this.width - this.font.width(hint)) / 2, 56,
                failure != null ? 0xFFE8C46A : 0xFFB9B4DC, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        Screens.open(parent);
    }
}
