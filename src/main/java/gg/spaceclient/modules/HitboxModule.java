package gg.spaceclient.modules;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.module.Module;
import gg.spaceclient.setting.BooleanSetting;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

/**
 * Turns on Minecraft's own hitbox rendering - the same boxes and the same blue
 * eye-direction arrow you get from F3+B.
 *
 * The flag lives on the entity render dispatcher as a private boolean, so it is
 * flipped by reflection. That has a consequence worth stating plainly: this is
 * the game's own global switch, so it applies to every entity at once.
 * Per-category filters and custom colours would mean intercepting each entity
 * as it is drawn, which needs a mixin against a method whose signature has not
 * been confirmed for this version - see the README.
 */
public class HitboxModule extends Module {
    private final BooleanSetting keepOnDisable = new BooleanSetting(
            "keep_on_disable", "Leave on when disabled",
            "Do not switch the game's own hitbox view back off", false);

    private Field flagField;
    private boolean lookedUp = false;
    private boolean warned = false;

    public HitboxModule() {
        super("hitbox", "Hitbox", "Shows entity hitboxes and look directions", false);
        addSettings(keepOnDisable);
    }

    private Field flagField() {
        if (lookedUp) return flagField;
        lookedUp = true;

        Object dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (dispatcher == null) return null;

        // There is exactly one plain boolean on the dispatcher, and it is this
        // one, so matching by type avoids depending on the field's name.
        for (Field field : dispatcher.getClass().getDeclaredFields()) {
            if (field.getType() == boolean.class) {
                field.setAccessible(true);
                flagField = field;
                return field;
            }
        }
        return null;
    }

    private void setFlag(boolean value) {
        try {
            Field field = flagField();
            if (field == null) {
                if (!warned) {
                    warned = true;
                    SpaceClient.LOGGER.warn("Hitbox: no toggle found on the entity renderer");
                }
                return;
            }
            field.set(Minecraft.getInstance().getEntityRenderDispatcher(), value);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                SpaceClient.LOGGER.warn("Hitbox: could not set the toggle", t);
            }
        }
    }

    @Override
    protected void onEnable() {
        setFlag(true);
    }

    @Override
    public void onTick() {
        // Re-applied because F3+B and other code can reset it
        setFlag(true);
    }

    @Override
    protected void onDisable() {
        if (!keepOnDisable.get()) setFlag(false);
    }
}
