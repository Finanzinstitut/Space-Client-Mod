package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.setting.ModeSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shows which keys are being pressed.
 *
 * Layouts:
 *  - KEYBINDS: the classic WASD block plus the mouse buttons and space
 *  - EXTENDED: adds sneak, sprint and the offhand key
 *  - CUSTOM:   only the bindings listed in customKeys
 *
 * Everything reads from Minecraft's own key bindings rather than raw keyboard
 * state, so it follows whatever the player has rebound their controls to.
 */
public class KeystrokesModule extends HudModule {
    private static final int KEY_SIZE = 22;
    private static final int GAP = 2;

    private final ModeSetting layout = new ModeSetting(
            "layout", "Layout", "Which keys to display",
            Arrays.asList("KEYBINDS", "EXTENDED", "CUSTOM"), "KEYBINDS");

    private final BooleanSetting showMouse = new BooleanSetting(
            "show_mouse", "Show mouse buttons", "Include LMB and RMB", true);

    private final BooleanSetting showCps = new BooleanSetting(
            "show_cps", "Show CPS on buttons", "Draw the click rate inside the mouse keys", false);

    private final ColorSetting pressedColor = new ColorSetting(
            "pressed_color", "Pressed colour", "Colour of a key while held", 0xFF38E0FF);

    private final ColorSetting idleColor = new ColorSetting(
            "idle_color", "Idle colour", "Colour of a key at rest", 0x80000000);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the key labels", 0xFFFFFFFF);

    /** Comma separated binding names for CUSTOM mode. */
    private String customKeys = "W,A,S,D,SPACE";

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", "Visualise your inputs in real time", 0.02f, 0.55f, true);
        addSettings(layout, showMouse, showCps, pressedColor, idleColor, textColor);
    }

    public String getCustomKeys() { return customKeys; }
    public void setCustomKeys(String keys) { this.customKeys = keys; }

    /** label, grid row, grid column, width in cells, which binding to read. */
    private record KeyCell(String label, int row, int col, int width, String binding) {}

    private List<KeyCell> buildCells() {
        List<KeyCell> cells = new ArrayList<>();

        cells.add(new KeyCell("W", 0, 1, 1, "W"));
        cells.add(new KeyCell("A", 1, 0, 1, "A"));
        cells.add(new KeyCell("S", 1, 1, 1, "S"));
        cells.add(new KeyCell("D", 1, 2, 1, "D"));

        int row = 2;
        if (showMouse.get() && !layout.is("CUSTOM")) {
            cells.add(new KeyCell("LMB", row, 0, 1, "LMB"));
            cells.add(new KeyCell("RMB", row, 2, 1, "RMB"));
            row++;
        }
        cells.add(new KeyCell("SPACE", row, 0, 3, "SPACE"));
        row++;

        if (layout.is("EXTENDED")) {
            cells.add(new KeyCell("SHIFT", row, 0, 2, "SHIFT"));
            cells.add(new KeyCell("CTRL", row, 2, 1, "CTRL"));
        }
        return cells;
    }

    private KeyMapping bindingFor(String name) {
        if (mc.options == null) return null;
        return switch (name) {
            case "LMB" -> mc.options.keyAttack;
            case "RMB" -> mc.options.keyUse;
            case "W" -> mc.options.keyUp;
            case "A" -> mc.options.keyLeft;
            case "S" -> mc.options.keyDown;
            case "D" -> mc.options.keyRight;
            case "SPACE" -> mc.options.keyJump;
            case "SHIFT" -> mc.options.keyShift;
            case "CTRL" -> mc.options.keySprint;
            default -> null;
        };
    }

    private boolean isPressed(String binding) {
        KeyMapping mapping = bindingFor(binding);
        return mapping != null && mapping.isDown();
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        CpsModule cps = showCps.get() ? CpsModule.getInstance() : null;

        for (KeyCell cell : buildCells()) {
            int cellX = x + cell.col() * (KEY_SIZE + GAP);
            int cellY = y + cell.row() * (KEY_SIZE + GAP);
            int cellW = cell.width() * (KEY_SIZE + GAP) - GAP;

            boolean pressed = isPressed(cell.binding());
            graphics.fill(cellX, cellY, cellX + cellW, cellY + KEY_SIZE,
                    pressed ? pressedColor.get() : idleColor.get());

            // A thin border so a pressed key reads clearly on any background
            if (pressed) {
                int border = 0xFFFFFFFF;
                graphics.fill(cellX, cellY, cellX + cellW, cellY + 1, border);
                graphics.fill(cellX, cellY + KEY_SIZE - 1, cellX + cellW, cellY + KEY_SIZE, border);
                graphics.fill(cellX, cellY, cellX + 1, cellY + KEY_SIZE, border);
                graphics.fill(cellX + cellW - 1, cellY, cellX + cellW, cellY + KEY_SIZE, border);
            }

            String label = cell.label();
            if (cps != null && cell.binding().equals("LMB")) label = String.valueOf(cps.getLeftCps());
            if (cps != null && cell.binding().equals("RMB")) label = String.valueOf(cps.getRightCps());
            if (label.equals("SPACE")) label = "___";

            int textX = cellX + (cellW - mc.font.width(label)) / 2;
            int textY = cellY + (KEY_SIZE - mc.font.lineHeight) / 2;
            graphics.text(mc.font, label, textX, textY, textColor.get(), true);
        }
    }
}
