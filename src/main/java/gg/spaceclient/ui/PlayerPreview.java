package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.lang.reflect.Method;

/**
 * Draws the player, turned to whatever angle the wardrobe asks for.
 *
 * Found by reflection rather than called directly, and that is a deliberate
 * choice rather than laziness. This version rebuilt the render pipeline - the
 * helper that draws an entity into a screen has moved or changed shape in
 * every recent version, and this codebase has already lost a build to guessing
 * at a renamed render method. Reflection turns a wrong guess into a blank panel
 * instead of a project that will not compile.
 *
 * When the lookup fails the bay says so plainly, which is more use than an
 * empty rectangle: it tells you the preview is missing a hook rather than that
 * your cosmetics are not loading.
 */
public final class PlayerPreview {

    private static boolean searched = false;
    private static Method renderer = null;
    private static String failure = null;

    private PlayerPreview() {}

    /**
     * Looks for the entity-in-screen helper once and remembers the outcome.
     *
     * Names tried in order of how recently they were used, since a hit on the
     * first is the common case and each miss costs a class scan.
     */
    private static synchronized void find() {
        if (searched) return;
        searched = true;

        String[] classes = {
                "net.minecraft.client.gui.screens.inventory.InventoryScreen",
                "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen",
        };
        // This version renamed the family from render* to extract*, which is
        // why a list of the old names found nothing. The new name goes first;
        // the rest stay as a cushion for whatever the next version calls it.
        String[] names = {
                "extractEntityInInventoryFollowsMouse",
                "extractEntityInInventory",
                "renderEntityInInventoryFollowsMouse",
                "renderEntityInInventory",
        };

        for (String className : classes) {
            Class<?> owner;
            try {
                owner = Class.forName(className);
            } catch (Throwable ignored) {
                continue;
            }
            for (Method method : owner.getMethods()) {
                for (String name : names) {
                    if (!method.getName().equals(name)) continue;
                    renderer = method;
                    SpaceClient.LOGGER.info("Player preview will use {}.{}",
                            owner.getSimpleName(), method.getName());
                    return;
                }
            }
        }

        failure = "preview unavailable on this version";
        SpaceClient.LOGGER.warn("No entity-in-screen renderer found for the wardrobe preview");
    }

    /** True when a live model can be drawn, so callers can lay out accordingly. */
    public static boolean available() {
        find();
        return renderer != null;
    }

    public static void draw(GuiGraphicsExtractor graphics,
                            int x1, int y1, int x2, int y2, float spin) {
        find();

        Minecraft mc = Minecraft.getInstance();
        if (renderer == null || mc.player == null) {
            String message = mc.player == null ? "join a world to preview" : failure;
            if (message != null) {
                graphics.text(mc.font, message,
                        x1 + (x2 - x1 - mc.font.width(message)) / 2, (y1 + y2) / 2 - 4,
                        Theme.TEXT_DIM, false);
            }
            return;
        }

        // The helper turns the model toward a point, so the drag is handed to
        // it as a synthetic pointer position rather than as an angle.
        int scale = Math.max(20, Math.min((y2 - y1) / 2, 46));
        float synthetic = spin;

        try {
            Object[] args = buildArgs(graphics, x1 + 6, y1 + 6, x2 - 6, y2 - 6,
                    scale, synthetic, mc);
            if (args == null) {
                renderer = null;
                failure = "preview shape changed on this version";
                return;
            }
            renderer.invoke(null, args);
        } catch (Throwable t) {
            renderer = null;
            failure = "preview failed on this version";
            SpaceClient.LOGGER.warn("Player preview call failed, disabling it", t);
        }
    }

    /**
     * Fills the parameter list for the follows-mouse helper.
     *
     * Its shape on this version is (graphics, x1, y1, x2, y2, scale, yOffset,
     * mouseX, mouseY, entity), read off the class file rather than guessed.
     * Filling by position against that shape, and refusing anything that does
     * not match it, is safer than trying to be clever about unknown orders.
     */
    private static Object[] buildArgs(GuiGraphicsExtractor graphics,
                                      int x1, int y1, int x2, int y2, int scale,
                                      float mouseX, Minecraft mc) {
        Class<?>[] types = renderer.getParameterTypes();
        if (types.length != 10) return null;

        return new Object[]{
                graphics, x1, y1, x2, y2, scale,
                0f,        // yOffset - the model sits on the floor of the bay
                mouseX,    // drives the turn
                0f,        // vertical look stays level
                mc.player
        };
    }
}
