package com.urntt.flight;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.flight.config.FlightConfig;
import com.urntt.flight.config.FlightConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class FlightClient implements ClientModInitializer {
	public static final String MOD_ID = "flight";
	public static final String TOGGLE_KEY_NAME = "key.flight.toggle";
	public static final String OPEN_SETTINGS_KEY_NAME = "key.flight.open_settings";

	/** Translation key of the feature's name, shared by the toggle message and the configuration screen. */
	public static final String FEATURE_NAME_KEY = "options.flight.enabled";
	public static final Component FEATURE_NAME = Component.translatable(FEATURE_NAME_KEY);
	/** Action bar message shown when the toggle key is pressed on a server the multiplayer rules rule out. */
	public static final Component BLOCKED_MESSAGE = Component.translatable("message.flight.blocked");

	private static FlightConfig config;
	private static FlightController controller;

	@Override
	public void onInitializeClient() {
		config = FlightConfig.load(FlightConfig.defaultPath());
		controller = new FlightController(config);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
		KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(TOGGLE_KEY_NAME, InputConstants.UNKNOWN.getValue(), category));
		KeyMapping openSettingsKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(OPEN_SETTINGS_KEY_NAME, InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				toggle(client);
			}
			while (openSettingsKey.consumeClick()) {
				client.gui.setScreen(new FlightConfigScreen(client.gui.screen()));
			}
		});

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> controller.onJoin(Scene.of(client)));
		// The disconnect event may arrive on the network thread; the controller is only used on the client thread.
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(controller::onDisconnect));
	}

	public static FlightConfig config() {
		return config;
	}

	public static FlightController controller() {
		return controller;
	}

	private static void toggle(final Minecraft client) {
		FlightController.ToggleResult result = controller.toggle();
		if (client.player == null) {
			return;
		}

		Component message = switch (result) {
			case ENABLED -> CommonComponents.optionStatus(FEATURE_NAME, true);
			case DISABLED -> CommonComponents.optionStatus(FEATURE_NAME, false);
			case BLOCKED -> BLOCKED_MESSAGE;
		};
		client.player.sendOverlayMessage(message);
	}
}
