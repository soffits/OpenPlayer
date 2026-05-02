# OpenPlayer

OpenPlayer is an open, multi-loader AI NPC framework for Minecraft. The project targets a legally clean foundation for Player2NPC-style functionality while keeping implementation, dependencies, and build inputs auditable.

## Architecture

- `common`: loader-neutral constants, lifecycle seams, public AI player NPC API contracts, runtime entity registration, pure Java command intent types, runtime intent parser configuration, an opt-in provider-backed intent parser seam, and an automation backend seam.
- `fabric`: Fabric entrypoints that delegate to common initialization.
- `forge`: Forge entrypoints and client event wiring that delegate to common initialization.

The initial target is Minecraft 1.20.1 on Java 17 with an Architectury-style multiloader Gradle layout.

## Milestone Status

Current implementation includes runtime NPC sessions, duplicate prevention, basic command intents, item pickup with inventory persistence, owner lifecycle cleanup and restore, spawn/despawn networking, a minimal client control screen with safe runtime status, a default player-shaped renderer, a default vanilla automation backend, an optional reflective Baritone command bridge, and a disabled-by-default runtime intent parser that can use an opt-in JDK-only OpenAI-compatible provider. Automatone is not directly integrated yet.

## Dependencies

- Architectury API `9.2.14` is used from public Maven coordinates for shared Fabric and Forge entity registration and lifecycle hooks. Architectury API is LGPL-3.0-only.
- The OpenAI-compatible intent provider uses only Java 17 `java.net.http.HttpClient` and adds no external dependency.
- No pathfinding or automation jar is vendored. Direct PlayerEngine vendoring remains forbidden.
- Baritone is supported only through an optional reflective command bridge. OpenPlayer does not add a hard Gradle dependency on Baritone; install a Minecraft 1.20.1-compatible Baritone API/mod separately when using `OPENPLAYER_AUTOMATION_BACKEND=baritone`.
- Baritone upstream publishes Minecraft 1.20.1 Fabric and Forge API jars from public GitHub releases and marks the project as LGPL-3.0 with an anime exception. This repository does not redistribute those jars.

## Runtime Intent Parser

Raw command text submitted through the public NPC service is parsed by the runtime-owned intent parser before becoming an `AiPlayerNpcCommand`. The parser is disabled by default and returns unavailable intents without contacting a provider.

Set safe JVM system properties or environment variables with these exact names to enable the OpenAI-compatible provider:

- `OPENPLAYER_INTENT_PARSER_ENABLED=true`
- `OPENPLAYER_INTENT_PROVIDER_ENDPOINT=https://example.invalid/v1/chat/completions`
- `OPENPLAYER_INTENT_PROVIDER_API_KEY=...`
- `OPENPLAYER_INTENT_PROVIDER_MODEL=...`

## Automation Backend

Runtime command execution goes through a small automation backend seam. The default backend is `vanilla`, which preserves the current Minecraft navigation behavior for move, look, follow owner, and stop commands. Set `OPENPLAYER_AUTOMATION_BACKEND=disabled` to reject automation commands without adding any external automation dependency.

Set `OPENPLAYER_AUTOMATION_BACKEND=baritone` to try the optional Baritone command bridge. The bridge reflects `baritone.api.BaritoneAPI` at runtime and reports `baritone (unavailable)` if Baritone classes, the primary Baritone instance, or the Baritone command manager are unavailable. Supported commands are currently limited to `stop`, `goto x y z`, and `follow players` after owner availability is checked.

The Baritone backend is intentionally honest about scope: stock Baritone controls the primary Baritone-managed local player, not arbitrary server-side OpenPlayer NPC entities. This phase is not true NPC entity pathfinding. Future work must add a real NPC-backed pathfinding adapter before claiming Baritone drives `OpenPlayerNpcEntity` directly.

## Roadmap

- Establish loader-neutral NPC domain contracts.
- Add entity, persistence, and networking slices.
- Evaluate provider-backed command parsing behavior in runtime playtesting.
- Expand Baritone support from the current optional local-player command bridge into real NPC-backed pathfinding if a clean adapter boundary is identified.
- Evaluate Automatone integration for movement automation once public coordinates, loader/version compatibility, license posture, and adapter boundaries are clear.
- Add player skin and profile support.

## License

OpenPlayer is licensed under `AGPL-3.0-only`. See `LICENSE`.
