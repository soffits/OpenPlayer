# AICore Runtime

OpenPlayer keeps product concerns in the OpenPlayer layer: profiles, UI, provider configuration, chat presentation, commands, entity lifecycle, owner policy, and local companion personality. AICore is the decoupled Java server-side capability/runtime layer under `common/src/main/java/dev/soffits/openplayer/aicore`.

AICore follows mineflayer API shape and behavior semantics where that is useful, but it is not a Node, mineflayer, PrismarineJS, or client-bot runtime. It exposes Java domain records, a bot facade, typed tools, a capability registry, policy validation, bounded events, and truthful unsupported results for mechanics that do not yet have reviewed server-side NPC adapters.

## Runtime Pieces

- `AICoreBotFacade` is the Java bot facade. It owns a stable `AICoreBotState`, `ToolRegistry`, `AICorePluginRegistry`, and `AICoreEventBus`.
- `AICoreToolCatalog` is the authoritative provider-facing registry for mineflayer-aligned tool names and capability statuses.
- `AICoreProviderJsonToolParser` parses untrusted provider JSON into bounded `ToolCall` values without executing scripts, commands, JavaScript, or provider-origin plugins.
- `MinecraftPrimitiveTools` bridges existing OpenPlayer NPC primitives through AICore validation and maps implemented tools to current `CommandIntent` primitives where practical.
- `AICoreEventBus` stores sanitized session events in a bounded in-memory ring buffer.
- `AICoreWorldQueryAdapter` provides pure loaded-world block/entity raycast and visibility seams for deterministic tests and non-Minecraft snapshots.
- `AICoreNpcToolExecutor` executes reviewed live OpenPlayer NPC adapters for loaded block queries, cursor raycasts, yaw/pitch look, hotbar selection, selected-stack toss, main-hand swing, safe selected edible use, loaded-area pathfinder goto, and reviewed block/entity interaction bridges.
- `AICorePluginRegistry` lists Java-registered capability modules only. Provider output cannot load arbitrary code.

## Policy Model

Every tool validates independently. Mutating tools are rejected when `allowWorldActions` is false. Creative, command-block, and admin-adjacent parity entries exist only so the surface is explicit; they return `rejected_admin_capability` by default.

Unsupported mechanics are not faked. Examples include fishing without an NPC-owned hook adapter, elytra without server-side NPC physics, resource-pack client flows on a server-side NPC, workstation sessions without no-loss window adapters, and pathfinder goals that cannot be represented as bounded loaded-area vanilla navigation requests.

Provider-backed parsing accepts only structured `tool`, `chat`, or `unavailable` output. Provider `tool` output is limited to primitives that bridge to the current `CommandIntent` runtime path; facade-only live executor tools validate in AICore but are rejected by provider parsing until a reviewed provider execution route exists. The old internal generic provider shape using `kind` and `instruction` fields is not accepted, and provider plans remain rejected until reviewed queue semantics exist.

## Existing Primitive Bridge

Existing OpenPlayer primitives still work through AICore where practical:

- Observation/status: `observe_self`, `observe_world`, `report_status`.
- Loaded searches and raycasts: `find_loaded_blocks`, `find_loaded_entities`, mineflayer-facing `find_blocks`, `find_block`, `nearest_entity`, `block_at`, `can_see_block`, `block_in_sight`, `block_at_cursor`, and `entity_at_cursor` through loaded-world seams. `block_at_entity_cursor` remains unsupported because entity-specific cursor/view state is not available in both live and pure adapters.
- Movement/look: `move_to`, bounded coordinate `pathfinder_goto`, `look_at`, raw `look` yaw/pitch in the NPC executor as a facade-only tool, and `pathfinder_stop`.
- Block mutation: `dig`, `break_block_at`, `place_block`, `place_block_at`.
- Inventory/equipment: `inventory`, `inventory_query`, `equip`, `equip_item`, facade-only `set_quick_bar_slot` and `toss_stack`, `toss`, `drop_item`, `pickup_items_nearby`.
- Interaction/use: `activate_block`, `activate_entity`, `activate_entity_at`, `use_on_entity`, `activate_item`, `consume`, and `swing_arm` through reviewed ordinary-player-like NPC adapters where runtime preconditions pass.
- Combat stop/attack bridge: `attack`, `attack_nearest`, `attack_target`, `pvp_stop`, `pvp_force_stop`.
- Control: `stop`, `pause`, `unpause`.

These bridges preserve current runtime validation and do not reintroduce hidden resource strategy chains.
