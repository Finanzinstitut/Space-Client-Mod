package gg.spaceclient.mixin;

import gg.spaceclient.wavey.CapeHolder;
import gg.spaceclient.wavey.PlayerDelegate;
import gg.spaceclient.wavey.WaveyCapes;
import gg.spaceclient.wavey.sim.BasicSimulation;
import gg.spaceclient.wavey.util.Vector3;

import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Gives every player its cape simulation and steps it once a tick.
 *
 * WaveyCapes' own PlayerMixin (tr7zw, tr7zw Protective License - see
 * THIRD-PARTY-WaveyCapes-LICENSE.txt), carried over for 26.2: on LivingEntity
 * because that is where tick lives, acting only for avatars, and only while
 * the Wavey Cape module is on.
 */
@Mixin(LivingEntity.class)
public abstract class WaveyCapeEntityMixin implements CapeHolder {

    @Unique
    private BasicSimulation spaceclient$simulation;

    @Unique
    private Vector3 spaceclient$animatorPosition = new Vector3();

    @Unique
    private boolean spaceclient$dirty = false;

    @Override
    public BasicSimulation getSimulation() { return spaceclient$simulation; }

    @Override
    public void setSimulation(BasicSimulation sim) { this.spaceclient$simulation = sim; }

    @Override
    public Vector3 getLastPlayerAnimatorPosition() { return spaceclient$animatorPosition; }

    @Override
    public void setLastPlayerAnimatorPosition(Vector3 pos) { this.spaceclient$animatorPosition = pos; }

    @Override
    public void setDirty() { this.spaceclient$dirty = true; }

    @Override
    public UUID getWCUUID() { return ((Entity) (Object) this).getUUID(); }

    @Inject(method = "tick", at = @At("TAIL"))
    private void spaceclient$moveCloak(CallbackInfo ci) {
        if (!WaveyCapes.enabled) return;
        if (!((Object) this instanceof Avatar avatar)) return;
        // Only on the client: the server's copy of a player has no cape to move
        if (!avatar.level().isClientSide()) return;

        try {
            updateSimulation(16);
            PlayerDelegate delegate = new PlayerDelegate(avatar);
            if (spaceclient$dirty) {
                spaceclient$dirty = false;
                spaceclient$simulation.applyMovement(new Vector3(1f, 1f, 0));
                // A few steps at once so a fresh cape starts settled
                for (int i = 0; i < 5; i++) simulate(delegate);
            }
            simulate(delegate);
            gg.spaceclient.render.CapeReport.simulated();
        } catch (Throwable t) {
            gg.spaceclient.render.CapeReport.failed(t);
        }
    }
}
