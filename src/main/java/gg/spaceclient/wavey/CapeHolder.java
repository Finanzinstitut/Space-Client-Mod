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
import gg.spaceclient.wavey.sim.BasicSimulation;
import gg.spaceclient.wavey.sim.StickSimulation;
import gg.spaceclient.wavey.sim.StickSimulation.Vector2;
import gg.spaceclient.wavey.sim.StickSimulation3d;
import gg.spaceclient.wavey.sim.StickSimulationDungeons;
import gg.spaceclient.wavey.WaveyMth;
import gg.spaceclient.wavey.util.Vector3;

import java.util.UUID;

public interface CapeHolder {
    public BasicSimulation spaceclient$getSimulation();

    public Vector3 spaceclient$getLastPlayerAnimatorPosition();

    public void spaceclient$setLastPlayerAnimatorPosition(Vector3 pos);

    public void spaceclient$setSimulation(BasicSimulation sim);

    UUID spaceclient$getWCUUID();

    void spaceclient$setDirty();

    public default void spaceclient$updateSimulation(int partCount) {
        BasicSimulation simulation = spaceclient$getSimulation();
        if (simulation == null || spaceclient$incorrectSimulation(simulation)) {
            simulation = spaceclient$createSimulation();
            spaceclient$setSimulation(simulation);
        }
        if (simulation == null) {
            return;
        }
        if (simulation.init(partCount))
            spaceclient$setDirty();

    }

    public default boolean spaceclient$incorrectSimulation(BasicSimulation sim) {
        CapeMovement style = WaveyCapes.config.capeMovement;
        if (style == CapeMovement.BASIC_SIMULATION && sim.getClass() != StickSimulation.class) {
            return true;
        } else if (style == CapeMovement.BASIC_SIMULATION_3D && sim.getClass() != StickSimulation3d.class) {
            return true;
        } else if (style == CapeMovement.DUNGEONS && sim.getClass() != StickSimulationDungeons.class) {
            return true;
        }
        return false;
    }

    public default BasicSimulation spaceclient$createSimulation() {
        CapeMovement style = WaveyCapes.config.capeMovement;
        if (style == CapeMovement.BASIC_SIMULATION) {
            return new StickSimulation();
        }
        if (style == CapeMovement.BASIC_SIMULATION_3D) {
            return new StickSimulation3d();
        }
        if (style == CapeMovement.DUNGEONS) {
            return new StickSimulationDungeons();
        }
        return null;
    }

    public default void spaceclient$simulate(MinecraftPlayer abstractClientPlayer) {
        BasicSimulation simulation = spaceclient$getSimulation();
        if (simulation == null || simulation.empty()) {
            return; // no cape, nothing to update
        }
        double d = abstractClientPlayer.getXCloak() - abstractClientPlayer.getX();
        double m = abstractClientPlayer.getZCloak() - abstractClientPlayer.getZ();
        float n = abstractClientPlayer.getYBodyRotO() + abstractClientPlayer.getYBodyRot()
                - abstractClientPlayer.getYBodyRotO();
        double o = WaveyMth.sin(n * 0.017453292F);
        double p = -WaveyMth.cos(n * 0.017453292F);
        float heightMul = WaveyCapes.config.heightMultiplier;
        float straveMul = WaveyCapes.config.straveMultiplier;
        if (abstractClientPlayer.isUnderWater()) {
            heightMul *= 2; // let the cape have more drag than the player underwater
        }
        // gives the cape a small swing when jumping/falling to not clip with
        // itself/simulate some air getting under it
        double fallHack = WaveyMth.clamp((abstractClientPlayer.getYo() - abstractClientPlayer.getY()) * 10, 0, 1);
        if (abstractClientPlayer.isUnderWater()) {
            simulation.setGravity(WaveyCapes.config.gravity / 10f);
        } else {
            simulation.setGravity(WaveyCapes.config.gravity);
        }

        Vector3 gravity = new Vector3(0, -1, 0);
        Vector2 strave = new Vector2((float) (abstractClientPlayer.getX() - abstractClientPlayer.getXo()),
                (float) (abstractClientPlayer.getZ() - abstractClientPlayer.getZo()));
        strave.rotateDegrees(-abstractClientPlayer.getYRot());
        double changeX = (d * o + m * p) + fallHack
                + (abstractClientPlayer.isCrouching() && !simulation.isSneaking() ? 3 : 0);
        double changeY = ((abstractClientPlayer.getY() - abstractClientPlayer.getYo()) * heightMul)
                + (abstractClientPlayer.isCrouching() && !simulation.isSneaking() ? 1 : 0);
        double changeZ = -strave.x * straveMul;
        simulation.setSneaking(abstractClientPlayer.isCrouching());
        Vector3 change = new Vector3((float) changeX, (float) changeY, (float) changeZ);
        if (abstractClientPlayer.isVisuallySwimming()) {
            float rotation = abstractClientPlayer.getXRot(); // -90 = swimming up, 0 = straight, 90 = down
            // the simulation has the body as reference, so if the player is swimming
            // straight down, gravity needs to point up(the cape should move into the
            // direction of the head, not the feet)
            // offset the rotation to swimming up doesn't rotate the vector at all
            rotation += 90;
            // apply rotation
            gravity.rotateDegrees(rotation);

            change.rotateDegrees(rotation);
        }
        simulation.setGravityDirection(gravity);

        simulation.applyMovement(change);
        simulation.simulate();
    }

}
