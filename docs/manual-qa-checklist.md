# Manual QA Checklist

Use this checklist before publishing a local/offline OpenPlayer release candidate. Run the checks on Minecraft `1.20.1` with Java `17`, once with the Fabric artifact and once with the Forge artifact.

## Setup

- Build with `./gradlew build` and install only the loader-specific jar for the loader under test.
- Install the matching Architectury API dependency for the loader under test.
- Start from a clean test world and a test config directory when possible.
- Create at least two local character files under `<Minecraft config>/openplayer/characters`.
- Create at least one assignment file under `<Minecraft config>/openplayer/assignments` that points to an existing character.
- Put one valid `64x64` or `64x32` PNG under `<Minecraft config>/openplayer/skins` and reference it as `skins/<file>.png` from a character file.
- Keep provider and automation environment variables unset for the default offline pass.

## Fabric Client Pass

- Confirm the game reaches the title screen with OpenPlayer and Architectury API installed.
- Open the OpenPlayer controls screen with the default `O` key.
- Confirm the local assignment gallery loads valid characters and assignments.
- Confirm invalid character or assignment files show safe file names and English validation messages.
- Select a local assignment and verify display name, assignment id, character id, description, skin status, lifecycle status, conversation status, and action hints.
- Spawn the selected assignment and confirm exactly one matching NPC appears.
- Press spawn again for the same assignment and confirm the NPC is reused or relocated, not duplicated.
- Spawn a second assignment and confirm both companions keep independent lifecycle status.
- Despawn one assignment and confirm the other remains active.
- Press stop and confirm active movement or queued actions stop.

## Forge Client Pass

- Repeat the Fabric client pass with the Forge artifact and Forge-compatible Architectury API.
- Confirm the OpenPlayer key binding, UI, networking, rendering, lifecycle controls, and status text are equivalent to Fabric.
- Confirm Forge logs do not report missing loader metadata, wrong Minecraft version, or license metadata errors.

## Local Skins And Rendering

- With a valid local PNG skin present on the client, confirm the selected NPC renders that local skin.
- Rename or remove the local PNG and confirm the NPC falls back without disconnecting or crashing.
- Use an invalid `localSkinFile` value such as a non-PNG file name and confirm the UI reports a safe unavailable status.
- Equip or collect items and confirm held items render in the NPC hands.
- Put armor, a head-slot item, and elytra on the NPC where practical and confirm player-like feature layers render.

## File Operations

- Press reload after adding a valid character file and confirm it appears.
- Export the selected character and confirm the exported file appears under `<Minecraft config>/openplayer/exports` with only supported fields.
- Place a valid `.properties` import file under `<Minecraft config>/openplayer/imports`, type the safe file name, and import it.
- Try unsafe import names with parent traversal, absolute-path shape, backslashes, hidden files, and non-`.properties` extensions; confirm each rejects safely.
- Confirm UI and logs do not expose absolute host paths or secrets.

## Conversation And Provider Disabled

- With provider endpoint, model, or API key missing, select a character with `conversationPrompt` or `conversationSettings`.
- Confirm conversation status reports disabled or unavailable without contacting any provider.
- Send command text to that selected assignment and confirm it reports parser disabled rather than executing raw free-form model text.
- Confirm status lines show bounded player text, deterministic greetings, or safe failure messages only.
- Confirm no API keys, endpoint secrets, raw provider responses, stack traces, or absolute paths appear in the UI.
- Confirm strategy/meta pack examples under `docs/strategy-packs` are documented as advisory docs-only reference and are not auto-loaded or executed by the runtime.

## Automation Intents

- With default settings, submit basic allowed intents such as follow, move, look, stop, collect items, patrol, and report status.
- Confirm accepted movement/navigation tasks are server-authoritative, bounded, and visible through safe status text.
- Confirm `REPORT_STATUS` returns bounded health, selected slot, active task, queue, controller status, and generic capability diagnostics without an endgame route tree.
- Submit malformed coordinates, too-distant targets, and unloaded target chunks; confirm they reject without crashing.
- Confirm `STOP` interrupts active and queued tasks, stops navigation, and resets controller status.

## Navigation Monitor

- Send a reachable move or patrol command and confirm it completes or remains active with sane status.
- Send an unreachable or obstructed movement target and confirm timeout or stuck detection stops the task.
- Confirm repeated status checks do not reveal hidden filesystem data, secrets, raw provider text, or unexpected coordinates beyond normal local UI needs.
- Confirm the Status tab labels capability diagnostics as viewer/world status and does not label viewer inventory or dimension state as selected-NPC inventory.
- Confirm stronghold, End, dragon, and speedrun-like requests decompose to generic primitives or report `UNAVAILABLE`/missing-adapter capability diagnostics rather than special Java route commands.
- Confirm unknown or modded dimension/item/block requests use observed registry-backed primitives where possible or report missing adapters, not a blanket product-level unsupported principle.

## Interaction Helpers

- With world actions disabled, submit `EQUIP_ARMOR`, `USE_SELECTED_ITEM`, `SWAP_TO_OFFHAND`, `EQUIP_BEST_ITEM`, `DROP_ITEM`, `BREAK_BLOCK`, `PLACE_BLOCK`, `ATTACK_NEAREST`, and `GUARD_OWNER`.
- Confirm each disabled world or inventory action rejects without mutating the world, inventory, or nearby entities.
- Enable local world actions only in a throwaway test world by spawning a selected character whose local character file has `allowWorldActions=true`.
- Confirm armor equip, offhand swap, selected edible item use, selected stack drop, block break, block place, attack nearest, and guard owner follow documented preconditions and cooldowns.
- Confirm player-only, container, crafting, trading, and arbitrary block-entity interactions without reviewed adapters reject safely as missing adapters or policy/state failures.
- Watch for item duplication, owner targeting, player targeting, unloaded-chunk mutation, or permission bypasses; treat any occurrence as a release blocker.

## Multiplayer And Restart Basics

- Run a dedicated or LAN server with the loader-specific server artifact and matching dependencies.
- Join with a matching client and confirm the server owns local assignment resolution and lifecycle actions.
- Confirm a client without the same local skin file falls back safely while still seeing the NPC.
- Disconnect and reconnect the owner; confirm active tasks stop and companions do not duplicate.
- Stop and restart the server; confirm persisted NPC identities restore only when valid and do not create duplicate selected assignments.
- Confirm non-owner clients cannot control another owner's selected assignments through the UI.

## Release Blockers

- Fabric or Forge cannot launch on Minecraft `1.20.1` with Java `17`.
- Build artifacts contain secrets, local credential files, remote skin caches, opaque vendored jars, or copied proprietary code.
- UI or logs expose provider secrets, absolute host paths, stack traces for normal validation failures, or raw provider responses.
- Disabled parser or disabled world actions still execute provider requests or world mutation.
- Owner disconnect, despawn, stop, or server stop leaves active tasks running or creates duplicate companions.
