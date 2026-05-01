# OpenPlayer Notice

OpenPlayer is an independent implementation. Player2NPC by Goodbird-git is treated as product inspiration and behavioral reference only. OpenPlayer must not copy Player2NPC source code line-for-line or vendor opaque jars.

## Reference Posture

- Player2NPC: reference and inspiration only; decompiled behavior may be inspected for ideas, but no copied implementation code.
- PlayerEngine: not vendored and not required for this foundation slice; decompiled behavior may be inspected for ideas, but no copied implementation code.

## Dependency Posture

- Architectury API: included from public Maven coordinates as `dev.architectury:architectury`, `dev.architectury:architectury-fabric`, and `dev.architectury:architectury-forge` at version `9.2.14`; reviewed as LGPL-3.0-only and used as the multiloader framework for shared Fabric and Forge development.

## Planned Dependency Posture

- LangChain4j: possible future LLM integration layer; not included in Phase 1.
- Baritone: possible future pathfinding automation integration; not included in Phase 1.
- Automatone: possible future automation integration; not included in Phase 1.

Any dependency added later must be introduced from public coordinates with its license and provenance reviewed before use.
