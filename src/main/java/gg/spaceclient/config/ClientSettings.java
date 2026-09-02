package gg.spaceclient.config;

import com.google.gson.JsonObject;
import gg.spaceclient.setting.ColorSetting;

import java.util.Arrays;
import java.util.List;

/**
 * Interface-wide settings, kept separate from the per-module ones because they
 * apply to the menu itself rather than to anything in the world.
 */
public class ClientSettings {
    /**
     * The photographs come after the drawn styles on purpose.
     *
     * Cycling starts at the cheap ones, so somebody who just wants a plain
     * background never has to page through three images to reach it.
     */
    public static final List<String> BACKGROUND_STYLES =
            Arrays.asList("SPACE", "DARK", "SOLID_BLACK", "TRANSPARENT",
                    "AURORA", "NEBULA", "BLACK_HOLE", "GALAXY");

    private String backgroundStyle = "SPACE";

    /**
     * The accent lives in a ColorSetting so the same colour wheel widget the
     * modules use can drive it too.
     */
    private final ColorSetting accent = new ColorSetting(
            "accent", "Accent colour", "Colour used across the interface", 0xFF7C5CFF);
    // The launcher's violet, so the in-game menu matches it out of the box.
    public String backgroundStyle() { return backgroundStyle; }
    public int accentColor() { return accent.get(); }
    public ColorSetting accentSetting() { return accent; }

    public void cycleBackground() {
        int index = BACKGROUND_STYLES.indexOf(backgroundStyle);
        backgroundStyle = BACKGROUND_STYLES.get((index + 1) % BACKGROUND_STYLES.size());
    }

    public int red()   { return accent.getRed(); }
    public int green() { return accent.getGreen(); }
    public int blue()  { return accent.getBlue(); }

    public void setChannels(int r, int g, int b) {
        accent.setComponents(255, r, g, b);
    }

    public void save(JsonObject json) {
        json.addProperty("background_style", backgroundStyle);
        json.addProperty("accent_color", accent.get());
    }

    public void load(JsonObject json) {
        if (json.has("background_style")) {
            String value = json.get("background_style").getAsString();
            if (BACKGROUND_STYLES.contains(value)) backgroundStyle = value;
        }
        if (json.has("accent_color")) {
            accent.set(json.get("accent_color").getAsInt());
        }
    }
}
