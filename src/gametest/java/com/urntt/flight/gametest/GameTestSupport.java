package com.urntt.flight.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.flight.FlightClient;
import com.urntt.flight.config.FlightConfig;
import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared helpers for the client game tests.
 */
@SuppressWarnings("UnstableApiUsage")
final class GameTestSupport {
	static final Logger LOGGER = LoggerFactory.getLogger("flight-gametest");

	/** Number of ticks the jump key is held while measuring how high the player gets. */
	static final int CLIMB_TICKS = 20;
	/**
	 * Upper bound on how high holding jump lifts a player who cannot fly. A vanilla jump peaks at about 1.25 blocks;
	 * flying for {@link #CLIMB_TICKS} ticks climbs about 7 blocks.
	 */
	static final double MAX_JUMP_HEIGHT = 1.5;
	/**
	 * Ticks a player may float on a server that does not allow flight before vanilla kicks them
	 * ({@code ServerGamePacketListenerImpl.getMaximumFlyingTicks} at normal gravity).
	 */
	static final int MAX_FLOATING_TICKS = 80;

	private GameTestSupport() {
	}

	/**
	 * Switches every player to {@code mode} and waits until the client has applied it.
	 */
	static void setGameMode(final ClientGameTestContext context, final TestServerContext server, final GameType mode) {
		server.runCommand("gamemode " + mode.getName() + " @a");
		context.waitFor(client -> client.gameMode != null && client.gameMode.getPlayerMode() == mode);
		// The server sends the new abilities after the game mode; let the client receive them too.
		context.waitTicks(2);
	}

	static void waitForGround(final ClientGameTestContext context) {
		context.waitFor(client -> client.player != null && client.player.onGround());
	}

	/**
	 * Presses jump twice in quick succession, which toggles flight for players who may fly.
	 */
	static void doubleTapJump(final ClientGameTestContext context) {
		context.getInput().holdKey(options -> options.keyJump);
		context.waitTick();
		context.getInput().releaseKey(options -> options.keyJump);
		context.waitTick();
		context.getInput().holdKey(options -> options.keyJump);
		context.waitTick();
		context.getInput().releaseKey(options -> options.keyJump);
		context.waitTick();
	}

	/**
	 * Holds jump for {@link #CLIMB_TICKS} ticks and returns how far the player rose above the starting height.
	 */
	static double climb(final ClientGameTestContext context, final String situation) {
		double startY = y(context);
		double maxY = startY;
		context.getInput().holdKey(options -> options.keyJump);
		for (int tick = 0; tick < CLIMB_TICKS; tick++) {
			context.waitTick();
			maxY = Math.max(maxY, y(context));
		}
		context.getInput().releaseKey(options -> options.keyJump);
		double gain = maxY - startY;
		LOGGER.info("Height gained holding jump for {} ticks {}: {}", CLIMB_TICKS, situation, "%.2f".formatted(gain));
		return gain;
	}

	/**
	 * Double-taps jump and checks that the player takes off and climbs higher than a jump could take them.
	 */
	static void checkTakesOff(final ClientGameTestContext context, final String situation) {
		doubleTapJump(context);
		check(isFlying(context), "double-tapping jump should start flight " + situation);
		double gain = climb(context, situation);
		check(gain > MAX_JUMP_HEIGHT, "expected to climb more than " + MAX_JUMP_HEIGHT + " blocks " + situation
				+ ", got " + gain);
	}

	/**
	 * Double-taps jump and checks that the player neither starts flying nor rises higher than a jump.
	 */
	static void checkCannotFly(final ClientGameTestContext context, final String situation) {
		doubleTapJump(context);
		check(!isFlying(context), "double-tapping jump should not start flight " + situation);
		waitForGround(context);
		double gain = climb(context, situation);
		check(gain <= MAX_JUMP_HEIGHT, "expected to climb at most " + MAX_JUMP_HEIGHT + " blocks " + situation
				+ ", got " + gain);
		waitForGround(context);
	}

	/**
	 * Holds sneak until the player lands, which ends flight.
	 */
	static void descendToGround(final ClientGameTestContext context) {
		context.getInput().holdKey(options -> options.keyShift);
		waitForGround(context);
		context.getInput().releaseKey(options -> options.keyShift);
		context.waitTick();
	}

	static boolean isFlying(final ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getAbilities().flying);
	}

	static double y(final ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.getY());
	}

	/**
	 * Returns the mod's key mapping registered under {@code name}, bound to {@code key} for the test.
	 */
	static KeyMapping bindKey(final ClientGameTestContext context, final String name, final String key) {
		KeyMapping mapping = Objects.requireNonNull(KeyMapping.get(name), name);
		context.runOnClient(client -> {
			mapping.setKey(InputConstants.getKey(key));
			KeyMapping.resetMapping();
		});
		return mapping;
	}

	static void unbindKey(final ClientGameTestContext context, final KeyMapping mapping) {
		context.runOnClient(client -> {
			mapping.setKey(mapping.getDefaultKey());
			KeyMapping.resetMapping();
		});
	}

	/**
	 * Changes the mod's configuration on the client thread.
	 */
	static void configure(final ClientGameTestContext context, final Consumer<FlightConfig> change) {
		context.runOnClient(client -> change.accept(FlightClient.config()));
	}

	static boolean isEnabled(final ClientGameTestContext context) {
		return context.computeOnClient(client -> FlightClient.config().isEnabled());
	}

	static FlightConfig loadSavedConfig() {
		return FlightConfig.load(FlightConfig.defaultPath());
	}

	static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
