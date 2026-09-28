package gg.spaceclient.mixin;

import net.fabricmc.loader.api.FabricLoader;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Leaves out the mixins that would fight another installed mod over the same
 * code. With the WaveyCapes mod present, its own PlayerMixin already gives
 * every player a cape simulation; ours on top of it clashed at the first
 * remote player's tick, so the port's mixins stand down and WaveyCapes draws.
 */
public class SpaceMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> WAVEY = Set.of(
            "gg.spaceclient.mixin.WaveyCapeEntityMixin",
            "gg.spaceclient.mixin.CapeLayerMixin");

    private boolean waveyCapesInstalled;

    @Override
    public void onLoad(String mixinPackage) {
        waveyCapesInstalled = FabricLoader.getInstance().isModLoaded("waveycapes");
    }

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !(waveyCapesInstalled && WAVEY.contains(mixinClassName));
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
