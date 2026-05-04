# OpenPlayer Agent Rules

OpenPlayer is an AGPL-3.0-only Minecraft mod developed as a legally clean, open replacement direction for Player2NPC-style AI NPC features. Keep every repository-visible text string in English except translated locale resource values.

## Scope

- Edit only files inside this repository.
- Target Minecraft 1.20.1 and Java 17 until the project intentionally changes targets.
- Support Fabric and Forge through a shared common module where practical.
- Prefer the Architectury multiloader structure: `common`, `fabric`, and `forge`.
- Keep APIs small until a shipped integration needs them.

## Branching Model

- Long-lived development lines are Minecraft-version branches named `mc/<minecraft-version>`, starting with `mc/1.20.1`.
- Do not rely on permanent `main` or `dev` branches for normal development; write workflows and docs so they survive default-branch renames.
- Start new port lines from the nearest useful existing Minecraft line, such as `mc/1.21.1` from `mc/1.20.1` when that port begins.
- Use short-lived purpose branches for work against the active line: `feat/<short-name>`, `fix/<short-name>`, `docs/<short-name>`, `ci/<short-name>`, or `refactor/<short-name>`.
- Use `backport/mc-1.20.1/<short-name>` style branches for backports from newer Minecraft lines into older maintained lines.
- Merge short-lived branches back into the intended `mc/<version>` branch after local verification or CI, then delete the merged branch.
- Release tags are independent of branch names and must keep the product-prefixed scheme, such as `openplayer_1.20.1_1-alpha.1` or `openplayer_1.20.1_1`.

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
- Do not hardcode gameplay route planners in Java. Use generic primitives, capability adapters, validation, and truthful status; use local strategy/meta packs only as advisory goal-decomposition reference.
- Normal player-like behavior should be attempted when reviewed adapters and policy allow it. A missing behavior is an adapter/interface gap or world-state/policy failure, not a permanent product-level prohibition.
- While OpenPlayer runtime and intent surfaces are unreleased or unfinished, remove abandoned internal intent/API designs fully instead of keeping aliases. Do not preserve compatibility for provider/internal command names that were never a stable public contract; prefer clean generic primitives and capability adapters over old synonyms.

## Code Style

- Use strict, explicit Java types.
- Prefer self-documenting names over comments.
- Do not add dead code, speculative abstractions, or compatibility shims without a concrete need.
- Avoid inline logic comments. Short file headers, API contracts, and security contracts are acceptable.
- Keep pure Java seams separate from Minecraft runtime code when possible.

## UI Conventions

- All user-facing Minecraft UI text must be translatable via `assets/openplayer/lang/*.json`; avoid hard-coded `Component.literal("English UI text")` except dynamic values, ids, commands, file names, and developer/debug-only text.
- Add new UI translation keys to `en_us.json`, `ja_jp.json`, and `fr_fr.json` for now.

## Verification

Run the strongest available check before reporting completion:

- `./gradlew build` when the Gradle wrapper exists.
- `gradle build` when a suitable system Gradle and JDK 17 are available.
- Report clearly when Java or Gradle is unavailable.

## Release Notes

- GitHub release notes and changelogs must include meaningful human-readable content, not only a Full Changelog link.
- When using generated notes, seed or augment the release body from commit summaries, a curated `RELEASE_NOTES.md`, or another tag-adjacent source.

## OpenCode Delegation

- Build context before editing, then give OpenCode a broad enough mandate to inspect, design, and edit within the requested capability or cleanup cluster.
- Do not micromanage OpenCode with one-alias, one-method, or one-file follow-up prompts when the user asked for a broad phase/full-repo cleanup. Use narrow follow-up prompts only for concrete review blockers, safety regressions, build failures, or user-visible overclaims.
- Prefer coherent capability clusters over artificial tiny phases. For PlayerEngine-style parity work, the durable boundaries are no OP/admin/cheat operations, no permission bypass, no arbitrary provider-origin execution, no opaque bot/runtime dependency, and no fake success.
- Preserve unrelated user or agent work in the git worktree.
- Do not commit unless the user explicitly requests it.
- Report verification results and any environment limitation.
