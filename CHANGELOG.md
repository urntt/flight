# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each version carries the targeted Minecraft version as build metadata, for example `1.0.0+26.3`.

## [Unreleased]

## [1.0.0+26.3] - 2026-10-02

### Added

- Let the local player fly like in Creative mode in every game mode, including Survival and Adventure: double-tap jump to take off.
- Keep the player flying when the game mode changes while the mod is enabled. Landing or double-tapping jump still ends the flight, and turning the mod off ends a flight that only the mod allowed.
- Add a "Toggle Flight Ability" key binding, unbound by default, that turns the ability to take off on or off and shows the new state on the action bar.
- Add a configuration screen built from vanilla widgets, with separate defaults for singleplayer worlds and allowed servers, and options to reset to the default on world exit or game exit. The mod is enabled by default.
- Add multiplayer modes (disabled, whitelist, blacklist) and a server list screen for editing the addresses they use. The mod is disabled on multiplayer servers by default.
- Add an "Open flight Settings" key binding, unbound by default, so the configuration screen is available without Mod Menu, which is optional.
- Save all settings to `config/flight.json`.
- Add English and Simplified Chinese translations.
- Add a mod icon.
