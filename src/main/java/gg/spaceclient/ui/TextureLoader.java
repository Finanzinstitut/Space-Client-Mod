package gg.spaceclient.ui;

import gg.spaceclient.SpaceClient;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Turns PNG bytes - a server icon, a cosmetic thumbnail - into a texture the
 * menu can draw.
 *
 * Called directly: decoding, wrapping and registering are the same on every
 * supported version. It used to go by reflection, which a real 1.21.11 game,
 * running under obfuscated names, could not follow.
 */
public final class TextureLoader {

    /** What happened the first time an icon was asked for. */
    private static String status = "no icon requested yet";

    public static String status() { return status; }

    public static boolean working() { return status.startsWith("using "); }

    private TextureLoader() {}

    /**
     * Registers an image under the given identifier.
     *
     * @return the identifier, or null if the bytes are not a readable image.
     */
    public static Identifier register(byte[] bytes, Identifier id) {
        if (bytes == null || bytes.length == 0 || id == null) return null;
        try {
            NativeImage image = NativeImage.read(bytes);
            DynamicTexture texture = new DynamicTexture(id::toString, image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            status = "using DynamicTexture";
            return id;
        } catch (Throwable t) {
            if (!status.startsWith("failed")) {
                SpaceClient.LOGGER.warn("Could not load an image as a texture: {}", t.toString());
            }
            status = "failed: " + t.getClass().getSimpleName();
            return null;
        }
    }
}
