# flight

A simple client-side Fabric mod for Minecraft: Java Edition that lets you fly like in Creative mode in every game mode, including Survival and Adventure.

Double-tap jump (Space by default) to take off, just like in Creative mode. Flying works the same way too: hold jump to rise, sneak to descend, sprint to fly faster, and double-tap jump again or land to stop. Switching game modes while flying keeps you flying. Only your own player is affected.

## Warnings

**This mod is disabled in multiplayer by default. To use it in multiplayer, change the settings on its configuration screen.**

The mod only changes your game client. The server still treats you as a player who cannot fly, which has the consequences below. **The mod does not try to get around any anti-cheat**, and it does not hide your flight from the server in any way.

### Multiplayer

This mod changes player movement. Servers that run anti-cheat systems may detect it, which can get your movement set back, get you kicked, or get you banned, and using it may break a server's rules. On some servers, and in some server game modes such as minigames, flying gets you kicked or banned. Check each server's rules before joining with this mod enabled. Use it in multiplayer at your own risk.

Servers that do not allow flight, which is the default (`allow-flight=false` in `server.properties`), kick a player who stays in the air for about four seconds with "Flying is not enabled on this server". Only the server's owner can change this setting.

### Fall damage

Because the server does not know that you are flying, it applies fall damage when you land, counting the height you came down since you last moved up. For example, flying 10 blocks up and landing costs 7 health (3.5 hearts), and coming down slowly does not help.

To turn fall damage off in a world, set the `fall_damage` game rule to `false`:

- **Command:** `/gamerule fall_damage false`
- **World settings:** open the game menu, then **World Options... → Edit Game Rules... → Player → Deal fall damage**, set it to **OFF**, and click **Done**. When creating a world, the same rule is under **More → Game Rules**.

Changing game rules in an existing world needs commands to be allowed in it. If they are not, turn on **Allow Commands** on the **World Options** screen, click **Apply Changes**, and open **World Options** again. On a server, only its operators can change game rules.

### Player movement check

The server checks how fast players move and moves a player back when the movement looks too fast. Singleplayer worlds run on a built-in server too: the world's owner is exempt from this check there, but players who join the world through **Open to LAN** are checked, and so is everyone on a server. To turn the check off, set the `player_movement_check` game rule to `false`:

- **Command:** `/gamerule player_movement_check false`
- **World settings:** open the game menu, then **World Options... → Edit Game Rules... → Player → Do player movement check**, set it to **OFF**, and click **Done**. When creating a world, the same rule is under **More → Game Rules**.

This game rule does not affect the kick for flying on servers that do not allow flight.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download the jar from this repository's [Releases](https://github.com/urntt/flight/releases) page. Each release supports a single Minecraft version, shown after the `+` in its version number. For example, `1.0.0+26.3` is for Minecraft 26.3.
3. Put the jar into your `.minecraft/mods` folder.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional. When installed, it opens the mod's configuration screen from its mod list.

## Usage

The mod is enabled by default in singleplayer and disabled on multiplayer servers.

While the mod is enabled, double-tap jump to take off in any game mode. Game mode changes never end the flight; only double-tapping jump or landing does. Turning the mod off mid-flight drops you, unless your game mode lets you fly anyway (Creative or Spectator).

The mod adds two key bindings in **Options → Controls → Key Binds**, both unbound by default:

- **Toggle Flight Ability** turns the mod on or off, that is, whether double-tapping jump can take off, and shows the new state on the action bar. It does not start a flight by itself; turning it off mid-flight drops you as described above. On a server that the multiplayer settings rule out, it only shows that the mod is disabled there.
- **Open flight Settings** opens the configuration screen. With Mod Menu installed, you can also open it from the mod list.

### Settings

All settings are saved to `config/flight.json` as soon as you change them.

| Setting | Default | Meaning |
| --- | --- | --- |
| Flight Ability | On | The current state, the same one the toggle key switches. |
| Singleplayer Default | On | The state a reset restores in singleplayer worlds, including worlds you open to LAN. |
| Server Default | On | The state a reset restores on servers that the multiplayer mode allows. |
| Reset on World Exit | Off | Every world starts from its default state instead of keeping the last state. |
| Reset on Game Exit | Off | After restarting the game, the first world where the mod is allowed starts from its default state. |
| Multiplayer mode | Disabled | **Disabled**: never active on servers. **Whitelist**: active only on servers in the server list. **Blacklist**: active on all servers except those in the server list. |
| Server List | Empty | The addresses the whitelist and blacklist modes use. |

The multiplayer mode is a hard limit: on a server it rules out, the mod stays off whatever the current state is. Joining another player's LAN world or a Realm counts as multiplayer.

Server list entries are compared with the address you connect to, ignoring upper and lower case. An entry without a port, such as `mc.example.com`, matches the server on any port, while an entry with a port, such as `mc.example.com:25566`, matches only that port. The server list screen marks invalid addresses in red and does not save until they are fixed or removed.

## Development

Building requires the JDK version set by `java_version` in `gradle.properties`.

Build the mod:

```bash
./gradlew build
```

The jar is written to `build/libs/`.

Run the client game tests, which start Minecraft and check taking off, game mode changes, fall damage and the game rules in singleplayer, the multiplayer modes and the kick for flying on a local dedicated server, the key bindings, the reset rules, and the saved configuration:

```bash
./gradlew runClientGameTest
```

The game tests need a display. On a headless Linux machine, run them under Xvfb. Xvfb offers no sRGB-capable OpenGL visuals, so install Mesa's Vulkan driver (`mesa-vulkan-drivers` on Ubuntu) for the game to fall back to:

```bash
xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest
```

Screenshots taken by the tests are saved to `build/run/clientGameTest/screenshots/`.

The multiplayer tests start a local dedicated server, so the test setup in `build.gradle` accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA) for that test server.

## License

[MIT](LICENSE)
