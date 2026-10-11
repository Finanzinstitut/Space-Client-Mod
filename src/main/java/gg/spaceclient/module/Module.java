package gg.spaceclient.module;

import com.google.gson.JsonObject;
import gg.spaceclient.setting.Setting;
import gg.spaceclient.setting.SettingGroup;
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
    private final List<SettingGroup> groups = new ArrayList<>();
    private boolean enabled;

    /**
     * Whether onEnable has run without a matching onDisable.
     *
     * Kept apart from {@link #enabled}, which is what the player chose: a
     * module switched on by somebody who loses the rank for it stays chosen,
     * and comes back by itself if the rank does.
     */
    private boolean active;

    private Access access = Access.EVERYONE;

    protected Module(String id, String name, String description, boolean enabledByDefault) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.enabled = enabledByDefault;
        this.active = enabledByDefault;
    }

    /** Restricts this module to a rank. Called from the module's constructor. */
    protected void requires(Access access) {
        this.access = access;
    }

    public Access getAccess() { return access; }

    /** Whether this account's rank lets it use the module. */
    public boolean hasAccess() {
        return access.allows(gg.spaceclient.net.Badges.ownRank());
    }

    /**
     * Brings onEnable and onDisable in line with choice and rank together.
     * Called whenever either may have moved - from the tick for the rank,
     * which can change while playing when the list is edited.
     */
    public void syncAccess() {
        boolean want = enabled && hasAccess();
        if (want == active) return;
        active = want;
        if (want) onEnable(); else onDisable();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<Setting> getSettings() { return settings; }

    /** Settings split into sub-screens, for modules with too many to list flat. */
    public List<SettingGroup> getGroups() { return groups; }

    /** True when there is anything at all to configure. */
    public boolean hasSettings() {
        return !settings.isEmpty() || !groups.isEmpty();
    }
    /** On, and allowed to be. What every feature asks before doing its work. */
    public boolean isEnabled() { return enabled && hasAccess(); }

    public void setEnabled(boolean enabled) {
        // Switching on without the rank does nothing; switching off always works
        if (enabled && !hasAccess()) return;
        this.enabled = enabled;
        syncAccess();
    }

    public void toggle() {
        setEnabled(!this.enabled);
    }

    /** Called once when the module is switched on. */
    protected void onEnable() {}

    /** Called once when switched off - undo anything global here. */
    protected void onDisable() {}

    protected void addSettings(Setting... toAdd) {
        for (Setting s : toAdd) settings.add(s);
    }

    protected void addGroups(SettingGroup... toAdd) {
        for (SettingGroup g : toAdd) {
            groups.add(g);
            // Grouped settings still take part in saving and loading
            settings.addAll(g.settings());
        }
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
        if (json.has("enabled")) {
            // Set the field first so a hook that reads isEnabled() sees the
            // final state, then fire the hook itself. Kept even without the
            // rank: the choice is the player's, the rank only pauses it.
            enabled = json.get("enabled").getAsBoolean();
            syncAccess();
        }
        if (json.has("settings")) {
            JsonObject settingsJson = json.getAsJsonObject("settings");
            for (Setting s : settings) s.load(settingsJson);
        }
    }
}
