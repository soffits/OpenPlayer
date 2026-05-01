# OpenPlayer Agent Rules

OpenPlayer is an AGPL-3.0-only Minecraft mod developed as a legally clean, open replacement direction for Player2NPC-style AI NPC features. Keep every repository-visible text string in English.

## Scope

- Edit only files inside this repository.
- Target Minecraft 1.20.1 and Java 17 until the project intentionally changes targets.
- Support Fabric and Forge through a shared common module where practical.
- Prefer the Architectury multiloader structure: `common`, `fabric`, and `forge`.
- Keep APIs small until a shipped integration needs them.

## License And Provenance

- Project code and metadata must declare `AGPL-3.0-only`.
- Do not copy Player2NPC code line-for-line.
- Decompiled Player2NPC or PlayerEngine behavior may be inspected for reference ideas, but do not copy decompiled code line-for-line or paste it into this repository.
- Do not vendor PlayerEngine or any other opaque jar.
- Use dependencies only through documented package managers and public coordinates.
- Document new dependency provenance and license posture before adding it.

## Architecture

- Place loader-neutral logic in `common` under `dev.soffits.openplayer`.
- Keep Fabric-only entrypoints in `fabric`.
- Keep Forge-only entrypoints and event wiring in `forge`.
- Keep constants centralized in `OpenPlayerConstants`.
- Keep configuration centralized once configuration exists.
- Avoid networking, entities, GUI, skins, LLM providers, Baritone, Automatone, or PlayerEngine integration unless the active task explicitly asks for that phase.

## Code Style

- Use strict, explicit Java types.
- Prefer self-documenting names over comments.
- Do not add dead code, speculative abstractions, or compatibility shims without a concrete need.
- Avoid inline logic comments. Short file headers, API contracts, and security contracts are acceptable.
- Keep pure Java seams separate from Minecraft runtime code when possible.

## Verification

Run the strongest available check before reporting completion:

- `./gradlew build` when the Gradle wrapper exists.
- `gradle build` when a suitable system Gradle and JDK 17 are available.
- Report clearly when Java or Gradle is unavailable.

## OpenCode Delegation

- Build context before editing.
- Make the smallest correct change for the requested phase.
- Preserve unrelated user or agent work in the git worktree.
- Do not commit unless the user explicitly requests it.
- Report verification results and any environment limitation.
