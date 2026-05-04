# OpenPlayer

OpenPlayer is a clean-room, local-first AI companion runtime for Minecraft. It brings PlayerEngine-style NPC companionship into an auditable open-source mod without PlayerEngine, Player2NPC, opaque jars, online character services, account login, or remote skin downloads.

OpenPlayer targets Minecraft `1.20.1`, Java `17`, Fabric, and Forge through an Architectury-style multiloader build.

## Features

- Local companion profiles and assignments stored under `<Minecraft config>/openplayer`.
- Player-shaped NPC rendering with local PNG skins, held items, armor, head items, elytra, arrows, and bee stingers.
- Server-authoritative spawn, despawn, follow, stop, chat, status, and queued task controls through the OpenPlayer UI and `/openplayer` command tree.
- Optional OpenAI-compatible intent parsing through Java `HttpClient`; provider access is disabled until endpoint, model, and API key are configured.
- Bounded vanilla NPC actions for movement, following, patrol, inventory/equipment helpers, resource tasks, simple crafting/smelting/container interactions, farming, combat defense, building primitives, and truthful status diagnostics where adapters exist.
- Local strategy/meta-pack documentation for advisory planning without executable scripts or Java route planners.

## Local-First Safety

OpenPlayer treats provider output as untrusted. Text must parse into constrained intents, pass runtime validation, obey per-character `allowWorldActions`, and execute through reviewed Minecraft-side primitives or capability adapters.

OpenPlayer does not claim commercial PlayerEngine or Player2 online parity, and it does not promise every Minecraft behavior is implemented. Missing behavior should surface as a capability, adapter, policy, or world-state gap rather than fake success.

## Install

Download the loader jar that matches your Minecraft setup from GitHub Releases:

- `openplayer-fabric-1.20.1_1-alpha.1.jar`
- `openplayer-forge-1.20.1_1-alpha.1.jar`

Install the matching Architectury API for your loader. Open the in-game controls with `O` by default.

## Configuration

Local characters are Java `.properties` files under `<Minecraft config>/openplayer/characters`. Optional assignment files live under `<Minecraft config>/openplayer/assignments`; local PNG skins live under `<Minecraft config>/openplayer/skins`.

```properties
id=alex_helper
displayName=Alex Helper
description=Local companion example.
localSkinFile=skins/alex_helper.png
conversationPrompt=Helpful, concise Minecraft companion.
allowWorldActions=false
```

Provider settings can be supplied as JVM system properties, environment variables, or the in-game provider page:

- `OPENPLAYER_INTENT_PROVIDER_ENDPOINT`
- `OPENPLAYER_INTENT_PROVIDER_API_KEY`
- `OPENPLAYER_INTENT_PROVIDER_MODEL`

Never store provider keys, passwords, access tokens, cookies, or credentials in character files, strategy packs, screenshots, logs, or release artifacts.

## Build

```sh
./gradlew build
```

Release tags and `mod_version` use product-prefixed Minecraft-line names. Examples: `openplayer_1.20.1_1`, `openplayer_1.20.1_1-alpha.1`, `openplayer_1.20.1_1-beta.1`, `openplayer_1.20.1_1-rc.1`, `openplayer_1.20.1_2`, and `openplayer_1.21.1_1`.

Runtime artifact filenames keep the loader prefix and omit the repeated product prefix from `mod_version`, for example `openplayer-fabric-1.20.1_1.jar` and `openplayer-forge-1.20.1_1.jar`.

## Maintainer Docs

- `docs/playerengine-parity-roadmap.md` tracks the clean-room PlayerEngine-style runtime roadmap.
- `docs/strategy-meta-packs.md` documents local advisory strategy/meta packs.

## License

OpenPlayer is licensed under `AGPL-3.0-only`. See `LICENSE`.
