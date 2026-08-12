package gg.spaceclient.config;

import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;

/**
 * Interface-wide settings, kept separate from the per-module ones because they
 * apply to the menu itself rather than to anything in the world.
 */
public class ClientSettings {
    public static final List<String> BACKGROUND_STYLES =
            Arrays.asList("SPACE", "DARK", "SOLID_BLACK", "TRANSPARENT");

    private String backgroundStyle = "SPACE";
    // The launcher's violet, so the in-game menu matches it out of the box.
    private int accentColor = 0xFF7C5CFF;

    public String backgroundStyle() { return backgroundStyle; }
    public int accentColor() { return accentColor; }

    public void cycleBackground() {
        int index = BACKGROUND_STYLES.indexOf(backgroundStyle);
        backgroundStyle = BACKGROUND_STYLES.get((index + 1) % BACKGROUND_STYLES.size());
    }

    public int red()   { return (accentColor >> 16) & 0xFF; }
    public int green() { return (accentColor >> 8) & 0xFF; }
    public int blue()  { return accentColor & 0xFF; }

    public void setChannels(int r, int g, int b) {
        accentColor = 0xFF000000
                | ((r & 0xFF) << 16)
                | ((g & 0xFF) << 8)
                | (b & 0xFF);
    }

    public void save(JsonObject json) {
        json.addProperty("background_style", backgroundStyle);
        json.addProperty("accent_color", accentColor);
    }

    public void load(JsonObject json) {
        if (json.has("background_style")) {
            String value = json.get("background_style").getAsString();
            if (BACKGROUND_STYLES.contains(value)) backgroundStyle = value;
        }
        if (json.has("accent_color")) {
            accentColor = json.get("accent_color").getAsInt();
        }
    }
}
