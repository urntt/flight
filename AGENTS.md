# AGENTS.md

Instructions for AI coding agents working in this repository. `CLAUDE.md` imports this file, so keep all agent guidance here.

## Project

`flight` is a client-side [Fabric](https://fabricmc.net/) mod for Minecraft: Java Edition that lets the local player fly like in Creative mode in every game mode, including Survival and Adventure: double-tapping jump takes off.

The sibling mod [urntt/nojumpdelay](https://github.com/urntt/nojumpdelay) has the same structure, and its configuration options and screens were the model for this mod's. Consult it for verified API usage, but do not add a dependency between the two mods.

## Project decisions

These decisions are settled. Do not deviate from them without the user's explicit approval.

### Minecraft and toolchain

- Development started on Minecraft 26.3 with Java 25. `gradle.properties` is the single source of truth for the mod, Minecraft, Fabric Loader, Loom, Fabric API, Mod Menu, and Java versions. `build.gradle`, `fabric.mod.json`, the mixin config, and the CI workflows read them from there; do not restate them elsewhere.
- Follow the latest official Fabric template ([FabricMC/fabric-example-mod](https://github.com/FabricMC/fabric-example-mod), also available from the [template generator](https://fabricmc.net/develop/template/)): the `net.fabricmc.fabric-loom` Gradle plugin, Mojang's official names with no `mappings` dependency, and `implementation` (not `modImplementation`) for dependencies. Do not use Yarn.
- Pin `loom_version` to a release version instead of the template's `-SNAPSHOT`, so builds are reproducible.
- Target only the latest stable (release) Minecraft version. Updates, fixes, and new features are always developed against it. Snapshots, pre-releases, and release candidates are not supported targets.
- Do not maintain older Minecraft versions and do not set up multi-version builds (no per-version branches, no Stonecutter or other preprocessors). When a new stable version is released, port the mod to it and drop the previous one.

### Identity

| Item | Value |
| --- | --- |
| Mod ID | `flight` |
| Display name | `flight` |
| Maven group | `com.urntt` |
| Base package | `com.urntt.flight` |
| Version format | `<SemVer>+<Minecraft version>`, for example `1.0.0+26.3` |
| License | MIT |

The mod version itself follows [Semantic Versioning](https://semver.org/); the `+<Minecraft version>` suffix is build metadata naming the Minecraft version the build targets. Version numbers start at `1.0.0`.

### Distribution

- Releases are published only as GitHub Releases. Do not publish to Modrinth, CurseForge, or any other mod platform, and do not add publishing tooling for them.
- `README.md` must clearly warn that this is a movement modification and that the server still treats the player as unable to fly:
  - Using it in multiplayer may conflict with server anti-cheat systems and server rules, and may get the player set back, kicked, or banned. Servers that do not allow flight kick a player who floats too long.
  - Landing after a flight causes fall damage. Explain how to turn it off with the `fall_damage` game rule, by command and through the world settings.
  - The server's player movement check can move the player back. Explain where it applies and how to turn it off with the `player_movement_check` game rule, by command and through the world settings.
  - The mod does not try to get around any anti-cheat.
- Keep these warnings accurate: they are verified by the client game tests (fall damage, the game rule commands and screens, the kick on a server without `allow-flight`, no kick for the owner of a singleplayer world).

### Scope and behavior

- Client-only: `fabric.mod.json` declares `"environment": "client"`. There is no server-side component and no networking.
- The mod only lets the player toggle vanilla Creative flight by double-tapping jump. Flight itself stays vanilla: its speed, its controls, and landing ending it.
- Out of scope, and must not be added: changing flight speed, noclip, preventing fall damage, and anything that hides flight from the server or tries to get around anti-cheat (for example faking the on-ground flag, altering or suppressing movement packets, or avoiding the floating kick).
- Only the local player (`LocalPlayer`) is affected. The `mayfly` ability keeps the value the game mode and the server set, so fall damage, sprinting while hungry, and everything else that reads it stay vanilla; only the double-tap check in `LocalPlayer.aiStep` treats the player as allowed to fly.
- While the feature is active, game mode changes and ability updates from the server never end the flight; they may still start it, as in vanilla. Only the player ends the flight, by double-tapping jump or by landing.
- When the feature stops being active (toggled off, or the current server is ruled out), a flight that only the mod allowed (flying without `mayfly`) ends.
- `FlightController` is the single owner of whether the feature is active, which is the toggle state being on and the current scene (singleplayer or a multiplayer server, determined on join) being allowed, and of the rule that keeps the player flying across ability updates.
- The toggle state is enabled by default. A configurable key binding, "Toggle Flight Ability", toggles it; it switches whether the player can take off, not the flying state itself. It is unbound by default. Each toggle shows the new state on the action bar and saves it to the configuration file, so it persists across game restarts.
- Defaults depend on the scene: a singleplayer default (worlds hosted by this client, including ones opened to LAN) and a server default (servers the multiplayer mode allows). Both are on by default.
- Reset rules restore the scene's default when the player joins an allowed scene: "reset on world exit" for every world, and "reset on game exit" for the first allowed world after the game starts. Both are off by default. Resets happen on join so that they use the next scene's default and still work after a crash.
- The multiplayer mode is a hard limit: `DISABLED` (the default) rules out every server, `WHITELIST` allows only servers in the server list, and `BLACKLIST` allows every server except those in it. On a ruled-out server the feature stays off and the toggle key only reports that it is disabled there. Joining another player's LAN world or a Realm counts as multiplayer.
- Server list entries match the connected address by host (case-insensitive, after IDN conversion, and required to be a valid domain name or IP address) and by port only when the entry specifies one.
- The configuration screen is built from vanilla widgets and opens through Mod Menu or a second key binding, "open settings", which is also unbound by default.

### Localization

- All user-facing text, including key binding names, the key binding category, action bar messages, and the configuration screen, uses translation keys. Never hard-code display strings.
- Provide translations for `en_us` and `zh_cn`, and keep both complete whenever a translation key is added or changed.

### Dependencies

- Required: Fabric Loader and Fabric API.
- Optional: Mod Menu, declared under `suggests` in `fabric.mod.json`. The mod must load and work normally without it, so Mod Menu classes may only be referenced from the Mod Menu entrypoint.
- Configuration is hand-written without a config library: a JSON file in the Fabric config directory, serialized with Gson (bundled with Minecraft). Any configuration screen uses vanilla widgets.
- Do not add other dependencies without the user's explicit approval.

### Implementation

- Language: Java only.
- Source sets: `src/main` holds only `fabric.mod.json` and the icon. All code and client resources live in `src/client`, and the client game tests live in `src/gametest`.
- Mixins: prefer the MixinExtras injectors bundled with Fabric Loader (for example `@ModifyExpressionValue` and `@WrapOperation`) over `@Redirect` and `@Overwrite`, to stay compatible with other mods and keep porting work small.

### Testing

- The client game tests in `src/gametest` cover the address matching, the defaults, the reset rules and the ability update rule (`FlightLogicGameTest`); taking off, game mode changes, landing, fall damage and the `fall_damage` game rule, staying connected in singleplayer, the toggle key, reset on world exit, and the settings and game rule screens in singleplayer (`FlightClientGameTest`); and each multiplayer mode and the vanilla kick for floating on a local dedicated server (`FlightMultiplayerGameTest`). Keep them passing and extend them when behavior changes.
- The dedicated server needs `eula = true` in the `configureTests` block of `build.gradle`; it accepts the Minecraft EULA only for that local test server.
- After porting to a new Minecraft version, run the client game tests. A successful build does not prove that the mixins still have the intended effect.
- `README.md` describes how to run them, including on a headless machine.

### CI, releases, and changelog

- GitHub Actions (`.github/workflows/build.yml`) builds the project and runs the client game tests on every push and pull request.
- Maintain `CHANGELOG.md` following [Keep a Changelog](https://keepachangelog.com/). Record every user-visible change under `Unreleased` in the same change that introduces it.
- `.github/workflows/release.yml` builds the mod and publishes a GitHub Release for the project version, with the jar attached and the matching `CHANGELOG.md` section as release notes. It runs when a tag `v<version>` (for example `v1.0.0+26.3`) is pushed, or when started manually on a branch, in which case it creates that tag on the branch's latest commit. It fails if a pushed tag does not match the project version, if the changelog has no section for the version, or if the release already exists.
- Release only when the user asks. To release, set `mod_version` in `gradle.properties`, rename `Unreleased` in `CHANGELOG.md` to `[<version>] - <YYYY-MM-DD>` above a new empty `Unreleased` section, commit, and push. Then start the release workflow on `main`. Claude Code cloud sessions cannot push tags, so start the workflow through the GitHub Actions API instead.

## Engineering principles

- Fix root causes, not symptoms. Diagnose the underlying cause before implementing a permanent fix. If an immediate mitigation is necessary, treat it as temporary and follow through with a root-cause fix.
- Prefer configuration-driven design for values that are expected to vary by environment, deployment, or product requirements. Avoid unexplained or duplicated magic values, but do not introduce configuration where a well-named constant is the clearer source of truth.
- Preserve a single source of truth and clear ownership for data, state, configuration, business logic, and authoritative documentation. Avoid duplicating canonical information across multiple locations.
- Do not maintain parallel legacy and replacement implementations without an explicit migration and removal plan.

## Documentation

- Keep documentation aligned with the code. When a code change affects documented behavior, APIs, architecture, configuration, workflows, or usage, update the relevant documentation in the same change.
- Keep each document's responsibility clear. For example, use `README.md` for project overview and usage, and `VISION.md` for product direction, architectural principles, or long-term decisions.
- Always specify a language identifier for fenced code blocks in Markdown.

## Language

- Communicate with the user in Chinese, including explanations, progress updates, and user-facing planning.
- Use English for development artifacts, including source code, comments, docstrings, documentation, READMEs, Git branch names, commit messages, and other deliverables intended to live in the repository.

## Git

- Do not change or override the Git author or committer identity. When an identity must be configured for commits created during the task, use:
  - Name: `urntt`
  - Email: `urntts@gmail.com`
- Do all actions on the user's behalf. Do not rewrite existing commit authorship unless explicitly requested. Do not add `Co-Authored-By` trailers or session links to commit messages or pull request descriptions.
- Develop on `main` and push directly to it. Branches and pull requests are not required.
- Because changes land on `main` without review, make sure `./gradlew build` and the client game tests pass locally before pushing.
- If a branch is used, give it a category-based prefix that reflects the purpose of the change, such as `feat/`, `fix/`, `refactor/`, `docs/`, `test/`, or `chore/`.
- Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification for commit messages.
