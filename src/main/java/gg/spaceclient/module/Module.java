package gg.spaceclient.module;

import com.google.gson.JsonObject;
import gg.spaceclient.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** A single toggleable feature. */
public abstract class Module {
    protected static final Minecraft mc = Minecraft.getInstance();

    private final String id;
    private final String name;
    private final String description;
    private final List<Setting> settings = new ArrayList<>();
    private boolean enabled;

    protected Module(String id, String name, String description, boolean enabledByDefault) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.enabled = enabledByDefault;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<Setting> getSettings() { return settings; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void toggle() { this.enabled = !this.enabled; }

    protected void addSettings(Setting... toAdd) {
        for (Setting s : toAdd) settings.add(s);
    }

    /** Called every client tick while enabled. */
    public void onTick() {}

    public void save(JsonObject json) {
        json.addProperty("enabled", enabled);
        JsonObject settingsJson = new JsonObject();
        for (Setting s : settings) s.save(settingsJson);
        json.add("settings", settingsJson);
    }

    public void load(JsonObject json) {
        if (json.has("enabled")) enabled = json.get("enabled").getAsBoolean();
        if (json.has("settings")) {
            JsonObject settingsJson = json.getAsJsonObject("settings");
            for (Setting s : settings) s.load(settingsJson);
        }
    }
}
