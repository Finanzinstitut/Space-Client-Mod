/*
 * Based on WaveyCapes by tr7zw - https://github.com/tr7zw/WaveyCapes
 * Copyright (c) tr7zw, 2021. Used under the tr7zw Protective License, see
 * THIRD-PARTY-WaveyCapes-LICENSE.txt: use, modification and compilation are
 * permitted; the work may not be used for commercial advantage or monetary
 * compensation. Ported into Space Client for Minecraft 26.2; the simulation
 * and the cape renderer are tr7zw's, only the glue around them is new.
 */
package gg.spaceclient.wavey.render;

import gg.spaceclient.wavey.PlayerWrapper;

import net.minecraft.client.renderer.rendertype.*;

public class VanillaCapeRenderer implements CapeRenderer {

    @Override
    public CapeInfos getCapeInfo(PlayerWrapper capeRenderInfo) {
        var cape = capeRenderInfo.getCapeTexture();
        if (cape != null) {
            return new CapeInfos(this, RenderTypes.entityTranslucent(cape), false);
        }
        return null;
    }

    @Override
    public boolean vanillaUvValues() {
        return true;
    }

}
