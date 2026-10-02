package com.urntt.flight.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.urntt.flight.FlightClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	/**
	 * Keeps the local player flying when the server sends new abilities, which it does on every game mode change. The
	 * other abilities in the packet are applied as in vanilla.
	 */
	@ModifyExpressionValue(method = "handlePlayerAbilities",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/game/ClientboundPlayerAbilitiesPacket;isFlying()Z"))
	private boolean flight$keepFlying(final boolean flying, @Local final Player player) {
		return FlightClient.controller().flyingAfterAbilityUpdate(player.getAbilities().flying, flying);
	}
}
