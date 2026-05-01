# OpenPlayer

OpenPlayer is an open, multi-loader AI NPC framework for Minecraft. The project targets a legally clean foundation for Player2NPC-style functionality while keeping implementation, dependencies, and build inputs auditable.

## Architecture

- `common`: loader-neutral constants, lifecycle seams, public AI player NPC API contracts, runtime entity registration, and pure Java command intent types.
- `fabric`: Fabric entrypoints that delegate to common initialization.
- `forge`: Forge entrypoints and client event wiring that delegate to common initialization.

The initial target is Minecraft 1.20.1 on Java 17 with an Architectury-style multiloader Gradle layout.

## Milestone Status

Phase 4 adds owner/profile-aware runtime session reuse and duplicate prevention for server-spawnable OpenPlayer NPC entities. Command execution is still explicitly rejected, and networking, GUI, skin loading, pathfinding automation, and LLM provider calls are not implemented.

## Dependencies

- Architectury API `9.2.14` is used from public Maven coordinates for shared Fabric and Forge entity registration and lifecycle hooks. Architectury API is LGPL-3.0-only.

## Roadmap

- Establish loader-neutral NPC domain contracts.
- Add entity, persistence, and networking slices.
- Add safe command parsing and provider-backed LLM integration.
- Evaluate LangChain4j for LLM provider abstraction.
- Evaluate Baritone and Automatone integrations for movement automation.
- Add player skin and profile support.

## License

OpenPlayer is licensed under `AGPL-3.0-only`. See `LICENSE`.
