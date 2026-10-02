package com.urntt.flight.gametest;

import static com.urntt.flight.gametest.GameTestSupport.LOGGER;
import static com.urntt.flight.gametest.GameTestSupport.MAX_FLOATING_TICKS;
import static com.urntt.flight.gametest.GameTestSupport.bindKey;
import static com.urntt.flight.gametest.GameTestSupport.check;
import static com.urntt.flight.gametest.GameTestSupport.checkCannotFly;
import static com.urntt.flight.gametest.GameTestSupport.checkTakesOff;
import static com.urntt.flight.gametest.GameTestSupport.configure;
import static com.urntt.flight.gametest.GameTestSupport.doubleTapJump;
import static com.urntt.flight.gametest.GameTestSupport.isEnabled;
import static com.urntt.flight.gametest.GameTestSupport.isFlying;
import static com.urntt.flight.gametest.GameTestSupport.setGameMode;
import static com.urntt.flight.gametest.GameTestSupport.unbindKey;
import static com.urntt.flight.gametest.GameTestSupport.waitForGround;

import com.urntt.flight.FlightClient;
import com.urntt.flight.Scene;
import com.urntt.flight.config.MultiplayerMode;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

/**
 * Checks the multiplayer modes on a local dedicated server, which the client reaches as {@code localhost}, and that
 * the server's own flight check still applies: the mod does not try to get around it.
 */
@SuppressWarnings("UnstableApiUsage")
public final class FlightMultiplayerGameTest implements FabricClientGameTest {
	/** Ticks to wait for the kick beyond the vanilla limit, covering the take-off and network latency. */
	private static final int KICK_GRACE_TICKS = 100;

	@Override
	public void runTest(final ClientGameTestContext context) {
		configure(context, config -> {
			config.setEnabled(true);
			config.setMultiplayerDefault(true);
			config.setResetOnWorldExit(false);
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});
		KeyMapping toggleKey = bindKey(context, FlightClient.TOGGLE_KEY_NAME, "key.keyboard.j");

		// The default server properties keep vanilla's allow-flight=false.
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			// The connection is not closed with try-with-resources: the server ends it by kicking the player below.
			TestDedicatedServerConnection connection = server.connect();
			connection.waitForChunksRender();
			setGameMode(context, server, GameType.SURVIVAL);
			waitForGround(context);
			Scene scene = context.computeOnClient(client -> FlightClient.controller().scene());
			LOGGER.info("Connected to {}", scene);
			check(scene instanceof Scene.Multiplayer, "a dedicated server should count as multiplayer, got " + scene);

			checkCannotFly(context, "on a server with multiplayer disabled");
			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(isEnabled(context), "a blocked toggle should leave the state unchanged");
			context.takeScreenshot("flight-blocked-on-server");

			configure(context, config -> {
				config.setServers(List.of("localhost"));
				config.setMultiplayerMode(MultiplayerMode.WHITELIST);
			});
			checkTakesOff(context, "on a whitelisted server");

			configure(context, config -> config.setMultiplayerMode(MultiplayerMode.BLACKLIST));
			context.waitTick();
			check(!isFlying(context), "the flight should end once the multiplayer mode rules out the server");
			waitForGround(context);
			checkCannotFly(context, "on a blacklisted server");

			configure(context, config -> config.setMultiplayerMode(MultiplayerMode.WHITELIST));
			checkKickedForFloating(context);
		}

		configure(context, config -> {
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});
		unbindKey(context, toggleKey);
	}

	/**
	 * Hovers on a server that does not allow flight and checks that the server kicks the player, as it would any
	 * client that floats too long.
	 */
	private static void checkKickedForFloating(final ClientGameTestContext context) {
		doubleTapJump(context);
		check(isFlying(context), "double-tapping jump should start flight on a whitelisted server");
		int ticks = context.waitFor(client -> client.level == null, MAX_FLOATING_TICKS + KICK_GRACE_TICKS);
		context.waitForScreen(DisconnectedScreen.class);
		String expectedReason = context.computeOnClient(
				client -> Component.translatable("multiplayer.disconnect.flying").getString());
		String shown = context.computeOnClient(client -> client.gui.screen().getNarrationMessage().getString());
		LOGGER.info("Disconnected {} ticks after taking off: {}", ticks, shown);
		check(shown.contains(expectedReason), "expected to be kicked with '" + expectedReason + "', got '" + shown + "'");
		context.takeScreenshot("flight-kicked-for-floating");
		context.setScreen(() -> null);
	}
}
