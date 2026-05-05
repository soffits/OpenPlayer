# OpenPlayer Agent Rules

OpenPlayer is an AGPL-3.0-only Minecraft mod developed as a legally clean, local-first AI companion runtime. Keep every repository-visible text string in English except translated locale resource values.

## Scope

- Edit only files inside this repository.
- Target the active Minecraft line and Java 17 until the project intentionally changes targets.
- Support Fabric and Forge through a shared common module where practical.
- Prefer the Architectury multiloader structure: `common`, `fabric`, and `forge`.
- Keep APIs small until a shipped integration needs them.

## Branching Model

- Long-lived development lines are Minecraft-version branches named `mc/<minecraft-version>`.
- Do not rely on permanent `main` or `dev` branches for normal development; write workflows and docs so they survive default-branch renames.
- Start new port lines from the nearest useful existing Minecraft line when that port begins.
- Use short-lived purpose branches for work against the active line: `feat/<short-name>`, `fix/<short-name>`, `docs/<short-name>`, `ci/<short-name>`, or `refactor/<short-name>`.
- Use `backport/mc-<minecraft-version>/<short-name>` style branches for backports from newer Minecraft lines into older maintained lines.
- Merge short-lived branches back into the intended `mc/<version>` branch after local verification or CI, then delete the merged branch.
- Release tags are independent of branch names and must match `mod_version` SemVer, with an optional leading `v` that release automation strips before comparing.

## License And Provenance

- Project code and metadata must declare `AGPL-3.0-only`.
- Do not copy proprietary, closed, or opaque code line-for-line.
- Do not paste decompiled implementation details into this repository.
- Do not vendor opaque jars.
- Use dependencies only through documented package managers and public coordinates after review.
- Document new dependency provenance and license posture before adding it.

## Architecture

- Place loader-neutral logic in `common` under `dev.soffits.openplayer`.
- Keep Fabric-only entrypoints in `fabric`.
- Keep Forge-only entrypoints and event wiring in `forge`.
- Keep constants centralized in `OpenPlayerConstants`.
- Keep configuration centralized once configuration exists.
- Avoid networking, entities, GUI, skins, LLM providers, pathfinding integrations, or automation-runtime integrations unless the active task explicitly asks for that phase.
- Do not hardcode gameplay route planners in Java. Use generic primitives, capability adapters, validation, and truthful status; use local strategy/meta packs only as advisory goal-decomposition reference.
- Normal player-like behavior should be attempted when reviewed adapters and policy allow it. A missing behavior is an adapter/interface gap or world-state/policy failure, not a permanent product-level prohibition.
- While OpenPlayer runtime and intent surfaces are unreleased or unfinished, remove abandoned internal intent/API designs fully instead of keeping aliases. Do not preserve compatibility for provider/internal command names that were never a stable public contract; prefer clean generic primitives and capability adapters over old synonyms.
- When replacing an internal runtime/provider/API design, delete the old-style code in the same change unless it is a real shipped external contract. Do not wait, leave dormant fallback paths, or keep legacy shims “just in case”; stale internal surfaces create misleading behavior and must be removed with their tests/docs/prompts.
- Do not infer whether a runtime class is live or retired from its name. Before editing or dismissing automation, movement, AICore, provider, or primitive-execution code, trace the production path from player-facing entrypoints and entity ticks through constructors/factories to the actual executor. For example, `VanillaAutomationBackend` is still the live server-side NPC primitive executor through `OpenPlayerNpcEntity` -> `RuntimeCommandExecutor` -> `OpenPlayerAutomationConfig.createBackend()` unless that path is deliberately replaced in the same reviewed change.

## Code Style

- Do not add non-English text to code, tests, comments, docs, prompts, commit messages, branch names, metadata, or generated release notes inputs; translated locale resource values under `assets/openplayer/lang/*.json` are the only exception.
- Use strict, explicit Java types.
- Prefer self-documenting names over comments.
- Do not add dead code, speculative abstractions, or compatibility shims without a concrete need.
- When a new AICore/provider contract supersedes an old internal contract, delete the old parser/provider/runtime branches, tests, prompt language, and docs instead of supporting both shapes by default.
- Avoid inline logic comments. Short file headers, API contracts, and security contracts are acceptable.
- Keep pure Java seams separate from Minecraft runtime code when possible.
- Keep non-test source files under 500 lines. When a non-test file grows past that limit, split it by domain responsibility before adding more behavior; tests may exceed the limit when needed for readable coverage. Existing oversized files are refactoring debt, not a place to continue piling new logic. Do not satisfy the limit by minifying, compressing statements, merging imports, or otherwise reducing physical line count without reducing responsibility; normal readable formatting is part of the rule.

## UI Conventions

- All user-facing Minecraft UI text must be translatable via `assets/openplayer/lang/*.json`; avoid hard-coded `Component.literal("English UI text")` except dynamic values, ids, commands, file names, and developer/debug-only text.
- Add new UI translation keys to every locale file under `assets/openplayer/lang/*.json`, keeping key and placeholder parity.

## Verification

Run the strongest available check before reporting completion:

- `./gradlew build` when the Gradle wrapper exists.
- `gradle build` when a suitable system Gradle and JDK 17 are available.
- Report clearly when Java or Gradle is unavailable.

## Release Notes

- Release notes are generated by the release workflow from commits and GitHub metadata.
- Use meaningful Conventional Commits so generated release notes are useful.
- Write handwritten release notes only when explicitly requested.

## OpenCode Delegation

- Build context before editing, then give OpenCode a broad enough mandate to inspect, design, and edit within the requested capability or cleanup cluster.
- Do not micromanage OpenCode with one-alias, one-method, or one-file follow-up prompts when the user asked for a broad phase/full-repo cleanup. Use narrow follow-up prompts only for concrete review blockers, safety regressions, build failures, or user-visible overclaims.
- Prefer coherent capability clusters over artificial tiny phases. Durable boundaries are no OP/admin/cheat operations, no permission bypass, no arbitrary provider-origin execution, no opaque bot/runtime dependency, and no fake success.
- For Lia's explicit fast-loop mode, run one broad OpenCode phase per planned milestone: OpenCode must inspect, implement, and run the required Java 17 build/check before stopping; if that build passes, treat it as the phase verification baseline.
- In fast-loop mode, Sakina reviews the diff for blockers only. If review is clean, do not run redundant local builds/tests and do not wait for GitHub CI; commit/push and immediately continue the next broad phase. If a concrete blocker is found, fix that blocker narrowly, add or update regression coverage, then run the smallest necessary targeted check plus broader build/check only when warranted by the fix.
- OpenCode prompts for this repository should usually include the Java 17 command shape `JAVA_HOME=/opt/data/tools/jdk-17.0.19+10 PATH=/opt/data/tools/jdk-17.0.19+10/bin:$PATH ./gradlew :common:check build` or the stronger task-specific build/check command.
- Preserve unrelated user or agent work in the git worktree.
- Do not commit unless the user explicitly requests it.
- Report verification results, pushed commits, remaining truthful limitations, and any environment limitation.
