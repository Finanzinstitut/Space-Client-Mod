package gg.spaceclient.modules.visual;

import gg.spaceclient.module.Category;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.IntSetting;

/**
 * Overrides the time of day locally.
 *
 * This is client-side only: other players still see the real time, and the sun
 * position the server uses for mob spawning does not change.
 *
 * The extra: FOLLOW_REAL_TIME maps the in-game sky to your actual clock, so
 * playing in the evening gives you an in-game evening. Nobody else does that.
 */
public class TimeChangerModule extends Module {
    private final IntSetting fixedTime = new IntSetting(
            "time", "Time of day", "0 = dawn, 6000 = noon, 18000 = midnight", 6000, 0, 23999);

    private final BooleanSetting followRealTime = new BooleanSetting(
            "follow_real", "Follow real time", "Match the sky to your actual clock", false);

    public TimeChangerModule() {
        super("timechanger", "Time Changer", "Sets the displayed time of day locally", Category.VISUAL);
        addSettings(fixedTime, followRealTime);
    }

    @Override
    public void onTick() {
        if (mc.world == null) return;

        long target;
        if (followRealTime.get()) {
            java.time.LocalTime now = java.time.LocalTime.now();
            // Minecraft day starts at 06:00 real-world equivalent
            double hours = now.getHour() + now.getMinute() / 60.0;
            target = (long) (((hours - 6 + 24) % 24) / 24.0 * 24000);
        } else {
            target = fixedTime.get();
        }

        mc.world.setTimeOfDay(target);
    }
}
