/*
 * Based on WaveyCapes by tr7zw - https://github.com/tr7zw/WaveyCapes
 * Copyright (c) tr7zw, 2021. Used under the tr7zw Protective License, see
 * THIRD-PARTY-WaveyCapes-LICENSE.txt: use, modification and compilation are
 * permitted; the work may not be used for commercial advantage or monetary
 * compensation. Ported into Space Client for Minecraft 26.2; the simulation
 * and the cape renderer are tr7zw's, only the glue around them is new.
 */
package gg.spaceclient.wavey.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import gg.spaceclient.wavey.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import gg.spaceclient.wavey.VertexConsumerUtil;
import gg.spaceclient.wavey.PlayerWrapper;
import gg.spaceclient.wavey.*;
import gg.spaceclient.wavey.sim.BasicSimulation;
import gg.spaceclient.wavey.util.Vector3;
import gg.spaceclient.wavey.util.Vector4;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class CustomCapeRenderer {

    private static final int PART_COUNT = 16;
    private final ModelPart[] customCape = NMSUtil.buildCape(64, 64, x -> 0, y -> y);

    private static final float CAPE_WIDTH = 10F / 16F;
    private static final float CAPE_HEIGHT = 1F;
    private static final float CAPE_DEPTH = 1F / 16F;

    public void render(PlayerWrapper capeRenderInfo, CapeRenderer renderer, VertexConsumer vertexConsumer,
            PoseStack poseStack, int packedLight, float delta) {

        if (WaveyCapes.config.capeStyle == CapeStyle.SMOOTH && renderer.vanillaUvValues()) {
            renderSmoothCape(poseStack, vertexConsumer, capeRenderInfo, delta, packedLight);
        } else {
            ModelPart[] parts = customCape;
            for (int part = 0; part < PART_COUNT; part++) {
                ModelPart model = parts[part];
                modifyPoseStack(poseStack, capeRenderInfo, delta, part);
                renderer.render(capeRenderInfo, part, model, poseStack, vertexConsumer, packedLight,
                        OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
    }

    private void renderSmoothCape(PoseStack poseStack, VertexConsumer bufferBuilder, PlayerWrapper capeRenderInfo,
            float delta, int light) {

        float alpha = 1f;

        /*float capeWidth = 10F / 16F;
        float capeHeight = 1.0F;
        float capeDepth = 1F / 16F;*/

        Matrix4f[] positionMatrices = new Matrix4f[PART_COUNT];
        Vector3[] frontNormalVecs = new Vector3[PART_COUNT];
        Vector3[] backNormalVecs = new Vector3[PART_COUNT];
        for (int part = 0; part < PART_COUNT; part++) {
            modifyPoseStack(poseStack, capeRenderInfo, delta, part);
            positionMatrices[part] = new Matrix4f(poseStack.last().pose());
            frontNormalVecs[part] = getNormalVec(positionMatrices[Math.max(part - 1, 0)],
                    positionMatrices[Math.max(part - 1, 0)], positionMatrices[part],
                    new Vector3(CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH),
                    new Vector3(-CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH),
                    new Vector3(CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH),
                    light == 15728880);
            backNormalVecs[part] = getNormalVec(positionMatrices[Math.max(part - 1, 0)],
                    positionMatrices[Math.max(part - 1, 0)], positionMatrices[part],
                    new Vector3(CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0),
                    new Vector3(-CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0),
                    new Vector3(CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), 0), light == 15728880);

            poseStack.popPose();
        }

        for (int part = 0; part < PART_COUNT; part++) {
            if (part == 0) {
                float minU = 1 / 64F;
                float maxU = 11 / 64F;

                float minV = 0;
                float maxV = 1 / 32F;

                Vector3 normalVec = getNormalVec(positionMatrices[0], positionMatrices[0], positionMatrices[0],
                        new Vector3(CAPE_WIDTH / 2, 0, 0), new Vector3(-CAPE_WIDTH / 2, 0, 0),
                        new Vector3(CAPE_WIDTH / 2, 0, CAPE_DEPTH), light == 15728880);

                VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[0], CAPE_WIDTH / 2, 0, 0, maxU, maxV,
                        OverlayTexture.NO_OVERLAY, light, normalVec.x, normalVec.y, normalVec.z, alpha);
                VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[0], -CAPE_WIDTH / 2, 0, 0, minU, maxV,
                        OverlayTexture.NO_OVERLAY, light, normalVec.x, normalVec.y, normalVec.z, alpha);
                VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[0], -CAPE_WIDTH / 2, 0, -CAPE_DEPTH, minU,
                        minV, OverlayTexture.NO_OVERLAY, light, normalVec.x, normalVec.y, normalVec.z, alpha);
                VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[0], CAPE_WIDTH / 2, 0, -CAPE_DEPTH, maxU,
                        minV, OverlayTexture.NO_OVERLAY, light, normalVec.x, normalVec.y, normalVec.z, alpha);
            }

            if (part == PART_COUNT - 1) {
                float minU = 11 / 64F;
                float maxU = 21 / 64F;

                float minV = 0;
                float maxV = 1 / 32F;

                Vector3 normalVec = getNormalVec(positionMatrices[part], positionMatrices[part], positionMatrices[part],
                        new Vector3(CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH),
                        new Vector3(-CAPE_WIDTH / 2F, CAPE_HEIGHT, -CAPE_DEPTH),
                        new Vector3(CAPE_WIDTH / 2F, CAPE_HEIGHT, 0), light == 15728880);

                VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[part], CAPE_WIDTH / 2F, CAPE_HEIGHT,
                        -CAPE_DEPTH, maxU, minV, OverlayTexture.NO_OVERLAY, light, normalVec.x, normalVec.y,
                        normalVec.z, alpha);
                VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[part], -CAPE_WIDTH / 2F, CAPE_HEIGHT,
                        -CAPE_DEPTH, minU, minV, OverlayTexture.NO_OVERLAY, light, normalVec.x, normalVec.y,
                        normalVec.z, alpha);
                VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[part], -CAPE_WIDTH / 2F, CAPE_HEIGHT, 0,
                        minU, maxV, OverlayTexture.NO_OVERLAY, light, normalVec.x, normalVec.y, normalVec.z, alpha);
                VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[part], CAPE_WIDTH / 2F, CAPE_HEIGHT, 0,
                        maxU, maxV, OverlayTexture.NO_OVERLAY, light, normalVec.x, normalVec.y, normalVec.z, alpha);
            }

            float minU = 0;
            float maxU = 1 / 64F;

            float minV = (1 / 32F) * (part + 1);
            float maxV = minV + (1 / 32F);

            Vector3 normalVec = getNormalVec(positionMatrices[part], positionMatrices[part],
                    positionMatrices[Math.max(part - 1, 0)],
                    new Vector3(-CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0),
                    new Vector3(-CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH),
                    new Vector3(-CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), 0), light == 15728880);

            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[part], -CAPE_WIDTH / 2F,
                    (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0, minU, maxV, OverlayTexture.NO_OVERLAY, light,
                    normalVec.x, normalVec.y, normalVec.z, alpha);
            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[part], -CAPE_WIDTH / 2F,
                    (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, maxU, maxV, OverlayTexture.NO_OVERLAY, light,
                    normalVec.x, normalVec.y, normalVec.z, alpha);
            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[Math.max(part - 1, 0)], -CAPE_WIDTH / 2F,
                    part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, maxU, minV, OverlayTexture.NO_OVERLAY, light,
                    normalVec.x, normalVec.y, normalVec.z, alpha);
            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[Math.max(part - 1, 0)], -CAPE_WIDTH / 2F,
                    part * (CAPE_HEIGHT / PART_COUNT), 0, minU, minV, OverlayTexture.NO_OVERLAY, light, normalVec.x,
                    normalVec.y, normalVec.z, alpha);

            minU = 11 / 64F;
            maxU = 12 / 64F;

            normalVec = getNormalVec(positionMatrices[part], positionMatrices[part],
                    positionMatrices[Math.max(part - 1, 0)],
                    new Vector3(CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH),
                    new Vector3(CAPE_WIDTH / 2F, (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0),
                    new Vector3(CAPE_WIDTH / 2F, part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH), light == 15728880);

            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[part], CAPE_WIDTH / 2F,
                    (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, minU, maxV, OverlayTexture.NO_OVERLAY, light,
                    normalVec.x, normalVec.y, normalVec.z, alpha);
            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[part], CAPE_WIDTH / 2F,
                    (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0, maxU, maxV, OverlayTexture.NO_OVERLAY, light,
                    normalVec.x, normalVec.y, normalVec.z, alpha);
            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[Math.max(part - 1, 0)], CAPE_WIDTH / 2F,
                    part * (CAPE_HEIGHT / PART_COUNT), 0, maxU, minV, OverlayTexture.NO_OVERLAY, light, normalVec.x,
                    normalVec.y, normalVec.z, alpha);
            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[Math.max(part - 1, 0)], CAPE_WIDTH / 2F,
                    part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, minU, minV, OverlayTexture.NO_OVERLAY, light,
                    normalVec.x, normalVec.y, normalVec.z, alpha);

            minU = 1 / 64F;
            maxU = 11 / 64F;

            Vector3 normalVecTop = frontNormalVecs[part].clone().add(frontNormalVecs[Math.max(part - 1, 0)]).div(2);
            Vector3 normalVecBottom = frontNormalVecs[part].clone()
                    .add(frontNormalVecs[Math.min(part + 1, PART_COUNT - 1)]).div(2);

            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[Math.max(part - 1, 0)], CAPE_WIDTH / 2,
                    part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, maxU, minV, OverlayTexture.NO_OVERLAY, light,
                    normalVecTop.x, normalVecTop.y, normalVecTop.z, alpha);
            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[Math.max(part - 1, 0)], -CAPE_WIDTH / 2,
                    part * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, minU, minV, OverlayTexture.NO_OVERLAY, light,
                    normalVecTop.x, normalVecTop.y, normalVecTop.z, alpha);
            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[part], -CAPE_WIDTH / 2,
                    (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, minU, maxV, OverlayTexture.NO_OVERLAY, light,
                    normalVecBottom.x, normalVecBottom.y, normalVecBottom.z, alpha);
            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[part], CAPE_WIDTH / 2,
                    (part + 1) * (CAPE_HEIGHT / PART_COUNT), -CAPE_DEPTH, maxU, maxV, OverlayTexture.NO_OVERLAY, light,
                    normalVecBottom.x, normalVecBottom.y, normalVecBottom.z, alpha);

            minU = 12 / 64F;
            maxU = 22 / 64F;

            normalVecTop = backNormalVecs[part].clone().add(backNormalVecs[Math.max(part - 1, 0)]).div(2);
            normalVecBottom = backNormalVecs[part].clone().add(backNormalVecs[Math.min(part + 1, PART_COUNT - 1)])
                    .div(2);

            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[Math.max(part - 1, 0)], CAPE_WIDTH / 2,
                    part * (CAPE_HEIGHT / PART_COUNT), 0, minU, minV, OverlayTexture.NO_OVERLAY, light, normalVecTop.x,
                    normalVecTop.y, normalVecTop.z, alpha);
            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[Math.max(part - 1, 0)], -CAPE_WIDTH / 2,
                    part * (CAPE_HEIGHT / PART_COUNT), 0, maxU, minV, OverlayTexture.NO_OVERLAY, light, normalVecTop.x,
                    normalVecTop.y, normalVecTop.z, alpha);
            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[part], -CAPE_WIDTH / 2,
                    (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0, maxU, maxV, OverlayTexture.NO_OVERLAY, light,
                    normalVecBottom.x, normalVecBottom.y, normalVecBottom.z, alpha);
            VertexConsumerUtil.addVertex(bufferBuilder, positionMatrices[part], CAPE_WIDTH / 2,
                    (part + 1) * (CAPE_HEIGHT / PART_COUNT), 0, minU, maxV, OverlayTexture.NO_OVERLAY, light,
                    normalVecBottom.x, normalVecBottom.y, normalVecBottom.z, alpha);
        }
    }

    private void modifyPoseStack(PoseStack poseStack, PlayerWrapper capeRenderInfo, float h, int part) {
        if (WaveyCapes.config.capeMovement != CapeMovement.VANILLA) {
            modifyPoseStackSimulation(poseStack, capeRenderInfo, h, part);
            return;
        }

        var renderState = capeRenderInfo.getRenderState();
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, 0.125D);

        var entity = capeRenderInfo.getAvatar();

        poseStack.mulPose(Axis.XP.rotationDegrees(6.0F + renderState.capeLean / 2.0F + renderState.capeFlap
                + getNatrualWindSwing(part, entity.isUnderWater())));
        poseStack.mulPose(Axis.ZP.rotationDegrees(renderState.capeLean2 / 2.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - renderState.capeLean2 / 2.0F));
    }

    private void modifyPoseStackSimulation(PoseStack poseStack, PlayerWrapper capeRenderInfo, float delta, int part) {

        var entity = capeRenderInfo.getAvatar();

        BasicSimulation simulation = ((CapeHolder) entity).spaceclient$getSimulation();
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, 0.125D);

        float x = simulation.getPoints().get(part).getLerpX(delta) - simulation.getPoints().get(0).getLerpX(delta);
        if (x > 0) {
            x = 0;
        }
        float y = simulation.getPoints().get(0).getLerpY(delta) - part
                - simulation.getPoints().get(part).getLerpY(delta);
        float z = simulation.getPoints().get(0).getLerpZ(delta) - simulation.getPoints().get(part).getLerpZ(delta);

        float sidewaysRotationOffset = 0;
        float partRotation = getRotation(delta, part, simulation);

        float height = 0;
        //        if (abstractClientPlayer.isCrouching()) {
        //            height += 25.0F;
        //            poseStack.translate(0, 0.15F, 0);
        //        }

        float naturalWindSwing = getNatrualWindSwing(part, entity.isUnderWater());

        // vanilla rotating and wind
        poseStack.mulPose(Axis.XP.rotationDegrees(6.0F + height + naturalWindSwing));
        poseStack.mulPose(Axis.ZP.rotationDegrees(sidewaysRotationOffset / 2.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - sidewaysRotationOffset / 2.0F));
        poseStack.translate(-z / PART_COUNT, y / PART_COUNT, x / PART_COUNT); // movement from the simulation
        // offsetting so the rotation is on the cape part
        // float offset = (float) (part * (16 / partCount))/16; // to fold the entire
        // cape into one position for debugging
        poseStack.translate(0, /*-offset*/ +(0.48 / 16), -(0.48 / 16)); // (0.48/16)
        poseStack.translate(0, part * 1f / PART_COUNT, part * (0) / PART_COUNT);
        poseStack.mulPose(Axis.XP.rotationDegrees(-partRotation)); // apply actual rotation
        // undoing the rotation
        poseStack.translate(0, -part * 1f / PART_COUNT, -part * (0) / PART_COUNT);
        poseStack.translate(0, -(0.48 / 16), (0.48 / 16));

    }

    private float getRotation(float delta, int part, BasicSimulation simulation) {
        if (part == PART_COUNT - 1) {
            return getRotation(delta, part - 1, simulation);
        }
        return (float) getAngle(simulation.getPoints().get(part).getLerpedPos(delta),
                simulation.getPoints().get(part + 1).getLerpedPos(delta));
    }

    private double getAngle(Vector3 a, Vector3 b) {
        Vector3 angle = b.subtract(a);
        return Math.toDegrees(Math.atan2(angle.x, angle.y)) + 180;
    }

    private float getNatrualWindSwing(int part, boolean underwater) {
        long highlightedPart = (System.currentTimeMillis() / (underwater ? 9 : 3)) % 360;
        float relativePart = (float) (part + 1) / PART_COUNT;
        if (WaveyCapes.config.windMode == WindMode.WAVES) {
            return (float) (Math.sin(Math.toRadians((relativePart) * 360 - (highlightedPart))) * 3);
        }
        return 0;
    }

    private static Vector3 getNormalVec(Matrix4f matrix1, Matrix4f matrix2, Matrix4f matrix3, Vector3 vector1,
            Vector3 vector2, Vector3 vector3, boolean inverse) {
        Vector3 vector1Transformed = transform(matrix1, new Vector4(vector1.x, vector1.y, vector1.z, 1)).toVec3();
        Vector3 vector2Transformed = transform(matrix2, new Vector4(vector2.x, vector2.y, vector2.z, 1)).toVec3();
        Vector3 vector3Transformed = transform(matrix3, new Vector4(vector3.x, vector3.y, vector3.z, 1)).toVec3();

        vector2Transformed.subtract(vector1Transformed);
        vector3Transformed.subtract(vector1Transformed);

        vector2Transformed.cross(vector3Transformed);
        vector2Transformed.normalize();
        return inverse ? vector2Transformed.mul(-1) : vector2Transformed;
    }

    private static Vector4 transform(Matrix4f matrix, Vector4 vector) {

        Vector4f vector4f = matrix.transform(new Vector4f(vector.x, vector.y, vector.z, vector.w));
        return new Vector4(vector4f.x, vector4f.y, vector4f.z, vector4f.w);
    }

}
