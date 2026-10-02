package com.urntt.flight.gametest;

import static com.urntt.flight.gametest.GameTestSupport.LOGGER;
import static com.urntt.flight.gametest.GameTestSupport.MAX_FLOATING_TICKS;
import static com.urntt.flight.gametest.GameTestSupport.bindKey;
import static com.urntt.flight.gametest.GameTestSupport.check;
import static com.urntt.flight.gametest.GameTestSupport.checkCannotFly;
import static com.urntt.flight.gametest.GameTestSupport.checkTakesOff;
import static com.urntt.flight.gametest.GameTestSupport.configure;
import static com.urntt.flight.gametest.GameTestSupport.descendToGround;
import static com.urntt.flight.gametest.GameTestSupport.isEnabled;
import static com.urntt.flight.gametest.GameTestSupport.isFlying;
import static com.urntt.flight.gametest.GameTestSupport.loadSavedConfig;
import static com.urntt.flight.gametest.GameTestSupport.setGameMode;
import static com.urntt.flight.gametest.GameTestSupport.unbindKey;
import static com.urntt.flight.gametest.GameTestSupport.waitForGround;
import static com.urntt.flight.gametest.GameTestSupport.y;

import com.urntt.flight.FlightClient;
import com.urntt.flight.config.FlightConfigScreen;
import com.urntt.flight.config.MultiplayerMode;
import com.urntt.flight.config.ServerListScreen;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.gui.screens.options.InWorldGameRulesScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Checks flight, game mode changes, fall damage, the toggle key, the reset on world exit, and the settings key in
 * singleplayer worlds.
 */
@SuppressWarnings("UnstableApiUsage")
public final class FlightClientGameTest implements FabricClientGameTest {
	/** How far the player flies up before landing to measure fall damage. */
	private static final double FALL_HEIGHT = 10.0;
	/** Game modes switched through while flying, ending in Survival mode. */
	private static final List<GameType> GAME_MODE_CYCLE =
			List.of(GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR, GameType.SURVIVAL);
	/** Ticks to wait after a game mode change before checking that the player still hovers. */
	private static final int HOVER_CHECK_TICKS = 10;

	@Override
	public void runTest(final ClientGameTestContext context) {
		configure(context, config -> {
			config.setEnabled(true);
			config.setSingleplayerDefault(true);
			config.setResetOnWorldExit(false);
			config.setResetOnGameExit(false);
		});
		KeyMapping toggleKey = bindKey(context, FlightClient.TOGGLE_KEY_NAME, "key.keyboard.j");
		KeyMapping openSettingsKey = bindKey(context, FlightClient.OPEN_SETTINGS_KEY_NAME, "key.keyboard.k");

		// Commands are allowed so that the game rules can be edited from the World Options screen, as in the README.
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
				.adjustSettings(settings -> settings.setAllowCommands(true))
				.create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			// Health must only change through fall damage while it is measured.
			server.runCommand("gamerule natural_health_regeneration false");
			setGameMode(context, server, GameType.SURVIVAL);
			waitForGround(context);
			check(!context.computeOnClient(client -> client.player.getAbilities().mayfly),
					"Survival mode should not grant the mayfly ability itself");

			checkTakesOff(context, "in Survival mode");
			checkGameModeChangesKeepFlight(context, server);
			descendToGround(context);
			check(!isFlying(context), "landing should end the flight");

			checkNotKickedInSingleplayer(context);
			checkFallDamage(context, server);

			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(!isEnabled(context), "toggle key should disable the feature");
			check(!loadSavedConfig().isEnabled(), "disabled state should be saved to the config file");
			context.takeScreenshot("flight-toggled-off");
			checkCannotFly(context, "in Survival mode with the mod disabled");
			checkVanillaCreativeFlight(context, server);

			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(isEnabled(context), "toggle key should enable the feature again");
			check(loadSavedConfig().isEnabled(), "enabled state should be saved to the config file");
			context.takeScreenshot("flight-toggled-on");

			checkTakesOff(context, "in Survival mode before turning the mod off mid-air");
			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(!isFlying(context), "turning the mod off should end the flight it allowed");
			waitForGround(context);
			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(isEnabled(context), "toggle key should enable the feature again");

			checkSettingsScreens(context, openSettingsKey);

			// Leave this world disabled to check the reset on world exit below.
			configure(context, config -> config.setResetOnWorldExit(true));
			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(!isEnabled(context), "toggle key should disable the feature before leaving");
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			check(isEnabled(context), "reset on world exit should restore the singleplayer default");

			configure(context, config -> config.setResetOnWorldExit(false));
			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(!isEnabled(context), "toggle key should disable the feature before leaving");
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			check(!isEnabled(context), "without reset on world exit the state should carry over");
		}

		configure(context, config -> config.setEnabled(true));
		unbindKey(context, toggleKey);
		unbindKey(context, openSettingsKey);
	}

	/**
	 * Switches through every game mode while flying and checks that the player keeps flying and hovering.
	 */
	private static void checkGameModeChangesKeepFlight(final ClientGameTestContext context, final TestServerContext server) {
		for (GameType mode : GAME_MODE_CYCLE) {
			double before = y(context);
			setGameMode(context, server, mode);
			context.waitTicks(HOVER_CHECK_TICKS);
			check(isFlying(context), "switching to " + mode.getName() + " mode should keep the player flying");
			double drop = before - y(context);
			LOGGER.info("Height lost in {} ticks after switching to {} mode while flying: {}",
					HOVER_CHECK_TICKS, mode.getName(), "%.2f".formatted(drop));
			check(drop < 0.1, "the player should keep hovering after switching to " + mode.getName() + " mode, but fell "
					+ drop + " blocks");
		}
	}

	/**
	 * Hovers longer than a server without allow-flight tolerates, then flies forward. The integrated server of a
	 * singleplayer world allows flight and skips the movement speed check for the world's owner, so the player stays
	 * connected.
	 */
	private static void checkNotKickedInSingleplayer(final ClientGameTestContext context) {
		checkTakesOff(context, "before hovering in singleplayer");
		context.waitTicks(MAX_FLOATING_TICKS * 2);
		context.getInput().holdKey(options -> options.keySprint);
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(MAX_FLOATING_TICKS / 2);
		context.getInput().releaseKey(options -> options.keyUp);
		context.getInput().releaseKey(options -> options.keySprint);
		check(context.computeOnClient(client -> client.level != null && client.player != null),
				"the owner of a singleplayer world should not be kicked for flying");
		check(isFlying(context), "the player should still be flying after hovering and flying forward");
		LOGGER.info("Still connected after hovering for {} ticks and flying forward for {} ticks in singleplayer",
				MAX_FLOATING_TICKS * 2, MAX_FLOATING_TICKS / 2);
		descendToGround(context);
	}

	/**
	 * Flies up and lands twice: once with the default game rules, where the server applies fall damage because it does
	 * not consider the player able to fly, and once with the {@code fall_damage} game rule turned off, as the README
	 * suggests.
	 */
	private static void checkFallDamage(final ClientGameTestContext context, final TestServerContext server) {
		float damage = flyUpAndLand(context, server);
		LOGGER.info("Fall damage after flying {} blocks up and landing with default game rules: {}", FALL_HEIGHT, damage);
		check(damage > 0.0F, "the server should apply fall damage after landing from a flight in Survival mode");

		server.runCommand("gamerule fall_damage false");
		check(!server.computeOnServer(s -> s.getGameRules().get(GameRules.FALL_DAMAGE)),
				"the fall_damage game rule should be off after the command from the README");
		damage = flyUpAndLand(context, server);
		LOGGER.info("Fall damage after flying {} blocks up and landing with fall_damage off: {}", FALL_HEIGHT, damage);
		check(damage == 0.0F, "landing should not hurt with the fall_damage game rule off, got " + damage);
	}

	/**
	 * Heals the player, takes off, climbs {@link #FALL_HEIGHT} blocks, sneaks down until landing, and returns the
	 * health lost.
	 */
	private static float flyUpAndLand(final ClientGameTestContext context, final TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			player.setHealth(player.getMaxHealth());
		});
		double ground = y(context);
		float healthBefore = serverHealth(server);
		checkTakesOff(context, "before measuring fall damage");
		context.getInput().holdKey(options -> options.keyJump);
		context.waitFor(client -> client.player.getY() >= ground + FALL_HEIGHT);
		context.getInput().releaseKey(options -> options.keyJump);
		descendToGround(context);
		context.waitTicks(5);
		return healthBefore - serverHealth(server);
	}

	private static float serverHealth(final TestServerContext server) {
		return server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth());
	}

	/**
	 * With the mod disabled, Creative mode flight and its end on switching to Survival mode stay vanilla.
	 */
	private static void checkVanillaCreativeFlight(final ClientGameTestContext context, final TestServerContext server) {
		setGameMode(context, server, GameType.CREATIVE);
		checkTakesOff(context, "in Creative mode with the mod disabled");
		setGameMode(context, server, GameType.SURVIVAL);
		check(!isFlying(context), "switching to Survival mode should end the flight while the mod is disabled");
		waitForGround(context);
	}

	/**
	 * Opens the settings with the key binding, then takes screenshots of both settings screens in English and in
	 * Simplified Chinese.
	 */
	private static void checkSettingsScreens(final ClientGameTestContext context, final KeyMapping openSettingsKey) {
		context.getInput().pressKey(openSettingsKey);
		context.waitForScreen(FlightConfigScreen.class);
		context.setScreen(() -> null);

		configure(context, config -> {
			config.setMultiplayerMode(MultiplayerMode.WHITELIST);
			config.setServers(List.of("mc.example.com", "192.168.1.5"));
		});
		// Keep the cursor away from the widgets so no tooltip covers them.
		context.getInput().setCursorPos(0, 0);
		takeSettingsScreenshots(context, "en_us");
		switchLanguage(context, "zh_cn");
		takeSettingsScreenshots(context, "zh_cn");
		switchLanguage(context, "en_us");

		configure(context, config -> {
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});
	}

	private static void takeSettingsScreenshots(final ClientGameTestContext context, final String language) {
		context.setScreen(() -> new FlightConfigScreen(null));
		context.takeScreenshot("flight-config-screen-" + language);
		context.getInput().scroll(-20);
		context.waitTick();
		context.takeScreenshot("flight-config-screen-bottom-" + language);

		context.setScreen(() -> new ServerListScreen(new FlightConfigScreen(null), FlightClient.config()));
		context.takeScreenshot("flight-server-list-" + language);
		context.setScreen(() -> null);

		takeGameRuleScreenshot(context, "fall", "game-rules-fall-" + language);
		takeGameRuleScreenshot(context, "movement", "game-rules-movement-" + language);
	}

	/**
	 * Follows the path the README gives to the game rules (Game Menu, World Options, Edit Game Rules) and takes
	 * screenshots of the World Options screen and of the rules matching {@code search}. The game rules button sits in
	 * a scrolling container that {@code clickScreenButton} cannot reach, so the rules screen is opened the same way
	 * the button opens it.
	 */
	private static void takeGameRuleScreenshot(final ClientGameTestContext context, final String search,
			final String name) {
		context.setScreen(() -> new PauseScreen(true));
		context.clickScreenButton("options.worldOptions.button");
		context.waitForScreen(WorldOptionsScreen.class);
		context.takeScreenshot("world-options-" + name);
		context.setScreen(() -> {
			Minecraft client = Minecraft.getInstance();
			Screen worldOptions = client.gui.screen();
			return new InWorldGameRulesScreen(client.player.connection,
					rules -> client.gui.setScreen(worldOptions), worldOptions);
		});
		// The screen asks the server for the current values first.
		context.waitTicks(10);
		context.getInput().typeChars(search);
		context.waitTick();
		context.takeScreenshot(name);
		context.setScreen(() -> null);
	}

	private static void switchLanguage(final ClientGameTestContext context, final String language) {
		CompletableFuture<Void> reload = context.computeOnClient(client -> {
			client.getLanguageManager().setSelected(language);
			client.options.languageCode = language;
			return client.reloadResourcePacks();
		});
		context.waitFor(client -> reload.isDone() && client.gui.overlay() == null);
	}
}
