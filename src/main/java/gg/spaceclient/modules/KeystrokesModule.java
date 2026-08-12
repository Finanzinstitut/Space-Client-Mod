package gg.spaceclient.modules;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import gg.spaceclient.setting.ModeSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows which keys are being pressed.
 *
 * Three layouts, as originally intended:
 *  - KEYBINDS: the movement block plus mouse buttons and space
 *  - FULL:     every bound control the game has, laid out as a grid
 *  - CUSTOM:   only the controls listed in customKeys
 *
 * Everything reads Minecraft's own key bindings rather than raw keyboard state,
 * so the display follows whatever the player has rebound their controls to, and
 * a key shows as pressed whichever physical key it currently sits on.
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
            "pressed_color", "Pressed colour", "Colour of a key while held", 0xFFFFFFFF);

    private final ColorSetting idleColor = new ColorSetting(
            "idle_color", "Idle colour", "Colour of a key at rest", 0x80404040);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the key labels", 0xFFFFFFFF);

    /** Comma separated control names for CUSTOM mode. */
    private String customKeys = "W,A,S,D,SPACE,SHIFT";

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", "Visualise your inputs in real time", 0.02f, 0.55f, true);
        addSettings(layout, showMouse, showCps, pressedColor, idleColor, textColor);
        // Each key already draws its own plate; a module-wide one behind them
        // would just add a second grey box.
        setBackgroundEnabled(false);
    }

    public String getCustomKeys() { return customKeys; }
    public void setCustomKeys(String keys) { this.customKeys = keys; }

    public List<String> availableKeys() {
        return new ArrayList<>(bindings().keySet());
    }

    /** Every control this module can display, by short label. */
    private Map<String, KeyMapping> bindings() {
        Map<String, KeyMapping> map = new LinkedHashMap<>();
        if (mc.options == null) return map;

        map.put("W", mc.options.keyUp);
        map.put("A", mc.options.keyLeft);
        map.put("S", mc.options.keyDown);
        map.put("D", mc.options.keyRight);
        map.put("SPACE", mc.options.keyJump);
        map.put("SHIFT", mc.options.keyShift);
        map.put("CTRL", mc.options.keySprint);
        map.put("LMB", mc.options.keyAttack);
        map.put("RMB", mc.options.keyUse);
        map.put("DROP", mc.options.keyDrop);
        map.put("INV", mc.options.keyInventory);
        map.put("SWAP", mc.options.keySwapOffhand);
        map.put("PICK", mc.options.keyPickItem);
        map.put("CHAT", mc.options.keyChat);
        map.put("TAB", mc.options.keyPlayerList);
        return map;
    }

    private boolean isPressed(String label) {
        KeyMapping mapping = bindings().get(label);
        return mapping != null && mapping.isDown();
    }

    /** label, grid row, grid column, width in cells. */
    private record KeyCell(String label, int row, int col, int width) {}

    private List<KeyCell> buildCells() {
        List<KeyCell> cells = new ArrayList<>();

        if (layout.is("CUSTOM")) {
            Map<String, KeyMapping> known = bindings();
            int col = 0;
            int row = 0;
            for (String raw : customKeys.split(",")) {
                String label = raw.trim().toUpperCase();
                if (label.isEmpty() || !known.containsKey(label)) continue;

                int width = label.length() > 3 ? 2 : 1;
                if (col + width > 4) { col = 0; row++; }
                cells.add(new KeyCell(label, row, col, width));
                col += width;
            }
            return cells;
        }

        if (layout.is("FULL")) {
            // Movement block on top, then every other bound control in rows of four
            cells.add(new KeyCell("W", 0, 1, 1));
            cells.add(new KeyCell("A", 1, 0, 1));
            cells.add(new KeyCell("S", 1, 1, 1));
            cells.add(new KeyCell("D", 1, 2, 1));
            cells.add(new KeyCell("LMB", 2, 0, 1));
            cells.add(new KeyCell("RMB", 2, 2, 1));
            cells.add(new KeyCell("SPACE", 3, 0, 3));

            String[] extra = {"SHIFT", "CTRL", "DROP", "INV", "SWAP", "PICK", "CHAT", "TAB"};
            int col = 0;
            int row = 4;
            for (String label : extra) {
                if (col >= 3) { col = 0; row++; }
                cells.add(new KeyCell(label, row, col, 1));
                col++;
            }
            return cells;
        }

        // KEYBINDS
        cells.add(new KeyCell("W", 0, 1, 1));
        cells.add(new KeyCell("A", 1, 0, 1));
        cells.add(new KeyCell("S", 1, 1, 1));
        cells.add(new KeyCell("D", 1, 2, 1));

        int row = 2;
        if (showMouse.get()) {
            cells.add(new KeyCell("LMB", row, 0, 1));
            cells.add(new KeyCell("RMB", row, 2, 1));
            row++;
        }
        cells.add(new KeyCell("SPACE", row, 0, 3));
        return cells;
    }

    private int gridColumns() {
        int max = 1;
        for (KeyCell cell : buildCells()) {
            max = Math.max(max, cell.col() + cell.width());
        }
        return max;
    }

    private int gridRows() {
        int max = 0;
        for (KeyCell cell : buildCells()) max = Math.max(max, cell.row());
        return max + 1;
    }

    @Override
    public int getWidth() { return gridColumns() * (KEY_SIZE + GAP) - GAP; }

    @Override
    public int getHeight() { return gridRows() * (KEY_SIZE + GAP) - GAP; }

    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        CpsModule cps = showCps.get() ? CpsModule.getInstance() : null;

        for (KeyCell cell : buildCells()) {
            int cellX = x + cell.col() * (KEY_SIZE + GAP);
            int cellY = y + cell.row() * (KEY_SIZE + GAP);
            int cellW = cell.width() * (KEY_SIZE + GAP) - GAP;

            boolean pressed = isPressed(cell.label());
            graphics.fill(cellX, cellY, cellX + cellW, cellY + KEY_SIZE,
                    pressed ? pressedColor.get() : idleColor.get());

            String label = cell.label();
            if (cps != null && label.equals("LMB")) label = String.valueOf(cps.getLeftCps());
            if (cps != null && label.equals("RMB")) label = String.valueOf(cps.getRightCps());
            if (label.equals("SPACE") && cell.width() >= 3) label = "___";

            // Pressed keys invert so the label stays readable on the bright fill
            int color = pressed ? 0xFF202020 : textColor.get();

            int textX = cellX + (cellW - mc.font.width(label)) / 2;
            int textY = cellY + (KEY_SIZE - mc.font.lineHeight) / 2;
            graphics.text(mc.font, label, textX, textY, color, false);
        }
    }
}
