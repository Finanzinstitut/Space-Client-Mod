package gg.spaceclient.platform;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * What the mod wants to hear about, said once, whatever the mod loader.
 *
 * The rest of the mod registers here and never names a loader. Each loader has
 * one small entry class (FabricEntry, NeoForgeEntry, ForgeEntry) that listens
 * to that loader's own events and calls the matching fire method below. That
 * keeps everything loader specific in three short files rather than spread
 * through the forty that need a tick or a screen.
 */
public final class Hooks {

    /** One HUD layer: drawn every frame the HUD is drawn. */
    public interface HudLayer {
        void draw(GuiGraphicsExtractor graphics, DeltaTracker delta);
    }

    /** A screen that has just built its widgets. */
    public interface ScreenInit {
        void init(Minecraft client, Screen screen, int width, int height);
    }

    /** A screen that has just been drawn. */
    public interface ScreenDrawn {
        void drawn(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);
    }

    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static final List<Consumer<Minecraft>> TICKS = new ArrayList<>();
    private static final List<ScreenInit> SCREEN_INITS = new ArrayList<>();
    private static final Map<Screen, List<ScreenDrawn>> SCREEN_DRAWN =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final List<HudLayer> BEFORE_CHAT = new ArrayList<>();
    private static final List<HudLayer> AFTER_CHAT = new ArrayList<>();
    private static final List<Runnable> JOINS = new ArrayList<>();

    private Hooks() {}

    // ---------------------------------------------------------------- registering

    /**
     * A key mapping, shown in the game's controls screen. Must be called while
     * the mod starts: loaders take their key list once, early.
     */
    public static KeyMapping key(KeyMapping mapping) {
        KEYS.add(mapping);
        return mapping;
    }

    /** Called at the end of every client tick. */
    public static void onTick(Consumer<Minecraft> listener) { TICKS.add(listener); }

    /** Called after any screen builds its widgets. */
    public static void onScreenInit(ScreenInit listener) { SCREEN_INITS.add(listener); }

    /** Called after one particular screen is drawn, for as long as it lives. */
    public static void onScreenDrawn(Screen screen, ScreenDrawn listener) {
        SCREEN_DRAWN.computeIfAbsent(screen, s -> new ArrayList<>()).add(listener);
    }

    /** A HUD layer drawn just below the chat. */
    public static void hudBeforeChat(HudLayer layer) { BEFORE_CHAT.add(layer); }

    /** A HUD layer drawn just above the chat. */
    public static void hudAfterChat(HudLayer layer) { AFTER_CHAT.add(layer); }

    /** Called when the player has joined a server or a world. */
    public static void onJoin(Runnable listener) { JOINS.add(listener); }

    // ---------------------------------------------------------------- for the loader entries

    public static List<KeyMapping> keys() { return List.copyOf(KEYS); }

    public static void fireTick(Minecraft client) {
        for (Consumer<Minecraft> listener : TICKS) listener.accept(client);
    }

    public static void fireScreenInit(Minecraft client, Screen screen, int width, int height) {
        // Rebuilt screens start over: a listener from the last build would
        // otherwise draw twice
        SCREEN_DRAWN.remove(screen);
        for (ScreenInit listener : SCREEN_INITS) listener.init(client, screen, width, height);
    }

    public static void fireScreenDrawn(Screen screen, GuiGraphicsExtractor graphics,
                                       int mouseX, int mouseY, float delta) {
        List<ScreenDrawn> listeners = SCREEN_DRAWN.get(screen);
        if (listeners == null) return;
        for (ScreenDrawn listener : List.copyOf(listeners)) {
            listener.drawn(screen, graphics, mouseX, mouseY, delta);
        }
    }

    public static void fireBeforeChat(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        for (HudLayer layer : BEFORE_CHAT) layer.draw(graphics, delta);
    }

    public static void fireAfterChat(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        for (HudLayer layer : AFTER_CHAT) layer.draw(graphics, delta);
    }

    public static void fireJoin() {
        for (Runnable listener : JOINS) {
            try {
                listener.run();
            } catch (Throwable ignored) {
                // A join is never worth interrupting
            }
        }
    }
}
