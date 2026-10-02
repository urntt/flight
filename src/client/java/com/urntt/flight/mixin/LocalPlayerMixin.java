package com.urntt.flight.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.urntt.flight.FlightClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	/**
	 * Lets the local player toggle flight by double-tapping jump in every game mode while the feature is active.
	 * Only the check in {@code aiStep} is changed: the {@code mayfly} ability itself keeps the value the game mode and
	 * the server gave it, so everything else that reads it (fall damage, sprinting while hungry, music) stays vanilla.
	 */
	@ModifyExpressionValue(method = "aiStep",
			at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;mayfly:Z"))
	private boolean flight$allowFlightToggle(final boolean mayfly) {
		return mayfly || FlightClient.controller().isActive();
	}

	/**
	 * Ends a flight that only this mod allowed once the feature is no longer active, for example after the toggle key
	 * turned it off, so the player is never left flying without the {@code mayfly} ability.
	 */
	@Inject(method = "aiStep", at = @At("HEAD"))
	private void flight$endUnallowedFlight(final CallbackInfo ci) {
		LocalPlayer player = (LocalPlayer) (Object) this;
		Abilities abilities = player.getAbilities();
		if (abilities.flying && !abilities.mayfly && !FlightClient.controller().isActive()) {
			abilities.flying = false;
			player.onUpdateAbilities();
		}
	}
}
