# OpenPlayer

OpenPlayer is an open, multi-loader AI NPC framework for Minecraft. The project targets a legally clean foundation for Player2NPC-style functionality while keeping implementation, dependencies, and build inputs auditable.

## Architecture

- `common`: loader-neutral constants, lifecycle seams, public AI player NPC API contracts, runtime entity registration, pure Java command intent types, runtime intent parser configuration, an opt-in provider-backed intent parser seam, and an automation backend seam.
- `fabric`: Fabric entrypoints that delegate to common initialization.
- `forge`: Forge entrypoints and client event wiring that delegate to common initialization.

The initial target is Minecraft 1.20.1 on Java 17 with an Architectury-style multiloader Gradle layout.

## Milestone Status

Current implementation includes runtime NPC sessions, duplicate prevention, a server-side companion lifecycle manager for selected local assignments, local character and assignment definition parsing, safe local character create/update/import/export repository foundations, server-authoritative local companion selection from the OpenPlayer UI, basic and expanded local command intents, item pickup with inventory persistence, explicit NPC hotbar, offhand, armor, and held-equipment helpers, owner lifecycle cleanup and restore, spawn/despawn networking, a paged local assignment gallery with safe runtime status, a player-shaped renderer with optional profile skin resource support, client-side local PNG skin loading, and vanilla feature layers for held items, armor, head-slot items, elytra, arrows, and bee stingers, a vanilla NPC-backed task layer, a disabled-by-default runtime intent parser that can use an opt-in JDK-only OpenAI-compatible provider, and an optional per-character conversation loop on top of that parser. Phase O adds cross-loader manual QA and release packaging guidance for the current near-parity local/offline Player2NPC-style companion experience. This is not commercial online 1:1 Player2NPC parity, and Automatone is not directly integrated yet.

## Local Character Config

OpenPlayer local character definitions use dependency-free Java `.properties` files. The default mod-owned path is `<Minecraft config>/openplayer/characters`, resolved through Architectury's loader config directory hook on Fabric and Forge. Missing directories return an empty character list.

Each character is one `*.properties` file:

```properties
id=alex_helper
displayName=Alex Helper
description=Optional short description shown by future UI.
skinTexture=openplayer:skins/alex_helper
localSkinFile=skins/alex_helper.png
defaultRoleId=helper
conversationPrompt=Optional role/personality text for selected-character conversation.
conversationSettings=Optional non-sensitive local conversation preferences.
```

Supported fields are exactly `id`, `displayName`, `description`, `skinTexture`, `localSkinFile`, `defaultRoleId`, `conversationPrompt`, and `conversationSettings`. Unknown fields are validation errors.

At runtime, selected local assignment sessions use a deterministic internal role id derived from the assignment id with the `openplayer-local-assignment-` prefix. `defaultRoleId` is reserved metadata for future behavior role selection and is not used as the selected companion's session identity.

Local assignment definitions are optional dependency-free Java `.properties` files under `<Minecraft config>/openplayer/assignments`. Supported fields are exactly `id`, `characterId`, and optional `displayName`. Assignment ids and character ids use the same safe id shape as character ids. OpenPlayer also exposes a deterministic default assignment for every loaded local character, with assignment id equal to the character id; this preserves old selected-character requests when no explicit assignment file exists.

Selected-assignment lifecycle actions are handled server-side by a small companion manager on top of `RuntimeAiPlayerNpcService` and `OpenPlayerApi`. The manager does not store transient session ids as assignment identity; it resolves the current session by owner UUID and the stable local assignment role id each time. Spawning the same selected assignment again calls the runtime service with the same stable identity, so the existing companion is reused or relocated instead of duplicating. Two assignments can point at the same character and run independently. Unknown or invalid selected assignment ids are rejected without targeting any runtime session, and each owner is limited to four active local assignments.

Validation rules:

- `id` and `defaultRoleId` use 2-64 lowercase ASCII characters: letters, digits, underscore, or hyphen, starting with a letter or digit.
- `displayName` is required, 1-32 characters, and must not contain control characters.
- `description`, `conversationPrompt`, and `conversationSettings` are optional bounded text fields and must not contain secret-like labels or credentials.
- `skinTexture` is an optional lowercase Minecraft resource id in `namespace:path` form.
- `localSkinFile` is an optional local PNG skin path under `<Minecraft config>/openplayer/skins`, written relative to `<Minecraft config>/openplayer`. Use values like `skins/alex_helper.png`. Absolute paths, drive prefixes, parent traversal, backslashes, empty path segments, paths outside `skins/`, and non-PNG paths are rejected.
- Character files must not store provider API keys, access tokens, passwords, cookies, credentials, or other secrets.

## Local Character File Management

OpenPlayer can safely write local character `.properties` files only under documented mod-owned directories beneath `<Minecraft config>/openplayer`:

- `<Minecraft config>/openplayer/characters/<id>.properties` for active local character definitions.
- `<Minecraft config>/openplayer/imports/<id>.properties` as the only accepted import source directory.
- `<Minecraft config>/openplayer/exports/<id>.properties` as the only export destination directory.

Create/update, import, and export use the same `LocalCharacterDefinition` validation as repository loading. Writes serialize only `id`, `displayName`, `description`, `skinTexture`, `localSkinFile`, `defaultRoleId`, `conversationPrompt`, and `conversationSettings`, omitting blank optional fields. File replacement writes a temp file in the same directory, then uses an atomic move when available with a replace fallback.

The file-management path rejects absolute paths, parent traversal, backslashes, drive prefixes, hidden arbitrary files, non-`.properties` file names, unknown fields, unsafe ids, and secret-like labels or credentials. Import accepts only a safe file name from `imports`; export accepts only a loaded local character id and writes to `exports`. Result messages are safe English text and do not expose absolute filesystem paths to clients.

The current in-game UI surface is intentionally small: reload the local list, export the selected character, or type a safe import file name such as `alex_helper.properties` into the command box and press `Import File Name`. Full form-based character editing remains a future UI polish task; the pure Java service already covers create/update/import/export for server-side use.

## Local Skin PNGs

Local PNG skins are loaded only by the Minecraft client from its own `<Minecraft config>/openplayer/skins` directory. OpenPlayer does not download skins, query player accounts, cache external profile data, sync image bytes, or store raw image bytes in entity NBT.

For a selected local character, the renderer first tries the client's matching `localSkinFile` when that file exists, is a regular PNG, stays under `skins/`, and has a standard player skin size of `64x32` or `64x64`. If that local file is missing, invalid, or not present on a multiplayer client, rendering falls back to the configured `skinTexture` resource id when present, then to the deterministic default player skin.

The server-side character list sends only safe skin status text such as `default`, `resource`, `local file`, or `local file unavailable`; it does not send absolute filesystem paths.

## NPC Rendering

OpenPlayer NPCs use the vanilla player model with the existing local skin fallback order: matching client local PNG skin, configured skin resource id, then deterministic default player skin. The renderer also composes Minecraft 1.20.1 vanilla feature layers for held items, armor, head-slot items such as pumpkins or skulls, elytra, arrows, and bee stingers. These layers read from the NPC's persisted equipment inventory and do not use account capes, remote profile metadata, or external skin services.

## OpenPlayer Controls UI

Press the OpenPlayer controls key, `O` by default, to open the compact control screen. The screen requests the local companion list from the server, so clients only send stable assignment ids and never send full mutable character or assignment data.

The left panel is a paged local assignment gallery with loading, empty, validation-error, selected-row, active/spawned, and despawned states. More than six assignments are navigable with Prev/Next buttons, and selection is preserved by stable assignment id across list refreshes. Validation errors show only safe file names and messages, not absolute filesystem paths or stack traces.

Selecting a row shows its display name, assignment id, character id, description, skin status, lifecycle status, conversation status, recent spoken status lines, and action hints. Lifecycle status is resolved by the server-side companion manager from the active runtime session and reports `despawned` when no matching owner plus stable assignment id session exists. Spawn, despawn, follow, stop, and command text then target that selected assignment, and button labels reflect whether the selected assignment or default NPC path is targeted. With no character selected, the Spawn button keeps the original default OpenPlayer NPC spawn behavior.

Despawning a runtime NPC stops its active runtime commands before the entity is discarded. Owner disconnect and server stop continue to use the runtime cleanup hooks so companion tasks are stopped without persisting transient session ids as long-term identity.

The Status tab remains safe and presence-only for automation backend, parser state, endpoint host, model, API key status, source labels, and local character file operation results. It also shows bounded vanilla endgame and current-dimension viewer/world diagnostic lines for the current status requester. Those lines are diagnostic-only, not selected-NPC inventory or queued execution: material counts are labelled `source=viewer_inventory`, current-dimension observations are labelled `source=current_viewer_dimension`, and stronghold, End travel, dragon fight, and speedrun support still report missing vanilla primitives instead of claiming success. Unknown or modded dimensions are described as observed loaded-world state with player-like recovery options rather than globally unsupported. Character list entries report conversation as `not configured`, `unavailable: parser disabled`, or `available`; spoken status lines are server-authored bounded strings such as deterministic greetings, sanitized player messages, accepted intent summaries, or safe failures. They never include provider credentials, absolute paths, raw provider responses, or raw model output.

Singleplayer hosts and players with sufficient server permission can save the OpenAI-compatible provider fallback from this screen. The provider form writes only `<Minecraft config>/openplayer/provider.properties`, never character files. Leaving the API key box blank preserves the existing persisted key; use the explicit clear-key toggle before saving to remove it.

## Dependencies

- Architectury API `9.2.14` is used from public Maven coordinates for shared Fabric and Forge entity registration and lifecycle hooks. Architectury API is LGPL-3.0-only.
- The OpenAI-compatible intent provider uses only Java 17 `java.net.http.HttpClient` and adds no external dependency.
- Profile skin resource ids and local PNG skins are local client resources only; OpenPlayer does not add account skin lookup or downloading dependencies.
- No pathfinding or automation jar is vendored. Direct PlayerEngine vendoring remains forbidden.

## QA And Release Packaging

Release candidates should be built with Java 17 from the repository root:

```sh
./gradlew build
```

Run `git diff --check` before reporting documentation or packaging changes. Manual release QA is tracked in `docs/manual-qa-checklist.md`, including Fabric and Forge passes for local characters, assignments, skins, gallery, file operations, conversation status, automation intents, navigation monitor behavior, interaction helpers, disabled world actions, provider-disabled behavior, and multiplayer or server restart basics.

OpenPlayer currently publishes artifacts only through GitHub Releases. Version tags use `v{version}`, with alpha, beta, release-candidate, pre, and canary tags marked as prereleases; stable tags omit prerelease channel text. Prerelease notes should summarize user-facing changes and name the Fabric and Forge runtime jars for that version. The release workflow uploads only the loader-specific runtime jars from `fabric/build/libs/openplayer-fabric-{version}.jar` and `forge/build/libs/openplayer-forge-{version}.jar`. Do not upload `common/build/libs` jars, `*-dev-shadow.jar` files, `*-plain.jar` files, Gradle caches, logs, local configs, provider credentials, remote skin caches, opaque jars, or copied proprietary code. Packaging scope, known limitations, GitHub-only release policy, future Modrinth note, configuration flags, artifact locations, and AGPL source obligations are documented in `docs/release-packaging.md`.

## Runtime Intent Parser

Raw command text submitted through the public NPC service is parsed by the runtime-owned intent parser before becoming an `AiPlayerNpcCommand`. The parser is disabled until provider endpoint, model, and API key all resolve, and returns unavailable intents without contacting a provider while incomplete.

For a selected local character, command text uses the per-character conversation loop only when that character has `conversationPrompt` or `conversationSettings` and the runtime parser is enabled. If the parser is disabled, selected-character conversation returns `Conversation unavailable: intent parser disabled`, does not contact any provider, and does not fall back to raw action execution. If the selected character has no conversation fields, command text keeps the existing direct command-text behavior.

The conversation loop assembles a bounded prompt from the selected character's display text, `conversationPrompt`, `conversationSettings`, `allowWorldActions` policy, recent in-memory history, and the player's message. Those character fields are non-secret text only. Provider endpoint, model, and API key are never read from character files.

Provider output is untrusted. It must pass through the existing `IntentParser`, become a constrained `CommandIntent`, and then submit through the selected-character `CompanionLifecycleManager` path. Invalid, unavailable, oversized, or unparsable output is rejected without submitting an NPC action. Conversation history and spoken status are in-memory only and bounded; the UI shows sanitized player messages, deterministic greetings, accepted intent summaries, and safe failure/status messages rather than raw provider responses.

Set safe JVM system properties or environment variables with these exact names to enable the OpenAI-compatible provider. The provider is auto-enabled only when endpoint, API key, and model all resolve. JVM system properties have highest priority, environment variables have second priority, and the in-game UI fallback in `<Minecraft config>/openplayer/provider.properties` has third priority:

- `OPENPLAYER_INTENT_PROVIDER_ENDPOINT=https://example.invalid/v1/chat/completions`
- `OPENPLAYER_INTENT_PROVIDER_API_KEY=your-provider-key`
- `OPENPLAYER_INTENT_PROVIDER_MODEL=...`

The UI fallback uses `endpoint`, `model`, and `apiKey` fields in `provider.properties`. Server status may reveal the endpoint host, whether a model is configured, whether an API key is present, and which source supplied each value, but it never sends the API key value back to clients.

Remaining conversation gaps: there is no TTS, speech recognition, audio dependency, chat bubble, raw model response display, persisted conversation memory, per-character model or provider override, per-character API key support, rate-limit scheduler beyond provider failure rejection, or broader action permission profile beyond the automation backend safety gates.

## Automation Backend

Runtime command execution goes through OpenPlayer's built-in vanilla NPC task layer, which runs server-side tasks against `OpenPlayerNpcEntity` through vanilla Minecraft APIs. It supports `STOP`, `MOVE`, `LOOK`, `FOLLOW_OWNER`, `PATROL`, and no-mutation `REPORT_STATUS`, plus disabled-by-default local world/inventory/combat actions for `COLLECT_ITEMS`, `EQUIP_BEST_ITEM`, `EQUIP_ARMOR`, `USE_SELECTED_ITEM`, `SWAP_TO_OFFHAND`, `DROP_ITEM`, `BREAK_BLOCK`, `PLACE_BLOCK`, `ATTACK_NEAREST`, and `GUARD_OWNER`.

The vanilla task layer is focused NPC behavior, not full PlayerEngine or third-party pathfinding parity. `PATROL`, `MOVE`, `LOOK`, `FOLLOW_OWNER`, and no-mutation `REPORT_STATUS` run through bounded vanilla navigation and local NPC state. Per-character `allowWorldActions=true` allows local world, inventory, and combat vanilla actions for that selected character only, including active and passive item collection; missing or default spawns remain disabled. World, inventory, and combat actions reject at execution time unless that NPC was spawned from a character with `allowWorldActions=true`.

Queued long-running vanilla tasks now use a small NPC-backed controller monitor with max ticks, simple stuck/progress detection, cancellation, completion, deterministic terminal reasons, and bounded status strings. On timeout or stuck detection, the backend stops vanilla navigation and clears the active task instead of looping forever. `STOP` clears queued and active tasks, stops navigation, zeroes movement, and resets controller status to idle. `REPORT_STATUS` includes active kind, queued count, controller state, and bounded reason text without exposing coordinates, paths, secrets, or raw provider text. `MOVE`, `PATROL`, `BREAK_BLOCK`, and `PLACE_BLOCK` reject unloaded target chunks at submission time.

Immediate player-like interaction intents use a deterministic NPC-ticked cooldown, so repeated accepted interactions reject until the cooldown elapses. Failed precondition checks do not consume this cooldown. With per-character world actions enabled, `EQUIP_ARMOR` swaps the best available armor upgrade from NPC inventory into empty or weaker armor slots, `USE_SELECTED_ITEM` only completes local vanilla eating for selected stackable edible main-hand stacks that do not have container or remainder semantics, and `SWAP_TO_OFFHAND` swaps the selected hotbar stack with the NPC offhand. These intents require blank instructions; empty, unusable, unsupported, or cooldown-blocked requests reject with bounded status text. `STOP` resets this interaction cooldown along with queued and active tasks.

## Player-Like Interaction Layer

`OpenPlayerNpcEntity` exposes focused server-side helpers for the current selected hotbar slot, inventory item access, held and equipment slots, selected-stack offhand swapping, best-available armor upgrades, selected eat/drink item use, selecting a hotbar tool for block breaking, selecting hotbar block items for placement, and visible main-hand swing actions. The selected main-hand slot is now constrained to the nine-slot hotbar; invalid persisted selections fall back to slot `0`.

The interaction layer translates agent intent into normal Minecraft player-like actions where reviewed first-party adapters can preserve authority, cooldown, reach/LOS, real inventory/world-state mutation, and truthful completion. Vanilla APIs that require a real `Player` instance, full item cooldown managers, container menus, or client authority are treated as missing adapters until a safe NPC-backed implementation exists, not as permanent product restrictions.

## Roadmap

- Establish loader-neutral NPC domain contracts.
- Add entity, persistence, and networking slices.
- Evaluate provider-backed command parsing behavior in runtime playtesting.
- Continue near-parity local/offline extension work only where gaps are explicitly documented; cross-loader QA and release packaging guidance now exist for the current local companion release scope.
- Expand NPC-backed automation beyond the current vanilla task layer only through a clean pathfinding and action adapter boundary.
- Evaluate Automatone integration for movement automation once public coordinates, loader/version compatibility, license posture, and adapter boundaries are clear.
- Keep future parity work clean-room and local/offline by default: no account login, online character service, remote skin downloads, opaque jars, or secrets in character files.

## License

OpenPlayer is licensed under `AGPL-3.0-only`. See `LICENSE`.
