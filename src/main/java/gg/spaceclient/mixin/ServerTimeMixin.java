package gg.spaceclient.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands each of the server's time packets to the TPS readout as it arrives. */
@Mixin(ClientPacketListener.class)
public class ServerTimeMixin {

    @Inject(method = "handleSetTime", at = @At("HEAD"))
    private void spaceclient$serverTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        try {
            gg.spaceclient.modules.TpsModule.onServerTime(packet.gameTime());
        } catch (Throwable ignored) {
            // The readout misses one sample
        }
    }
}
