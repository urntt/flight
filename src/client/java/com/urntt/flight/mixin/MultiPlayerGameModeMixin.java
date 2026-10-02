package com.urntt.flight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.urntt.flight.FlightClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	/**
	 * Keeps the local player flying when the client applies a new game mode's abilities, which Survival and Adventure
	 * mode would otherwise reset to not flying. The other abilities of the game mode are applied as in vanilla.
	 */
	@WrapOperation(method = "setLocalMode(Lnet/minecraft/world/level/GameType;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/GameType;updatePlayerAbilities(Lnet/minecraft/world/entity/player/Abilities;)V"))
	private void flight$keepFlying(final GameType mode, final Abilities abilities, final Operation<Void> original) {
		boolean wasFlying = abilities.flying;
		original.call(mode, abilities);
		abilities.flying = FlightClient.controller().flyingAfterAbilityUpdate(wasFlying, abilities.flying);
	}
}
