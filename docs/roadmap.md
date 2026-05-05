# Roadmap

OpenPlayer is an alpha Minecraft companion mod. The goal is simple: make NPC companions feel useful in a normal survival world without turning them into admin tools or hidden command blocks.

This list tracks what already works and what still needs real game-side work.

## Ready now

- [x] Local companion profiles in the Minecraft config folder.
- [x] Local PNG skins for companions.
- [x] Player-shaped NPCs with visible equipment.
- [x] In-game controls and `/openplayer` commands.
- [x] Spawn, stop, reattach, chat, and status commands.
- [x] Optional OpenAI-compatible provider setup.
- [x] Provider test/status page with sanitized errors.
- [x] Safe local logs for debugging provider and automation problems.
- [x] Owner/permission checks for actions that can affect the world.
- [x] Basic movement, looking, following, patrol, and guard behavior.
- [x] Item pickup and item drop helpers.
- [x] Basic inventory helpers such as equipment selection, armor, offhand swap, and selected item use.
- [x] Nearby block break/place actions when policy allows them.
- [x] Simple hostile-defense actions.
- [x] Companion/world status snapshots for chat and planning.
- [x] Nearby terrain, danger, safe standing spots, and visible work areas in companion planning context.
- [x] Active task status, failure reasons, and cancellation points.
- [x] Early support for longer tasks such as gathering materials, crafting a simple item, and delivering it.
- [x] Early support for multiple companions sharing work and build plans.

## Next polish before wider testing

- [ ] Give players clearer in-game messages when an action cannot run.
- [ ] Make the status screen easier to read during long tasks.
- [ ] Add a small first-run guide for provider setup and local profiles.
- [ ] Improve release notes so pack makers know which jar to install and what changed.
- [ ] Add more gameplay smoke tests around the common "ask companion to make or fetch something" flow.

## Survival tasks

- [x] Let companions reason about simple resource goals instead of relying only on one-shot commands.
- [x] Break visible loaded blocks, collect drops, and verify inventory changes for supported resource flows.
- [x] Plan simple crafting goals such as making and delivering a furnace.
- [ ] Handle more recipe shapes and modded recipes without special-case Java macros.
- [ ] Improve tool choice, replacement, and self-maintenance while a task is running.
- [ ] Add better recovery when an item drops in an awkward place or the companion gets stuck.
- [ ] Make food, danger, and low-health behavior feel more natural during long tasks.

## Movement and pathing

- [x] Move through already-loaded areas with server-side NPC navigation.
- [x] Keep nearby chunks active around a working companion instead of force-loading faraway targets.
- [x] Report when a target is outside loaded or reachable space.
- [ ] Improve obstacle handling and stuck recovery.
- [ ] Improve short-range target search for blocks, items, and entities.
- [x] Preserve coordinates and evidence for nearby terrain, hazards, trees, farms, containers, and workstations.
- [ ] Add better path previews/status for long tasks.
- [ ] Review any future pathing library before adoption. OpenPlayer should not quietly bundle a client-player bot and call it NPC control.

## Inventory, crafting, and containers

- [x] Track basic inventory status.
- [x] Pick up and drop items.
- [x] Use simple crafting/resource planning for supported goals.
- [x] Keep supported container transfers from losing items when something goes wrong.
- [ ] Make crafting table, furnace, and container workflows friendlier in normal survival play.
- [x] Notice nearby crafting tables, furnaces, chests, barrels, farms, and trees as structured local evidence.
- [ ] Add more no-loss inventory moves with better error messages.
- [ ] Add clearer support for locked, custom, or modded containers.

## Building

- [x] Add early build plans, material checks, and shared work claims.
- [ ] Add small, strict building plans that place real blocks from inventory.
- [ ] Show progress and partial failures clearly while building.
- [ ] Add rollback/recovery behavior for interrupted builds where practical.
- [ ] Keep free-form model-generated code or scripts out of building.

## Interaction and combat

- [x] Support reviewed safe interactions for ordinary nearby targets.
- [x] Keep combat limited to explicit hostile/danger behavior.
- [ ] Expand ordinary block/entity interactions slowly, with reach and line-of-sight checks.
- [ ] Add better friendly-fire and owner-protection feedback.
- [ ] Avoid broad right-click passthrough from model text.

## Farming, fishing, and repeatable work

- [ ] Add crop harvesting/replanting for nearby loaded farms.
- [ ] Add repeatable work with caps, cancellation, and per-step checks.
- [ ] Add fishing only after there is a real server-side hook workflow for NPCs.
- [ ] Add more routine chores only when the companion can verify what happened in the world.

## Portals and dimensions

- [x] Report current dimension as observed game state.
- [ ] Improve portal detection and travel status.
- [ ] Add vanilla Nether portal preparation/building only when materials and placement checks are ready.
- [ ] Keep modded dimensions generic. Do not hard-code the world model to only Overworld, Nether, and End.

## Modpack support

- [x] Keep the core mod local and Java-side rather than depending on a Node bot.
- [x] Use Minecraft registries where possible instead of tiny vanilla-only lists.
- [ ] Make recipe, block, item, and container behavior friendlier for datapacks and mods.
- [ ] Add a clearer compatibility page for pack makers.

## Not planned

- [ ] No online character marketplace.
- [ ] No bundled account service.
- [ ] No hidden teleport, `/give`, creative-mode shortcuts, or command-block style completion.
- [ ] No provider-generated code execution.
- [ ] No copying closed bot code into the mod.

## Release checks

Before a public prerelease:

- [ ] `git diff --check`
- [ ] Java 17 Gradle build for the current branch.
- [ ] Fabric and Forge jars are present and named with the release version.
- [ ] GitHub Build workflow is green.
- [ ] Release notes explain user-facing changes, known limits, Minecraft version, Java version, and loader artifacts.
- [ ] README stays short and player-readable.
