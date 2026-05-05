# OpenPlayer Policy Runtime Plan

> This document records the implementation plan for externalized NPC movement/safety policies and the next provider-runtime reliability phases. It is a plan, not a shipped capability claim.

## Goal

Make OpenPlayer NPC automation reliable enough for multi-step survival tasks by separating slow LLM planning from fast local runtime decisions. The LLM decides goals and high-level next primitives; the server-side runtime resolves movement goals, safety constraints, item pickup, block harvesting truth, stuck recovery, and immediate danger handling.

## Provenance And Reference Posture

OpenPlayer can learn from public MIT-licensed Minecraft bot ecosystems, but must implement OpenPlayer-native Java code for server-side `OpenPlayerNpcEntity` behavior.

Reference projects inspected for concepts only:

- `PrismarineJS/mineflayer-pathfinder` (MIT): movement profiles, movement goals, avoid/break/place exclusions, entity avoidance.
- `PrismarineJS/mineflayer-collectblock` (MIT): collect workflow, safe break checks, item drop observation, item entity pickup.
- `mindcraft-bots/mindcraft` (MIT): local modes for unstuck, danger avoidance, self-defense, and opportunistic item collection.

Do not copy JavaScript source into OpenPlayer. Recreate the concepts as small Java abstractions over the existing Minecraft 1.20.1 server-side runtime.

## Architecture Direction

```text
OpenPlayer runtime
  ├─ Provider planner: slow, high-level strategy and tool calls
  ├─ Intent parser/validator: structured provider output only
  ├─ Automation backend: server-authoritative primitive execution
  ├─ Navigation backend: vanilla execution with OpenPlayer goal resolution
  ├─ MovementProfile: externalized policy data
  ├─ ModeScheduler: fast local safety/recovery modes
  └─ StructuredObservation: truthful task results back to the planner
```

The runtime must never treat provider text as trusted execution. Policies constrain what the runtime may do; they do not grant permissions beyond profile/server settings.

## Externalized Policy Model

Each NPC profile should be able to select its own policy. A policy may come from built-in resources, server config override, datapack-like resources, or a future addon mod. The first implementation should support at least a bundled default policy and server-side config override; datapack/addon hooks can be prepared but do not need to be complete if that would broaden Phase 1 too far.

Example profile binding:

```properties
movementPolicy=openplayer:companion_safe
allowWorldActions=true
allowObstacleBreaking=true
allowSelfDefense=false
```

Example policy shape:

```json
{
  "id": "openplayer:companion_safe",
  "movement": {
    "canBreakObstacles": true,
    "canPlaceScaffold": false,
    "maxFallDistance": 3,
    "avoidLiquids": true,
    "avoidHostiles": true
  },
  "blocks": {
    "neverBreak": [
      "minecraft:chest",
      "minecraft:furnace",
      "minecraft:crafting_table",
      "minecraft:bed"
    ],
    "avoid": [
      "minecraft:lava",
      "minecraft:fire",
      "minecraft:cactus",
      "minecraft:magma_block"
    ],
    "lowRiskBreakable": [
      "minecraft:snow",
      "minecraft:short_grass",
      "minecraft:tall_grass",
      "#minecraft:leaves"
    ]
  },
  "entities": {
    "avoid": [
      "minecraft:creeper",
      "minecraft:skeleton"
    ],
    "defendAgainst": [
      "minecraft:zombie",
      "minecraft:spider",
      "minecraft:skeleton"
    ],
    "neverAttack": [
      "minecraft:villager",
      "minecraft:iron_golem"
    ]
  },
  "planner": {
    "allowAskUser": true,
    "allowLlmPolicyPatchDrafts": true
  }
}
```

### LLM Policy Drafts

LLMs may propose policy changes only through a constrained tool or future UI flow. They must not write arbitrary files.

Required safety rules:

- LLM policy changes create a draft/override, not an immediate unrestricted runtime mutation.
- The draft is validated against an allowlist schema and server-side permissions.
- The runtime rejects dangerous expansions such as breaking containers, beds, block entities, protected blocks, or attacking neutral/villager-like entities unless a server-authorized profile explicitly allows it.
- Safe event logs record deterministic summaries of policy changes; raw provider text must not be written to safe logs.
- Per-NPC overrides are allowed, but cannot exceed global server caps.

Effective policy resolution:

```text
global server caps
  -> built-in/default policy
  -> server policy override
  -> NPC profile-selected policy
  -> validated per-NPC runtime override draft if approved
```

The effective policy is always bounded by global caps.

## Movement And Goal Abstractions

Implement OpenPlayer-native equivalents of the useful mineflayer concepts:

- `MovementProfile`: describes what the NPC can step on, avoid, break, place, and how much risk is acceptable.
- `MovementGoal`: typed navigation targets such as `NEAR_POSITION`, `ADJACENT_TO_BLOCK`, `REACHABLE_BLOCK_FACE`, `FOLLOW_ENTITY`, and `AVOID_ENTITY`.
- `NavigationTargetResolver`: converts primitive targets into safe vanilla navigation targets or returns precise rejection reasons.
- `StructuredObservation`: records target, result, inventory delta, dropped item delta, block result, safety event, and suggested next action.

## Mindcraft Adoption Notes

Mindcraft has useful ideas beyond local movement policy. Treat these as clean-room design patterns, not source to port.

Adoptable patterns:

- **Profile layering**: Mindcraft composes defaults, task/base profile, and per-agent profile. OpenPlayer should mirror this for character profile + provider + policy + prompt style, with explicit effective-profile reporting.
- **Action manager semantics**: Mindcraft wraps every long action with current action label, timeout, interrupt, resume, output summary, and loop guard. OpenPlayer already has automation snapshots; extend toward a unified task/action handle with timeout, cancellation reason, last result, and retry budget.
- **Self-prompt / autonomous goal loop**: Mindcraft has an idle self-prompt loop for assigned goals. OpenPlayer should add a safer server-side autonomous planner loop only when explicitly enabled per NPC, with cooldown, max no-action count, and stop/pause commands.
- **Task validators and scoring**: Mindcraft validates tasks by inventory presence, blueprint mismatch, and task score. OpenPlayer should represent long goals as verifiable task objectives: target inventory counts, block/world predicates, delivery-to-player predicates, and progress/missing-items summaries.
- **Recipe/resource dependency graph**: Mindcraft's item goal tree decomposes craft/smelt/collect/hunt dependencies and chooses the next ready leaf. OpenPlayer should eventually implement a Java resource planner for furnace/crafting chains instead of asking the LLM to infer every dependency from scratch.
- **Skill library retrieval**: Mindcraft retrieves relevant skill docs by embedding/word overlap. OpenPlayer can do a lightweight, static variant: tool docs shown to the provider should be selected by available capabilities and current objective, not always dumped wholesale.
- **Multi-agent coordination model**: Mindcraft has bot-to-bot conversations, blocked actions, per-agent initial inventory, and shared task progress. OpenPlayer can later expose local NPC coordination through structured assignments and shared blackboard events rather than raw chat only.
- **Blueprint validation/diff**: Mindcraft's construction validator returns mismatches and explanations. OpenPlayer building should use a build-plan diff model: expected block, actual block, action needed, and score/progress.
- **Memory separation**: Mindcraft separates short conversation turns, compressed memory, remembered places, and full histories. OpenPlayer should keep world/place/task memory separate from chat memory and never mix raw provider traces into safe memory.
- **Profile-selectable model roles**: Mindcraft separates chat/code/vision/embedding models. OpenPlayer may eventually separate chat/planning from local tool reasoning, but must keep provider credentials server-side and redacted.

Patterns to avoid or heavily constrain:

- Freeform LLM code execution (`newAction`) is intentionally unsafe for OpenPlayer's default runtime. If ever added, it must be sandboxed, disabled by default, and never required for survival tasks.
- Cheat/bootstrap-heavy evaluation setup is useful for benchmarks, not normal companion gameplay.
- Raw command-string parsing is less safe than OpenPlayer's structured tool JSON contract; keep structured schema and server-side validators.

## Implementation Phases

### Phase 1: Externalized Movement Policy + Primitive Correctness

Goal: fix the current live task blocker and make primitive results truthful enough for the LLM to continue multi-step survival tasks.

Scope:

1. Add policy/profile primitives.
   - `MovementProfile`
   - `BlockSafetyPolicy`
   - `EntitySafetyPolicy`
   - `MovementPolicyLoader`
   - bundled `openplayer:companion_safe` default policy
   - NPC profile policy binding if a small existing profile properties seam exists; otherwise add a minimal optional profile property with safe fallback.

2. Fix item pickup navigation.
   - `COLLECT_ITEMS` must not navigate directly to `ItemEntity.position()` as a block target.
   - Resolve item pickup as an entity/range goal.
   - Completion must require inventory delta or item disappearance while the NPC is close enough to plausibly pick it up.
   - Replace vague failures with precise reasons such as `item_target_lost`, `item_no_reachable_pickup_position`, `item_navigation_rejected`, `item_not_picked_up`, or `inventory_full`.

3. Make block breaking truthful.
   - `BREAK_BLOCK` should report the broken block id, target coordinate, inventory delta, and nearby drop delta.
   - If a survival block requires the correct harvest tool for drops, do not let the observation imply the drop was collected. Prefer rejecting missing required tools where reliable.
   - Stone without a pickaxe must not be reported as cobblestone acquisition.

4. Strengthen locate-to-action coordination.
   - `find_loaded_blocks` observations should include a recommended next coordinate/action when appropriate.
   - Provider prompt text should instruct the model to use returned coordinates for the matching next action instead of substituting unrelated `actionableTargets` coordinates.

5. Tests and verification.
   - Add pure Java/main-method tests matching existing project style where possible.
   - Cover item pickup target resolution, item pickup completion semantics, block break truthful observation, and provider prompt/schema drift.
   - Run Java 17 targeted tests and full `./gradlew build`.

Acceptance criteria:

- The uploaded log failure `COLLECT_ITEMS cancelled reason=navigation_item_position_rejected` cannot recur through the same item-position path.
- `BREAK_BLOCK completed` is no longer an empty success; it includes post-action facts.
- The provider is told to use located coordinates consistently.
- No new opaque pathfinding dependency is added.
- No provider-origin policy/action bypass is introduced.

### Phase 2: Fast Local Modes And Active Planner Prompt

Goal: make short-latency safety/recovery decisions local while keeping the LLM as the slow strategic planner.

Scope:

1. Add a small local mode scheduler.
   - `AutomationMode`
   - `ModeScheduler`
   - `UnstuckMode`
   - `ItemCollectingMode`
   - `DangerAvoidanceMode`
   - `SelfDefenseMode`

2. Use policy-driven obstacle handling.
   - Low-risk obstacles can be cleared only if policy and task permissions allow it.
   - Dangerous or ambiguous obstacles become structured observations for the planner.
   - Never break containers, block entities, beds, protected blocks, or user-valuable blocks by default.

3. Handle danger locally.
   - Creepers and low-health situations pause or avoid locally.
   - Simple hostile defense can run locally only when policy allows combat.
   - LLM sees periodic summarized safety events, not tick-level combat decisions.

4. Add active planner behavior.
   - Prompt should tell the provider to actively inspect inventory/world, gather missing materials, craft missing tools, and continue toward explicit user goals.
   - The provider should ask the user only for ambiguity, authorization, missing runtime capability, unsafe world edits, or policy denial.
   - Add `ask_user` as a structured tool only if it can be validated and clearly separated from automation actions; otherwise keep asking through `chat` for this phase.

5. Furnace task scenario.
   - Add a regression scenario or harness around `make a furnace and throw it to me`.
   - The expected behavior is not fake macro success: it must either progress through real primitives or truthfully report the missing adapter/policy/world-state blocker.

Acceptance criteria:

- Stuck, danger, and opportunistic pickup do not depend on provider latency.
- Per-NPC policy controls which local modes may run.
- LLM policy drafts remain constrained and auditable.
- The furnace task gets farther than the current locate/dig/pickup failure and fails truthfully if still missing a capability.

### Phase 3: Profile Layering And Effective Runtime Profile

Goal: make NPC behavior transparent and composable instead of relying on one flat character file.

Scope:

1. Define the effective profile merge order.
   - server defaults
   - built-in role preset
   - NPC character profile
   - selected movement/safety policy
   - validated runtime overrides
2. Add an effective-profile snapshot for debugging/status.
3. Surface the selected policy, enabled local modes, disabled local modes, provider source, and key permission gates without exposing secrets.
4. Keep profile merging deterministic and server-authoritative.

Acceptance criteria:

- Each NPC can report its effective runtime profile in safe status/debug output.
- Profile merge order is documented and tested.
- No provider-origin override can grant permissions beyond global server caps.

### Phase 4: Unified Action Manager Semantics

Goal: give every long-running primitive a consistent lifecycle and truthful state.

Scope:

1. Introduce or consolidate an action/task handle with:
   - action label
   - start time
   - timeout
   - last progress time
   - interrupt/cancel state
   - retry budget
   - structured last result
   - resume token where safe
2. Replace ambiguous success summaries with structured completion, cancellation, and timeout reasons.
3. Add loop guards for repeated zero-progress actions.
4. Ensure snapshots remain side-effect-free.

Acceptance criteria:

- Status output can distinguish running, completed, cancelled, timed out, interrupted, and no-progress-loop actions.
- Repeated fast failures are throttled or escalated instead of spinning.
- Provider-facing summaries stay deterministic and bounded.

### Phase 5: Autonomous Planner Loop

Goal: let explicitly enabled NPCs continue assigned goals when idle, without giving the provider tick-level control.

Scope:

1. Add a per-NPC autonomous planner loop guarded by profile/policy settings.
2. Support start, pause, resume, and stop controls.
3. Add cooldown, max no-action count, max consecutive failure count, and user-interruption behavior.
4. Feed the planner structured objective progress and last action result.
5. Pause autonomous planning while a user is actively chatting or manually controlling the NPC.

Acceptance criteria:

- Autonomous planning is disabled by default unless the NPC profile explicitly enables it.
- The loop stops or pauses after repeated no-action/failure responses.
- Users can reliably stop the loop and current action.

### Phase 6: Objective Validators And Task Progress

Goal: make long user goals verifiable by runtime predicates instead of chat claims.

Scope:

1. Add objective types for inventory counts, delivery to player, block/world predicates, entity predicates, and build-plan predicates.
2. Add task progress summaries with complete/incomplete, missing requirements, current blocker, and suggested next action.
3. Attach objective validation to user assignments when the goal can be parsed or inferred safely.
4. Log deterministic progress snapshots to safe diagnostics.

Acceptance criteria:

- The furnace task can be represented as a verifiable objective such as `deliver minecraft:furnace x1 to owner`.
- Completion requires runtime-observed predicates, not provider claims.
- Missing items and blockers are reported in structured form.

### Phase 7: Resource Dependency Planner

Goal: stop asking the LLM to rediscover Minecraft recipe/resource trees from scratch.

Scope:

1. Add a Java resource planner for craft, smelt, collect, and simple hunt dependencies.
2. Build a dependency graph from available Minecraft recipe data where practical.
3. Select the next ready leaf based on inventory, tools, policy, and known world observations.
4. Track failed methods and try alternate methods when available.
5. Keep the planner advisory: execution still goes through validators and automation primitives.

Acceptance criteria:

- `minecraft:furnace` decomposes into cobblestone acquisition and tool prerequisites.
- The planner reports missing resources and the next executable primitive target.
- Planner output cannot bypass profile/policy permissions.

### Phase 8: Capability-Scoped Tool Documentation

Goal: reduce provider mistakes by showing only relevant tool/capability docs for the current objective and runtime state.

Scope:

1. Classify tools by capability domain: movement, observe, inventory, resource, craft, container, combat, building, chat, ask.
2. Select provider tool guidance from current objective, enabled policies, and available adapters.
3. Always include safety and structured-output rules.
4. Add tests that ensure disabled or unavailable capabilities are not over-advertised.

Acceptance criteria:

- A furnace/resource objective emphasizes locate, dig, pickup, craft, drop/deliver, and status tools.
- Disabled combat/building/container tools are not presented as available actions.
- Prompt/tool docs remain deterministic and bounded.

### Phase 9: Multi-Agent Coordination Blackboard

Goal: support multiple local NPCs coordinating through structured assignments instead of raw chat only.

Scope:

1. Add a local shared blackboard for active assignments, claimed targets, item requests, delivery promises, and task progress.
2. Let NPCs publish and read bounded, deterministic coordination events.
3. Add conflict handling for duplicate target claims and stale claims.
4. Keep owner/user commands authoritative over NPC-to-NPC coordination.

Acceptance criteria:

- Two NPCs can avoid claiming the same resource target when a shared assignment is active.
- Coordination events are visible in safe diagnostics without raw provider text.
- No NPC can command another NPC beyond allowed assignment policy.

### Phase 10: Blueprint Diff And Building Validation

Goal: make building tasks inspectable and repairable through deterministic diffs.

Scope:

1. Define a build-plan/blueprint representation suitable for OpenPlayer's server-side runtime.
2. Add validation that compares expected vs actual block state.
3. Return mismatch records with expected block, actual block, coordinate, required action, and progress score.
4. Use the same primitive validators for break/place actions.

Acceptance criteria:

- Building progress can be scored without provider narration.
- The runtime can suggest concrete repair actions for mismatches.
- Protected/valuable blocks remain governed by policy.

### Phase 11: Memory Separation

Goal: keep chat, places, tasks, and world facts separate so long-running NPC behavior stays useful and auditable.

Scope:

1. Separate conversation memory from place memory, task memory, world fact memory, and NPC preference memory.
2. Store only safe deterministic summaries in durable runtime memories.
3. Never store raw provider traces, raw prompts, credentials, or unredacted user secrets in safe memory.
4. Add clear pruning/expiration rules for stale world facts.

Acceptance criteria:

- Remembered places are queryable separately from chat summaries.
- Task failures and blockers can be summarized without leaking raw trace content.
- Memory writes are bounded, redacted, and permission-aware.

### Phase 12: Profile-Selectable Model Roles

Goal: allow advanced profiles to separate provider roles without exposing credentials or complicating the default path.

Scope:

1. Support optional per-profile roles such as chat/planner, summarizer, and embeddings/retrieval if the provider layer can do so safely.
2. Keep server-side provider credential handling unchanged: keys never return to clients or safe logs.
3. Add status that reports role presence/source, not secret values.
4. Preserve a simple single-provider default.

Acceptance criteria:

- Advanced profiles can choose different configured model roles when available.
- Missing optional roles degrade safely to the default provider.
- Credential redaction and provider-output distrust rules remain unchanged.

### Phase 13: Optional Sandboxed Code/Script Experiments

Goal: document the unsafe Mindcraft-style `newAction` idea as a non-default, highly constrained future experiment rather than part of normal automation.

Scope:

1. Keep freeform LLM code execution out of default OpenPlayer survival automation.
2. If explored, require an explicit experimental flag, sandbox, permission gate, audit log, timeout, filesystem restrictions, and no access to credentials.
3. Prefer deterministic Java adapters and structured tools for all core gameplay tasks.

Acceptance criteria:

- No shipped survival task depends on freeform provider-written code.
- Any future experiment is disabled by default and clearly labeled unsafe/experimental.
- Repository docs do not imply arbitrary code execution is required or recommended.

## Verification Commands

Use Java 17 explicitly:

```bash
JAVA_HOME=/opt/data/tools/jdk-17.0.19+10 PATH=/opt/data/tools/jdk-17.0.19+10/bin:$PATH ./gradlew \
  :common:runMinecraftPrimitiveToolsTest \
  :common:runProviderBackedIntentParserTest \
  :common:runOpenAiCompatibleIntentProviderTest \
  :common:runRuntimeIntentValidatorTest \
  build
```

Before commit:

```bash
git diff --check
git status --short --branch
```

## Non-Goals

- Do not vendor JS source code into OpenPlayer.
- Do not add Baritone, AltoClef, PlayerEngine, mineflayer, or opaque runtime dependencies.
- Do not implement admin/cheat operations as normal survival automation.
- Do not allow provider output to mutate policy or execute actions without server validation.
- Do not claim end-to-end survival autonomy until scenarios prove it.
