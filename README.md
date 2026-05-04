# OpenPlayer

OpenPlayer is a local-first AI companion mod for Minecraft. It provides player-shaped NPC companions with local profiles, local skins, bounded automation, and optional OpenAI-compatible intent parsing.

## Features

- Local companion profiles, assignments, and skins under the Minecraft config directory.
- Player-shaped NPC rendering with equipment and skin support.
- In-game controls and an `/openplayer` command tree for spawning, stopping, chatting, status, and queued tasks.
- Optional provider-backed conversation and intent parsing that stays disabled until explicitly configured.
- Reviewed Minecraft-side primitives for movement, following, inventory helpers, resource tasks, building primitives, defensive combat, and truthful diagnostics where adapters exist.

## Safety

OpenPlayer treats provider output as untrusted. Requests must parse into constrained intents, pass runtime validation, obey character policy, and execute through reviewed primitives or capability adapters.

OpenPlayer does not promise every Minecraft behavior is implemented. Missing behavior should be reported as a capability, adapter, policy, or world-state gap instead of fake success.

## Install

Download the loader jar that matches your Minecraft setup from GitHub Releases. Install the matching Architectury API for your loader. Open the in-game controls with `O` by default.

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

## Docs

See `docs/`.

## License

OpenPlayer is licensed under `AGPL-3.0-only`. See `LICENSE`.

## References/Credits

- <https://minecraft.net/>
- <https://architectury.dev/>
- <https://fabricmc.net/>
- <https://files.minecraftforge.net/>
