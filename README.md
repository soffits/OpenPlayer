# OpenPlayer

OpenPlayer is an open, multi-loader AI NPC framework for Minecraft. The project targets a legally clean foundation for Player2NPC-style functionality while keeping implementation, dependencies, and build inputs auditable.

## Architecture

- `common`: loader-neutral constants, lifecycle seams, public AI player NPC API contracts, runtime entity registration, pure Java command intent types, runtime intent parser configuration, an opt-in provider-backed intent parser seam, and an automation backend seam.
- `fabric`: Fabric entrypoints that delegate to common initialization.
- `forge`: Forge entrypoints and client event wiring that delegate to common initialization.

The initial target is Minecraft 1.20.1 on Java 17 with an Architectury-style multiloader Gradle layout.

## Milestone Status

Current implementation includes runtime NPC sessions, duplicate prevention, a server-side companion lifecycle manager for selected local characters, local character definition parsing, server-authoritative local character selection from the OpenPlayer UI, basic command intents, item pickup with inventory persistence, explicit NPC hotbar and held-equipment helpers, owner lifecycle cleanup and restore, spawn/despawn networking, a minimal client control screen with safe runtime status, a player-shaped renderer with optional profile skin resource support, client-side local PNG skin loading, and vanilla feature layers for held items, armor, head-slot items, elytra, arrows, and bee stingers, a vanilla NPC-backed task backend, an optional reflective Baritone command bridge, a disabled-by-default runtime intent parser that can use an opt-in JDK-only OpenAI-compatible provider, and an optional per-character conversation loop on top of that parser. Automatone is not directly integrated yet.

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

At runtime, selected local character sessions use a deterministic internal role id derived from `id` with the `openplayer-local-character-` prefix. `defaultRoleId` is reserved metadata for future behavior role selection and is not used as the selected character's session identity.

Selected-character lifecycle actions are handled server-side by a small companion manager on top of `RuntimeAiPlayerNpcService` and `OpenPlayerApi`. The manager does not store transient session ids as character identity; it resolves the current session by owner UUID and the stable local character role id each time. Spawning the same selected character again calls the runtime service with the same stable identity, so the existing companion is reused or relocated instead of duplicating. Unknown or invalid selected character ids are rejected without targeting any runtime session.

Validation rules:

- `id` and `defaultRoleId` use 2-64 lowercase ASCII characters: letters, digits, underscore, or hyphen, starting with a letter or digit.
- `displayName` is required, 1-32 characters, and must not contain control characters.
- `description`, `conversationPrompt`, and `conversationSettings` are optional bounded text fields and must not contain secret-like labels or credentials.
- `skinTexture` is an optional lowercase Minecraft resource id in `namespace:path` form.
- `localSkinFile` is an optional local PNG skin path under `<Minecraft config>/openplayer/skins`, written relative to `<Minecraft config>/openplayer`. Use values like `skins/alex_helper.png`. Absolute paths, drive prefixes, parent traversal, backslashes, empty path segments, paths outside `skins/`, and non-PNG paths are rejected.
- Character files must not store provider API keys, access tokens, passwords, cookies, credentials, or other secrets.

## Local Skin PNGs

Local PNG skins are loaded only by the Minecraft client from its own `<Minecraft config>/openplayer/skins` directory. OpenPlayer does not download skins, query player accounts, cache external profile data, sync image bytes, or store raw image bytes in entity NBT.

For a selected local character, the renderer first tries the client's matching `localSkinFile` when that file exists, is a regular PNG, stays under `skins/`, and has a standard player skin size of `64x32` or `64x64`. If that local file is missing, invalid, or not present on a multiplayer client, rendering falls back to the configured `skinTexture` resource id when present, then to the deterministic default player skin.

The server-side character list sends only safe skin status text such as `default`, `resource`, `local file`, or `local file unavailable`; it does not send absolute filesystem paths.

## NPC Rendering

OpenPlayer NPCs use the vanilla player model with the existing local skin fallback order: matching client local PNG skin, configured skin resource id, then deterministic default player skin. The renderer also composes Minecraft 1.20.1 vanilla feature layers for held items, armor, head-slot items such as pumpkins or skulls, elytra, arrows, and bee stingers. These layers read from the NPC's persisted equipment inventory and do not use account capes, remote profile metadata, or external skin services.

## OpenPlayer Controls UI

Press the OpenPlayer controls key, `O` by default, to open the compact control screen. The screen requests the local character list from the server, so clients only send stable character ids and never send full mutable character data.

The left panel shows loading, empty, validation-error, and character-list states. Validation errors show only safe file names and messages, not absolute filesystem paths or stack traces.

Selecting a character shows its display name, id, description, skin status, lifecycle status, and conversation status. Lifecycle status is resolved by the server-side companion manager from the active runtime session and reports `despawned` when no matching owner plus stable character id session exists. Spawn, despawn, follow, stop, and command text then target that selected character. With no character selected, the Spawn button keeps the original default OpenPlayer NPC spawn behavior.

Despawning a runtime NPC stops its active runtime commands before the entity is discarded. Owner disconnect and server stop continue to use the runtime cleanup hooks so companion tasks are stopped without persisting transient session ids as long-term identity.

The bottom status lines remain safe and presence-only for automation backend, parser state, endpoint, model, and API key status. Character list entries report conversation as `not configured`, `unavailable: parser disabled`, or `available`; they never include provider credentials, absolute paths, or raw provider responses.

## Dependencies

- Architectury API `9.2.14` is used from public Maven coordinates for shared Fabric and Forge entity registration and lifecycle hooks. Architectury API is LGPL-3.0-only.
- The OpenAI-compatible intent provider uses only Java 17 `java.net.http.HttpClient` and adds no external dependency.
- Profile skin resource ids and local PNG skins are local client resources only; OpenPlayer does not add account skin lookup or downloading dependencies.
- No pathfinding or automation jar is vendored. Direct PlayerEngine vendoring remains forbidden.
- Baritone is supported only through an optional reflective command bridge. OpenPlayer does not add a hard Gradle dependency on Baritone; install a Minecraft 1.20.1-compatible Baritone API/mod separately when using `OPENPLAYER_AUTOMATION_BACKEND=baritone`.
- Baritone upstream publishes Minecraft 1.20.1 Fabric and Forge API jars from public GitHub releases and marks the project as LGPL-3.0 with an anime exception. This repository does not redistribute those jars.

## Runtime Intent Parser

Raw command text submitted through the public NPC service is parsed by the runtime-owned intent parser before becoming an `AiPlayerNpcCommand`. The parser is disabled by default and returns unavailable intents without contacting a provider.

For a selected local character, command text uses the per-character conversation loop only when that character has `conversationPrompt` or `conversationSettings` and the runtime parser is enabled. If the parser is disabled, selected-character conversation returns `Conversation unavailable: intent parser disabled`, does not contact any provider, and does not fall back to raw action execution. If the selected character has no conversation fields, command text keeps the existing direct command-text behavior.

The conversation loop assembles a bounded prompt from the selected character's display text, `conversationPrompt`, `conversationSettings`, recent in-memory history, and the player's message. Those character fields are non-secret text only. Provider endpoint, model, and API key remain environment variables or JVM properties only and are never read from character files.

Provider output is untrusted. It must pass through the existing `IntentParser`, become a constrained `CommandIntent`, and then submit through the selected-character `CompanionLifecycleManager` path. Invalid, unavailable, oversized, or unparsable output is rejected without submitting an NPC action. Conversation history is in-memory only, bounded, and stores player messages plus accepted intent summaries rather than raw provider responses.

Set safe JVM system properties or environment variables with these exact names to enable the OpenAI-compatible provider:

- `OPENPLAYER_INTENT_PARSER_ENABLED=true`
- `OPENPLAYER_INTENT_PROVIDER_ENDPOINT=https://example.invalid/v1/chat/completions`
- `OPENPLAYER_INTENT_PROVIDER_API_KEY=...`
- `OPENPLAYER_INTENT_PROVIDER_MODEL=...`

Remaining conversation gaps: there is no chat bubble or separate NPC spoken-response UI, no persisted conversation memory, no per-character model or provider override, no per-character API key support, no rate-limit scheduler beyond provider failure rejection, and unsupported intent kinds still rely on the automation backend to reject safely.

## Automation Backend

Runtime command execution goes through a small automation backend seam. The default backend is `vanilla`, which runs server-side tasks against `OpenPlayerNpcEntity` through vanilla Minecraft APIs. It supports `STOP`, `MOVE`, `LOOK`, `FOLLOW_OWNER`, `COLLECT_ITEMS`, and disabled-by-default world actions for `BREAK_BLOCK`, `PLACE_BLOCK`, and `ATTACK_NEAREST`.

The vanilla backend is a focused NPC task layer, not full PlayerEngine or Baritone parity. `COLLECT_ITEMS` navigates to nearby visible item entities and relies on the NPC inventory pickup path when close, with a bounded close-range attempt so a full inventory cannot keep it active forever. Set `OPENPLAYER_AUTOMATION_ALLOW_WORLD_ACTIONS=true` or JVM property `openplayer.automation.allowWorldActions=true` to allow world-mutating and violent vanilla actions. When enabled, `BREAK_BLOCK` accepts `x y z`, navigates near the loaded target block, rejects air or unbreakable blocks, selects the best scored tool from the NPC hotbar when one is available, swings the main hand, then uses server block destruction with drops. `PLACE_BLOCK` accepts `x y z`, uses the selected hotbar slot when it already contains a `BlockItem`, otherwise selects the first hotbar `BlockItem`, rejects occupied targets, checks simple vanilla survival and collision rules, swings the main hand, and places the block's default state when close. `ATTACK_NEAREST` accepts a blank instruction or radius number, avoids the owner and other OpenPlayer NPCs, swings the main hand, and attacks the nearest visible living target within the original command radius while stopping if the NPC leaves that bound. These tasks are bounded by simple radii and reach checks, but they are not fully deterministic pathfinding plans and do not include full inventory rearrangement, crafting, container interaction, digging pathfinding, item-use callbacks that require a real `Player`, block/entity interact routing, cooldown tracking, or complete automation parity.

## Player-Like Interaction Layer

`OpenPlayerNpcEntity` exposes focused server-side helpers for the current selected hotbar slot, inventory item access, held and equipment slots, selecting a hotbar tool for block breaking, selecting hotbar block items for placement, and visible main-hand swing actions. The selected main-hand slot is now constrained to the nine-slot hotbar; invalid persisted selections fall back to slot `0`.

The interaction layer intentionally avoids broad player emulation. Vanilla APIs that require a real `Player` instance, full item cooldown managers, container menus, or client authority are left as explicit gaps until a safe NPC-backed adapter exists.

Set `OPENPLAYER_AUTOMATION_BACKEND=disabled` to reject automation commands without adding any external automation dependency.

Set `OPENPLAYER_AUTOMATION_BACKEND=baritone` to try the optional Baritone command bridge. The bridge reflects `baritone.api.BaritoneAPI` at runtime and reports `baritone (unavailable)` if Baritone classes, the primary Baritone instance, or the Baritone command manager are unavailable. Supported commands are currently limited to `stop`, `goto x y z`, and `follow players` after owner availability is checked.

The Baritone backend is intentionally honest about scope: stock Baritone controls the primary Baritone-managed local player, not arbitrary server-side OpenPlayer NPC entities. This phase is not true NPC entity pathfinding. Future work must add a real NPC-backed pathfinding adapter before claiming Baritone drives `OpenPlayerNpcEntity` directly.

## Roadmap

- Establish loader-neutral NPC domain contracts.
- Add entity, persistence, and networking slices.
- Evaluate provider-backed command parsing behavior in runtime playtesting.
- Continue near-1:1 local parity extension phases for multi-companion assignments, gallery/detail UI polish, local character editing/import/export, spoken response UX, expanded safe intents, NPC-backed navigation, interaction management, and cross-loader packaging QA.
- Expand NPC-backed automation beyond the current vanilla task layer only through a clean pathfinding and action adapter boundary.
- Evaluate Automatone integration for movement automation once public coordinates, loader/version compatibility, license posture, and adapter boundaries are clear.
- Keep future parity work clean-room and local/offline by default: no account login, online character service, remote skin downloads, opaque jars, or secrets in character files.

## License

OpenPlayer is licensed under `AGPL-3.0-only`. See `LICENSE`.
