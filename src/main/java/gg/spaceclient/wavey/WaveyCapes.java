/*
 * Glue for the WaveyCapes port (see CapeHolder for the attribution).
 * Space Client code, written for Minecraft 26.2.
 */
package gg.spaceclient.wavey;

import gg.spaceclient.wavey.config.Config;
import gg.spaceclient.wavey.render.CapeNodeCollector;

/**
 * Where the port keeps what WaveyCapes kept in its mod class: the settings and
 * the one cape collector. The settings are filled from the Wavey Cape module
 * rather than from WaveyCapes' own config file.
 */
public final class WaveyCapes {
    private WaveyCapes() {}

    public static Config config = new Config();

    /** Whether the module is on; the layer and the simulation both stand down otherwise. */
    public static volatile boolean enabled = false;

    private static final CapeNodeCollector COLLECTOR = new CapeNodeCollector();

    public static CapeNodeCollector collector() { return COLLECTOR; }
}
