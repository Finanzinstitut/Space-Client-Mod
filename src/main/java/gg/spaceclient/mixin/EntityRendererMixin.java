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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts a second line above the name tag with what that player is listening to.
 *
 * This rides on the vanilla name tag rather than drawing its own text pass, so
 * everything that decides whether a name is visible at all - distance, sneaking,
 * team settings, F1 - already applies without any of it being reimplemented.
 * If vanilla draws no name, this never runs at all.
 *
 * Which overload: submitNameDisplay exists twice, with and without a trailing
 * int. The one taking the int is the one that does the work; the shorter one
 * fills in a default and calls it. Injecting into the longer one therefore
 * fires exactly once either way. If nothing ever appears, that assumption is
 * the thing to flip - drop the last parameter from the descriptor below.
 *
 * Your own head is skipped unless the self setting is on - the track is
 * already in the HUD element, so it would otherwise read twice.
 *
 * Identity comes from AvatarRenderState.id, the entity id. The render state
 * carries no UUID, but the id is enough to find the player in the level and ask
 * it - which beats stamping a UUID on through a mixin interface, because the
 * field is already there and nothing has to keep it in sync.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    /** Roughly one line of name tag text, in world units. */
    private static final double LINE_HEIGHT = 0.28;

    /** The same quarter black vanilla puts behind a name tag. */
    private static final int BACKGROUND = 0x40000000;

    /**
     * The four parameter overload, as insurance.
     *
     * The assumption was that the short one delegates to the long one. If that
     * holds, this fires first and does nothing, because the long one is about
     * to do the work. If it does not hold, this is the only one that ever runs
     * and it takes over. Either way the line is drawn exactly once, and the
     * counters say which world we are in.
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
        if (NowPlayingShare.longHookSeen()) {
            NowPlayingShare.noteHook(false, false);
            return;
        }
        boolean drew = draw(state, poseStack, collector, camera);
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
        boolean drew = draw(state, poseStack, collector, camera);
        NowPlayingShare.noteHook(true, drew);
    }

    /** @return whether a line was actually drawn */
    private static boolean draw(EntityRenderState state,
                                PoseStack poseStack,
                                SubmitNodeCollector collector,
                                CameraRenderState camera) {
        try {
            if (!(state instanceof AvatarRenderState avatar)) return false;

            Vec3 attachment = state.nameTagAttachment;
            if (attachment == null) return false;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return false;

            Entity entity = mc.level.getEntity(avatar.id);
            if (!(entity instanceof Player player)) return false;
            if (player == mc.player && !NowPlayingShare.showOnSelf()) return false;

            String song = NowPlayingShare.songFor(player.getUUID());
            if (song == null || song.isEmpty()) return false;

            // Above the name rather than below it, and above the score line
            // too - the score sits under the name, so one line up is clear.
            Vec3 above = attachment.add(0.0, LINE_HEIGHT, 0.0);

            collector.submitNameTag(
                    poseStack,
                    above,
                    BACKGROUND,
                    Component.literal("\u266A " + song),
                    !state.isDiscrete,
                    state.lightCoords,
                    camera
            );
            return true;

        } catch (Throwable ignored) {
            // A song is never worth a broken frame
            return false;
        }
    }
}
