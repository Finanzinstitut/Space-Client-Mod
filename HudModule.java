package gg.spaceclient.module;

import com.google.gson.JsonObject;
import gg.spaceclient.setting.BooleanSetting;
import gg.spaceclient.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A module that draws on the HUD. Position is a fraction of the screen so
 * elements keep their place when the window is resized.
 */
public abstract class HudModule extends Module {
    private float xPercent;
    private float yPercent;

    /**
     * HUD elements get their own look, separate from the menu: a plain grey
     * plate behind the content so the readout stays legible over any terrain.
     */
    private final BooleanSetting background = new BooleanSetting(
            "background", "Background", "Draw a grey plate behind this element", true);

    private final ColorSetting backgroundColor = new ColorSetting(
            "background_color", "Background colour", "Colour of the plate", 0x80404040);

    protected HudModule(String id, String name, String description,
                        float defaultX, float defaultY, boolean enabledByDefault) {
        super(id, name, description, enabledByDefault);
        this.xPercent = defaultX;
        this.yPercent = defaultY;
        addSettings(background, backgroundColor);
    }

    /** Lets a module opt out of the plate when it draws its own. */
    protected void setBackgroundEnabled(boolean enabled) {
        background.set(enabled);
    }

    /** Width and height of the drawn content, used to size the plate. */
    public abstract int getWidth();
    public abstract int getHeight();

    /**
     * Draws the plate, then the element itself. Subclasses implement render();
     * this is what the HUD loop calls.
     */
    public void draw(GuiGraphicsExtractor graphics, int x, int y) {
        if (background.get()) {
            int padding = 3;
            graphics.fill(
                    x - padding, y - padding,
                    x + getWidth() + padding, y + getHeight() + padding,
                    backgroundColor.get());
        }
        render(graphics, x, y);
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
