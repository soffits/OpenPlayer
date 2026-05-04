# AICore Tool Schema

Provider output is untrusted structured JSON. The production provider path accepts explicit conversation/refusal JSON or exactly one structured tool call. The JSON parser also understands bounded plans, but plans are parser-only until OpenPlayer has explicit task queue semantics; provider plans are rejected and are not executed as fake multi-step success.

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
    "z": -3,
    "forceLook": true,
    "digFace": "auto"
  }
}
```

## Bounded Plan

Bounded plans are accepted by `AICoreProviderJsonToolParser` for schema-level parsing tests and future queue work, but the production provider-backed intent path rejects `plan` JSON because there is no reviewed multi-step task queue execution contract yet.

```json
{
  "plan": [
    {"tool": "find_blocks", "args": {"matching": "minecraft:oak_log", "maxDistance": 24, "count": 8}},
    {"tool": "pathfinder_goto", "args": {"goal": {"type": "goal_look_at_block", "x": 10, "y": 64, "z": -3}}},
    {"tool": "dig", "args": {"x": 10, "y": 64, "z": -3}}
  ]
}
```

## Validation Rules

- The JSON root must be an object containing exactly one structured provider action: `tool`, `chat`, `unavailable`, or `plan`.
- The production provider path executes only a root `tool` object, after schema, capability, policy, and runtime validation.
- `priority` on structured tool JSON is optional and defaults to `NORMAL`; when present it must be `LOW`, `NORMAL`, or `HIGH`.
- Tool names must be lower snake case and present in `AICoreToolCatalog`.
- `args` must be an object. Nested objects are preserved as JSON text for later adapter-specific validation.
- Plans are bounded by the parser's configured maximum step count and every step parses independently, but provider-backed production parsing rejects plans instead of executing them.
- Required arguments are enforced by `ToolSchema`.
- Numeric bounds are enforced for common safety fields such as `maxDistance`, `count`, `ticks`, `timeoutTicks`, and `slot`.
- Mutating tools require `allowWorldActions`.
- Provider output never loads plugins, executes JavaScript, runs commands, bypasses policy, or claims unsupported mechanics succeeded.

## Provider-Facing Statuses

- `SUCCESS`: validation accepted the tool or the existing OpenPlayer primitive accepted the bridged structured command.
- `REJECTED`: policy, schema, bounds, admin capability, or runtime validation rejected the request.
- `FAILED`: the surface exists but a real adapter is missing or the mechanic is not applicable to a server-side NPC.
- `RUNNING`: reserved for future asynchronous adapters.

## Removed Macro Names

The provider-facing registry intentionally does not expose legacy hidden macro names such as `GET_ITEM`, `SMELT_ITEM`, `COLLECT_FOOD`, `FARM_NEARBY`, `FISH` as a fake macro, `BUILD_STRUCTURE`, or `TRAVEL_NETHER`. Future provider plans may combine transparent primitive tools only after queue semantics exist; today, provider-backed production parsing rejects plans. The removed internal generic primitive shape is not a compatibility surface.
