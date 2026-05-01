# OpenPlayer

OpenPlayer is an open, multi-loader AI NPC framework for Minecraft. The project targets a legally clean foundation for Player2NPC-style functionality while keeping implementation, dependencies, and build inputs auditable.

## Architecture

- `common`: loader-neutral constants, lifecycle seams, public AI player NPC API contracts, and pure Java command intent types.
- `fabric`: Fabric entrypoints that delegate to common initialization.
- `forge`: Forge entrypoints and client event wiring that delegate to common initialization.

The initial target is Minecraft 1.20.1 on Java 17 with an Architectury-style multiloader Gradle layout.

## Milestone Status

Phase 2 adds public API and domain contracts for AI player NPC roles, sessions, profiles, and command submission. It still does not implement playable NPC entities, networking, GUI, skin loading, pathfinding automation, or LLM provider calls.

## Roadmap

- Establish loader-neutral NPC domain contracts.
- Add entity, persistence, and networking slices.
- Add safe command parsing and provider-backed LLM integration.
- Evaluate LangChain4j for LLM provider abstraction.
- Evaluate Baritone and Automatone integrations for movement automation.
- Add player skin and profile support.

## License

OpenPlayer is licensed under `AGPL-3.0-only`. See `LICENSE`.
