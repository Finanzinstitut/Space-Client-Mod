package gg.spaceclient.mixin;

//#if MC < 26.1
//$$ import net.minecraft.client.renderer.LightTexture;
//$$ import net.minecraft.world.entity.LivingEntity;
//$$
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.gen.Invoker;
//$$
//$$ /** The light texture's own darkness calculation, for Fullbright on 1.21.11. */
//$$ @Mixin(LightTexture.class)
//$$ public interface LightTextureInvoker {
//$$
//$$     @Invoker("calculateDarknessScale")
//$$     float spaceclient$darknessScale(LivingEntity entity, float factor, float partialTicks);
//$$ }
//#else
/** Only needed on 1.21.11; see the version block above. */
public interface LightTextureInvoker {
}
//#endif
