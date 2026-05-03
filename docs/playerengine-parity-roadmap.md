# PlayerEngine-Style Parity Roadmap

> **For Hermes:** Use `subagent-driven-development` and OpenCode for implementation. Keep each phase small, reviewed, and verified before moving to the next phase.

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
- Canonical `/openplayer` command tree plus `/ai` and `/aichat` aliases.
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

**Status:** Phase 7B implements bounded server-side `DEPOSIT_ITEM`, `STASH_ITEM`, and `WITHDRAW_ITEM` for exact item/count and deposit-all normal inventory transfers against loaded nearby vanilla chests and barrels only, plus asynchronous `SMELT_ITEM <output_item_id> [count]` execution through the vanilla furnace workstation adapter. Transfers are gated by `allowWorldActions`, ignore armor/offhand, use deterministic nearest-container/workstation ordering, and apply all-or-nothing snapshots for NPC inventory plus container/furnace slots. `STASH_ITEM` stores one local dimension/block-position stash memory on the NPC and `WITHDRAW_ITEM` prefers that valid stash before falling back nearby. `GET_ITEM` can now plan table-required crafting steps only when a loaded nearby crafting table capability is present; steps preserve table metadata so inventory-only execution rejects them. Recipes are still queried dynamically through the server `RecipeManager`, but workstation execution is capability-based: smoker, blast furnace, campfire, and custom mod machines need explicit safe adapters before execution is claimed. `SMELT_ITEM` uses NPC-carried input and non-container fuel, and completes only after requested output is collected into NPC normal inventory.

**Objective:** Add chest/barrel/furnace/smoker/crafting-table interactions.

**Capabilities:**

- Find nearby safe loaded vanilla chest/barrel containers.
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

**Status:** Phase 10 implements bounded `FARM_NEARBY` and truthful `FISH` command handling. `FARM_NEARBY` accepts blank or a capped positive radius, scans only loaded nearby blocks, harvests one mature vanilla crop per queued task, uses vanilla block drops, and replants only after successful placement when the NPC carries the matching seed/item. `FISH` accepts blank, capped positive seconds, `stop`, or `cancel`, but actual cast/reel execution is deterministically rejected until a safe NPC fishing-hook adapter exists because vanilla fishing hooks are player-bound; OpenPlayer does not simulate fake hook or loot. Both commands remain gated by `allowWorldActions` and surface bounded monitor/rejection reasons for no target, harvested/replanted counts, no-replant cases, unsupported fishing, and cancel paths.

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

**Status:** Phase 12 implements a strict advanced-task vocabulary and truthful safety layer. `LOCATE_LOADED_BLOCK <block_or_item_id> [radius]`, `LOCATE_LOADED_ENTITY <entity_type_id> [radius]`, and `FIND_LOADED_BIOME <biome_id> [radius]` are report-only reconnaissance commands capped to already-loaded server-visible area; they do not navigate, mutate blocks, generate chunks, or call long-range locate APIs. High-risk families `LOCATE_STRUCTURE`, `EXPLORE_CHUNKS`, `USE_PORTAL`, `TRAVEL_NETHER`, `LOCATE_STRONGHOLD`, and `END_GAME_TASK` are recognized but deterministically rejected with exact reasons until separate reviewed safe phases define bounded execution, cancellation, and diagnostics.

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

- Each task must be added as a separate reviewed phase with explicit safety and cancellation semantics.

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

Review the next advanced family as its own bounded phase, starting with explicit safety/cancellation semantics before any chunk exploration, structure location, portal, Nether, stronghold, or End behavior is implemented.
