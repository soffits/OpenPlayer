# AICore Runtime

OpenPlayer keeps product concerns in the OpenPlayer layer: profiles, UI, provider configuration, chat presentation, commands, entity lifecycle, owner policy, and local companion personality. AICore is the decoupled Java server-side capability/runtime layer under `common/src/main/java/dev/soffits/openplayer/aicore`.

AICore follows mineflayer API shape and behavior semantics where that is useful, but it is not a Node, mineflayer, PrismarineJS, or client-bot runtime. It exposes Java domain records, a bot facade, typed tools, a capability registry, policy validation, bounded events, and truthful unsupported results for mechanics that do not yet have reviewed server-side NPC adapters.

## Runtime Pieces

- `AICoreBotFacade` is the Java bot facade. It owns a stable `AICoreBotState`, `ToolRegistry`, `AICorePluginRegistry`, and `AICoreEventBus`.
- `AICoreToolCatalog` is the authoritative provider-facing registry for mineflayer-aligned tool names and capability statuses.
- `AICoreProviderJsonToolParser` parses untrusted provider JSON into bounded `ToolCall` values without executing scripts, commands, JavaScript, or provider-origin plugins.
- `MinecraftPrimitiveTools` bridges existing OpenPlayer NPC primitives through AICore validation and maps implemented tools to current `CommandIntent` primitives where practical.
- `AICoreEventBus` stores sanitized session events in a bounded in-memory ring buffer.
- `AICorePluginRegistry` lists Java-registered capability modules only. Provider output cannot load arbitrary code.

## Policy Model

Every tool validates independently. Mutating tools are rejected when `allowWorldActions` is false. Creative, command-block, and admin-adjacent parity entries exist only so the surface is explicit; they return `rejected_admin_capability` by default.

Unsupported mechanics are not faked. Examples include fishing without an NPC-owned hook adapter, elytra without server-side NPC physics, resource-pack client flows on a server-side NPC, workstation sessions without no-loss window adapters, and pathfinder calls before a reviewed path adapter is connected.

## Existing Primitive Bridge

Existing OpenPlayer primitives still work through AICore where practical:

- Observation/status: `observe_self`, `observe_world`, `report_status`.
- Loaded searches: `find_loaded_blocks`, `find_loaded_entities`, plus mineflayer-facing `find_blocks`, `find_block`, and `nearest_entity` validation.
- Movement/look: `move_to`, `look_at`, `pathfinder_stop`.
- Block mutation: `dig`, `break_block_at`, `place_block`, `place_block_at`.
- Inventory/equipment: `inventory`, `inventory_query`, `equip`, `equip_item`, `toss`, `drop_item`, `pickup_items_nearby`.
- Combat stop/attack bridge: `attack`, `attack_nearest`, `attack_target`, `pvp_stop`, `pvp_force_stop`.
- Control: `stop`, `pause`, `unpause`.

These bridges preserve current runtime validation and do not reintroduce hidden resource strategy chains.
