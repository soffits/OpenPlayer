# Player2NPC Rewrite Parity Requirements

This document defines the remaining OpenPlayer work needed to reach Player2NPC-style rewrite parity while staying legally clean and aligned with the current OpenPlayer architecture. It is a requirements and implementation guidance document only; it does not authorize copying Player2NPC code, decompiled code, assets, private APIs, account workflows, or provider secrets.

OpenPlayer remains an `AGPL-3.0-only` Minecraft 1.20.1 Java 17 mod using an Architectury-style layout:

- `common`: loader-neutral domain contracts, runtime services, entity logic, client-neutral data shapes where possible, and pure Java seams.
- `fabric`: Fabric entrypoints and loader-specific wiring.
- `forge`: Forge entrypoints, client event wiring, and loader-specific wiring.

Current implementation already includes runtime NPC sessions, duplicate prevention, owner lifecycle cleanup and restore, spawn/despawn networking, basic command intents, item pickup with inventory persistence, a minimal client control screen, a player-shaped renderer with optional local resource id skin support, a vanilla NPC-backed automation layer, an optional reflective Baritone command bridge, and a disabled-by-default provider-backed intent parser seam.

## Non-Goals And Legal Constraints

- Do not copy Player2NPC, PlayerEngine, or decompiled implementation code line-for-line.
- Do not vendor opaque jars or assets.
- Do not require commercial Minecraft account login, Microsoft account login, Mojang session services, or account token storage for parity features.
- Do not store API keys, access tokens, passwords, cookies, or provider credentials in character files, logs, screenshots, test fixtures, or docs.
- Do not add network skin downloads, external player profile lookup, LLM providers, Baritone, Automatone, GUI frameworks, or new dependencies unless a future implementation task explicitly approves that phase and documents provenance and license posture.
- Keep repository-visible strings in English.
- Keep APIs small until an implemented integration needs broader contracts.

## Cross-Phase Requirements

- Prefer loader-neutral implementation in `common` whenever Minecraft APIs allow it.
- Keep new public API types under `dev.soffits.openplayer.api` only when external callers need them.
- Keep runtime-only services under `dev.soffits.openplayer.runtime`, entity behavior under `dev.soffits.openplayer.entity`, client UI and renderer code under `dev.soffits.openplayer.client`, and shared constants in `OpenPlayerConstants`.
- Use deterministic identifiers for local characters and sessions where stability matters, but do not expose mutable filesystem paths as public identity.
- Validate all user-editable text and file paths before using them.
- Fail closed for invalid config, missing skins, unavailable automation, unavailable parser providers, unsafe actions, or unknown sessions.
- Preserve single-player and multiplayer server safety: server-authoritative actions, bounded commands, owner checks, and no client-only authority over world mutation.
- Add tests at the smallest practical seam before or with implementation work. Prefer pure Java tests for config, validation, and parsing seams; use Minecraft runtime tests only where entity, renderer, or networking behavior requires it.

## Phase 1: Local Character Config And Repository

### Goal

Add a local character repository that lets a player define reusable NPC characters without requiring external accounts, online profile lookup, or provider secrets.

### Requirements

- Define a compact character model that maps cleanly to the existing `AiPlayerNpcSpec`, `NpcRoleId`, `NpcOwnerId`, `NpcProfileSpec`, and `NpcSpawnLocation` flow.
- Store local character definitions in a mod-owned config/data location appropriate for Minecraft 1.20.1 on Fabric and Forge.
- Character fields should include a stable local character id, display name, optional description, optional local skin reference, optional default role id, and optional behavior/conversation settings reserved for later phases.
- Character ids must be stable, ASCII-safe, path-safe, and independent from display names.
- Selected local character runtime sessions must bind to a deterministic session role id derived from the stable character id. Display names and optional default role metadata must not be used as selected-character action identity.
- Character display names must be validated before being passed to `NpcProfileSpec`.
- Repository reads must tolerate missing directories and malformed individual character files by reporting actionable validation errors without crashing the game.
- Repository writes must avoid partial file corruption where practical.
- Do not store provider keys or credentials in character files.
- Do not add compatibility importers for Player2NPC files unless explicitly requested in a later task.

### Implementation Guidance

- Keep file parsing and validation isolated from Minecraft runtime calls where possible.
- Use a small repository service in `common` and expose only the minimum methods needed by UI and runtime code.
- Consider JSON only if it can be handled without adding new dependencies; otherwise use a simple documented format supported by existing Minecraft or Java APIs.
- Treat unknown fields as invalid until there is a concrete compatibility need.

### Acceptance Criteria

- A valid local character can be loaded and converted into spawn-ready profile data.
- Invalid ids, blank names, unsafe skin paths, and malformed files are rejected with clear English messages.
- Missing repository directories produce an empty character list, not a startup failure.
- No character repository path allows escaping the mod-owned directory.

## Phase 2: Character Selection And Detail UI

### Goal

Replace or extend the minimal `OpenPlayerControlScreen` so players can select a local character, review details, spawn/despawn that selected character, and send commands to the selected companion.

### Current Implementation Notes

OpenPlayer now extends `OpenPlayerControlScreen` with a compact server-fed local character list and selected-character detail panel. The server loads `<Minecraft config>/openplayer/characters` through the existing Architectury loader config-directory hook, and client actions send only a stable character id before the server resolves character data. Empty repositories, validation errors, selected lifecycle state, skin status, conversation status, and the existing safe runtime status lines are visible in the screen. With no selected character, the spawn control keeps the original default OpenPlayer NPC spawn behavior.

### Requirements

- Keep client UI code in `common` where possible and route loader entrypoints through existing Fabric and Forge client wiring.
- Preserve the current safe status display for automation backend, parser state, endpoint, model, and API key presence.
- Add a character list with empty, loading, validation-error, and selected states.
- Add a detail panel showing display name, local character id, description, skin status, lifecycle status, and conversation/AI status when available.
- Ensure controls are usable at desktop and small Minecraft window sizes.
- Do not expose secrets in UI. API key status must remain presence-only.
- UI actions must send server-authoritative requests; they must not directly mutate server world state from the client.

### Implementation Guidance

- Keep UI text literals English and concise.
- Extend networking only with small request/response packets needed for selection, character list/status, and selected-character actions.
- Avoid building a large GUI framework. Prefer minimal Minecraft screen components until a concrete design need appears.

### Acceptance Criteria

- A player can open the OpenPlayer UI, select a local character, spawn it, despawn it, stop it, make it follow, and send command text.
- Empty repositories and invalid character files are visible and actionable in the UI.
- The UI does not display provider secrets, filesystem absolute paths, or stack traces.

## Phase 3: Local Skin PNG Loading

### Goal

Support local PNG skins referenced by local character definitions without downloading skins or requiring any account login.

### Requirements

- Accept only local PNG files under a mod-owned skin directory or another explicitly documented safe subdirectory.
- Validate skin paths before loading and reject absolute paths, parent traversal, non-PNG extensions, and missing files.
- Support standard Minecraft-compatible skin dimensions used by player skins.
- Register client-side texture resources from local files and pass safe resource ids to the existing renderer path through `NpcProfileSpec.skinTexture` or a successor field.
- Fall back to deterministic default player skins when a local PNG is missing, invalid, or not yet loaded.
- Do not fetch remote skins, query account profile services, or cache external profile data.

### Implementation Guidance

- Keep path validation pure Java and testable.
- Keep actual texture registration client-side.
- Avoid storing raw image bytes in entity NBT; persist only stable local references or resource ids.
- Ensure multiplayer clients do not assume they have the same local skin file unless a future explicit sync feature is approved.

### Acceptance Criteria

- Valid local PNG skins render on the local client for matching OpenPlayer NPCs.
- Invalid or missing skins degrade to deterministic defaults without disconnecting or crashing.
- Security tests cover path traversal and absolute path rejection.

## Phase 4: Player-Like Renderer Feature Layers

### Goal

Bring the `OpenPlayerNpcRenderer` closer to player-like visual parity by adding expected player feature layers while preserving the existing `OpenPlayerNpcEntity` model and skin behavior.

### Requirements

- Add visible support for held items, armor, elytra where applicable, head layers, cape-like layers only when backed by local resources, and player model overlays that are compatible with Minecraft 1.20.1 APIs.
- Render equipment from the NPC inventory already persisted by `OpenPlayerNpcEntity`.
- Do not use account capes, commercial login state, or remote profile metadata.
- Keep renderer code client-only and loader-neutral where possible.
- Ensure invalid skin resources and missing optional layers do not crash rendering.

### Implementation Guidance

- Prefer vanilla renderer feature layer classes when they can be composed cleanly with `LivingEntityRenderer<OpenPlayerNpcEntity, PlayerModel<OpenPlayerNpcEntity>>`.
- Do not duplicate large vanilla renderer code unless no API path exists; if custom layers are needed, keep them minimal and documented.
- Verify armor and hand slots align with `OpenPlayerNpcEntity.getArmorSlots`, `getHandSlots`, `getItemBySlot`, and `setItemSlot`.

### Acceptance Criteria

- Equipped armor and held items are visible on OpenPlayer NPCs.
- Skin overlays and model layers render without affecting default fallback skins.
- Rendering works on both Fabric and Forge clients.

## Phase 5: Companion Lifecycle Manager

### Goal

Add a higher-level companion lifecycle manager that coordinates local character selection, ownership, spawning, despawning, persistence restore, duplicate prevention, and owner cleanup.

### Requirements

- Build on `RuntimeAiPlayerNpcService` instead of replacing it unless a concrete limitation requires a small refactor.
- Maintain one active companion per owner and selected local character unless a future task explicitly adds multi-companion support.
- Handle login, logout, dimension changes, death/respawn, server stop, and persisted entity restore consistently.
- Stop runtime commands when an owner disconnects or a companion is despawned.
- Preserve existing duplicate-prevention behavior based on owner, role id, and profile name or replace it with a documented stable character identity key.
- Ensure lifecycle state is visible to the UI and command submission layer.

### Implementation Guidance

- Keep the manager server-side and expose small query/action methods to networking handlers.
- Avoid persisting transient session ids as long-term character identity.
- Keep restored entities bound to validated local character data when possible, but do not delete user-created character files automatically.

### Acceptance Criteria

- Spawning the same selected character twice relocates or reuses the existing companion instead of creating duplicates.
- Owner disconnect or server stop stops companion tasks safely.
- Persisted NPC entities are restored into runtime sessions after world load when their identity is valid.
- UI status remains accurate after spawn, despawn, restore, and owner lifecycle events.

## Phase 6: Player-Like Interaction And Action Layer

### Goal

Expand NPC behavior from command execution into a safer player-like interaction layer for common companion actions.

### Requirements

- Keep actions server-authoritative and bounded by reach, visibility, permissions, loaded chunks, cooldowns, and owner safety checks.
- Build on the existing `AutomationBackend`, `AutomationController`, `RuntimeCommandExecutor`, and `VanillaAutomationBackend` seams where practical.
- Support action primitives needed for player-like parity: move, look, follow, stop, collect item, attack nearest allowed target, break block, place block, use held item, interact with block, interact with entity, equip/swap selected item, drop item, and inventory transfer if later approved.
- Keep world-mutating and violent actions disabled by default unless current automation safety configuration explicitly allows them.
- Never claim stock Baritone controls `OpenPlayerNpcEntity` unless a true NPC-backed adapter exists.
- Do not add PlayerEngine or Automatone integration without explicit dependency provenance, license review, and a clean adapter boundary.

### Implementation Guidance

- Prefer small action objects or command intents over a large imperative scripting surface.
- Return `CommandSubmissionResult` values with user-actionable rejection reasons.
- Keep each action bounded so stuck tasks eventually stop.
- Separate validation, planning, and per-tick execution enough to test validation without a running client.

### Acceptance Criteria

- Supported actions produce deterministic accept/reject results for invalid targets, missing items, unsafe worlds, disabled permissions, and unknown sessions.
- Stuck movement, unreachable targets, full inventory collection, and unloaded target chunks time out or reject safely.
- Existing `STOP` behavior interrupts active actions reliably.

## Phase 7: Per-Character AI And Conversation Loop

### Goal

Add an optional per-character conversation and intent loop that can translate player messages into companion responses and action intents without requiring a provider by default.

### Requirements

- Keep the default runtime safe and offline: AI/conversation must be disabled unless explicitly configured.
- Reuse or extend the existing `IntentParser` and provider-backed parser seam rather than adding a second unrelated provider path.
- Per-character settings may include prompt/personality text, enabled/disabled state, model preference, local cooldowns, and action permission profile, but must not include API keys or secrets.
- Provider credentials must remain environment variables or JVM properties, never character config fields.
- Conversation history must be bounded and should avoid storing secrets or raw provider credentials.
- AI output must be parsed into constrained `CommandIntent` or successor action types; raw model text must not directly execute arbitrary commands.
- Add clear status reporting for disabled parser, unavailable provider, rejected output, rate limits, and active conversation state.

### Implementation Guidance

- Keep pure Java prompt assembly, output validation, and history trimming seams testable.
- Treat provider responses as untrusted input.
- Prefer explicit per-character action permissions over implicit prompt instructions.
- Do not add external provider dependencies unless explicitly approved; current OpenAI-compatible support uses Java 17 `HttpClient` only.

### Acceptance Criteria

- With AI disabled, all conversation features report unavailable without contacting a provider.
- With a configured provider, valid responses can become bounded companion intents through the existing submission path.
- Invalid, unsafe, or unparsable provider output is rejected without executing actions.
- No tests, logs, docs, or fixtures contain real provider credentials.

## Phase 8: Tests And Acceptance Criteria

### Goal

Provide enough automated and manual verification for implementation agents to complete parity work safely across loaders.

### Required Automated Tests

- Character id, display name, description, and reserved field validation.
- Character repository read behavior for missing directory, empty directory, valid file, malformed file, unknown field, duplicate id, and partial write handling where testable.
- Safe local skin path validation, including parent traversal, absolute path, wrong extension, missing file, and valid PNG reference.
- Conversion from local character config to `AiPlayerNpcSpec` or successor spawn request shape.
- Lifecycle manager duplicate prevention, owner cleanup, selected-character state, and persisted identity restore seams.
- Intent/action validation for each supported action, including disabled world actions and unknown sessions.
- Conversation loop disabled-by-default behavior, provider-unavailable behavior, untrusted output rejection, and bounded history trimming.

### Required Build Verification

- Run `./gradlew build` with Java 17 before reporting implementation completion whenever the wrapper exists.
- If Java 17 or Gradle is unavailable, report the exact limitation and any partial checks that did run.
- Run `git diff --check` before reporting documentation or code changes.

### Required Manual Acceptance Passes

- Fabric client can open the UI, list characters, select one, spawn/despawn it, and send a command.
- Forge client can open the UI, list characters, select one, spawn/despawn it, and send a command.
- Local skin PNG renders when valid and falls back when invalid.
- Armor and held items render on NPCs after inventory changes.
- Owner disconnect, reconnect, dimension change, and server restart do not create duplicate companions.
- Disabled AI and disabled world actions are visibly unavailable and do not perform work.

## Implementation Order

1. Add the local character model, validation, and repository tests.
2. Add server-side selected-character and lifecycle manager seams.
3. Extend networking and UI for character list, selection, detail, and selected-character actions.
4. Add local skin path validation and client texture registration.
5. Add player-like renderer feature layers.
6. Expand the player-like action layer behind existing automation safety settings.
7. Add optional per-character conversation loop on top of the existing disabled-by-default intent provider seam.
8. Complete cross-loader manual passes and update README status once implementation is real.

Each implementation phase should be independently buildable and should avoid claiming parity for behavior that is still stubbed, unavailable, or only implemented for one loader.
