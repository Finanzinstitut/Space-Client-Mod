package gg.spaceclient.module;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A module that draws something on the HUD. Position is stored as a fraction of
 * the screen so an element keeps its place when the window is resized or the
 * player switches monitors.
 */
public abstract class HudModule extends Module {
    private float xPercent;
    private float yPercent;
    private float scale = 1.0f;

    protected HudModule(String id, String name, String description, float defaultX, float defaultY) {
        super(id, name, description, Category.HUD);
        this.xPercent = defaultX;
        this.yPercent = defaultY;
    }

    public float getXPercent() { return xPercent; }
    public float getYPercent() { return yPercent; }
    public float getScale() { return scale; }

    public void setPosition(float x, float y) {
        this.xPercent = Math.max(0f, Math.min(1f, x));
        this.yPercent = Math.max(0f, Math.min(1f, y));
    }

    public void setScale(float scale) {
        this.scale = Math.max(0.5f, Math.min(3.0f, scale));
    }

    public int getX(int screenWidth) { return (int) (xPercent * screenWidth); }
    public int getY(int screenHeight) { return (int) (yPercent * screenHeight); }

    /** Size of the element, used by the HUD editor for its drag handles. */
    public abstract int getWidth();
    public abstract int getHeight();

    /** Draw at the given screen position. Scaling is applied by the caller. */
    public abstract void render(GuiGraphics context, int x, int y);

    @Override
    public void save(JsonObject json) {
        super.save(json);
        json.addProperty("x", xPercent);
        json.addProperty("y", yPercent);
        json.addProperty("scale", scale);
    }

    @Override
    public void load(JsonObject json) {
        super.load(json);
        if (json.has("x")) xPercent = json.get("x").getAsFloat();
        if (json.has("y")) yPercent = json.get("y").getAsFloat();
        if (json.has("scale")) scale = json.get("scale").getAsFloat();
    }
}
