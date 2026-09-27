/*
 * Based on WaveyCapes by tr7zw - https://github.com/tr7zw/WaveyCapes
 * Copyright (c) tr7zw, 2021. Used under the tr7zw Protective License, see
 * THIRD-PARTY-WaveyCapes-LICENSE.txt: use, modification and compilation are
 * permitted; the work may not be used for commercial advantage or monetary
 * compensation. Ported into Space Client for Minecraft 26.2; the simulation
 * and the cape renderer are tr7zw's, only the glue around them is new.
 */
package gg.spaceclient.wavey;

import gg.spaceclient.wavey.nms.MinecraftPlayer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.Minecraft;
public class PlayerDelegate implements MinecraftPlayer {

    private final net.minecraft.world.entity.Avatar player;

    public PlayerDelegate(net.minecraft.world.entity.Avatar player) {
        this.player = player;
    }

    public net.minecraft.world.entity.Avatar getPlayer() { return player; }

    // What lombok's @Delegate generated in the original
    public boolean isVisuallySwimming() { return player.isVisuallySwimming(); }
    public float getXRot() { return player.getXRot(); }
    public boolean isCrouching() { return player.isCrouching(); }
    public double getY() { return player.getY(); }
    public float getYRot() { return player.getYRot(); }
    public double getZ() { return player.getZ(); }
    public double getX() { return player.getX(); }
    public boolean isUnderWater() { return player.isUnderWater(); }

    public double getXCloak() {

        float delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (player instanceof AbstractClientPlayer acp) {
            return acp.avatarState().getInterpolatedCloakX(delta);
        } else if (player instanceof net.minecraft.client.entity.ClientMannequin cm) {
            return cm.avatarState().getInterpolatedCloakX(delta);
        } else {
            return 0;
        }

    }

    public double getZCloak() {

        float delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (player instanceof AbstractClientPlayer acp) {
            return acp.avatarState().getInterpolatedCloakZ(delta);
        } else if (player instanceof net.minecraft.client.entity.ClientMannequin cm) {
            return cm.avatarState().getInterpolatedCloakZ(delta);
        } else {
            return 0;
        }

    }

    public float getYBodyRotO() {
        return player.yBodyRotO;
    }

    public float getYBodyRot() {
        return player.yBodyRot;
    }

    public double getYo() {
        return player.yo;
    }

    public double getXo() {
        return player.xo;
    }

    public double getZo() {
        return player.zo;
    }

}
