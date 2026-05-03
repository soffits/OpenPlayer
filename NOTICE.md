# OpenPlayer Notice

OpenPlayer is an independent implementation. Player2NPC by Goodbird-git is treated as product inspiration and behavioral reference only. OpenPlayer must not copy Player2NPC source code line-for-line or vendor opaque jars.

## Reference Posture

- Player2NPC: reference and inspiration only; decompiled behavior may be inspected for ideas, but no copied implementation code.
- PlayerEngine: not vendored and not required; direct vendoring of opaque PlayerEngine jars remains forbidden. Decompiled behavior may be inspected for ideas, but no copied implementation code.

## Dependency Posture

- Architectury API: included from public Maven coordinates as `dev.architectury:architectury`, `dev.architectury:architectury-fabric`, and `dev.architectury:architectury-forge` at version `9.2.14`; reviewed as LGPL-3.0-only and used as the multiloader framework for shared Fabric and Forge development.
- The built-in automation layer uses vanilla Minecraft runtime APIs. No pathfinding or automation jar is vendored or redistributed by this repository.

## Planned Dependency Posture

- LangChain4j: possible future LLM integration layer; not included yet.
- Automatone: possible future automation integration; not included yet. Stable public Maven Central or Modrinth coordinates were not identified in the quick check.

Any dependency added later must be introduced from public coordinates with its license and provenance reviewed before use. Future automation integrations also need Minecraft 1.20.1 loader compatibility validation and a clean adapter boundary.
