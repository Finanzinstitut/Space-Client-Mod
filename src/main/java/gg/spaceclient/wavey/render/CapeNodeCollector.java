/*
 * Based on WaveyCapes by tr7zw - https://github.com/tr7zw/WaveyCapes
 * Copyright (c) tr7zw, 2021. Used under the tr7zw Protective License, see
 * THIRD-PARTY-WaveyCapes-LICENSE.txt: use, modification and compilation are
 * permitted; the work may not be used for commercial advantage or monetary
 * compensation. Ported into Space Client for Minecraft 26.2; the simulation
 * and the cape renderer are tr7zw's, only the glue around them is new.
 */
package gg.spaceclient.wavey.render;

import com.mojang.blaze3d.vertex.*;
import gg.spaceclient.wavey.PlayerWrapper;
import gg.spaceclient.wavey.*;
import net.minecraft.client.player.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.state.*;

import net.minecraft.client.renderer.feature.*;
import net.minecraft.client.renderer.rendertype.*;

public class CapeNodeCollector {

    private final CustomCapeRenderer customCapeRenderer = new CustomCapeRenderer();
    private final VanillaCapeRenderer vanillaCape = new VanillaCapeRenderer();

    public void submitCape(

            SubmitNodeCollector submitNodeCollector,

            PlayerWrapper playerWrapper, PoseStack stack, int packedLight, float delta) {
        var renderer = getCapeRenderer(playerWrapper);
        if (renderer == null) {
            return;
        }
        var capeInfo = renderer.getCapeInfo(playerWrapper);
        if (capeInfo == null) {
            return;
        }
        // WaveyCapes drew an extra glint pass for glinting capes here. No cape
        // renderer in this port sets isGlint, and 26.3 removed the texture-less
        // entity glint type it used, so the pass is left out.

        submitNodeCollector.submitCustomGeometry(stack, capeInfo.renderType(), (pose, vertexConsumer) -> {
            PoseStack sharedStack = new PoseStack();
            sharedStack.last().set(pose);
            customCapeRenderer.render(playerWrapper, renderer, vertexConsumer, sharedStack, packedLight, delta);
        });

    }

    private CapeRenderer getCapeRenderer(PlayerWrapper capeRenderInfo) {
        if (capeRenderInfo.getCapeTexture() == null || !capeRenderInfo.isCapeVisible()) {
            return null;
        } else {
            return vanillaCape;
        }
    }

}
