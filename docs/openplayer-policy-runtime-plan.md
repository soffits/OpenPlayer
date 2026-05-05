# OpenPlayer Policy Runtime Plan

> This document records the completed runtime-foundation direction for externalized NPC movement/safety policies and provider-runtime reliability. It is not a shipped capability overclaim: future user-facing claims must still match the live adapters and verified runtime behavior.

## Goal

Make OpenPlayer NPC automation reliable enough for multi-step survival tasks by separating slow LLM planning from fast local runtime decisions. The LLM decides goals and high-level next primitives; the server-side runtime resolves chunk presence, movement goals, safety constraints, item pickup, block harvesting truth, stuck recovery, immediate danger handling, objective progress, resource/build planning, and team coordination.

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

## Implemented Runtime Foundations

The four planned development phases from this document have been implemented on `mc/1.20.1`. Do not treat the removed phase checklist as remaining work. Future roadmap items should be written as new concrete gaps based on live testing, logs, or product goals.

Implemented commits:

- `cb6c08b fix: harden primitive runtime truthfulness`
- `32c0c34 feat: add strong planner runtime foundations`
- `e8dc98e feat: add resource planning foundations`
- `2e26dce feat: add multi-agent coordination foundations`

Completed foundation areas:

- Player-like bounded active chunk tickets for active or queued NPC automation.
- Externalized movement and safety policy data with fail-closed fallbacks.
- Primitive truthfulness hardening for navigation, dropped item pickup, block breaking, and provider prompt coordinate use.
- Strong single-agent planner runtime foundations: planner mailbox/jobs, local modes, long-action lifecycle, objective progress, and effective profile/status surfaces.
- Resource/crafting/delivery planning foundations for goals such as making and delivering a furnace, including dependency steps, capability-scoped tool docs, model-role config, and objective validators.
- Multi-agent/build foundations: capability registry, team task blackboard, work claims, build diff/project sections, tick-bounded action lifecycle, world fact memory, idle policy gates, and experiment boundaries.

## Remaining Documentation Rule

Do not keep completed implementation checklists in this file as if they are still planned work. When a new gap is discovered, add a new dated roadmap section or issue-linked plan that states:

- observed failure or user goal;
- current live behavior;
- desired behavior;
- safety boundaries;
- implementation scope;
- verification command;
- artifact/release expectation if any.

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

For focused changes, also run the matching main-method JavaExec tasks registered in `common/build.gradle`, such as policy loader, chunk ticket model, planner session, provider schema/prompt, primitive tools, navigation, automation backend, resource planning, and coordination tests.
