package gg.spaceclient.module;

import com.google.gson.JsonObject;
import gg.spaceclient.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * A single toggleable feature. Subclasses add their settings in the constructor
 * and override the hooks they care about.
 */
public abstract class Module {
    protected static final Minecraft mc = Minecraft.getInstance();

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting> settings = new ArrayList<>();

    private boolean enabled;

    protected Module(String id, String name, String description, Category category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public List<Setting> getSettings() { return settings; }
    public boolean isEnabled() { return enabled; }

    protected void addSettings(Setting... toAdd) {
        for (Setting s : toAdd) settings.add(s);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /** Called once when the module is switched on. */
    protected void onEnable() {}

    /** Called once when the module is switched off - undo anything global here. */
    protected void onDisable() {}

    /** Called every client tick while enabled. */
    public void onTick() {}

    public void save(JsonObject json) {
        json.addProperty("enabled", enabled);
        JsonObject settingsJson = new JsonObject();
        for (Setting s : settings) s.save(settingsJson);
        json.add("settings", settingsJson);
    }

    public void load(JsonObject json) {
        if (json.has("enabled")) {
            // Set the field directly so onEnable runs through setEnabled below
            boolean shouldEnable = json.get("enabled").getAsBoolean();
            if (json.has("settings")) {
                JsonObject settingsJson = json.getAsJsonObject("settings");
                for (Setting s : settings) s.load(settingsJson);
            }
            setEnabled(shouldEnable);
        }
    }
}
