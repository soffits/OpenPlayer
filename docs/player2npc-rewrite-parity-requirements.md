# Player2NPC Rewrite Parity Requirements

This document defines the remaining OpenPlayer work needed to reach Player2NPC-style rewrite parity while staying legally clean and aligned with the current OpenPlayer architecture. It is a requirements and implementation guidance document only; it does not authorize copying Player2NPC code, decompiled code, assets, private APIs, account workflows, or provider secrets.

OpenPlayer remains an `AGPL-3.0-only` Minecraft 1.20.1 Java 17 mod using an Architectury-style layout:

- `common`: loader-neutral domain contracts, runtime services, entity logic, client-neutral data shapes where possible, and pure Java seams.
- `fabric`: Fabric entrypoints and loader-specific wiring.
- `forge`: Forge entrypoints, client event wiring, and loader-specific wiring.

Current implementation already includes runtime NPC sessions, duplicate prevention, owner lifecycle cleanup and restore, spawn/despawn networking, basic command intents, item pickup with inventory persistence, local assignment support for multiple companions per owner, a minimal client control screen, a player-shaped renderer with optional local resource id skin support, client-side local PNG skin loading, and vanilla feature layers for held items, armor, head-slot items, elytra, arrows, and bee stingers, a vanilla NPC-backed automation layer, an optional reflective Baritone command bridge, and a disabled-by-default provider-backed intent parser seam.

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

OpenPlayer now extends `OpenPlayerControlScreen` with a server-fed local assignment gallery and selected-assignment detail panel. The server loads `<Minecraft config>/openplayer/characters` and `<Minecraft config>/openplayer/assignments` through the existing Architectury loader config-directory hook, and client actions send only a stable assignment id before the server resolves assignment and character data. The gallery shows loading, empty, validation-error, selected, active/spawned, despawned, skin, and conversation availability states; more than six assignments are navigable with Prev/Next pagination and selection remains keyed by assignment id across refreshes. The detail panel shows display name, assignment id, character id, description, skin status, lifecycle status, conversation status, and action hints while the bottom runtime status remains safe and presence-only. With no selected assignment, the spawn control keeps the original default OpenPlayer NPC spawn behavior.

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

### Current Implementation Notes

Local character `localSkinFile` values are relative to `<Minecraft config>/openplayer` and must point under `skins/`, for example `skins/alex_helper.png`. The server resolves local files only to produce safe UI status text and still sends no absolute paths or image bytes. Clients independently load matching local character files from their own config directory, register valid `64x32` or `64x64` PNGs as dynamic textures, and fall back to `skinTexture` resource ids or deterministic default player skins when local files are unavailable.

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

### Current Implementation Notes

`OpenPlayerNpcRenderer` composes Minecraft 1.20.1 vanilla render layers for held items, humanoid armor, custom head-slot items, elytra, arrows, and bee stingers. The held-item and armor layers read through `OpenPlayerNpcEntity.getHandSlots`, `getArmorSlots`, and `getItemBySlot`, which are backed by the NPC's persisted internal inventory. Account capes, remote profile metadata, and external skin lookups remain unsupported; texture selection still follows the Phase 3 local PNG, configured resource id, and deterministic default skin fallback order.

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

### Current Implementation Notes

OpenPlayer now routes selected local-character spawn, despawn, follow, stop, command text, and UI lifecycle status through a server-side `CompanionLifecycleManager`. The manager is intentionally small and sits on top of `RuntimeAiPlayerNpcService` and `OpenPlayerApi`; it resolves local character data from the repository for each action and matches active companions by owner UUID plus the deterministic local-character session role id derived from the stable character id. It does not store transient runtime session ids as long-term character identity.

Spawning the same selected local character calls the runtime service with the same stable identity, so the existing runtime duplicate-prevention path reuses or relocates the current session instead of creating a duplicate. Unknown, invalid, blank, or deleted selected character ids reject safely and do not fall through to legacy NPC sessions. With no selected character id, networking keeps the legacy default OpenPlayer NPC path.

Lifecycle status shown in the UI comes from the manager and returns the matched runtime session status or `despawned` when no owner plus stable character id session exists. Runtime despawn now stops active commands before discarding the NPC entity, while the existing owner disconnect and server stop hooks continue to stop owner tasks and clear runtime sessions safely. Persisted NPC restore remains owned by `RuntimeAiPlayerNpcService`, which adopts valid persisted identities and uses the same stable local-character identity key for restored selected-character sessions.

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

### Current Implementation Notes

OpenPlayer now exposes a focused server-side action seam on `OpenPlayerNpcEntity`: selected hotbar slot validation and mutation, selected held item and equipment access through vanilla slot methods, bounded inventory item get/set helpers, hotbar tool selection for block breaking, hotbar block-item selection for placement, and visible main-hand swing actions. The selected main-hand slot is constrained to the nine-slot hotbar; invalid persisted selections are normalized to slot `0`.

The vanilla automation backend uses those helpers without adding dependencies. With world actions enabled, `BREAK_BLOCK` selects the best scored hotbar tool before destroying a loaded, reachable, breakable target block and swings the main hand. `PLACE_BLOCK` keeps selected-slot semantics when the selected hotbar item is a `BlockItem`; otherwise it selects the first hotbar `BlockItem`, then applies the existing simple survival and collision checks before placing and swinging. `ATTACK_NEAREST` also uses the same main-hand swing helper.

This phase remains intentionally narrow. OpenPlayer still does not emulate full player item use, block/entity interaction routing, container transfers, cooldown managers, crafting, inventory rearrangement, PlayerEngine, Automatone, or true Baritone control of `OpenPlayerNpcEntity`.

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

### Current Implementation Notes

OpenPlayer now has a focused selected-character conversation loop in `common` that reuses the existing runtime `IntentParser`; it does not add dependencies or a second provider path. The default runtime remains offline and safe. When `OPENPLAYER_INTENT_PARSER_ENABLED` is not enabled, configured character conversation reports `Conversation unavailable: intent parser disabled`, does not call the parser/provider, and does not execute fallback actions.

`conversationPrompt` and `conversationSettings` are treated as bounded, non-secret, per-character prompt text only. Provider endpoint, model, and API key stay in environment variables or JVM properties through `OpenPlayerIntentParserConfig` and are never character fields. Selected-character command text enters the conversation loop only when those conversation fields are present and the parser is enabled; otherwise command text preserves the existing direct selected-character behavior.

The loop has pure Java seams for prompt assembly, bounded in-memory history trimming, and untrusted parser output validation. Provider output must become a constrained `CommandIntent` through the existing parser, then submit through `CompanionLifecycleManager` selected-character command submission. Invalid, unavailable, oversized, or unparsable parser output rejects without action. Character list entries now expose safe conversation status text such as `not configured`, `unavailable: parser disabled`, or `available`.

Remaining gaps are explicit: no separate NPC spoken-response UI, no persisted conversation memory, no per-character model/provider override, no character-file secrets, no dedicated rate-limit scheduler, and no broader action permission profiles beyond the current automation backend safety gates.

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

## Near-1:1 Local Parity Extension Phases

These extension phases define future clean-room work for near-1:1 local Player2NPC-style parity after the baseline rewrite phases. They must remain local/offline by default and must not add commercial account login, online character services, remote skin downloads, opaque jars, Player2NPC code, PlayerEngine vendoring, provider secrets in files, or unreviewed dependencies.

## Phase H: Local Assignments And Multi-Companion Lifecycle

### Goal

Allow one owner to run multiple selected local companions with explicit local assignments while preserving stable identity, duplicate prevention, and server-authoritative lifecycle control.

### Requirements

- Extend selected-character identity with a stable local assignment id so the same character definition can support more than one companion slot when the player explicitly creates those slots.
- Support listing, selecting, spawning, despawning, stopping, and status reporting per assignment.
- Keep assignment data local and non-secret; do not include account ids, provider credentials, external service ids, or mutable absolute paths.
- Preserve owner checks, session cleanup, duplicate prevention, persisted restore, and dimension/server-stop safety for each companion.
- Add bounded limits for active companions per owner and reject requests above the configured local limit.

### Implementation Guidance

- Build on `CompanionLifecycleManager` and the existing stable local character role derivation instead of replacing runtime services.
- Treat assignment id as runtime action identity and character id as reusable profile/source data.
- Keep validation pure Java where practical and keep UI/network packets small.
- Prefer explicit user-created assignments over implicit auto-spawning from all character files.

### Acceptance Criteria

- A player can spawn two different local assignments without either overwriting the other's lifecycle state.
- Spawning the same assignment twice reuses or relocates that assignment instead of duplicating it.
- Logout, reconnect, server stop, restore, and despawn clean up or restore each assignment independently.
- Invalid assignment ids, deleted character files, and over-limit spawn requests reject with clear English status.

### Non-Goals

- No account login, cloud roster, online character service, or external assignment sync.
- No automatic party behavior, squad tactics, or shared long-term memory.
- No remote character import or Player2NPC file compatibility unless a later task explicitly approves it.

### Current Implementation Notes

OpenPlayer now loads optional local assignment files from `<Minecraft config>/openplayer/assignments/*.properties`. Supported assignment fields are exactly `id`, `characterId`, and optional `displayName`; unknown fields, unsafe ids, secret-like labels, absolute-path-like display names, duplicate explicit ids, deleted character references, and attempts to hijack another character's default assignment id are validation errors. Missing assignment directories are valid and produce no errors.

For backward compatibility, every loaded local character also has a deterministic default assignment with assignment id equal to the character id unless an explicit same-id assignment targets that same character. Client list rows remain character-oriented for display but include both assignment id and character id; selected actions send only the stable assignment id to the server. The legacy default NPC path still runs when no id is selected.

`CompanionLifecycleManager` now resolves selected ids as assignments, derives selected runtime role ids from assignment ids with the `openplayer-local-assignment-` prefix, and keys conversation history by owner plus assignment id. Existing character-id methods delegate through the default assignment behavior. Each owner can have up to four active local assignments; spawning a fifth distinct assignment rejects safely, while spawning an already-active assignment still reuses or relocates the existing runtime identity through `RuntimeAiPlayerNpcService`.

## Phase I: Character Gallery And Detail UI Polish

### Goal

Polish the in-game local character selection surface into a clearer gallery/detail experience without introducing a large GUI framework.

### Requirements

- Add a local character gallery view with readable empty, loading, validation-error, selected, spawned, and unavailable states.
- Improve detail presentation for display name, id, description, local skin status, assignment/lifecycle status, conversation availability, and action permissions.
- Keep all UI actions server-authoritative and keep client packets limited to stable ids and user intent.
- Ensure the screen remains usable at common small Minecraft window sizes.
- Never display provider secrets, absolute filesystem paths, stack traces, or raw provider responses.

### Implementation Guidance

- Continue extending the existing control screen unless a concrete Minecraft UI limitation requires a focused replacement.
- Use concise English labels and status text.
- Keep visual previews local-only and tolerant of missing local skin files.
- Avoid speculative widgets that are not needed for character selection, assignment status, or action submission.

### Acceptance Criteria

- Players can browse, select, inspect, spawn, despawn, and command local characters or assignments from one coherent UI.
- Invalid character files are visible with safe file names and actionable validation messages.
- The UI does not leak secrets or host filesystem details.
- Fabric and Forge clients expose equivalent controls and status text.

### Non-Goals

- No webview, embedded browser, online gallery, account avatar lookup, or remote skin preview.
- No new GUI dependency unless a future approved task documents provenance and license posture.
- No full character editing in this phase; editing belongs to Phase J.

## Phase J: In-Game Local Character Editor, Import, And Export

### Goal

Add safe in-game management for local character files so players can create, edit, import, and export local character definitions without leaving Minecraft.

### Requirements

- Support creating and editing approved character fields: id, display name, description, local skin file reference, skin resource id, default role metadata, conversation prompt, and non-secret conversation settings.
- Validate edited data through the same repository rules used for file loading.
- Support import/export only from explicit local files chosen under documented mod-owned directories.
- Reject secrets, absolute paths, parent traversal, non-PNG local skin references, unknown fields, malformed data, and duplicate ids.
- Writes must avoid partial file corruption where practical and report save failures clearly.

### Implementation Guidance

- Reuse the repository service and validation errors rather than duplicating editor-specific rules.
- Keep import/export formats dependency-free unless a later task explicitly approves a format and documents its license posture.
- Treat imported text as untrusted input, even when it came from the local filesystem.
- Prefer a compact form flow over a broad content-management UI.

### Acceptance Criteria

- A player can create, edit, save, reload, export, and re-import a valid local character without adding dependencies or secrets.
- Invalid edits cannot be saved over a valid character without a clear rejection.
- Exported files contain only approved fields and portable relative references.
- Imported files cannot escape the mod-owned directory or overwrite unrelated files.

### Non-Goals

- No Player2NPC compatibility importer by default.
- No online character service, cloud backup, account binding, or marketplace-style sharing.
- No remote skin download or automatic conversion from player account names.

### Current Implementation Notes

OpenPlayer now has a pure Java local character write/import/export foundation in the character repository. Active definitions are written only to `<Minecraft config>/openplayer/characters/<id>.properties`, imports are accepted only by safe file name from `<Minecraft config>/openplayer/imports/*.properties`, and exports write only loaded local character ids to `<Minecraft config>/openplayer/exports/<id>.properties`. The writer serializes exactly `id`, `displayName`, `description`, `skinTexture`, `localSkinFile`, `defaultRoleId`, `conversationPrompt`, and `conversationSettings`, omitting blank optional fields.

The file-management path validates through `LocalCharacterDefinition` and existing unknown-field checks before saving. It rejects unsafe ids, id/file mismatches, invalid existing target files, absolute paths, parent traversal, backslashes, drive prefixes, hidden arbitrary files, non-`.properties` names, unknown fields, and secret-like labels or credentials. Writes use a temporary file in the same directory followed by an atomic move when available, with replace fallback and temp cleanup. Result objects carry status plus safe English messages and do not expose absolute server paths to the client.

The in-game Phase J UI slice remains intentionally compact: the control screen can reload the local list, export the selected character, and submit a safe import file name typed into the existing input box. Broad form editing, Player2NPC compatibility import, online services, remote skin download, account lookup, provider credentials, cloud sync, and new dependencies remain out of scope.

## Phase K: Spoken Conversation UX, Greeting, And Response Surface

### Goal

Provide a small local conversation UX that shows companion greetings and spoken responses while keeping provider-backed parsing optional and disabled by default.

### Requirements

- Add local greeting text and response display surfaces for selected companions when conversation settings are present.
- Keep AI/provider use disabled unless explicitly configured through existing safe runtime configuration.
- Show clear statuses for offline, parser disabled, provider unavailable, rejected output, rate-limited, and response accepted states.
- Keep conversation history bounded, local, and free of secrets or raw provider credentials.
- Never execute raw model text; actions must still pass through constrained intent validation.

### Implementation Guidance

- Build on the existing conversation loop, `IntentParser`, and `CommandSubmissionResult` paths.
- Keep response rendering minimal, such as chat/status lines or a small UI panel, before adding richer presentation.
- Separate spoken text from action intents so a rejected action can still report safely.
- Treat provider responses and imported prompt text as untrusted.

### Acceptance Criteria

- A configured local character can show a deterministic greeting without contacting a provider.
- With parser disabled, the UI reports disabled conversation and performs no provider request.
- With an enabled provider, accepted output can show a bounded spoken response and submit only validated intents.
- Rejected or unsafe output is visible as a safe status and performs no world action.

### Current Implementation

OpenPlayer now has a focused Phase K spoken-status slice for selected local assignments with conversation config. Spawning a configured assignment records a deterministic local greeting keyed by owner UUID plus assignment id; this greeting does not call the parser or any provider. Selected command text records a sanitized bounded player line before the conversation loop runs, records an accepted `CommandIntent` summary after validated submission, and records safe failure lines such as parser-disabled or unable-to-handle statuses when a request is rejected or unavailable.

The spoken status surface is server-authoritative and sent to the client as bounded strings in the local character list response. `OpenPlayerControlScreen` shows the selected companion's recent lines in the detail pane. The in-memory status repository keeps only the last six events per owner plus assignment and does not persist logs. Provider output remains untrusted and is never displayed raw; only deterministic greetings, sanitized player input, accepted intent/action summaries, and safe status/failure text are shown.

### Non-Goals

- No voice synthesis, speech recognition, audio dependency, remote memory service, or persisted private chat logs.
- No per-character API keys or provider credentials in character files.
- No arbitrary command execution from free-form model text.
- No raw provider response display or client-authoritative conversation state.

## Phase L: Expanded Intent And Action Vocabulary

### Goal

Expand the clean-room command vocabulary for local companions while keeping every action bounded, permissioned, and server-authoritative.

### Requirements

- Add explicit intents for common companion tasks such as waiting, guarding, patrolling between local points, returning to owner, equipping items, using selected items, interacting with blocks/entities, transferring inventory when approved, and cancelling grouped tasks.
- Keep world-mutating and violent actions disabled by default unless local automation safety settings allow them.
- Return deterministic accept/reject statuses for missing targets, unsafe permissions, unavailable items, unloaded chunks, reach failures, cooldowns, and unknown sessions.
- Preserve `STOP` as a reliable interruption path for all new actions.
- Keep action parsing constrained; do not add arbitrary scripts or raw command execution.

### Implementation Guidance

- Extend existing `CommandIntent`, automation backend, and validation seams incrementally.
- Prefer small action records and explicit target shapes over broad stringly typed payloads.
- Add pure Java tests for parsing and validation before Minecraft runtime tests.
- Keep unsupported actions honest in status text rather than silently falling back.

### Acceptance Criteria

- Each new intent has validation tests for accepted, rejected, disabled, and unknown-session cases.
- Active tasks remain bounded and stop on owner disconnect, despawn, server stop, or explicit stop.
- Disabled world actions cannot mutate the world or attack entities.
- Existing commands continue to behave as before unless intentionally expanded.

### Current Implementation

OpenPlayer now includes a focused Phase L vocabulary slice without new dependencies or a pathfinding rewrite. The expanded intent enum and provider prompt include `EQUIP_BEST_ITEM`, `DROP_ITEM`, `REPORT_STATUS`, `GUARD_OWNER`, and `PATROL`. `PATROL` is a vanilla NPC navigation loop between the command start position and one strict `x y z` coordinate within a bounded local distance. `REPORT_STATUS` is accepted as a deterministic no-world-mutation status action.

The vanilla backend keeps mutating or violent Phase L actions behind the existing local world-action safety setting: `EQUIP_BEST_ITEM`, `DROP_ITEM`, and `GUARD_OWNER` reject while world actions are disabled. `DROP_ITEM` only drops the selected hotbar stack near the NPC. `EQUIP_BEST_ITEM` selects a useful hotbar weapon only when nearby combat context exists and otherwise rejects safely. `GUARD_OWNER` stays near the owner and attacks only visible hostile entities close to the owner. Combat avoids the owner, players, and other OpenPlayer NPCs. Instruction parsing has a pure Java validation seam covering strict coordinates, bounded patrol distance, and clamped optional radii.

### Non-Goals

- No full scripting language, macro runtime, or arbitrary server command bridge.
- No PlayerEngine, Automatone, or opaque automation jar integration in this phase.
- No client-authoritative world mutation.

## Phase M: NPC-Backed Navigation And Controller Foundation

### Goal

Create a clean NPC-backed movement/controller foundation for OpenPlayer NPCs so future pathfinding claims are accurate and not based on controlling the local player.

### Requirements

- Provide a server-side controller abstraction that drives `OpenPlayerNpcEntity` movement, looking, stopping, and task progress directly.
- Support bounded navigation requests with explicit target, range, timeout, stuck detection, and unloaded-chunk rejection.
- Keep backend status honest: distinguish vanilla NPC-backed control, optional reflective player-command bridges, unavailable adapters, and unsupported actions.
- Preserve owner checks and server authority for all movement requests.
- Avoid hard dependencies or vendored jars.

### Implementation Guidance

- Start with vanilla Minecraft navigation/control APIs available to the NPC entity before considering adapters.
- Keep a narrow adapter boundary so future pathfinding integrations can be reviewed independently.
- Do not claim Baritone or another player-controller backend drives `OpenPlayerNpcEntity` until a true NPC-backed adapter exists.
- Add instrumentation/status that helps diagnose stuck or rejected navigation without leaking coordinates beyond normal local UI needs.

### Acceptance Criteria

- Movement, follow, return, and stop can run through a documented NPC-backed controller path.
- Unreachable, unloaded, too-distant, or timed-out targets reject or stop safely.
- Status text accurately identifies the active controller and its limitations.
- Fabric and Forge behavior is manually verified for basic navigation tasks.

### Non-Goals

- No vendored PlayerEngine, Baritone, Automatone, or opaque pathfinding jar.
- No claim that stock local-player automation controls server-side NPCs.
- No advanced parkour, mining routes, or combat navigation beyond explicitly accepted bounded tasks.

### Current Implementation Notes

OpenPlayer now has a focused Phase M controller foundation inside the vanilla automation backend. Long-running queued tasks start an `AutomationControllerMonitor` that tracks elapsed ticks, timeout, repeated missing movement progress, cancellation, completion, and bounded terminal reasons through pure Java state. The backend applies that monitor to vanilla NPC-backed `MOVE`, `FOLLOW_OWNER`, `PATROL`, `COLLECT_ITEMS`, `BREAK_BLOCK`, `PLACE_BLOCK`, `ATTACK_NEAREST`, and `GUARD_OWNER` execution where applicable. When timeout or stuck detection fires, vanilla navigation is stopped and the active task is cleared deterministically.

`STOP` clears queued and active tasks, stops vanilla navigation, zeroes movement, and resets controller status to idle. `REPORT_STATUS` now includes active kind, queued count, controller state, and bounded reason text in addition to existing local health and selected-slot data; it does not expose paths, target coordinates, secrets, or raw provider text. Coordinate-targeted navigation and block tasks reject unloaded target chunks at submission time. This remains simple vanilla navigation only: no Baritone, AltoClef, Automatone, PlayerEngine, external pathfinding dependency, or broad planner has been added.

## Phase N: Player-Like Interaction Manager Expansion

### Goal

Expand player-like NPC interactions through a safe manager that centralizes inventory, equipment, item use, block/entity interaction, cooldown, and permission checks.

### Requirements

- Add a server-side interaction manager for selected hotbar slot, equipment changes, item use, block interaction, entity interaction, drops, pickup policy, and inventory transfer where explicitly approved.
- Respect reach, line-of-sight where practical, loaded chunks, permissions, cooldowns, owner safety, and disabled-by-default world action settings.
- Keep inventory and equipment persistence compatible with existing NPC entity storage.
- Reject interactions that require a real `Player` when no safe NPC-backed substitute exists.
- Report clear accept/reject reasons for UI and conversation surfaces.

### Implementation Guidance

- Build on existing `OpenPlayerNpcEntity` helpers and vanilla automation backend behavior.
- Keep each interaction path small and independently testable where possible.
- Prefer conservative rejection over partial emulation that can duplicate items, bypass permissions, or mutate the world unexpectedly.
- Document every interaction that is intentionally approximate rather than truly player-equivalent.

### Acceptance Criteria

- Hotbar selection, equipment changes, drops, pickup behavior, and approved block/entity interactions work through one manager path.
- Unsafe, disabled, unreachable, unloaded, or unsupported interactions reject without side effects.
- Inventory persistence remains stable across despawn, restore, and server restart.
- Manual QA confirms no obvious item duplication or owner-bypass behavior in supported flows.

### Non-Goals

- No full real-player emulation, commercial account session, client inventory authority, crafting automation, or container automation unless later approved.
- No bypass of server permissions, protections, cooldowns, or reach checks.
- No opaque helper jars or copied Player2NPC/PlayerEngine logic.

## Phase O: Cross-Loader Manual QA And Release Packaging

### Goal

Complete cross-loader verification and package clean local parity releases with accurate scope, licensing, and known limitations.

### Requirements

- Run Fabric and Forge manual QA for local characters, skins, UI, lifecycle, multi-companion assignments, conversation disabled/enabled states, navigation, interactions, and packaging metadata.
- Verify docs, README, changelog/release notes where present, mod metadata, license declarations, and dependency provenance remain accurate.
- Ensure release artifacts contain no secrets, local character files with credentials, remote skin caches, vendored opaque jars, or copied proprietary code.
- Keep packaging targeted to Minecraft 1.20.1 and Java 17 unless the project intentionally changes targets.
- Record known gaps honestly and do not market unsupported behavior as implemented.

### Implementation Guidance

- Use a repeatable manual QA checklist split by Fabric, Forge, single-player, and multiplayer/server-hosted scenarios.
- Run `./gradlew build` with Java 17 before implementation release candidates when available.
- Run `git diff --check` for documentation and packaging changes.
- Keep release notes concise and explicit about local/offline defaults and optional provider configuration.

### Acceptance Criteria

- Fabric and Forge builds launch and pass the manual local parity checklist.
- Packaged artifacts and repository-visible metadata declare `AGPL-3.0-only` and contain no secrets or opaque vendored jars.
- README and parity docs describe implemented behavior, disabled defaults, and known gaps accurately.
- Release notes identify clean-room scope and unsupported online/account features.

### Non-Goals

- No release gating on online services, account login, remote skins, cloud character sync, or proprietary dependencies.
- No hiding known limitations behind vague parity language.
- No committing or publishing artifacts without an explicit release task.

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
