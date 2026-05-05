# AICore Tool Schema

Provider output is untrusted structured JSON. The production provider path accepts explicit conversation/refusal JSON, exactly one structured tool call, or a bounded provider plan containing exactly one tool step. Multi-step plans are rejected until OpenPlayer has reviewed queue semantics; the runtime must not report fake multi-step success.

## Conversation Or Refusal

```json
{
  "chat": "I can help with that.",
  "priority": "NORMAL"
}
```

```json
{
  "unavailable": "A reviewed adapter is missing.",
  "priority": "NORMAL"
}
```

`chat` is the selected character's concise conversational reply. `unavailable` is blank or a short truthful refusal reason. Old internal provider output using generic `kind` and `instruction` fields is rejected.

## Single Tool

```json
{
  "tool": "dig",
  "priority": "NORMAL",
  "args": {
    "x": 10,
    "y": 64,
    "z": -3
  }
}
```

## Bounded Plan

Bounded provider plans are accepted only when they contain exactly one tool step. This keeps the provider JSON shape explicit without adding hidden Java-side multi-step queue semantics.

```json
{
  "plan": [
    {"tool": "dig", "args": {"x": 10, "y": 64, "z": -3}}
  ]
}
```

## Validation Rules

- The JSON root must be an object containing exactly one structured provider action: `tool`, `chat`, `unavailable`, or `plan`.
- The production provider path executes a root `tool` object or an exactly-one-step root `plan`, after schema, capability, policy, and runtime validation.
- `priority` on structured tool JSON is optional and defaults to `NORMAL`; when present it must be `LOW`, `NORMAL`, or `HIGH`.
- Tool names must be lower snake case and present in `AICoreToolCatalog`.
- `args` must be an object. Nested objects are preserved as JSON text for later adapter-specific validation.
- Plans are bounded by `ProviderPlanIntentCodec.MAX_STEPS`, currently one step. Over-max plans are rejected instead of being partially executed or reported as success.
- Required arguments are enforced by `ToolSchema`.
- Numeric bounds are enforced for common safety fields such as `maxDistance`, `count`, `ticks`, `timeoutTicks`, and `slot`.
- Mutating tools require `allowWorldActions`.
- `pathfinder_goto` accepts only coordinate goal objects that can be bridged to bounded loaded-area vanilla navigation; unsupported goal shapes fail validation or runtime preconditions instead of becoming hidden planners.
- Provider output never loads plugins, executes JavaScript, runs commands, bypasses policy, or claims unsupported mechanics succeeded.

## Provider-Facing Statuses

- `SUCCESS`: validation accepted the tool or the existing OpenPlayer primitive accepted the bridged structured command.
- `REJECTED`: policy, schema, bounds, admin capability, or runtime validation rejected the request.
- `FAILED`: the surface exists but a real adapter is missing or the mechanic is not applicable to a server-side NPC.
- `RUNNING`: reserved for future asynchronous adapters.

## Removed Macro Names

The provider-facing registry intentionally does not expose legacy hidden macro names such as `GET_ITEM`, `SMELT_ITEM`, `COLLECT_FOOD`, `FARM_NEARBY`, `FISH` as a fake macro, `BUILD_STRUCTURE`, or `TRAVEL_NETHER`. It also does not expose mechanics-specific Java tool names such as `open_furnace`, `furnace_*`, `open_chest`, `is_bed`, `armor_manager_*`, workstation aliases, villager trading aliases, mount/vehicle/elytra aliases, auto-eat aliases, or collectblock aliases. Future provider plans may combine transparent primitive tools only after queue semantics exist; today, provider-backed production parsing accepts exactly one plan step. The removed internal generic primitive shape is not a compatibility surface.
