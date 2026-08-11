package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shows which keys are being pressed.
 *
 * Three layouts:
 *  - KEYBINDS: just the movement keys plus the two mouse buttons (the classic view)
 *  - FULL:     a whole QWERTY keyboard
 *  - CUSTOM:   only the keys listed in the custom key setting
 */
public class KeystrokesModule extends HudModule {
    private static final int KEY_SIZE = 22;
    private static final int GAP = 2;

    private final ModeSetting layout = new ModeSetting(
            "layout", "Layout", "Which keys to display",
            Arrays.asList("KEYBINDS", "FULL", "CUSTOM"), "KEYBINDS");

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

    /** Comma separated key names for CUSTOM mode, e.g. "W,A,S,D,SPACE,F,Q". */
    private String customKeys = "W,A,S,D,SPACE";

    private static final String[][] FULL_LAYOUT = {
            {"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"},
            {"A", "S", "D", "F", "G", "H", "J", "K", "L"},
            {"Z", "X", "C", "V", "B", "N", "M"},
            {"SHIFT", "CTRL", "SPACE"}
    };

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", "Visualise your keyboard inputs in real time", 0.02f, 0.55f);
        addSettings(layout, showMouse, showCps, pressedColor, idleColor, textColor);
    }

    public String getCustomKeys() { return customKeys; }
    public void setCustomKeys(String keys) { this.customKeys = keys; }

    /** One drawable key: label, its position in the grid, and how to test it. */
    private record KeyCell(String label, int row, int col, int width, String binding) {}

    private List<KeyCell> buildCells() {
        List<KeyCell> cells = new ArrayList<>();

        if (layout.is("FULL")) {
            for (int row = 0; row < FULL_LAYOUT.length; row++) {
                String[] keys = FULL_LAYOUT[row];
                for (int col = 0; col < keys.length; col++) {
                    String key = keys[col];
                    int width = key.equals("SPACE") ? 4 : key.length() > 1 ? 2 : 1;
                    cells.add(new KeyCell(key, row, col, width, key));
                }
            }
        } else if (layout.is("CUSTOM")) {
            String[] keys = customKeys.split(",");
            int col = 0;
            int row = 0;
            for (String raw : keys) {
                String key = raw.trim().toUpperCase();
                if (key.isEmpty()) continue;
                cells.add(new KeyCell(key, row, col, key.length() > 1 ? 2 : 1, key));
                col += key.length() > 1 ? 2 : 1;
                if (col >= 6) { col = 0; row++; }
            }
        } else {
            // Classic WASD block
            cells.add(new KeyCell("W", 0, 1, 1, "W"));
            cells.add(new KeyCell("A", 1, 0, 1, "A"));
            cells.add(new KeyCell("S", 1, 1, 1, "S"));
            cells.add(new KeyCell("D", 1, 2, 1, "D"));
            if (showMouse.get()) {
                cells.add(new KeyCell("LMB", 2, 0, 1, "LMB"));
                cells.add(new KeyCell("RMB", 2, 2, 1, "RMB"));
                cells.add(new KeyCell("SPACE", 3, 0, 3, "SPACE"));
            } else {
                cells.add(new KeyCell("SPACE", 2, 0, 3, "SPACE"));
            }
        }
        return cells;
    }

    /** Reads the live pressed state for a cell. */
    private boolean isPressed(String binding) {
        if (mc.options == null) return false;

        switch (binding) {
            case "LMB": return mc.options.keyAttack.isDown();
            case "RMB": return mc.options.keyUse.isDown();
            case "W": return mc.options.keyUp.isDown();
            case "A": return mc.options.keyLeft.isDown();
            case "S": return mc.options.keyDown.isDown();
            case "D": return mc.options.keyRight.isDown();
            case "SPACE": return mc.options.keyJump.isDown();
            case "SHIFT": return mc.options.keyShift.isDown();
            case "CTRL": return mc.options.keySprint.isDown();
            default:
                // Anything else is looked up as a raw keyboard key
                if (binding.length() == 1) {
                    InputConstants.Key key = InputConstants.getKey("key.keyboard." + binding.toLowerCase());
                    return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getCode());
                }
                return false;
        }
    }

    private int gridColumns() {
        if (layout.is("FULL")) return 10;
        if (layout.is("CUSTOM")) return 6;
        return 3;
    }

    private int gridRows() {
        if (layout.is("FULL")) return FULL_LAYOUT.length;
        int max = 0;
        for (KeyCell c : buildCells()) max = Math.max(max, c.row());
        return max + 1;
    }

    @Override
    public int getWidth() {
        return gridColumns() * (KEY_SIZE + GAP) - GAP;
    }

    @Override
    public int getHeight() {
        return gridRows() * (KEY_SIZE + GAP) - GAP;
    }

    @Override
    public void render(GuiGraphics context, int x, int y) {
        CpsModule cps = null;
        if (showCps.get()) {
            cps = CpsModule.getInstance();
        }

        for (KeyCell cell : buildCells()) {
            int cellX = x + cell.col() * (KEY_SIZE + GAP);
            int cellY = y + cell.row() * (KEY_SIZE + GAP);
            int cellW = cell.width() * (KEY_SIZE + GAP) - GAP;

            boolean pressed = isPressed(cell.binding());
            int bg = pressed ? pressedColor.get() : idleColor.get();
            context.fill(cellX, cellY, cellX + cellW, cellY + KEY_SIZE, bg);

            // A pressed key gets a thin border so it reads clearly on any background
            if (pressed) {
                int border = 0xFFFFFFFF;
                context.fill(cellX, cellY, cellX + cellW, cellY + 1, border);
                context.fill(cellX, cellY + KEY_SIZE - 1, cellX + cellW, cellY + KEY_SIZE, border);
                context.fill(cellX, cellY, cellX + 1, cellY + KEY_SIZE, border);
                context.fill(cellX + cellW - 1, cellY, cellX + cellW, cellY + KEY_SIZE, border);
            }

            String label = cell.label();
            if (cps != null && cell.binding().equals("LMB")) label = cps.getLeftCps() + " CPS";
            if (cps != null && cell.binding().equals("RMB")) label = cps.getRightCps() + " CPS";
            if (label.equals("SPACE") && cell.width() >= 3) label = "___";

            int textWidth = mc.font.width(label);
            int textX = cellX + (cellW - textWidth) / 2;
            int textY = cellY + (KEY_SIZE - mc.font.lineHeight) / 2;
            context.drawString(mc.font, label, textX, textY, textColor.get(), true);
        }
    }
}
