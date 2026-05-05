# OpenPlayer Policy Runtime Plan

> This document records the implementation plan for externalized NPC movement/safety policies and the next provider-runtime reliability milestones. It is a plan, not a shipped capability claim.

## Goal

Make OpenPlayer NPC automation reliable enough for multi-step survival tasks by separating slow LLM planning from fast local runtime decisions. The LLM decides goals and high-level next primitives; the server-side runtime resolves movement goals, safety constraints, item pickup, block harvesting truth, stuck recovery, immediate danger handling, objective progress, and eventually resource/build planning.

## Provenance And Reference Posture

OpenPlayer can learn from public MIT-licensed Minecraft bot ecosystems, but must implement OpenPlayer-native Java code for server-side `OpenPlayerNpcEntity` behavior.

Reference projects inspected for concepts only:

- `PrismarineJS/mineflayer-pathfinder` (MIT): movement profiles, movement goals, avoid/break/place exclusions, entity avoidance.
- `PrismarineJS/mineflayer-collectblock` (MIT): collect workflow, safe break checks, item drop observation, item entity pickup.
- `mindcraft-bots/mindcraft` (MIT): profile layering, action lifecycle, self-prompt loop, objective validators, resource dependency planning, skill retrieval, multi-agent coordination, blueprint diff, and memory separation.

Do not copy JavaScript source into OpenPlayer. Recreate the concepts as small Java abstractions over the existing Minecraft 1.20.1 server-side runtime.

## Architecture Direction

```text
OpenPlayer runtime
  ├─ Provider planner: slow, high-level strategy and structured tool calls
  ├─ Intent parser/validator: provider output is untrusted input
  ├─ Automation backend: server-authoritative primitive execution
  ├─ Navigation backend: vanilla execution with OpenPlayer goal resolution
  ├─ MovementProfile: externalized policy data
  ├─ ModeScheduler: fast local safety/recovery modes
  ├─ Objective/Resource planner: verifiable long-goal progress
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

## Milestones

### Phase 1: Policy Foundation And Primitive Truthfulness

Goal: fix the current live task blocker and make primitive results truthful enough for the LLM to continue multi-step survival tasks.

Scope:

1. Add externalized policy/profile primitives.
   - `MovementProfile`
   - `BlockSafetyPolicy`
   - `EntitySafetyPolicy`
   - `MovementPolicyLoader`
   - bundled `openplayer:server_caps` and `openplayer:companion_safe` policy data
   - optional NPC profile policy binding through character profile properties
   - fail-closed emergency fallback only; no normal Minecraft ID policy tables in Java code
2. Fix item pickup navigation.
   - `COLLECT_ITEMS` must not navigate directly to `ItemEntity.position()` as a block target.
   - Resolve item pickup as an entity/range goal.
   - Completion must require inventory delta or item disappearance while the NPC is close enough to plausibly pick it up.
   - Replace vague failures with precise reasons such as `item_target_lost`, `item_no_reachable_pickup_position`, `item_navigation_rejected`, `item_not_picked_up`, or `inventory_full`.
3. Make block breaking truthful.
   - `BREAK_BLOCK` reports broken block id, target coordinate, inventory delta, and nearby drop delta.
   - If a survival block requires the correct harvest tool for drops, do not imply the drop was collected.
   - Stone without a pickaxe must not be reported as cobblestone acquisition.
4. Strengthen locate-to-action coordination.
   - `find_loaded_blocks` observations include a recommended next coordinate/action when appropriate.
   - Provider prompt text instructs the model to use returned coordinates for the matching next action instead of substituting unrelated `actionableTargets` coordinates.
5. Tests and verification.
   - Add main-method tests for policy loading/caps, item pickup target resolution, item pickup completion semantics, block break truthful observation, and provider prompt/schema drift.
   - Run Java 17 targeted tests and full `./gradlew build`.

Acceptance criteria:

- The uploaded log failure `COLLECT_ITEMS cancelled reason=navigation_item_position_rejected` cannot recur through the same item-position path.
- `BREAK_BLOCK completed` is no longer an empty success; it includes post-action facts.
- The provider is told to use located coordinates consistently.
- No new opaque pathfinding dependency is added.
- No provider-origin policy/action bypass is introduced.

### Phase 2: Local Modes And Autonomous Planner Loop

Goal: make short-latency safety/recovery decisions local while keeping the LLM as the slow strategic planner.

Scope:

1. Add a policy-gated local mode scheduler.
   - `AutomationMode`
   - `ModeScheduler`
   - `UnstuckMode`
   - `ItemCollectingMode`
   - `DangerAvoidanceMode`
   - `SelfDefenseMode`
2. Use policy-driven obstacle and danger handling.
   - Low-risk obstacles can be cleared only if policy and task permissions allow it.
   - Dangerous or ambiguous obstacles become structured observations for the planner.
   - Never break containers, block entities, beds, protected blocks, or user-valuable blocks by default.
   - Creepers and low-health situations pause/avoid locally; simple hostile defense runs locally only when policy allows combat.
   - LLM sees summarized safety events, not tick-level combat decisions.
3. Add safe autonomous planner behavior.
   - Per-NPC explicit enable switch.
   - Cooldown, max no-action count, stop/pause commands, and user-control interruption.
   - Prompt tells the provider to actively inspect inventory/world, gather missing materials, craft missing tools, and continue toward explicit user goals.
   - Provider asks the user only for ambiguity, authorization, missing capability, unsafe world edits, or policy denial.
   - Add `ask_user` as a structured tool only if it can be validated and clearly separated from automation actions; otherwise keep asking through `chat` for this phase.

Acceptance criteria:

- Stuck, danger, and opportunistic pickup do not depend on provider latency.
- Per-NPC policy controls which local modes may run.
- Autonomous retries are bounded and auditable.
- The furnace task gets farther than the current locate/dig/pickup failure and fails truthfully if still missing a capability.

### Phase 3: Effective Profiles, Action Lifecycle, And Objective Progress

Goal: make NPC behavior transparent and every long action/task verifiable.

Scope:

1. Profile layering and effective-profile reporting.
   - Merge order: server defaults -> built-in role preset -> NPC character profile -> selected policy -> validated runtime overrides.
   - Status/debug snapshot exposes selected policy, enabled/disabled local modes, provider source, permission gates, and runtime overrides without exposing secrets.
2. Unified action lifecycle.
   - Add action label, timeout, started tick, last progress tick, cancellation reason, last structured result, retry budget, and optional resume token.
   - Long actions must be interruptible, cancellable, and report loop guards.
3. Objective validators and task progress.
   - Inventory objective: target item/count exists.
   - Delivery objective: target player received item/count.
   - Block/world objective: expected block/world predicate holds.
   - Build objective: plan mismatch score and required next edits.
   - Progress reports include missing items, blocker reasons, and suggested next runtime action.
4. Memory separation.
   - Separate conversation memory, place memory, task memory, world-fact memory, and NPC preference memory.
   - Never mix raw provider traces into safe memory.

Acceptance criteria:

- Users can inspect why a NPC behaves a certain way without reading code.
- Long actions cannot fake success; they expose verifiable progress or precise failure.
- Objective state survives across planner turns without raw prompt leakage.

### Phase 4: Resource Planner, Tool Scope, And Multi-Agent Coordination

Goal: make survival/crafting tasks deliberate instead of asking the LLM to infer every dependency from scratch.

Scope:

1. Java resource dependency planner.
   - Represent craft, smelt, collect block, hunt entity, prerequisite, fail count, and next-ready leaf.
   - `furnace` decomposes into missing cobblestone, tool requirements, workstation requirements, and delivery objective.
   - Planner must return real primitive steps or precise missing adapter/policy/world blockers.
2. Capability-scoped tool documentation.
   - Tool docs shown to provider are selected by available capabilities and current objective.
   - For furnace-like tasks, foreground locate/break/pickup/craft/drop tools and suppress unrelated tools where safe.
3. Multi-agent coordination blackboard.
   - Structured assignments, claimed targets, item requests, delivery promises, blocked actions, and shared task progress.
   - Multi-NPC collaboration should use structured local events, not raw chat only.
4. Profile-selectable model roles.
   - Optional separation of chat/planning/tool-reasoning roles.
   - Credentials remain server-side and redacted; role separation must not bypass validators.

Acceptance criteria:

- “Make a furnace and throw it to me” can be represented as a verifiable resource/delivery plan.
- Provider receives only relevant capability docs for the current objective.
- Multiple NPCs can coordinate work without duplicate target claims.

### Phase 5: Building Validation And Steve-Style Multi-Agent Action Runtime

Goal: turn ambitious building and team tasks into verifiable, conflict-free action projects rather than independent NPC guesses.

Reference inspected:

- `YuvDwi/Steve` at commit `034afb5` (`2026-01-17 Integrate plugin system into ActionExecutor with async planning`).
- Relevant paths: `ActionExecutor`, `CollaborativeBuildManager`, `ActionRegistry`/`PluginManager`, `AgentStateMachine`, `WorldKnowledge`, and `CodeExecutionEngine`.

Adopt for OpenPlayer, clean-room style:

1. Async planning handoff.
   - Steve starts an async LLM planning future and keeps the game tick responsive while planning.
   - OpenPlayer should formalize this as `PlannerJob` / `PlannerMailbox`: one active planning job per NPC or team objective, cancellable, visible in status UI, and never blocking the server thread.
2. Action registry and capability plugins.
   - Steve's `ActionRegistry`/`ActionPlugin` shows a useful extension shape: action factories registered by id instead of one giant switch.
   - OpenPlayer should expose a Java-native, validator-backed `CapabilityRegistry` for built-in actions and future addon mods.
   - Provider-visible tools must be generated from the validated registry, not handwritten prompt drift.
3. Agent state machine and event stream.
   - Steve models states such as planning, executing, paused, failed, completed and publishes transition events.
   - OpenPlayer should add explicit runtime states for each NPC/team objective and write safe events for planner state transitions, action start/finish/failure, retry, handoff, and cancellation.
4. Collaborative build/project manager.
   - Steve's strongest concrete multi-agent idea is shared structure work: split a build plan into spatial sections, claim work atomically, track progress, and let agents help unfinished sections.
   - OpenPlayer should generalize this into `TeamTaskBlackboard` + `WorkClaimRegistry`:
     - claimed block positions,
     - claimed entities/items/containers,
     - assigned build sections,
     - delivery promises,
     - stalled/failed claims with retry owner,
     - dynamic rebalancing when a NPC finishes early or fails.
5. Incremental tick-based actions.
   - Steve's action classes tick incrementally and keep long tasks from freezing the game.
   - OpenPlayer should make every long primitive expose `start/tick/cancel/result/progress`, with bounded work per tick and deterministic progress snapshots.
6. World knowledge cache.
   - Steve keeps a compact local world summary: nearby blocks, entities, biome, players.
   - OpenPlayer already has runtime context snapshots; this phase should promote them into per-NPC/team `WorldFactMemory` with bounded refresh cadence, source timestamps, and no raw provider text.
7. Optional idle/team behavior.
   - Steve uses idle follow when no task is active.
   - OpenPlayer can support profile-controlled idle modes such as follow owner, guard, regroup, or standby, but they must be policy-bound and interruptible.

Do not adopt directly:

- Freeform LLM-generated JavaScript execution as a default gameplay path. Steve has a GraalVM `CodeExecutionEngine`; OpenPlayer should keep this out of the normal agent runtime. If ever explored, it must be disabled by default, permission-gated, audited, sandboxed, and unnecessary for survival/building goals.
- Cheat-like execution shortcuts such as teleporting/flying/setBlock-driven building as normal behavior. OpenPlayer should prefer vanilla server-side NPC primitives and truthful missing-capability failures.
- Raw action strings or provider-created task ids without schema validation. All Steve-inspired extension points must remain validator-backed.
- Copying Steve source. Only architecture ideas and behavior surfaces are used as clean-room inspiration.

Acceptance criteria:

- Multiple NPCs can work on a shared build/resource/delivery objective without duplicate claims or conflicting block edits.
- Users can inspect each NPC's state: planning, executing, paused, blocked, retrying, completed, or failed.
- Provider-visible tool docs are generated from the same registered capabilities that the runtime can actually execute.
- Team objectives rebalance when an agent finishes early, fails, despawns, or becomes policy-blocked.
- Building tasks can be debugged by objective diff and work-claim history.

### Phase 6: Optional Sandboxed Experiments And Evaluation Controls

Goal: keep unsafe/high-power experiments out of the default companion path while leaving room for controlled research.

Scope:

1. Optional sandboxed code/script experiments.
   - Freeform LLM code execution is intentionally unsafe for default OpenPlayer runtime.
   - If ever added, it must be sandboxed, disabled by default, permission-gated, audited, and never required for survival tasks.
2. Evaluation-only bootstrap controls.
   - Cheat/bootstrap-heavy setup may be useful for benchmarks.
   - Normal companion gameplay must not depend on cheat commands, OP permissions, or hidden macro success.

Acceptance criteria:

- Experimental scripting cannot affect default safety boundaries.
- Evaluation conveniences remain isolated from normal gameplay.
