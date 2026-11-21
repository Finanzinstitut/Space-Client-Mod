package gg.spaceclient.setting;

import com.google.gson.JsonObject;

/**
 * A row that opens something rather than holding a value: Drop Lock's item
 * grid, say. Drawn like a settings group, a row with an arrow, and saves
 * nothing.
 */
public class ActionSetting extends Setting {

    private final Runnable action;

    public ActionSetting(String id, String name, String description, Runnable action) {
        super(id, name, description);
        this.action = action;
    }

    public void run() { action.run(); }

    @Override
    public void save(JsonObject json) {}

    @Override
    public void load(JsonObject json) {}
}
