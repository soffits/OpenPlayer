# OpenPlayer

OpenPlayer is an open, multi-loader AI NPC framework for Minecraft. The project targets a legally clean foundation for Player2NPC-style functionality while keeping implementation, dependencies, and build inputs auditable.

## Architecture

- `common`: loader-neutral constants, lifecycle seams, public AI player NPC API contracts, runtime entity registration, pure Java command intent types, and an opt-in provider-backed intent parser seam.
- `fabric`: Fabric entrypoints that delegate to common initialization.
- `forge`: Forge entrypoints and client event wiring that delegate to common initialization.

The initial target is Minecraft 1.20.1 on Java 17 with an Architectury-style multiloader Gradle layout.

## Milestone Status

Current implementation includes runtime NPC sessions, duplicate prevention, basic command intents, item pickup with inventory persistence, owner lifecycle cleanup and restore, spawn/despawn networking, a minimal client control screen, a default player-shaped renderer, and an opt-in JDK-only OpenAI-compatible intent provider. Runtime provider wiring remains disabled by default, and pathfinding automation is not implemented.

## Dependencies

- Architectury API `9.2.14` is used from public Maven coordinates for shared Fabric and Forge entity registration and lifecycle hooks. Architectury API is LGPL-3.0-only.
- The OpenAI-compatible intent provider uses only Java 17 `java.net.http.HttpClient` and adds no external dependency.
- No pathfinding or automation dependency is included. Direct PlayerEngine vendoring remains forbidden, and any future Baritone or Automatone integration must first identify public coordinates, confirm Minecraft 1.20.1 loader compatibility, complete license and provenance review, and use a small adapter seam.

## Roadmap

- Establish loader-neutral NPC domain contracts.
- Add entity, persistence, and networking slices.
- Wire safe command parsing and provider-backed LLM integration into runtime configuration.
- Evaluate Baritone and Automatone integrations for movement automation once public coordinates, loader/version compatibility, license posture, and adapter boundaries are clear.
- Add player skin and profile support.

## License

OpenPlayer is licensed under `AGPL-3.0-only`. See `LICENSE`.
