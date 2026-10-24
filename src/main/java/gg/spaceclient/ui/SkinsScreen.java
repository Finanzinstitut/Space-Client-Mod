package gg.spaceclient.ui;

import gg.spaceclient.skin.SkinChanger;
import gg.spaceclient.skin.SkinImage;
import gg.spaceclient.skin.SkinLibrary;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The skin changer, in the game.
 *
 * The same library as the launcher's skin page - a skin saved in one is in
 * the other - with the figure on the left, the saved skins on the right, and
 * a player name field for taking somebody else's. "Put on" sends the skin to
 * Mojang and shows it on the player at once.
 */
public class SkinsScreen extends Screen {

    private static final int CARD_W = 58;
    private static final int CARD_H = 84;
    private static final int GAP = 6;
    private static final int SIDE_W = 120;

    private final Screen parent;
    private List<SkinLibrary.Entry> skins = List.of();
    private final Map<String, Identifier> textures = new HashMap<>();
    private SkinLibrary.Entry selected = null;
    private boolean back = false;
    private int scrollRow = 0;
    private int rowCount = 0;
    private String typed = "";

    public SkinsScreen(Screen parent) {
        super(Component.literal("Skins"));
        this.parent = parent;
    }

    private int margin() { return Math.max(16, (this.width - 660) / 2); }
    private int contentW() { return Math.min(660, this.width - 32); }
    private int gridX() { return margin() + SIDE_W + 12; }
    private int gridW() { return contentW() - SIDE_W - 12; }
    /**
     * A little higher than the other screens start, just clear of the
     * subtitle: at the usual 640 by 360 a figure and two rows of skins only
     * fit with those pixels.
     */
    private int top() { return ScreenChrome.TOP - 16; }
    private int gridTop() { return top() + 24; }
    private int bottom() { return ScreenChrome.bottomRow(this.height); }
    /** The bottom of the left column, just above the Back button. */
    private int sideBottom() { return bottom() - 6; }
    private int columns() { return Math.max(1, (gridW() + GAP) / (CARD_W + GAP)); }
    private int visibleRows() { return Math.max(1, (bottom() - 20 - gridTop()) / (CARD_H + GAP)); }

    @Override
    protected void init() {
        int taken = SkinLibrary.takeInbox();
        if (taken > 0) SkinChanger.say(taken + " skin(s) taken in from the drop folder.");
        skins = SkinLibrary.list();
        if (selected != null) {
            String id = selected.id();
            selected = skins.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        }
        if (selected == null && !skins.isEmpty()) selected = skins.get(0);

        int left = margin();
        int bottom = bottom();

        // The figure's controls, at the foot of its column
        int sideBottom = sideBottom();
        int turnW = 52;
        this.addRenderableWidget(new FlatButton(left, sideBottom - 42, turnW, 18,
                () -> back ? "Front" : "Turn", () -> false, () -> back = !back).asAction());
        this.addRenderableWidget(new FlatButton(left + turnW + 4, sideBottom - 42, SIDE_W - turnW - 4, 18,
                () -> selected != null && selected.slim() ? "Slim" : "Classic", () -> false,
                this::toggleModel).asAction());
        this.addRenderableWidget(new FlatButton(left, sideBottom - 20, SIDE_W, 20,
                () -> SkinChanger.busy() ? "Working…" : "Put on", () -> false, this::putOn).asAction());

        // Somebody else's skin by name, and files
        int gx = gridX();
        int toolY = top();
        int buttonsW = 64 + 4 + 64;
        EditBox name = new EditBox(this.font, gx + 4, toolY + 4, gridW() - buttonsW - 16, 12,
                Component.literal("Player name"));
        name.setBordered(false);
        name.setMaxLength(16);
        name.setTextColor(Theme.TEXT);
        name.setHint(Component.literal("Player name"));
        name.setValue(typed);
        name.setResponder(value -> typed = value);
        this.addRenderableWidget(name);
        this.addRenderableWidget(new FlatButton(gx + gridW() - buttonsW, toolY, 64, 18,
                () -> "Import", () -> false, this::importPlayer).asAction());
        this.addRenderableWidget(new FlatButton(gx + gridW() - 64, toolY, 64, 18,
                () -> "Add file", () -> false, this::addFiles).asAction());

        // The saved skins
        int columns = columns();
        int visible = visibleRows();
        rowCount = (skins.size() + columns - 1) / columns;
        scrollRow = Math.max(0, Math.min(Math.max(0, rowCount - visible), scrollRow));
        for (int slot = 0; slot < visible; slot++) {
            int row = scrollRow + slot;
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                if (index >= skins.size()) break;
                SkinLibrary.Entry entry = skins.get(index);
                this.addRenderableWidget(new SkinCard(
                        gx + column * (CARD_W + GAP), gridTop() + slot * (CARD_H + GAP), CARD_W, CARD_H,
                        entry, () -> texture(entry), () -> selected != null && selected.id().equals(entry.id()),
                        () -> selected = entry));
            }
        }

        this.addRenderableWidget(new FlatButton(left, bottom, contentW(), 24,
                () -> "Back", () -> false, this::onClose).asAction());
    }

    /** A saved skin's texture, made the first time it is drawn. */
    private Identifier texture(SkinLibrary.Entry entry) {
        Identifier known = textures.get(entry.id());
        if (known != null || textures.containsKey(entry.id())) return known;
        Identifier made = null;
        try {
            made = SkinImage.texture(entry.id(), Files.readAllBytes(entry.path()));
        } catch (Exception ignored) {
            // Shown as an empty card
        }
        textures.put(entry.id(), made);
        return made;
    }

    private void putOn() {
        if (selected == null || SkinChanger.busy()) return;
        SkinChanger.apply(selected);
    }

    /** The model the selected skin goes up with, remembered in the library. */
    private void toggleModel() {
        if (selected == null) return;
        try {
            SkinLibrary.setVariant(selected.id(), !selected.slim());
            this.rebuildWidgets();
        } catch (Exception e) {
            SkinChanger.say("Could not change the model: " + e.getMessage());
        }
    }

    private void importPlayer() {
        if (typed.isBlank() || SkinChanger.busy()) return;
        SkinChanger.importPlayer(typed).thenAccept(entry -> Minecraft.getInstance().execute(() -> {
            if (entry == null) return;
            typed = "";
            selected = entry;
            scrollRow = 0;
            this.rebuildWidgets();
        }));
    }

    /**
     * PNG files from disk. The game's own file dialog where this version has
     * one; otherwise the drop folder opens, and whatever goes into it is in
     * the library the next time this screen is opened.
     */
    private void addFiles() {
        Thread chooser = new Thread(() -> {
            String picked = FileDialog.openPngs("Add skins");
            Minecraft mc = Minecraft.getInstance();
            if (picked == null) {
                if (!FileDialog.available()) mc.execute(this::openInbox);
                return;
            }
            int added = 0;
            String failure = null;
            for (String path : picked.split("\\|")) {
                try {
                    Path file = Path.of(path);
                    byte[] png = Files.readAllBytes(file);
                    if (!SkinImage.fits(png)) {
                        failure = file.getFileName() + " is not a 64x64 or 64x32 skin.";
                        continue;
                    }
                    String name = file.getFileName().toString().replaceFirst("(?i)\\.png$", "");
                    SkinLibrary.store(png, name, false);
                    added++;
                } catch (Exception e) {
                    failure = e.getMessage();
                }
            }
            String message = added > 0 ? added + " skin(s) added." : failure;
            mc.execute(() -> {
                if (message != null) SkinChanger.say(message);
                scrollRow = 0;
                this.rebuildWidgets();
            });
        }, "Space Client skin chooser");
        chooser.setDaemon(true);
        chooser.start();
    }

    private void openInbox() {
        try {
            Path inbox = SkinLibrary.inbox();
            Files.createDirectories(inbox);
            //#if MC >= 26.3
            com.mojang.blaze3d.Blaze3D.openPath(inbox);
            //#else
            //$$ net.minecraft.util.Util.getPlatform().openPath(inbox);
            //#endif
            SkinChanger.say("Put PNG skins in the folder that opened, then come back here.");
        } catch (Exception e) {
            SkinChanger.say("Put PNG skins into " + SkinLibrary.inbox());
        }
    }

    private boolean scrollBy(double amount) {
        int max = Math.max(0, rowCount - visibleRows());
        if (max <= 0) return false;
        int before = scrollRow;
        scrollRow = Math.max(0, Math.min(max, scrollRow - (int) Math.signum(amount)));
        if (scrollRow != before) this.rebuildWidgets();
        return true;
    }

    // Both shapes, as everywhere in this mod: only one of them is real here
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollBy(scrollY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollBy(amount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ScreenChrome.background(graphics, this.width, this.height, mouseX, mouseY, delta);
        ScreenChrome.header(graphics, this.font, this.width,
                "Skins", "Playing as " + Minecraft.getInstance().getUser().getName());

        // The figure, as big as the room above its buttons allows
        int left = margin();
        int top = top();
        int panelBottom = sideBottom() - 46;
        graphics.fill(left, top, left + SIDE_W, panelBottom, Theme.CARD);
        if (selected != null) {
            Identifier image = texture(selected);
            // Everything above the name line is the figure's
            int room = panelBottom - top - 16;
            int scale = Math.max(1, Math.min(5, room / 32));
            int figureWidth = (selected.slim() ? 14 : 16) * scale;
            int figureTop = top + 2 + (room - 32 * scale) / 2;
            if (image != null) {
                SkinImage.drawFigure(graphics, image, left + (SIDE_W - figureWidth) / 2,
                        figureTop, scale, selected.slim(), back);
            }
            String name = selected.name();
            while (this.font.width(name) > SIDE_W - 8 && name.length() > 1) name = name.substring(0, name.length() - 1);
            graphics.text(this.font, name, left + (SIDE_W - this.font.width(name)) / 2,
                    panelBottom - 11, Theme.TEXT, false);
        } else {
            String note = "No skins yet";
            graphics.text(this.font, note, left + (SIDE_W - this.font.width(note)) / 2,
                    (top + panelBottom) / 2, Theme.TEXT_DIM, false);
        }

        // The name field's frame, drawn under the field itself
        int gx = gridX();
        int fieldRight = gx + gridW() - (64 + 4 + 64) - 6;
        graphics.fill(gx, top, fieldRight, top + 18, Theme.CARD);
        graphics.fill(gx, top + 17, fieldRight, top + 18, Theme.BORDER);

        if (skins.isEmpty()) {
            String note = "Import a player's skin, add a PNG, or save skins in the launcher.";
            graphics.text(this.font, note, gx, gridTop() + 6, Theme.TEXT_DIM, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String status = SkinChanger.status();
        if (!status.isEmpty()) {
            // Under the grid, clear of the buttons beside the figure
            String line = status;
            while (this.font.width(line) > gridW() && line.length() > 1) line = line.substring(0, line.length() - 1);
            graphics.text(this.font, line, gridX(), bottom() - 16,
                    SkinChanger.busy() ? Theme.TEXT_DIM : Theme.CYAN, false);
        }
    }

    @Override
    public void onClose() {
        gg.spaceclient.compat.Screens.set(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
