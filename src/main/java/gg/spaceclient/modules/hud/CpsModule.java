package gg.spaceclient.modules.hud;

import gg.spaceclient.module.HudModule;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Clicks per second. Other modules read the counts from here through
 * getInstance(), so the click history only has to be tracked once.
 */
public class CpsModule extends HudModule {
    private static CpsModule instance;

    private final Deque<Long> leftClicks = new ArrayDeque<>();
    private final Deque<Long> rightClicks = new ArrayDeque<>();
    private boolean leftWasDown;
    private boolean rightWasDown;

    private final BooleanSetting showRight = new BooleanSetting(
            "show_right", "Show right clicks", "Also count the right mouse button", true);

    private final ColorSetting textColor = new ColorSetting(
            "text_color", "Text colour", "Colour of the counter", 0xFFFFFFFF);

    public CpsModule() {
        super("cps", "CPS", "Shows your clicks per second", 0.02f, 0.20f);
        addSettings(showRight, textColor);
        instance = this;
    }

    /** May be null if the module was never constructed. */
    public static CpsModule getInstance() {
        return instance;
    }

    private void prune(Deque<Long> clicks, long now) {
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
            clicks.pollFirst();
        }
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        long now = System.currentTimeMillis();

        boolean left = mc.options.keyAttack.isDown();
        boolean right = mc.options.keyUse.isDown();

        // Count edges, not held frames, otherwise holding a button reads as spam
        if (left && !leftWasDown) leftClicks.addLast(now);
        if (right && !rightWasDown) rightClicks.addLast(now);
        leftWasDown = left;
        rightWasDown = right;

        prune(leftClicks, now);
        prune(rightClicks, now);
    }

    public int getLeftCps() {
        prune(leftClicks, System.currentTimeMillis());
        return leftClicks.size();
    }

    public int getRightCps() {
        prune(rightClicks, System.currentTimeMillis());
        return rightClicks.size();
    }

    private String text() {
        return showRight.get()
                ? getLeftCps() + " | " + getRightCps() + " CPS"
                : getLeftCps() + " CPS";
    }

    @Override
    public int getWidth() { return mc.font.width(text()); }

    @Override
    public int getHeight() { return mc.font.lineHeight; }

    @Override
    public void render(GuiGraphics context, int x, int y) {
        context.drawString(mc.font, text(), x, y, textColor.get(), true);
    }
}
