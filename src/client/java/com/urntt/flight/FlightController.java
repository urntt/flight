package com.urntt.flight;

import com.urntt.flight.config.FlightConfig;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether the local player may fly right now, from the configuration and the current {@link Scene}. It is the
 * only place that combines the toggle state, the defaults, the reset rules, and the multiplayer rules, and it owns the
 * rule that keeps the player flying across game mode changes.
 */
public final class FlightController {
	/** Outcome of {@link #toggle()}. */
	public enum ToggleResult {
		ENABLED,
		DISABLED,
		/** The current server is not allowed, so the toggle state was left unchanged. */
		BLOCKED
	}

	private final FlightConfig config;
	private Scene scene = Scene.NONE;
	private boolean allowedWorldJoinedSinceStart = false;

	public FlightController(final FlightConfig config) {
		this.config = config;
	}

	public Scene scene() {
		return this.scene;
	}

	/**
	 * Called when the player joins a world. On an allowed scene, restores the scene's default state if a reset rule
	 * applies: always with "reset on world exit", and for the first allowed world since the game started with
	 * "reset on game exit". Applying the reset on join instead of on exit lets it pick the next scene's default and
	 * also works after a crash.
	 */
	public void onJoin(final Scene scene) {
		this.scene = scene;
		if (!this.isAllowed()) {
			return;
		}

		boolean firstWorld = !this.allowedWorldJoinedSinceStart;
		this.allowedWorldJoinedSinceStart = true;
		if (this.config.resetOnWorldExit() || (this.config.resetOnGameExit() && firstWorld)) {
			this.config.setEnabled(this.defaultFor(scene));
		}
	}

	public void onDisconnect() {
		this.scene = Scene.NONE;
	}

	/**
	 * Returns whether the local player may fly now in every game mode.
	 */
	public boolean isActive() {
		return this.config.isEnabled() && this.isAllowed();
	}

	/**
	 * Returns whether the multiplayer rules allow the feature in the current scene.
	 */
	public boolean isAllowed() {
		return switch (this.scene) {
			case Scene.None none -> false;
			case Scene.Singleplayer singleplayer -> true;
			case Scene.Multiplayer multiplayer -> switch (this.config.multiplayerMode()) {
				case DISABLED -> false;
				case WHITELIST -> this.isListed(multiplayer.address());
				case BLACKLIST -> !this.isListed(multiplayer.address());
			};
		};
	}

	/**
	 * Returns the flying state after the game mode or the server changes the player's abilities. While the feature is
	 * active, such an update may start flight, as in vanilla, but never ends it: only the player ends flight, by
	 * double-tapping jump or by landing.
	 *
	 * @param wasFlying whether the player was flying before the update
	 * @param updated the flying state the update would set
	 */
	public boolean flyingAfterAbilityUpdate(final boolean wasFlying, final boolean updated) {
		return updated || (wasFlying && this.isActive());
	}

	/**
	 * Flips and saves the toggle state, unless the current server is not allowed.
	 */
	public ToggleResult toggle() {
		if (this.scene instanceof Scene.Multiplayer && !this.isAllowed()) {
			return ToggleResult.BLOCKED;
		}
		boolean enabled = !this.config.isEnabled();
		this.config.setEnabled(enabled);
		return enabled ? ToggleResult.ENABLED : ToggleResult.DISABLED;
	}

	private boolean defaultFor(final Scene scene) {
		return scene instanceof Scene.Singleplayer ? this.config.singleplayerDefault() : this.config.multiplayerDefault();
	}

	private boolean isListed(final @Nullable String address) {
		return address != null && this.config.servers().stream().anyMatch(entry -> ServerAddresses.matches(entry, address));
	}
}
