package gg.spaceclient.modules;

import gg.spaceclient.config.HudServerProfiles;
import gg.spaceclient.config.Profiles;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.util.CurrentServer;
import gg.spaceclient.util.Screens;

/**
 * Puts the right HUD layout on the screen when you arrive somewhere.
 *
 * Watches which server you are on and switches layouts when that changes. The
 * whole feature is the few lines in onTick; everything else here is about not
 * being annoying with them.
 */
public class ServerHudModule extends Module {

    private final BooleanSetting announce = new BooleanSetting(
            "announce", "Say so in chat",
            "One line when the layout changes", true);

    private final BooleanSetting backOnLeave = new BooleanSetting(
            "back_on_leave", "Restore on leaving",
            "Return to the layout you had before joining", true);

    public ServerHudModule() {
        super("serverhud", "Server layouts",
                "Switches the HUD layout to match the server you join", true);
        addSettings(announce, backOnLeave);
    }

    /**
     * The address seen on the previous tick.
     *
     * A sentinel rather than null to start with, because null is a real address
     * here - it means singleplayer - and starting out equal to it would skip
     * the first switch for somebody who loads straight into a world.
     */
    private static final String UNKNOWN = "?none?";
    private String lastSeen = UNKNOWN;

    /** The layout in use before a server took over, so leaving can undo it. */
    private String beforeServer = null;

    private String lastResult = "nothing yet";

    @Override
    public void onTick() {
        String address = CurrentServer.address();
        String seen = address == null ? "" : address;
        if (seen.equals(lastSeen)) return;

        boolean firstLook = UNKNOWN.equals(lastSeen);
        String leaving = lastSeen;
        lastSeen = seen;

        String wanted = HudServerProfiles.profileFor(address);

        // Arriving somewhere with nothing assigned: put back whatever was on
        // screen before, rather than leaving a server layout in the menus.
        if (wanted == null) {
            if (!firstLook && backOnLeave.get() && beforeServer != null
                    && !beforeServer.equals(Profiles.active())) {
                String back = beforeServer;
                beforeServer = null;
                switchTo(back, "back to " + back);
            } else {
                lastResult = seen.isEmpty()
                        ? "singleplayer, nothing assigned"
                        : seen + ", nothing assigned";
            }
            return;
        }

        if (wanted.equals(Profiles.active())) {
            lastResult = wanted + " already active";
            return;
        }

        // Remembered only when coming from somewhere with no assignment, so
        // hopping between two assigned servers does not overwrite the way home.
        if (!UNKNOWN.equals(leaving) && HudServerProfiles.profileFor(
                leaving.isEmpty() ? null : leaving) == null) {
            beforeServer = Profiles.active();
        }

        switchTo(wanted, wanted);
    }

    private void switchTo(String profile, String detail) {
        try {
            Profiles.switchTo(profile);
            lastResult = "switched to " + detail;
            if (announce.get()) {
                Screens.chat("[Space Client] Layout: " + profile);
            }
        } catch (Throwable t) {
            // A layout that will not load must not take the join down with it
            lastResult = "could not switch to " + profile;
        }
    }

    /** What the switcher last did, for the diagnostics screen. */
    public String status() {
        int assigned = HudServerProfiles.describe().size();
        return lastResult + " (" + assigned + " assigned)";
    }
}
