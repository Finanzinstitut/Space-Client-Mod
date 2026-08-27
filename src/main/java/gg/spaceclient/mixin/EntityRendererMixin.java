package gg.spaceclient.mixin;

import gg.spaceclient.net.NowPlayingShare;
import gg.spaceclient.render.NameBadge;

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
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Puts a second line above the name tag with what that player is listening to.
 *
 * Three approaches, and the reason for the third is worth keeping:
 *
 * Calling submitNameTag with hand picked arguments drew two dashes and no
 * text - two of its parameters have no obvious meaning and both guesses were
 * wrong. Calling submitNameDisplay a second time fixed that, because vanilla
 * then chose the arguments itself, but every other mod hooked into that same
 * method ran a second time too: Cosmetica badges, Essential icons and the
 * heart indicator all appeared twice.
 *
 * So this intercepts the single drawing call vanilla already makes. The
 * original is passed through untouched, then the very same call is made again
 * with the song as the text and the position one line higher. Every argument
 * whose meaning is unclear is simply handed back exactly as received, and
 * nothing else in the pipeline runs twice.
 *
 * The target deliberately names no owning class, so it matches whether the
 * call site is compiled against SubmitNodeCollector or its parent interface.
 *
 * The same interception also puts the Space Client badge in front of the name.
 * It goes here rather than in a draw call of its own precisely because the
 * text is already passing through: the badge is a glyph, so prefixing the
 * component is the entire implementation. Both overloads are handled, because
 * they fire independently and a badge that only appears sometimes is worse
 * than none.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    /** Roughly one line of name tag text, in world units. */
    private static final double LINE_HEIGHT = 0.28;

    private static final String SUBMIT_NAME_TAG =
            "submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "I"
                    + "Lnet/minecraft/network/chat/Component;"
                    + "Z"
                    + "I"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

    @Redirect(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
            at = @At(value = "INVOKE", target = SUBMIT_NAME_TAG),
            require = 0
    )
    private void spaceclient$withSongLong(SubmitNodeCollector collector,
                                          PoseStack poseStack,
                                          Vec3 position,
                                          int background,
                                          Component text,
                                          boolean flag,
                                          int light,
                                          CameraRenderState camera,
                                          EntityRenderState state,
                                          PoseStack outerPose,
                                          SubmitNodeCollector outerCollector,
                                          CameraRenderState outerCamera,
                                          int color) {
        collector.submitNameTag(poseStack, position, background,
                NameBadge.decorate(state, text), flag, light, camera);
        addSong(collector, poseStack, position, background, flag, light, camera, state, true);
    }

    @Redirect(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = SUBMIT_NAME_TAG),
            require = 0
    )
    private void spaceclient$withSongShort(SubmitNodeCollector collector,
                                           PoseStack poseStack,
                                           Vec3 position,
                                           int background,
                                           Component text,
                                           boolean flag,
                                           int light,
                                           CameraRenderState camera,
                                           EntityRenderState state,
                                           PoseStack outerPose,
                                           SubmitNodeCollector outerCollector,
                                           CameraRenderState outerCamera) {
        collector.submitNameTag(poseStack, position, background,
                NameBadge.decorate(state, text), flag, light, camera);
        addSong(collector, poseStack, position, background, flag, light, camera, state, false);
    }

    /** Draws the song line, if this player has one to show. */
    private void addSong(SubmitNodeCollector collector,
                         PoseStack poseStack,
                         Vec3 position,
                         int background,
                         boolean flag,
                         int light,
                         CameraRenderState camera,
                         EntityRenderState state,
                         boolean longOverload) {
        try {
            if (!(state instanceof AvatarRenderState avatar)) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            Entity entity = mc.level.getEntity(avatar.id);
            if (!(entity instanceof Player player)) return;
            if (player == mc.player && !NowPlayingShare.showOnSelf()) return;

            String song = NowPlayingShare.songFor(player.getUUID());
            if (song == null || song.isEmpty()) return;

            collector.submitNameTag(
                    poseStack,
                    position.add(0.0, LINE_HEIGHT, 0.0),
                    background,
                    Component.literal("\u266A " + song),
                    flag,
                    light,
                    camera);

            // Above the song rather than below, so the stack reads downward:
            // lyric, track, name. Both sides have to have lyrics switched on
            // before this appears at all.
            String lyric = NowPlayingShare.lyricFor(player.getUUID());
            if (lyric != null && !lyric.isEmpty()) {
                collector.submitNameTag(
                        poseStack,
                        position.add(0.0, LINE_HEIGHT * 2, 0.0),
                        background,
                        Component.literal(lyric),
                        flag,
                        light,
                        camera);
            }

            NowPlayingShare.noteHook(longOverload, true);

        } catch (Throwable ignored) {
            // A song is never worth a broken frame
        }
    }
}
