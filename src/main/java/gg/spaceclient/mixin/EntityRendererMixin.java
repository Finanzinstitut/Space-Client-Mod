package gg.spaceclient.mixin;

import gg.spaceclient.net.NowPlayingShare;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts a second line above the name tag with what that player is listening to.
 *
 * The first attempt called submitNameTag directly and guessed at two of its
 * arguments - an int and a boolean whose meaning the jar dump does not spell
 * out. The result was two small dashes and no text: something drew, but with
 * the wrong values.
 *
 * So this no longer calls the drawing method at all. It moves the name tag up,
 * swaps in the song as the text, and asks vanilla to draw a name tag again.
 * Every argument is then whatever vanilla itself passes, which removes the
 * guessing entirely and means the line is styled exactly like a real name.
 * The state is put back immediately, so the next frame sees no trace of it.
 *
 * The recursion guard matters: the second call lands right back in this
 * injection, and without the flag it would spiral until the stack ran out.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    /** Roughly one line of name tag text, in world units. */
    private static final double LINE_HEIGHT = 0.28;

    /** Set while vanilla is drawing our line, so it does not draw it again. */
    private static boolean drawing = false;

    @Shadow
    protected abstract void submitNameDisplay(EntityRenderState state,
                                              PoseStack poseStack,
                                              SubmitNodeCollector collector,
                                              CameraRenderState camera);

    @Shadow
    protected abstract void submitNameDisplay(EntityRenderState state,
                                              PoseStack poseStack,
                                              SubmitNodeCollector collector,
                                              CameraRenderState camera,
                                              int color);

    /**
     * The four parameter overload.
     *
     * Both overloads fire independently on this version - the counters showed
     * roughly 34000 against 30500 calls, so the short one is not simply
     * delegating. It still stands down whenever the long one is in use, so the
     * line is drawn once rather than twice.
     */
    @Inject(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("TAIL"),
            require = 0
    )
    private void spaceclient$songOverNameShort(EntityRenderState state,
                                               PoseStack poseStack,
                                               SubmitNodeCollector collector,
                                               CameraRenderState camera,
                                               CallbackInfo ci) {
        if (drawing) return;

        if (NowPlayingShare.longHookSeen()) {
            NowPlayingShare.noteHook(false, false);
            return;
        }
        boolean drew = draw(state, poseStack, collector, camera, null);
        NowPlayingShare.noteHook(false, drew);
    }

    @Inject(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
            at = @At("TAIL"),
            require = 0
    )
    private void spaceclient$songOverName(EntityRenderState state,
                                          PoseStack poseStack,
                                          SubmitNodeCollector collector,
                                          CameraRenderState camera,
                                          int color,
                                          CallbackInfo ci) {
        if (drawing) return;

        boolean drew = draw(state, poseStack, collector, camera, color);
        NowPlayingShare.noteHook(true, drew);
    }

    /** @return whether a line was actually drawn */
    private boolean draw(EntityRenderState state,
                         PoseStack poseStack,
                         SubmitNodeCollector collector,
                         CameraRenderState camera,
                         Integer color) {

        Component originalName = state.nameTag;
        Vec3 originalAttachment = state.nameTagAttachment;

        try {
            if (!(state instanceof AvatarRenderState avatar)) return false;
            if (originalAttachment == null) return false;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return false;

            Entity entity = mc.level.getEntity(avatar.id);
            if (!(entity instanceof Player player)) return false;
            if (player == mc.player && !NowPlayingShare.showOnSelf()) return false;

            String song = NowPlayingShare.songFor(player.getUUID());
            if (song == null || song.isEmpty()) return false;

            // One line up, and the song in place of the name. Vanilla reads
            // both of these when it draws, so changing them is enough to
            // redirect the whole thing.
            state.nameTag = Component.literal("\u266A " + song);
            state.nameTagAttachment = originalAttachment.add(0.0, LINE_HEIGHT, 0.0);

            drawing = true;
            if (color != null) {
                this.submitNameDisplay(state, poseStack, collector, camera, color);
            } else {
                this.submitNameDisplay(state, poseStack, collector, camera);
            }
            return true;

        } catch (Throwable ignored) {
            // A song is never worth a broken frame
            return false;

        } finally {
            // Restored in finally, because leaving a player's name replaced by
            // a song would outlive this frame and follow them around
            drawing = false;
            state.nameTag = originalName;
            state.nameTagAttachment = originalAttachment;
        }
    }
}
