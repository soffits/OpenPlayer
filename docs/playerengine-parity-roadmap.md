# PlayerEngine-Style Parity Roadmap

> **For Hermes:** Use `subagent-driven-development` and OpenCode for implementation. Keep each phase reviewed and verified, but group related player-like capabilities into coherent adapter clusters instead of splitting every small adapter into its own phase.

**Goal:** Build a legally clean, local/offline, open-source PlayerEngine-style Minecraft agent runtime for OpenPlayer, excluding Player2 commercial/online service features while pursuing broad runtime behavior parity.

**Architecture:** OpenPlayer remains an AGPL-3.0-only Minecraft 1.20.1 Java 17 multiloader mod. PlayerEngine and Player2NPC may be inspected for behavior categories, command/task surface, and runtime expectations, but their code, opaque jars, online APIs, auth flows, token storage, heartbeat services, and remote character/skin/TTS services must not be copied, vendored, or required. The runtime should evolve through bounded clean-room phases: observe, validate, plan, act, monitor, and report.

**Tech Stack:** Java 17, Minecraft 1.20.1, Architectury-style Fabric/Forge multiloader, vanilla server-side NPC runtime first, optional public dependencies only after explicit license/provenance review.

---

## Non-Goals

- Do not vendor PlayerEngine, Player2NPC, AltoClef, Baritone, Automatone, or any opaque jar.
- Do not copy decompiled code line-for-line or translate implementation details line-for-line.
- Do not implement Player2 commercial/online flows: login, token storage, cloud character service, heartbeat service, remote TTS/audio, remote skin downloads, or account/profile marketplaces.
- Do not let provider output directly mutate the world. Provider output remains untrusted and must pass deterministic validation and backend execution gates.
- Do not bypass per-character `allowWorldActions`; it remains the final world/inventory/combat action gate.

---

## Current Baseline

OpenPlayer already has:

- Local character and assignment configuration.
- Local skin loading and player-like NPC rendering/equipment layers.
- Server-authoritative companion lifecycle with persisted NPC reattach.
- Provider-backed conversation with bounded history and strict JSON intent parsing.
- Local provider configuration with secret-safe precedence and diagnostics.
- Safe debug events and local-only raw traces.
- Canonical `/openplayer` command tree plus `/ai` and `/aichat` aliases, including `/openplayer queue <assignmentId> <kind> [instruction]` for explicit validated automation intent submission.
- Vanilla server-side NPC actions for movement, follow, patrol, item pickup, block break/place, nearest attack, guard owner, equipment selection, item use, offhand swap, drop item, report status, and chat replies.
- Alpha.8 context snapshots containing bounded world/agent context for conversation prompts.

---

## Parity Phases

### Phase 1: Runtime Task Diagnostics Foundation

**Objective:** Add deterministic automation controller snapshots that expose active task, monitor state, queue state, cooldowns, and stable summaries.

**Why first:** Full PlayerEngine-style behavior needs visible task state before larger resource, crafting, container, survival, and building systems are reliable or debuggable.

**Files:**

- Create: `common/src/main/java/dev/soffits/openplayer/automation/AutomationControllerSnapshot.java`
- Create: `common/src/test/java/dev/soffits/openplayer/automation/AutomationControllerSnapshotTest.java`
- Modify: `common/src/main/java/dev/soffits/openplayer/automation/AutomationController.java`
- Modify: `common/src/main/java/dev/soffits/openplayer/automation/VanillaAutomationBackend.java`
- Modify: `common/build.gradle`

**Acceptance Criteria:**

- `AutomationController.snapshot()` is side-effect free.
- Snapshot records active kind, monitor status/reason, elapsed/max ticks, queued command count/kinds, and interaction cooldown ticks.
- `REPORT_STATUS` uses the same deterministic snapshot summary.
- Queue kind order is FIFO and defensively copied.
- Tests cover idle, active, queued, defensive copy, null/negative rejection, and bounded reason normalization.
- `./gradlew build` passes with Java 17.

---

### Phase 2: Structured Runtime Context

**Status:** Implemented.

**Objective:** Replace raw prompt-only context strings with structured runtime snapshots while preserving current conversation prompt output.

**Files:**

- Create: `common/src/main/java/dev/soffits/openplayer/runtime/context/RuntimeWorldSnapshot.java`
- Create: `common/src/main/java/dev/soffits/openplayer/runtime/context/RuntimeAgentSnapshot.java`
- Create: `common/src/main/java/dev/soffits/openplayer/runtime/context/RuntimeNearbySnapshot.java`
- Create: `common/src/main/java/dev/soffits/openplayer/runtime/context/RuntimeContextFormatter.java`
- Modify: `common/src/main/java/dev/soffits/openplayer/runtime/RuntimeAiPlayerNpcService.java`
- Modify: `common/src/main/java/dev/soffits/openplayer/conversation/ConversationPromptAssembler.java`

**Acceptance Criteria:**

- Context remains bounded and deterministic.
- No secrets, raw provider data, or unbounded world scans enter context.
- Prompt output remains compatible with existing provider flow.
- Tests cover formatting, bounds, and absence of sensitive data.

---

### Phase 3: Runtime Intent Validation Layer

**Objective:** Add a runtime validator between provider/command parsing and automation execution.

**Files:**

- Create: `common/src/main/java/dev/soffits/openplayer/runtime/validation/RuntimeIntentValidator.java`
- Create: `common/src/main/java/dev/soffits/openplayer/runtime/validation/RuntimeIntentValidationResult.java`
- Create: `common/src/test/java/dev/soffits/openplayer/runtime/RuntimeIntentValidatorTest.java`
- Modify: `common/src/main/java/dev/soffits/openplayer/runtime/CompanionLifecycleManager.java`
- Modify: `common/src/main/java/dev/soffits/openplayer/entity/RuntimeCommandExecutor.java`

**Acceptance Criteria:**

- Provider output remains untrusted after parse.
- Coordinate/radius/blank-instruction policies are centralized where practical.
- `allowWorldActions` is enforced before execution and still enforced by the backend.
- Rejected intents produce deterministic visible status and safe debug events.

---

### Phase 4: Command Vocabulary Parity

**Objective:** Normalize PlayerEngine-style command and intent categories without implementing every complex task immediately.

**New Intent Families:**

- Navigation: `GOTO`, richer target syntax.
- Inventory: `INVENTORY_QUERY`, `EQUIP_ITEM`, `GIVE_ITEM`, `DEPOSIT_ITEM`, `STASH_ITEM`.
- Resources: `GET_ITEM`, `COLLECT_FOOD`, `FARM_NEARBY`, `FISH`.
- Combat: `ATTACK_TARGET`, `DEFEND_OWNER` refinements.
- Control: `PAUSE`, `UNPAUSE`, `RESET_MEMORY`.
- Expression: `BODY_LANGUAGE`.
- Building: `BUILD_STRUCTURE` as a validated plan intent, not arbitrary code execution.

**Acceptance Criteria:**

- Unsupported complex actions return explicit `UNAVAILABLE`/rejected results.
- Prompt schema documents every supported and planned kind.
- `/openplayer` exposes testable command paths where useful.
- No unimplemented action pretends success.

---

### Phase 5: Inventory, Equipment, and Item Transfer

**Status:** MVP implemented for inventory query, exact-id equip, selected/exact-id drop, and owner-only give. Item/count give and drop are one-stack MVP operations capped to the item's vanilla max stack. Containers, stash memory, crafting, resource planning, arbitrary nearby-player transfer, and fuzzy item names remain out of scope.

**Objective:** Implement player-like inventory interaction parity before larger crafting/resource tasks.

**Capabilities:**

- Inventory count/list status.
- Select hotbar slot by item.
- Equip named weapon/tool/armor.
- Give or drop item/count to owner or nearby player.
- Pick up named nearby dropped item/count.
- Bounded stack transfer helpers.

**Acceptance Criteria:**

- Mutating item operations require `allowWorldActions` where appropriate.
- Item ids and counts are validated.
- Inventory summaries are visible through status snapshots and raw trace diagnostics.

---

### Phase 6: Resource and Crafting Planner MVP

**Status:** Phase 6B implemented a bounded `GET_ITEM <item_id> [count]` one-stack local inventory/crafting MVP backed by the server `RecipeManager`, so supported simple datapack and mod crafting recipes present at execution time can be planned through the common recipe-query seam. It validates exact namespaced item ids, rejects over-stack requests, supports safe non-special exact vanilla shaped/shapeless recipes with finite expanded item alternatives including tag-backed ingredients, rejects NBT-bearing ingredients/results and crafting remainders, reports unsupported recipe reasons or deterministic missing materials, and keeps world gathering, physical workstation menus, smelting, and visible resource execution deferred.

**Objective:** Implement clean-room `get <item> <count>` for simple local/offline tasks.

**Capabilities:**

- Dynamic server `RecipeManager` crafting recipe query for supported simple datapack and mod recipes.
- Inventory crafting for 2x2-compatible safe shaped/shapeless recipes.
- Crafting table recipe planning and atomic inventory mutation when a loaded nearby crafting table is available as a capability gate.
- Material dependency planner.
- Visible/reachable resource gathering is deferred until the bounded world-search and navigation phase.
- Tool selection and progress status.

**Acceptance Criteria:**

- The planner is bounded and cancellable.
- It does not mine hidden ores or search unloaded world areas in the MVP.
- It reports missing materials instead of looping indefinitely.

---

### Phase 7: Container, Stash, and Smelting

**Status:** Phase 7B implements bounded server-side `DEPOSIT_ITEM`, `STASH_ITEM`, and `WITHDRAW_ITEM` for exact item/count and deposit-all normal inventory transfers against remembered stash or loaded nearby safe container adapters / `Container` block entities where snapshot/restore no-loss semantics apply; vanilla chests and barrels are examples, not the only supported container surface. Unsupported, locked, or custom containers may reject with deterministic missing-adapter/state diagnostics, and providers must not use arbitrary inventory API calls or claim fake success. Transfers are gated by `allowWorldActions`, ignore armor/offhand, use deterministic nearest-container/workstation ordering, and apply all-or-nothing snapshots for NPC inventory plus container/furnace slots. `STASH_ITEM` stores one local dimension/block-position stash memory on the NPC and `WITHDRAW_ITEM` prefers that valid stash before falling back nearby. `GET_ITEM` can now plan table-required crafting steps only when a loaded nearby crafting table capability is present; steps preserve table metadata so inventory-only execution rejects them. Recipes are still queried dynamically through the server `RecipeManager`, but workstation execution is capability-based: smoker, blast furnace, campfire, and custom mod machines need explicit safe adapters before execution is claimed. `SMELT_ITEM` uses NPC-carried input and non-container fuel, and completes only after requested output is collected into NPC normal inventory.

**Objective:** Add chest/barrel/furnace/smoker/crafting-table interactions.

**Capabilities:**

- Find remembered stash or nearby safe loaded container adapters / `Container` block entities with snapshot/restore no-loss semantics; vanilla chests and barrels are examples, while unsupported, locked, or custom containers may reject deterministically.
- Deposit all normal inventory or exact item/count atomically.
- Withdraw exact item/count atomically.
- Remember one local stash location on the NPC entity.
- Smelt through the vanilla furnace workstation adapter with fuel checks and asynchronous output collection; smoker, blast furnace, campfire, and custom machine adapters remain deferred.
- Use nearby loaded crafting table capability as a recipe planner gate.

**Acceptance Criteria:**

- Container writes are bounded, permission-gated, and server-thread safe.
- Stash memory is local, size-bounded, and resettable.

---

### Phase 8: Navigation Backend Upgrade

**Objective:** Move beyond vanilla navigation while keeping a clean adapter boundary.

**Capabilities:**

- Robust goto/follow/get-to-block/get-to-entity.
- Search/explore loaded chunks.
- Avoidance and stuck recovery.
- Pathing telemetry in snapshots.

**Dependency Policy:**

- Start with first-party pathing improvements where practical.
- Only add optional public pathfinding dependencies after license/provenance review.
- Do not vendor opaque jars.

**Progress:**

- Phase 8 adds first-party navigation telemetry, replan, loaded-target checks, and bounded stuck recovery around the vanilla NPC navigation backend.
- Phase 8 now includes first-party `GOTO` execution for deterministic `x y z`, `owner`, `block <block_or_item_id> [radius]`, and `entity <entity_type_id> [radius]` syntax. Block/entity targeting uses a capped loaded-area search helper that scans only server-visible loaded chunks, reports bounded diagnostics, and queues observable/cancellable navigation through the existing controller snapshot and monitor.
- External pathfinder dependencies remain deferred pending license and provenance review; no Baritone, AltoClef, Automatone, PlayerEngine, or opaque jar dependency is included.

---

### Phase 9: Survival Automation Chains

**Status:** Phase 9 implements explicit first-party survival commands plus an idle survival monitor. `COLLECT_FOOD` accepts blank or a bounded radius and searches only already-loaded nearby item entities for safe ordinary edible drops accepted by the NPC local food policy, excluding potion/stew/container-remainder items. `DEFEND_OWNER` accepts blank or a bounded radius, requires the owner in the same dimension, pre-equips carried armor/weapon when possible, and attacks only hostile danger entities near the owner using the existing attack target path. The idle monitor runs only when `allowWorldActions` is true and no command is active or queued, applies cooldown/backoff, reports fire/lava/projectile danger diagnostics, attempts only bounded loaded adjacent avoidance through vanilla navigation, eats safe carried food before combat at low health, queues conservative self-defense/owner-defense, and equips armor upgrades as a low-risk fallback. Sleep-through-night, hunger-specific behavior, water-specific avoidance, and broader pathfinder-grade hazard avoidance remain deferred until they have explicit safe semantics.

**Objective:** Add background PlayerEngine-style survival priorities.

**Capabilities:**

- Hunger/food monitor.
- Eat safe food.
- Hostile detection and defense chain.
- Creeper/projectile/lava/water avoidance.
- Pre-equip armor/tool/weapon.
- Optional sleep-through-night behavior.
- Fire/self-preservation tasks.

**Acceptance Criteria:**

- Background actions obey character policy.
- Survival actions are observable, cancellable, and bounded.

---

### Phase 10: Farming, Fishing, and Repeatable Work Loops

**Status:** Phase 10 implements bounded `FARM_NEARBY` and truthful `FISH` command handling. `FARM_NEARBY` accepts blank or a capped positive radius, scans only loaded nearby blocks, harvests one mature crop per queued task, uses vanilla block drops, and replants only after successful placement when server block/item metadata exposes a same-block replant capability and the NPC carries the required item. Nether wart maturity is the current explicit adapter exception because vanilla exposes it outside `CropBlock`. `FISH` accepts blank, capped positive seconds, `stop`, or `cancel`, but actual cast/reel execution is deterministically rejected until a safe NPC fishing-hook adapter exists because vanilla fishing hooks are player-bound; OpenPlayer does not simulate fake hook or loot. Both commands remain gated by `allowWorldActions` and surface bounded monitor/rejection reasons for no target, harvested/replanted counts, no-replant cases, unsupported fishing, and cancel paths.

**Objective:** Implement common repeatable work commands.

**Capabilities:**

- Harvest mature crops.
- Replant if seeds are available.
- Fish with rod and stop/cancel support.
- Pickup/deposit loops with progress status.

---

### Phase 11: Building System

**Status:** Phase 11 implements a safe first-party `BUILD_STRUCTURE` MVP using strict primitive instructions only: `primitive=<line|wall|floor|box|stairs> origin=<x,y,z> size=<x,y,z> material=<item_id>`. Plans are capped at 64 placed blocks with each dimension at most 16, require exact namespaced carried block item materials, scan only already-loaded target chunks, reject occupied/colliding/unsupported/fluid targets, place only into air, consume one NPC-carried material only after each successful placement, rollback if post-placement consumption fails, and report truthful accepted/rejected/progress/completion reasons. Arbitrary provider-generated code, scripts, free-form blueprints, JSON schema expansion, chunk loading, overwrites, fluid placement, external pathing/building dependencies, and build resume after cancellation remain out of scope.

**Objective:** Implement safe structure building without arbitrary provider-generated code execution.

**Capabilities:**

- Local blueprint format.
- Simple primitives: line, wall, floor, box, stairs.
- Material estimate.
- Build at coordinate.
- Cancel/resume.
- Collision and permission checks.
- Optional provider-suggested blueprint JSON after strict schema validation.

**Non-Goal:** Execute arbitrary code generated by the provider.

---

### Phase 12: Advanced World Tasks

**Status:** Phase 12 implements a strict advanced-task vocabulary and truthful safety layer. `LOCATE_LOADED_BLOCK <block_or_item_id> [radius]`, `LOCATE_LOADED_ENTITY <entity_type_id> [radius]`, and `FIND_LOADED_BIOME <biome_id> [radius]` are report-only reconnaissance commands capped to already-loaded server-visible area; they do not navigate, mutate blocks, generate chunks, or call long-range locate APIs. `EXPLORE_CHUNKS` and `LOCATE_STRUCTURE` are implemented by later loaded-only phases. High-risk families `USE_PORTAL`, `TRAVEL_NETHER`, `LOCATE_STRONGHOLD`, and `END_GAME_TASK` are recognized but deterministically rejected with exact reasons until separate reviewed safe phases define bounded execution, cancellation, and diagnostics.

**Objective:** Expand toward advanced AltoClef-style task families after the runtime is mature.

**Candidates:**

- Locate structure.
- Explore/search chunks.
- Biome search.
- Portal construction/use.
- Nether travel.
- Stronghold location.
- End/dragon/speedrun tasks.

**Acceptance Criteria:**

- Related tasks should be added as reviewed capability clusters with explicit safety and cancellation semantics.

---

## Remaining Post-12 Parity Phases

The first twelve phases establish a bounded first-party runtime foundation, but PlayerEngine/AltoClef also exposes broader task families such as command control, body-language movement, real fishing, chunk search, structure location, portal/dimension travel, richer resource gathering, and speedrun/endgame tasks. These require clean-room reviewed capability clusters because they can mutate world state, run for a long time, load or inspect chunks, cross dimensions, or imply success when vanilla NPC support is incomplete.

### Phase 13: Control, Memory, and Expression Commands

**Objective:** Implement the low-risk command families that are currently recognized but rejected: `PAUSE`, `UNPAUSE`, `RESET_MEMORY`, and `BODY_LANGUAGE`, plus cleanup of unreachable truthful-unsupported code paths.

**Capabilities:**

- Pause and unpause automation without losing the active/queued task state unless explicitly stopped.
- Surface paused state in controller snapshots, status summaries, and safe diagnostics.
- Reset bounded local conversation/runtime memory without deleting character files or secrets.
- Execute safe body-language actions such as look, nod-like look motion, crouch/sneak pulse, swing/wave, and idle stance where vanilla NPC state supports it.
- Remove or hard-disable stale fake fishing execution paths until a real hook adapter exists.

**Acceptance Criteria:**

- Paused automation performs no world/inventory/combat mutations and does not advance active task progress.
- `STOP` still cancels paused tasks.
- `RESET_MEMORY` clears only safe local history/state and reports exactly what was cleared.
- Body-language instructions are bounded, visual-only, and deterministic; unsupported gestures reject without pretending.

### Phase 14: Interaction and Targeted Combat Refinement

**Status:** Implemented as a capability-gated subset. `INTERACT block <x> <y> <z>` queues loaded, nearby, line-of-sight/reach-bounded reviewed adapters for common vanilla block interactions including levers, buttons, doors, trapdoors, fence gates, bells, note blocks, bed status checks, crafting tables, furnaces, chests, and barrels; unsupported/custom blocks return deterministic missing-adapter diagnostics. `INTERACT entity ...` is runtime-validated and supports initial safe adapters such as shearing sheep with carried shears and milking cows/mooshrooms with carried buckets and inventory capacity. `ATTACK_TARGET [entity] <entity_type_or_uuid> [radius]` resolves loaded in-range targets by UUID or entity type and attacks only explicitly allowlisted hostile/danger vanilla entity types. Trading, breeding/taming without a safe adapter, villager UI, modded/custom interaction, players, owners, OpenPlayer NPCs, passive/friendly/neutral combat, and arbitrary non-hostile combat remain missing adapters or explicit combat-policy exclusions.

**Objective:** Implement safe `INTERACT` and richer `ATTACK_TARGET` semantics without arbitrary entity/block use.

**Capabilities:**

- `INTERACT block <x> <y> <z>` for loaded nearby reviewed vanilla block capability adapters where no fake success is reported.
- `INTERACT entity <entity_type_or_uuid> [radius]` for loaded nearby safe entity adapters such as sheep shearing and cow/mooshroom milking where carried tools/items and inventory deltas can be verified.
- `ATTACK_TARGET <entity_type_or_uuid> [radius]` with owner/same-dimension checks and hostile/explicit-target policy.
- Clear missing-adapter diagnostics for villagers/trading, animal breeding/taming, unsupported item-use-on-block, or modded interactions until specific adapters exist.

**Acceptance Criteria:**

- No arbitrary right-click passthrough from provider text.
- Interactions are loaded-only, nearby-only, cooldown-gated, and policy-gated.
- Combat target selection cannot attack players or passive entities unless a future explicit trust policy is added.

### Phase 15: Real Fishing and Repeatable Work Runtime

**Status:** Repeatable work foundation implemented for bounded `FARM_NEARBY` and no-loss `DEPOSIT_ITEM`/`STASH_ITEM` iterations. `FISH` accepts repeat/duration syntax for validation diagnostics but remains truthfully rejected because vanilla 1.20.1 fishing hooks and rod behavior are player-bound and OpenPlayer does not yet have a safe server-authoritative NPC hook adapter.

**Objective:** Replace truthful `FISH` rejection with a real server-authoritative NPC fishing adapter, or keep rejection if a safe adapter cannot be implemented cleanly.

**Capabilities:**

- Spawn/drive a real fishing hook or equivalent first-party server-side task that produces loot only through vanilla loot mechanics.
- Track cast, bite, reel, timeout, rod durability, inventory capacity, and cancellation.
- Add bounded repeat counts for farming/fishing/deposit loops without infinite automation.
- Current repeat syntax: `FARM_NEARBY` supports blank, legacy radius such as `8`, or `radius=8 repeat=3`/`radius=8 count=3`; `DEPOSIT_ITEM` and `STASH_ITEM` support `<item_id> [count] repeat=3` to avoid ambiguity with item counts; repeat is capped at 5.

**Acceptance Criteria:**

- No fake hook, fake swing success, or fabricated loot.
- Loot is considered complete only after entering NPC inventory.
- STOP/cancel recovers hook/task state without item loss.
- Repeatable work never uses unbounded loops; later iterations start only after the prior iteration truthfully completes, and failures stop further repeats.

### Phase 16: Loaded Chunk Exploration and Search Tasks

**Objective:** Upgrade `EXPLORE_CHUNKS` from deterministic unsupported to a bounded loaded/opt-in exploration task.

**Status:** Phase 16 implements a safe loaded-only subset. `EXPLORE_CHUNKS` accepts blank defaults, `radius=<blocks> steps=<count>`, and `reset`/`clear`; it is gated by `allowWorldActions`, scans only already-loaded chunks near the NPC, chooses bounded safe navigation targets near chunk centers, tracks capped per-controller visited-chunk memory, and reports active navigation through existing status snapshots. It does not generate chunks, call long-range locate APIs, teleport, mutate the world, or claim structure/Nether/End/speedrun support.

**Capabilities:**

- Search only already-loaded chunks with strict radius, candidate, step, and tick caps.
- Maintain visited-chunk memory per NPC/session with reset support.
- Search for blocks/entities/biomes through the existing loaded-area navigator and report progress.

**Acceptance Criteria:**

- No unbounded chunk generation or server locate abuse.
- Search is cancellable, status-visible, and capped by radius/chunk count/ticks.
- Memory is bounded and resettable.

### Phase 17: Universal Resource and Affordance Planner

**Status:** Foundation implemented for `GET_ITEM` exact visible dropped-item acquisition and generic affordance diagnostics. The current layer summarizes exact NPC carried count/capacity, total visible already-loaded LOS matching drops, exact-safe candidate drops with deterministic nearest ordering, loaded crafting-table/furnace workstation capabilities where safe adapters exist, nearby safe container observation, and loaded block-source diagnostics only. `GET_ITEM` may queue bounded targeted dropped-item collection only when exact-safe visible drops can satisfy the missing count and inventory capacity fits; oversized visible stacks are rejected for exact acquisition to avoid over-collection. Completion is based on actual NPC inventory count, not navigation success, and runtime failures may report dropped item unavailable/disappeared before pickup, full inventory, stuck/timeout, missing materials, or unsupported recipes. Generic block breaking, hidden mining, arbitrary resource gathering, buckets, shearing, trading, fishing, portals, and modded machines remain unsupported until separate safe adapters exist.

**Objective:** Move `GET_ITEM` and related resource tasks away from hardcoded per-item scripts toward a universal capability layer. OpenPlayer should implement reusable Minecraft affordances, while the AI/provider may choose goals and high-level strategies only through validated schemas.

**Capabilities:**

- Represent visible loaded-world affordances generically: breakable blocks, reachable dropped items, craftable recipes, smeltable recipes, usable workstations, safe containers, simple entity interactions, carried tools, and inventory capacity.
- Query server registries, tags, recipes, loot/drop outcomes where safely inspectable, and block/item/entity metadata instead of maintaining one `CollectXTask` per resource.
- Plan resource acquisition as bounded primitive chains such as locate-visible-source -> navigate -> equip tool -> break/collect -> verify inventory delta -> craft/smelt/deposit, with every step observable and cancellable.
- Let the AI choose among exposed strategies by emitting validated goals or primitive plans; never let provider text call arbitrary world methods, bypass policy, or invent unsupported operations.
- Keep small explicit adapters only for genuinely special mechanics with non-obvious semantics, such as buckets, shearing, milking, fishing, portals, trading, or modded machines.

**Acceptance Criteria:**

- No hidden ore X-ray, unloaded search, or hardcoded success claims.
- Generic planners derive possible actions from live server state and registries where possible, and reject unsupported metadata shapes deterministically.
- Blocks/items/entities are mutated only after target/tool/path/policy checks pass.
- Completion is based on actual NPC inventory/world-state deltas, not on provider claims.
- Special-case adapters are additive capability modules, not the default architecture.

### Phase 18: Structure Locate and Loot Tasks

**Status:** Phase 18 implements a loaded-only diagnostics foundation. `LOCATE_STRUCTURE <structure_id> [radius] [source=loaded]` is gated by `allowWorldActions`, capped to a small loaded-only radius, and currently supports conservative `minecraft:village` loaded-world evidence sightings from already-loaded distinctive blocks only. Results are accepted diagnostics with `source=loaded_scan` and `evidence_found`, `not_found`, or `unsupported_structure`; they include evidence kind, position, distance, capped checked positions, inspected loaded positions/chunks/candidates, and diagnostic-only nearby loaded chest/barrel hints when present. Container hints do not move items and do not guarantee ownership, loot, or structure membership. It does not use server locate APIs, implicitly load or generate chunks, teleport, navigate to structures, claim exact structure membership, auto-open containers, auto-withdraw items, or support Nether, End, stronghold, desert/jungle pyramid, mineshaft, or speedrun behavior.

**Objective:** Implement a safe subset of structure location/looting without uncontrolled long-range search.

**Capabilities:**

- Server-authorized `LOCATE_STRUCTURE` policies that use loaded-only sightings; server locate calls remain out of scope.
- Diagnostic loaded-world evidence and nearby loaded chest/barrel hints without item movement, ownership guarantees, loot guarantees, or structure membership guarantees.
- Truthful unsupported for complex/destructive structures until adapters exist.

**Acceptance Criteria:**

- No implicit chunk loading or long-distance teleport/path claims.
- Container diagnostics never delete, overwrite, move, open, or withdraw items; any later transfer requires separate explicit owner choice and reviewed no-loss container transfer semantics.
- Locate results clearly state source `loaded_scan` and status `evidence_found`, `not_found`, or `unsupported_structure`.

### Phase 19: Portal and Dimension Travel

**Status:** Phase 19 plus the broad player-like capability expansion implements a player-like MVP for `USE_PORTAL` and `TRAVEL_NETHER`. Instructions are strict key/value syntax with bounded radius. `USE_PORTAL` may name an arbitrary ResourceLocation target for existing loaded portal travel, or no target for any observed changed dimension; completion still requires observing the NPC dimension change. The runtime scans only already-loaded nearby blocks for existing portal evidence and navigates to/into a loaded portal. If requested or inventory-afforded for `TRAVEL_NETHER`, the runtime can build a simple 4x5 obsidian frame as a vanilla Nether portal build adapter from NPC-carried obsidian in loaded air-only space with collision checks and real inventory consumption after each successful placement, then attempts flint-and-steel ignition through a reviewed first-party player-like capability adapter and waits for an observed transition. The phase does not use commands, teleportation, locate APIs, creative placement, bucket-flow portal construction, container materials, hidden services, PlayerEngine/Baritone/AltoClef dependencies, or forced dimension changes.

**Objective:** Implement `USE_PORTAL` and `TRAVEL_NETHER` through reviewed portal/dimension safety semantics.

**Capabilities:**

- Detect nearby loaded existing portals and use them when pathable and safe.
- Optionally build and ignite simple vanilla Nether portals only from carried/available obsidian and flint_and_steel through reviewed first-party player-like capability adapters; bucket-flow and custom portal construction remain missing adapters.
- Track dimension transition, timeout, owner relation, return path, and hazard recovery.

**Acceptance Criteria:**

- No cross-dimension task claims without an observed dimension transition.
- Portal construction is no-loss and rollback-aware where possible.
- Nether hazards and return failure are reported, not hidden.

### Phase 20: Nether Survival, Travel Recovery, and Resource Chain

**Status:** Phase 20 implements a broad truthful foundation for current-dimension and vanilla Nether/resource recovery without adding opaque automation. Portal travel diagnostics now carry origin dimension, target dimension, source, portal/frame positions, observed transition, failure/timeout state, and return affordance through active status and failure summaries. Resource diagnostics include the current arbitrary ResourceLocation dimension id, observed loaded-world recovery affordances, and vanilla Nether constraints where applicable. Endgame preparation hints for blaze rods, blaze powder, ender pearls, eyes of ender, and food/safety items point planners toward visible primitives such as `TRAVEL_NETHER`, `USE_PORTAL`, `REPORT_STATUS`, `GET_ITEM`, `ATTACK_TARGET`, `COLLECT_FOOD`, and `SMELT_ITEM` while naming missing primitives. This phase does not add fortress search, barter/trading, stronghold search, End travel, dragon tactics, forced teleportation, fake hostile-drop collection, or opaque bot dependencies.

**Objective:** Make Nether travel and endgame-resource preparation recoverable and diagnosable as ordinary player-like actions over existing reviewed primitives.

**Capabilities:**

- Report portal origin/target dimensions, portal or frame location, build/use source, observed transitions, timeout/failure reasons, partial frame progress, and return-travel affordance.
- Surface current-dimension survival/resource diagnostics over observed loaded world state, including vanilla Nether-aware constraints such as lava/fire/cliffs and unusable water buckets where applicable.
- Expose endgame preparation as a visible primitive chain for blaze resources, pearl/eye resources, and food/safety preparation rather than a fake monolithic speedrun command.
- Identify available primitives and missing primitives so the planner can ask for player-like follow-up actions or truthfully stop.

**Acceptance Criteria:**

- Portal/current-dimension status gives enough state for a planner or user to request loaded portal use, exploration, owner-follow in the same dimension, STOP/cancel, or vanilla Nether return travel without teleporting or pretending recovery succeeded.
- Resource preparation diagnostics distinguish available carried/visible/craftable actions from missing search, barter, trade, or task-tree orchestration.
- No OP/admin/cheat commands, permission bypass, arbitrary provider-origin code execution, PlayerEngine/Baritone/AltoClef dependency, forced dimension change, or fake success.

### Phase 21: Stronghold, End Preparation, and Dragon Task Tree

**Objective:** Add stronghold and End preparation only as transparent task trees over reviewed primitives.

**Capabilities:**

- Stronghold estimation/search through explicit, reviewable eye-of-ender or loaded-world diagnostics, with bounded state and no hidden locate API claims.
- End portal preparation and End travel only after required materials, portal state, inventory safety, and recovery status are visible.
- Dragon-fight primitives as a cancellable task tree over reviewed movement, combat, inventory, block placement, bed/pearl, and recovery actions where safe for NPCs.

**Acceptance Criteria:**

- Every stronghold/End/dragon subtask is visible, cancellable, and based on implemented primitives.
- Partial progress, missing materials, unsafe dimension state, and recovery options are reported truthfully.
- No speedrun or dragon-completion claim unless the complete dependency chain exists and local integration QA covers it.

**Status:** Phase 21 now provides a deterministic task-tree diagnostic foundation for vanilla endgame preparation plus generic current-dimension recovery. `REPORT_STATUS` includes an `endgame_task_tree` summary with resource preparation, vanilla Nether/blaze resources, pearl/eye resources, stronghold estimation/search, End portal preparation, End travel, dragon-fight primitive, and current-dimension recovery nodes. `LOCATE_STRONGHOLD` accepts blank or `source=diagnostic` and returns a rich vanilla missing-adapter diagnostic instead of using `/locate`, hidden server stronghold APIs, chunk generation, or fake triangulation. `END_GAME_TASK` accepts blank or a reviewed diagnostic phase (`plan`, `prepare`, `stronghold`, `portal`, `travel`, `dragon`, `recovery`) and returns the same visible plan rather than claiming End travel, dragon completion, or speedrun success. Implemented nodes distinguish available reviewed primitives such as `TRAVEL_NETHER`, `USE_PORTAL`, `GET_ITEM`, `SMELT_ITEM`, `COLLECT_FOOD`, `EXPLORE_CHUNKS`, `FOLLOW_OWNER`, and `ATTACK_TARGET` from missing vanilla adapters such as fortress search, eye-of-ender observation/triangulation, End portal room/frame interaction, End travel orchestration, crystal handling, dragon combat positioning, and bed/pearl tactics.

This phase does not add stronghold location execution, eye-of-ender throwing, End portal activation, forced dimension changes, dragon-fight execution, opaque bot dependencies, command APIs, or any speedrun/dragon success claim.

### Phase 22: Task Tree UI, QA Hardening, and Release Candidate

**Objective:** Make advanced automation observable, cancellable, and release-ready.

**Status:** Phase 22 now exposes vanilla endgame and generic current-dimension viewer/world diagnostics on the existing OpenPlayer Controls Status tab through an append-only status packet extension. The status packet does not carry a selected assignment or NPC id, so these lines are not selected-NPC inventory, selected-NPC task state, or hidden queued execution. The visible lines are bounded, structured, and labelled as `diagnostic_snapshot` plus `not_queued`; material-count lines are labelled `source=viewer_inventory`, and current-dimension lines are labelled `source=current_viewer_dimension`. STOP/cancel semantics remain truthful: STOP clears real active or queued runtime tasks, while these diagnostics are snapshots rather than a hidden executor. The status surface shows representative diagnostic subtask statuses, arbitrary current dimension ids, missing primitives, deterministic truncation, and recovery/truth wording without exposing provider output or credentials.

The implementation remains diagnostic-only for vanilla stronghold, End travel, dragon fight, and speedrun flows. Unknown or modded dimensions are not globally unsupported; diagnostics report `current_dimension=<id>`, observed loaded-world recovery affordances, and player-like options such as loaded portal evidence, loaded exploration, owner-follow when in the same dimension, STOP/cancel, and inventory/safety prep. Missing vanilla primitives are still named honestly, including fortress search, eye-of-ender observation/triangulation, loaded stronghold evidence, End portal room/frame interaction, End travel orchestration, crystal handling, dragon combat positioning, and bed/pearl/block tactics. It does not use OP/admin commands, hidden server locate services, teleportation, forced dimension changes, chunk generation/loading, opaque bot dependencies, or copied PlayerEngine/Player2NPC implementation.

QA was hardened with broad capability-cluster checks for bounded viewer/world diagnostic lines, source labels for ServerPlayer-derived inventory and dimension state, no selected-NPC execution claims, no stronghold/End/dragon/speedrun success overclaims, language-key and placeholder parity across English/Japanese/French UI resources, truthful provider prompt wording, unsafe advanced instruction rejection, and avoiding admin command vocabulary in the new status lines.

**Capabilities:**

- Visible status UI for current viewer/world diagnostics, recovery affordances, and missing primitive diagnostics; selected-NPC active or queued task UI remains future work until the packet carries a reliable selected runtime identity.
- Integration QA for Nether/resource/stronghold/End flows across Fabric and Forge with clear fixture coverage and failure-mode checks.
- Release hardening for prompts, validation, debug traces, localization, compatibility, and documentation.

**Acceptance Criteria:**

- Users can see what the NPC is trying, cancel it, and understand recovery or missing-capability state.
- QA covers the broad capability clusters rather than only isolated adapters.
- Release notes and docs describe implemented behavior without overclaiming speedrun or dragon support.

---

## Verification Gates

Every phase must satisfy:

```bash
git diff --check
JAVA_HOME=/opt/data/tools/jdk-17.0.19+10 PATH=/opt/data/tools/jdk-17.0.19+10/bin:$PATH ./gradlew build
```

For UI/networking phases:

- Language key parity across `en_us.json`, `ja_jp.json`, and `fr_fr.json`.
- Append-only or backward-compatible packet evolution where possible.
- No hard-coded user-facing English UI strings outside language JSON.

For action/runtime phases:

- Provider output remains untrusted.
- `allowWorldActions` remains final authority for world/inventory/combat mutations.
- Debug events are secret-safe.
- Raw traces remain local-only and redacted for credentials.

---

## Immediate Next Step

Continue Phase 20 integration QA, then review Phase 21 stronghold/End task-tree semantics before implementing stronghold, End, dragon, or speedrun behavior.
