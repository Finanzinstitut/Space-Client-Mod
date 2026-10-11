/*
 * Based on WaveyCapes by tr7zw - https://github.com/tr7zw/WaveyCapes
 * Copyright (c) tr7zw, 2021. Used under the tr7zw Protective License, see
 * THIRD-PARTY-WaveyCapes-LICENSE.txt: use, modification and compilation are
 * permitted; the work may not be used for commercial advantage or monetary
 * compensation. Ported into Space Client for Minecraft 26.2; the simulation
 * and the cape renderer are tr7zw's, only the glue around them is new.
 */
package gg.spaceclient.wavey.render;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.spaceclient.wavey.PlayerWrapper;
import gg.spaceclient.wavey.WaveyCapes;
import net.minecraft.client.Minecraft;

import net.minecraft.client.model.player.*;

import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;

public class CustomCapeRenderLayer
        extends RenderLayer<net.minecraft.client.renderer.entity.state.AvatarRenderState, PlayerModel> {

    

    public CustomCapeRenderLayer(
            RenderLayerParent<net.minecraft.client.renderer.entity.state.AvatarRenderState, PlayerModel> renderLayerParent) {
        super(renderLayerParent);
    }

    

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight,
            AvatarRenderState renderState, float f, float g) {
        // Space Client: vanilla's own cape layer draws while the module is off
        if (!WaveyCapes.enabled) return;
        gg.spaceclient.render.CapeReport.submitted();
        PlayerWrapper capeRenderInfo = new PlayerWrapper(renderState);
        var avatar = capeRenderInfo.getAvatar();
        if (avatar == null) return;
        // The simulation is built on the entity's first tick; a frame drawn
        // before that has no points to bend the cape along yet
        if (WaveyCapes.config.capeMovement != gg.spaceclient.wavey.CapeMovement.VANILLA) {
            var simulation = ((gg.spaceclient.wavey.CapeHolder) avatar).spaceclient$getSimulation();
            if (simulation == null || simulation.getPoints().size() < 16) return;
        }
        float delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

                if (capeRenderInfo.isPlayerInvisible())
            return;
        if (capeRenderInfo.hasElytraEquipped())
            return;

        if (!capeRenderInfo.isCapeVisible()) {
            return;
        }

        poseStack.pushPose();

        getParentModel().root().translateAndRotate(poseStack);
        getParentModel().body.translateAndRotate(poseStack);

        if (capeRenderInfo.hasChestplateEquipped()) {
            poseStack.translate(0.0F, -0.053125F, 0.06875F);
        }

        WaveyCapes.collector().submitCape(submitNodeCollector, capeRenderInfo, poseStack,
                packedLight, delta);

        poseStack.popPose();
    }

}
