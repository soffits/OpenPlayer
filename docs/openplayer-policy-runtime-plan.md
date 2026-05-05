# OpenPlayer Policy Runtime Plan

> This document records the implementation plan for externalized NPC movement/safety policies and the next provider-runtime reliability milestones. It is a plan, not a shipped capability claim.

## Goal

Make OpenPlayer NPC automation reliable enough for multi-step survival tasks by separating slow LLM planning from fast local runtime decisions. The LLM decides goals and high-level next primitives; the server-side runtime resolves chunk presence, movement goals, safety constraints, item pickup, block harvesting truth, stuck recovery, immediate danger handling, objective progress, and eventually resource/build planning.

The target is a strong OpenPlayer-native agent runtime: long-horizon planning, multi-step decomposition, bounded retries, tool-chain execution, truthful observations, and per-NPC policy control. It should become more proactive and capable than a player at planning, while still acting through audited vanilla server-side NPC primitives rather than hidden cheats or opaque bot dependencies.

## Provenance And Reference Posture

OpenPlayer can learn from public Minecraft bot/agent ecosystems, but must implement OpenPlayer-native Java code for server-side `OpenPlayerNpcEntity` behavior.

Reference projects inspected for concepts only:

- `PrismarineJS/mineflayer-pathfinder` (MIT): movement profiles, movement goals, avoid/break/place exclusions, entity avoidance.
- `PrismarineJS/mineflayer-collectblock` (MIT): collect workflow, safe break checks, item drop observation, item entity pickup.
- `mindcraft-bots/mindcraft` (MIT): profile layering, action lifecycle, self-prompt loop, objective validators, resource dependency planning, skill retrieval, multi-agent coordination, blueprint diff, and memory separation.
- `YuvDwi/Steve` at commit `034afb5` (`2026-01-17 Integrate plugin system into ActionExecutor with async planning`): async planning handoff, action registry/plugin shape, state machine/events, collaborative build sections, tick-based actions, world knowledge cache, and optional idle follow behavior.

Do not copy source from reference projects into OpenPlayer. Recreate useful concepts as small Java abstractions over the existing Minecraft 1.20.1 server-side runtime.

## Architecture Direction

```text
OpenPlayer runtime
  ├─ Provider planner: slow, high-level strategy and structured tool calls
  ├─ Intent parser/validator: provider output is untrusted input
  ├─ Automation backend: server-authoritative primitive execution
  ├─ Active chunk tickets: bounded player-like local ticking for active NPC automation
  ├─ Navigation backend: vanilla execution with OpenPlayer goal resolution
  ├─ MovementProfile: externalized policy data
  ├─ ModeScheduler: fast local safety/recovery modes
  ├─ Action lifecycle/state machine: cancellable, observable long actions
  ├─ Objective/Resource planner: verifiable long-goal progress
  ├─ TeamTaskBlackboard: multi-NPC work claims and coordination
  └─ StructuredObservation: truthful task results back to the planner
```

The runtime must never treat provider text as trusted execution. Policies constrain what the runtime may do; they do not grant permissions beyond profile/server settings.

## Externalized Policy Model

Each NPC profile should be able to select its own policy. A policy may come from built-in resources, server config override, datapack-like resources, or a future addon mod.

Example profile binding:

```properties
movementPolicy=openplayer:companion_safe
allowWorldActions=true
allowObstacleBreaking=true
allowSelfDefense=false
```

Policy data belongs in bundled/server data such as:

```text
data/openplayer/movement_policies/server_caps.json
data/openplayer/movement_policies/companion_safe.json
```

Java may keep only emergency fail-closed fallbacks for missing/corrupt data. Concrete Minecraft block/entity lists should live in policy data, not in runtime code.

Effective policy resolution:

```text
global server caps
  -> built-in/default policy
  -> server policy override
  -> NPC profile-selected policy
  -> validated per-NPC runtime override draft if approved
```

The effective policy is always bounded by global caps.

### LLM Policy Drafts

LLMs may propose policy changes only through a constrained tool or future UI flow. They must not write arbitrary files.

Required safety rules:

- LLM policy changes create a draft/override, not an immediate unrestricted runtime mutation.
- The draft is validated against an allowlist schema and server-side permissions.
- The runtime rejects dangerous expansions such as breaking containers, beds, block entities, protected blocks, or attacking neutral/villager-like entities unless a server-authorized profile explicitly allows it.
- Safe event logs record deterministic summaries of policy changes; raw provider text must not be written to safe logs.
- Per-NPC overrides are allowed, but cannot exceed global server caps.

## Consolidated Implementation Phases

The roadmap is intentionally grouped into four broad phases. Each phase contains multiple workstreams so OpenCode can take a coherent capability cluster without losing the detailed requirements from the earlier six-phase plan.

### Phase 1: Runtime Foundation, Chunk Presence, Policy, And Primitive Truthfulness

Goal: remove the current low-level blockers that make long tasks collapse even when the provider chooses the right primitive.

Status note: parts of this phase have already landed on `mc/1.20.1`, including external movement policy data, bounded active NPC chunk tickets, stronger primitive tests, and planner prompt/retry improvements. The remaining work in this phase is hardening and regression closure, not a claim that every bullet below is complete.

Workstreams:

1. Player-like local chunk ticking for active NPC automation.
   - NPCs should keep nearby chunks ticking while they are actively executing or have queued automation, like a player walking through the world.
   - Use bounded local tickets only around the NPC's current chunk; do not force-load arbitrary long-range target chunks.
   - Keep long-range unloaded target rejection truthful.
   - Ticket radius, refresh cadence, and timeout are finite and server-side constants/configurable caps.
   - Release tickets on idle, stop, removal, death/despawn path, level/dimension change, or session cleanup.
   - Tests cover refresh, replacement after movement, idle release, and no stale tickets.
2. Externalized movement/safety policy primitives.
   - `MovementProfile`
   - `BlockSafetyPolicy`
   - `EntitySafetyPolicy`
   - `MovementPolicyLoader`
   - bundled `openplayer:server_caps` and `openplayer:companion_safe` policy data
   - optional NPC profile policy binding through character profile properties
   - fail-closed emergency fallback only; no normal Minecraft ID policy tables in Java code
3. Item pickup navigation correctness.
   - `COLLECT_ITEMS` must not navigate directly to `ItemEntity.position()` as a block target.
   - Resolve item pickup as an entity/range goal.
   - Completion must require inventory delta or item disappearance while the NPC is close enough to plausibly pick it up.
   - Replace vague failures with precise reasons such as `item_target_lost`, `item_no_reachable_pickup_position`, `item_navigation_rejected`, `item_not_picked_up`, or `inventory_full`.
4. Block breaking truthfulness.
   - `BREAK_BLOCK` reports broken block id, target coordinate, inventory delta, and nearby drop delta.
   - If a survival block requires the correct harvest tool for drops, do not imply the drop was collected.
   - Stone without a pickaxe must not be reported as cobblestone acquisition.
5. Locate-to-action coordination.
   - `find_loaded_blocks` observations include a recommended next coordinate/action when appropriate.
   - Provider prompt text instructs the model to use returned coordinates for the matching next action instead of substituting unrelated `actionableTargets` coordinates.
6. Tests and verification.
   - Add/maintain main-method tests for policy loading/caps, chunk ticket model behavior, item pickup target resolution, item pickup completion semantics, block break truthful observation, provider prompt/schema drift, and planner retry classification.
   - Run Java 17 targeted tests and full `./gradlew :common:check build`.

Acceptance criteria:

- Active NPC automation no longer stops merely because the owner/player is far away and local NPC chunks are not ticking.
- The uploaded log failure `COLLECT_ITEMS cancelled reason=navigation_item_position_rejected` cannot recur through the same item-position path.
- `BREAK_BLOCK completed` is no longer an empty success; it includes post-action facts.
- The provider is told to use located coordinates consistently.
- No new opaque pathfinding dependency is added.
- No provider-origin policy/action bypass is introduced.

### Phase 2: Strong Single-Agent Planner Loop, Local Modes, Action Lifecycle, And Objective Progress

Goal: make one NPC behave like a strong autonomous agent: it can pursue explicit goals over many turns, decompose work into primitives, retry with changed strategy, and expose verifiable progress without relying on provider latency for every tick-level decision.

Workstreams:

1. Policy-gated local mode scheduler.
   - `AutomationMode`
   - `ModeScheduler`
   - `UnstuckMode`
   - `ItemCollectingMode`
   - `DangerAvoidanceMode`
   - `SelfDefenseMode`
   - Stuck, danger, and opportunistic pickup should not depend on provider latency.
2. Policy-driven obstacle and danger handling.
   - Low-risk obstacles can be cleared only if policy and task permissions allow it.
   - Dangerous or ambiguous obstacles become structured observations for the planner.
   - Never break containers, block entities, beds, protected blocks, or user-valuable blocks by default.
   - Creepers and low-health situations pause/avoid locally.
   - Simple hostile defense runs locally only when policy allows combat.
   - The LLM sees summarized safety events, not tick-level combat decisions.
3. Strong autonomous planner behavior.
   - Per-NPC explicit enable switch.
   - Finite budgets for iterations, provider calls, tool steps, wall time, no-progress count, and retry count.
   - Stop/pause commands and user-control interruption.
   - Prompt tells the provider to actively inspect inventory/world, gather missing materials, craft missing tools, and continue toward explicit user goals.
   - Provider asks the user only for ambiguity, authorization, missing capability, unsafe world edits, or policy denial.
   - Add `ask_user` as a structured tool only if it can be validated and clearly separated from automation actions; otherwise keep asking through `chat`.
   - Retryable primitive failures re-enter planning; terminal safety/policy/missing-adapter failures stop truthfully.
4. Async planning handoff.
   - Steve's useful pattern is a non-blocking planning future checked from tick flow.
   - OpenPlayer should formalize this as `PlannerJob` / `PlannerMailbox`: one active planning job per NPC or objective, cancellable, visible in status UI, and never blocking the server thread.
5. Unified action lifecycle and state machine.
   - Explicit states: idle, planning, executing, paused, retrying, blocked, completed, failed, cancelled.
   - Long actions expose action label, timeout, started tick, last progress tick, cancellation reason, last structured result, retry budget, and optional resume token.
   - Long actions must be interruptible, cancellable, and report loop guards.
   - Safe events record planner state transitions, action start/finish/failure, retry, handoff, and cancellation.
6. Objective validators and task progress.
   - Inventory objective: target item/count exists.
   - Delivery objective: target player received item/count.
   - Block/world objective: expected block/world predicate holds.
   - Build objective: plan mismatch score and required next edits.
   - Progress reports include missing items, blocker reasons, and suggested next runtime action.
7. Memory separation and effective profile reporting.
   - Merge order: server defaults -> built-in role preset -> NPC character profile -> selected policy -> validated runtime overrides.
   - Status/debug snapshot exposes selected policy, enabled/disabled local modes, provider source, permission gates, and runtime overrides without exposing secrets.
   - Separate conversation memory, place memory, task memory, world-fact memory, and NPC preference memory.
   - Never mix raw provider traces into safe memory.

Acceptance criteria:

- A single NPC can continue a complex task over many tool/observation turns without stopping after the first failed primitive.
- Stuck, danger, and pickup recovery are bounded, local, and auditable.
- Users can inspect why a NPC behaves a certain way without reading code.
- Long actions cannot fake success; they expose verifiable progress or precise failure.
- Objective state survives across planner turns without raw prompt leakage.
- The furnace task gets farther than locate/dig/pickup and fails truthfully if still missing a capability.

### Phase 3: Resource, Crafting, Tool-Scope, And Delivery Planning

Goal: make survival/crafting tasks deliberate instead of asking the LLM to infer every dependency from scratch.

Workstreams:

1. Java resource dependency planner.
   - Represent craft, smelt, collect block, hunt entity, prerequisite, fail count, and next-ready leaf.
   - `furnace` decomposes into missing cobblestone, tool requirements, workstation requirements, and delivery objective.
   - Planner must return real primitive steps or precise missing adapter/policy/world blockers.
   - Support repeated observation updates as inventory/world state changes.
2. Crafting and smelting objective support.
   - Crafting actions must validate available recipes, inventory inputs, workstation requirements, and output item/count.
   - Smelting actions must validate furnace/container access, fuel, input item, output slot, and timeout/progress.
   - No hidden creative-mode item creation.
3. Delivery and inventory verification.
   - Delivery objectives must verify that the target player received the requested item/count or that the item entity was dropped close enough and still visible/collectable.
   - Inventory delta and item ownership/target proximity should be reported to the planner.
4. Capability-scoped tool documentation.
   - Tool docs shown to provider are selected by available capabilities and current objective.
   - For furnace-like tasks, foreground locate/break/pickup/craft/drop tools and suppress unrelated tools where safe.
   - Provider-visible tool docs should be generated from the same validated capability registry that the runtime can actually execute.
5. Profile-selectable model roles.
   - Optional separation of chat/planning/tool-reasoning roles.
   - Credentials remain server-side and redacted.
   - Role separation must not bypass validators.

Acceptance criteria:

- “Make a furnace and throw it to me” can be represented as a verifiable resource/crafting/delivery plan.
- Provider receives only relevant capability docs for the current objective.
- Craft/smelt/deliver tasks report precise missing materials, missing tools, missing workstation, policy denial, or completed objective state.
- No provider output can directly mint items, bypass crafting, or bypass delivery validation.

### Phase 4: Multi-Agent Coordination, Building Validation, Capability Registry, And Optional Experiments

Goal: make ambitious team/building tasks conflict-free and extensible while keeping unsafe/high-power experiments out of the default companion path.

Workstreams:

1. Java-native capability registry.
   - Steve's `ActionRegistry`/`ActionPlugin` shows a useful extension shape: action factories registered by id instead of one giant switch.
   - OpenPlayer should expose a validator-backed `CapabilityRegistry` for built-in actions and future addon mods.
   - Provider-visible tools must be generated from the validated registry, not handwritten prompt drift.
   - Raw action strings or provider-created task ids must never bypass schema validation.
2. Multi-agent coordination blackboard.
   - Structured assignments, claimed targets, item requests, delivery promises, blocked actions, and shared task progress.
   - Multi-NPC collaboration should use structured local events, not raw chat only.
   - Add `TeamTaskBlackboard` + `WorkClaimRegistry`:
     - claimed block positions,
     - claimed entities/items/containers,
     - assigned build sections,
     - delivery promises,
     - stalled/failed claims with retry owner,
     - dynamic rebalancing when a NPC finishes early, fails, despawns, or becomes policy-blocked.
3. Collaborative build/project manager.
   - Steve's strongest concrete multi-agent idea is shared structure work: split a build plan into spatial sections, claim work atomically, track progress, and let agents help unfinished sections.
   - OpenPlayer should implement this with vanilla server-side placement/breaking primitives and objective diff verification.
4. Blueprint diff and building validation.
   - Build plans report expected block, actual block, coordinate, action needed, mismatch score, progress, and work-claim history.
   - Building corrections should be guided by structured diff, not blind provider guesses.
   - Team objectives rebalance when a section is blocked or an agent finishes early.
5. Incremental tick-based actions for team work.
   - Every long primitive exposes `start/tick/cancel/result/progress`.
   - Work per tick is bounded and progress snapshots are deterministic.
6. World knowledge cache and idle/team behavior.
   - Promote runtime context snapshots into per-NPC/team `WorldFactMemory` with bounded refresh cadence, source timestamps, and no raw provider text.
   - Support profile-controlled idle modes such as follow owner, guard, regroup, or standby.
   - Idle/team modes must be policy-bound and interruptible.
7. Optional sandboxed code/script experiments.
   - Freeform LLM-generated JavaScript execution is intentionally unsafe for default OpenPlayer runtime.
   - Steve has a GraalVM `CodeExecutionEngine`; OpenPlayer should keep this out of the normal agent runtime.
   - If ever explored, it must be sandboxed, disabled by default, permission-gated, audited, and unnecessary for survival/building goals.
8. Evaluation-only bootstrap controls.
   - Cheat/bootstrap-heavy setup may be useful for benchmarks.
   - Normal companion gameplay must not depend on cheat commands, OP permissions, teleporting, flying, direct setBlock, or hidden macro success.

Acceptance criteria:

- Multiple NPCs can work on a shared build/resource/delivery objective without duplicate claims or conflicting block edits.
- Users can inspect each NPC's state: planning, executing, paused, blocked, retrying, completed, or failed.
- Provider-visible tool docs are generated from the same registered capabilities that the runtime can actually execute.
- Team objectives rebalance when an agent finishes early, fails, despawns, or becomes policy-blocked.
- Building tasks can be debugged by objective diff and work-claim history.
- Experimental scripting cannot affect default safety boundaries.
- Evaluation conveniences remain isolated from normal gameplay.

## Non-Goals And Safety Boundaries

- Do not embed Baritone, AltoClef, mineflayer, Mindcraft, Steve, or other opaque/runtime bot dependencies.
- Do not copy source from inspected projects into OpenPlayer.
- Do not add hidden Java macro success actions such as `MAKE_FURNACE` that skip primitive execution.
- Do not let provider text execute arbitrary code, shell commands, scripts, or Java reflection.
- Do not let provider output bypass `allowWorldActions`, profile policy, permission gates, or validators.
- Do not use creative/OP/cheat shortcuts for normal companion gameplay.
- Do not write raw provider output, raw prompts, raw user messages, credentials, or secrets into safe logs or memory.

## Verification Commands

Use Java 17 explicitly:

```bash
JAVA_HOME=/opt/data/tools/jdk-17.0.19+10 PATH=/opt/data/tools/jdk-17.0.19+10/bin:$PATH ./gradlew :common:check build
```

For focused changes, also run the matching main-method JavaExec tasks registered in `common/build.gradle`, such as policy loader, chunk ticket model, planner session, provider schema/prompt, primitive tools, navigation, and automation backend tests.
