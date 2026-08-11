package gg.spaceclient.mixin;

import gg.spaceclient.SpaceClient;
import gg.spaceclient.modules.visual.HitboxModule;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns vanilla's hitbox rendering on selectively.
 *
 * Rather than reimplementing the geometry, the module flips Minecraft's own
 * renderHitboxes flag per entity: entities that pass the filter get the real
 * vanilla box (and its blue eye arrow), everything else is left alone.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void spaceclient$beforeRender(
            E entity, double x, double y, double z, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            CallbackInfo ci) {

        HitboxModule module = (HitboxModule) SpaceClient.getModuleManager().get("hitbox");
        if (module == null || !module.isEnabled()) return;

        EntityRenderDispatcher self = (EntityRenderDispatcher) (Object) this;
        self.setRenderHitboxes(module.shouldRender(entity));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends Entity> void spaceclient$afterRender(
            E entity, double x, double y, double z, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            CallbackInfo ci) {

        HitboxModule module = (HitboxModule) SpaceClient.getModuleManager().get("hitbox");
        if (module == null || !module.isEnabled()) return;

        // Leave the flag as we found it so F3+B keeps behaving normally
        EntityRenderDispatcher self = (EntityRenderDispatcher) (Object) this;
        self.setRenderHitboxes(false);
    }
}
