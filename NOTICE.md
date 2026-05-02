# OpenPlayer Notice

OpenPlayer is an independent implementation. Player2NPC by Goodbird-git is treated as product inspiration and behavioral reference only. OpenPlayer must not copy Player2NPC source code line-for-line or vendor opaque jars.

## Reference Posture

- Player2NPC: reference and inspiration only; decompiled behavior may be inspected for ideas, but no copied implementation code.
- PlayerEngine: not vendored and not required; direct vendoring of opaque PlayerEngine jars remains forbidden. Decompiled behavior may be inspected for ideas, but no copied implementation code.

## Dependency Posture

- Architectury API: included from public Maven coordinates as `dev.architectury:architectury`, `dev.architectury:architectury-fabric`, and `dev.architectury:architectury-forge` at version `9.2.14`; reviewed as LGPL-3.0-only and used as the multiloader framework for shared Fabric and Forge development.
- The current automation backend seam uses vanilla Minecraft runtime APIs only. Baritone and Automatone are not directly integrated and no automation jars are vendored.

## Planned Dependency Posture

- LangChain4j: possible future LLM integration layer; not included yet.
- Baritone: possible future pathfinding automation integration; not included yet. GitHub metadata indicates `cabaletta/baritone` is LGPL-3.0, but stable public Maven Central or Modrinth coordinates were not identified in the quick check.
- Automatone: possible future automation integration; not included yet. Stable public Maven Central or Modrinth coordinates were not identified in the quick check.

Any dependency added later must be introduced from public coordinates with its license and provenance reviewed before use. Future Baritone or Automatone work also needs Minecraft 1.20.1 loader compatibility validation and an adapter implementation behind the existing automation backend seam.
