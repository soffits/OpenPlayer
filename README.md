# OpenPlayer

OpenPlayer adds AI companions to Minecraft as player-shaped NPCs. They can live on your world or server with local profiles, local skins, chat, status commands, and a growing set of ordinary Minecraft actions.

It is built for local ownership. Profiles, assignments, skins, logs, and provider settings live on your machine or server. You can use it without an online character service, account marketplace, or hosted bot platform.

## What it can do

- Spawn and manage player-like companion NPCs.
- Use local `.properties` profiles and local PNG skins.
- Chat with companions through in-game controls or `/openplayer` commands.
- Configure an OpenAI-compatible provider when you want model-backed chat or planning.
- Let trusted companions attempt Minecraft actions such as following, moving, looking, collecting drops, using inventory helpers, breaking or placing blocks, simple resource planning, defensive combat, and status reporting.
- Keep actions server-side and permission-gated instead of letting model text directly control the world.

OpenPlayer is still an alpha. Some Minecraft mechanics are not wired yet, especially complex UI workflows, long-distance pathing, and special cases such as fishing, trading, mounts, or modded machines. When something is missing, the mod should say what blocked it instead of pretending the action worked.

## Install

Download the Fabric or Forge jar that matches your Minecraft setup from GitHub Releases. Install the matching Architectury API for your loader, then open the in-game controls with `O` by default.

OpenPlayer targets Minecraft `1.20.1` and Java `17`.

## Configuration

Local characters are Java `.properties` files under `<Minecraft config>/openplayer/characters`. Optional assignment files live under `<Minecraft config>/openplayer/assignments`, and local PNG skins live under `<Minecraft config>/openplayer/skins`.

```properties
id=alex_helper
displayName=Alex Helper
description=Local companion example.
localSkinFile=skins/alex_helper.png
conversationPrompt=Helpful, concise Minecraft companion.
allowWorldActions=false
```

Provider settings can come from JVM system properties, environment variables, or the in-game provider page:

- `OPENPLAYER_INTENT_PROVIDER_ENDPOINT`
- `OPENPLAYER_INTENT_PROVIDER_API_KEY`
- `OPENPLAYER_INTENT_PROVIDER_MODEL`

Do not put provider keys, passwords, tokens, cookies, or other credentials in character files, screenshots, logs, strategy notes, or release artifacts.

## Build

```sh
./gradlew build
```

## Docs

See `docs/` for commands, tool details, and roadmap notes.

## License

OpenPlayer is licensed under `AGPL-3.0-only`. See `LICENSE`.

## References and credits

- <https://minecraft.net/>
- <https://architectury.dev/>
- <https://fabricmc.net/>
- <https://files.minecraftforge.net/>
