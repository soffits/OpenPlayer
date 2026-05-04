# Strategy And Meta Packs

OpenPlayer strategy/meta packs are local advisory reference material for an LLM or planner. They are not Java gameplay route planners, scripts, commands, or executable code.

Current status: docs and samples only. OpenPlayer does not automatically discover, load, parse, execute, or sync strategy packs at runtime yet.

## Purpose

- Describe high-level Minecraft goal decomposition in plain local text or bounded metadata.
- Help users, server owners, or modpack authors document preferred strategies without changing OpenPlayer core.
- Keep Java runtime logic focused on reviewed primitives, capability adapters, validation, monitoring, and truthful status.
- Let the LLM decide goals and plans from available context, then emit only supported OpenPlayer intent schemas.

## Local Layout

When runtime support is added later, use a local-only config directory such as:

```text
<Minecraft config>/openplayer/strategy-packs/<pack-id>/pack.properties
<Minecraft config>/openplayer/strategy-packs/<pack-id>/guides/*.md
```

For now, repository examples live under `docs/strategy-packs/` and are documentation fixtures only.

## Safety Contract

- Strategy packs must be read-only advisory input unless future adapter code explicitly implements validated actions.
- Do not execute scripts, shell commands, JavaScript, bytecode, jars, native libraries, macros, or arbitrary provider-generated code from strategy packs.
- Do not fetch strategy content from the network at runtime.
- Do not let strategy text bypass `allowWorldActions`, runtime validation, capability adapters, cooldowns, reach/LOS checks, loaded-world bounds, or inventory/world-state verification.
- Do not claim success from strategy text. Completion must come from observed NPC inventory, world, movement, or dimension state.
- Do not store provider API keys, account tokens, passwords, cookies, credentials, or other secrets in strategy packs.

## Metadata Shape

If a future loader is added, keep it bounded and non-executable. A simple properties file is enough:

```properties
id=vanilla_survival_reference
name=Vanilla Survival Reference
version=1
scope=vanilla
description=Advisory survival and resource-gathering goal decomposition for ordinary vanilla Minecraft play.
guide=guides/survival.md
```

Suggested fields:

- `id`: safe lowercase pack id.
- `name`: short display name.
- `version`: integer metadata version.
- `scope`: `vanilla`, `datapack`, `modpack`, or another safe local label.
- `description`: bounded non-secret summary.
- `guide`: relative markdown or text path inside the pack.
- `requiresCapability`: optional comma-separated capability ids that the guide expects, such as `movement.loaded` or `recipe.simple_crafting`.

Unknown fields should be rejected by any future parser rather than silently gaining behavior.

## Modpack Extension Path

Modded behavior should be added by users or modpack authors through local strategy packs plus reviewed adapters where mutation is required:

- Use strategy packs to describe goals, preferences, recipes, dimensions, hazards, and recovery advice.
- Use registry-backed primitives where possible for items, blocks, entities, recipes, tags, biomes, dimensions, and loaded-world observations.
- Add explicit capability adapters for mechanics that need special validation, such as custom machines, custom portals, non-vanilla trading UIs, fluid systems, or dimension-specific hazards.
- If a required adapter is missing, OpenPlayer should report a missing-adapter or policy/state diagnostic. That is an interface gap, not a permanent product-level refusal for ordinary player-like behavior.
