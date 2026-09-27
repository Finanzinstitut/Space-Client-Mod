/*
 * Based on WaveyCapes by tr7zw - https://github.com/tr7zw/WaveyCapes
 * Copyright (c) tr7zw, 2021. Used under the tr7zw Protective License, see
 * THIRD-PARTY-WaveyCapes-LICENSE.txt: use, modification and compilation are
 * permitted; the work may not be used for commercial advantage or monetary
 * compensation. Ported into Space Client for Minecraft 26.2; the simulation
 * and the cape renderer are tr7zw's, only the glue around them is new.
 */
package gg.spaceclient.wavey.sim;

import java.util.List;

import gg.spaceclient.wavey.util.CapePoint;
import gg.spaceclient.wavey.util.Vector3;

public interface BasicSimulation {

    void simulate();

    void setGravityDirection(Vector3 gravityDirection);

    float getGravity();

    void setGravity(float gravity);

    boolean isSneaking();

    void setSneaking(boolean sneaking);

    /**
     * @return true if it was re-initialized
     */
    boolean init(int partCount);

    boolean empty();

    void applyMovement(Vector3 movement);

    List<CapePoint> getPoints();

}