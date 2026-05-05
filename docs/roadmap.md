# Runtime Roadmap

OpenPlayer's runtime roadmap is focused on local-first companion behavior that is observable, cancellable, policy-gated, and truthful about missing capabilities.

## Runtime Principles

- Provider output is untrusted input, not executable authority.
- World, inventory, combat, and movement changes must pass runtime validation and character policy.
- Runtime behavior should be implemented through reviewed primitives and capability adapters.
- Java should avoid hardcoded gameplay route planners; advisory strategies belong in local docs or future bounded strategy packs.
- Missing behavior should produce clear diagnostics instead of fake success.
- Optional dependencies must be public, reviewed, and non-opaque before adoption.

## Current Baseline

OpenPlayer currently includes:

- Local character and assignment configuration.
- Local skin loading and player-shaped NPC rendering with equipment layers.
- Server-authoritative companion lifecycle with persisted NPC reattach.
- Provider-backed conversation with bounded history and strict JSON intent parsing.
- Local provider configuration with secret-safe precedence and diagnostics.
- Safe debug events and local-only raw traces.
- Canonical `/openplayer` command paths for validated automation intent submission.
- Server-side NPC actions for movement, follow, patrol, item pickup, block break/place, nearest hostile attack, owner guard, equipment selection, item use, offhand swap, drop item, status, and chat replies.
- Bounded world and agent context snapshots for conversation prompts.

## Capability Areas

### Diagnostics And Validation

- Keep automation snapshots deterministic and side-effect free.
- Surface active task, queue, monitor state, cooldowns, elapsed time, and bounded failure reasons.
- Centralize intent validation for coordinates, radii, blank instructions, policy gates, and unsupported operations.
- Ensure rejected intents produce visible status and safe diagnostics.

### Context And Status

- Keep runtime context bounded, deterministic, and free of secrets.
- Report viewer/world status separately from selected-NPC runtime state.
- Expose capability summaries for implemented, diagnostic-only, and missing-adapter surfaces.

### Movement And Exploration

- Improve loaded-world navigation, following, target search, avoidance, and stuck recovery.
- Keep exploration bounded to already-loaded areas unless a future reviewed capability explicitly expands that scope.
- Avoid teleportation, forced dimension changes, hidden locate services, or fake travel completion.

### Inventory, Crafting, And Containers

- Support exact item ids, bounded counts, safe equipment changes, pickup, drop, owner transfer, and inventory status.
- Plan simple crafting and smelting through server registries and reviewed generic container/material adapters.
- Use no-loss container transfer semantics with snapshot/restore behavior where practical.
- Report unsupported recipes, locked/custom containers, or missing adapters deterministically.

### Resource Tasks

- Derive possible actions from visible loaded-world state, registries, tags, recipes, and safe metadata.
- Execute resource acquisition through bounded primitive chains such as observe, navigate, equip, mutate, collect, and verify.
- Avoid hidden ore search, unloaded scans, arbitrary long-running loops, or success claims based only on provider text.

### Farming, Fishing, And Repeatable Work

- Keep crop work bounded to loaded nearby blocks with real harvest/replant checks.
- Add repeatable work only with explicit caps, cancellation, and per-iteration verification.
- Keep fishing rejected until a safe server-authoritative NPC hook adapter exists.

### Building

- Keep building limited to strict primitive plans with capped block counts and validated materials.
- Require loaded target space, collision checks, inventory consumption, rollback-aware behavior, and truthful progress.
- Do not execute provider-generated code, scripts, free-form blueprints, or arbitrary world mutation.

### Interaction And Combat

- Add interactions only through reviewed block/entity adapters with reach, line-of-sight, cooldown, inventory, and policy checks.
- Keep combat limited to explicit hostile or danger policies unless a future trust policy expands it.
- Do not provide arbitrary right-click passthrough from provider text.

### Portals And Dimensions

- Treat portal and dimension behavior as reviewed capability clusters with observed transition checks.
- Report current dimension, origin/target, portal evidence, timeout/failure reasons, and return affordances.
- Do not claim cross-dimension success without observed runtime state.

### Strategy Packs

- Keep strategy/meta packs local, advisory, non-executable, and unable to bypass runtime validation.
- Use them for goal decomposition, preferences, and documentation rather than hidden automation code.
- Add modded behavior through registry-backed primitives and reviewed adapters where mutation is required.

## Verification Gates

Every runtime change should run the strongest available project check before completion:

```bash
git diff --check
./gradlew build
```

For UI and networking changes, also verify language-key parity and keep packet changes small, explicit, and documented.
