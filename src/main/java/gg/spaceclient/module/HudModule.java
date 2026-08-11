package gg.spaceclient.module;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A module that draws on the HUD. Position is a fraction of the screen so
 * elements keep their place when the window is resized.
 */
public abstract class HudModule extends Module {
    private float xPercent;
    private float yPercent;

    protected HudModule(String id, String name, String description,
                        float defaultX, float defaultY, boolean enabledByDefault) {
        super(id, name, description, enabledByDefault);
        this.xPercent = defaultX;
        this.yPercent = defaultY;
    }

    public void setPosition(float x, float y) {
        this.xPercent = Math.max(0f, Math.min(1f, x));
        this.yPercent = Math.max(0f, Math.min(1f, y));
    }

    public int getX(int screenWidth) { return (int) (xPercent * screenWidth); }
    public int getY(int screenHeight) { return (int) (yPercent * screenHeight); }

    public abstract void render(GuiGraphicsExtractor graphics, int x, int y);

    @Override
    public void save(JsonObject json) {
        super.save(json);
        json.addProperty("x", xPercent);
        json.addProperty("y", yPercent);
    }

    @Override
    public void load(JsonObject json) {
        super.load(json);
        if (json.has("x")) xPercent = json.get("x").getAsFloat();
        if (json.has("y")) yPercent = json.get("y").getAsFloat();
    }
}
