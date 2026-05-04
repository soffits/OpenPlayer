# AICore Runtime

OpenPlayer keeps product concerns in the OpenPlayer layer: profiles, UI, provider configuration, chat presentation, commands, entity lifecycle, owner policy, and local companion personality. AICore is the decoupled Java server-side capability/runtime layer under `common/src/main/java/dev/soffits/openplayer/aicore`.

AICore follows mineflayer API shape and behavior semantics where that is useful, but it is not a Node, mineflayer, PrismarineJS, or client-bot runtime. It exposes Java domain records, a bot facade, typed tools, a capability registry, policy validation, bounded events, and truthful unsupported results for mechanics that do not yet have reviewed server-side NPC adapters.

## Runtime Pieces

- `AICoreBotFacade` is the Java bot facade. It owns a stable `AICoreBotState`, `ToolRegistry`, `AICorePluginRegistry`, and `AICoreEventBus`.
- `AICoreToolCatalog` is the authoritative provider-facing registry for mineflayer-aligned tool names and capability statuses.
- `AICoreProviderJsonToolParser` parses untrusted provider JSON into bounded `ToolCall` values without executing scripts, commands, JavaScript, or provider-origin plugins.
- `MinecraftPrimitiveTools` bridges existing OpenPlayer NPC primitives through AICore validation and maps implemented tools to current `CommandIntent` primitives where practical.
- `AICoreEventBus` stores sanitized session events in a bounded in-memory ring buffer.
- `AICoreWorldQueryAdapter` provides pure loaded-world block/entity raycast and visibility seams for deterministic tests and non-Minecraft snapshots, including entity-specific cursor raycasts when the snapshot carries an entity view vector.
- `AICoreNpcToolExecutor` executes reviewed live OpenPlayer NPC adapters for loaded block queries, NPC/entity cursor raycasts, visible control-state flags, bounded wait requests, dig preconditions and dig-time estimates, yaw/pitch look, hotbar selection, no-loss unequip, selected-stack toss, main-hand swing, held-use cancellation, selected-item use, datapack-aware recipe queries, loaded-area pathfinder goto and diagnostics, loaded block-entity container sessions, and reviewed generic block/entity interaction bridges.
- `AICorePluginRegistry` lists Java-registered capability modules only. Provider output cannot load arbitrary code.

## Policy Model

Every tool validates independently. Mutating tools are rejected when `allowWorldActions` is false. Creative, command-block, and admin-adjacent parity entries exist only so the surface is explicit; they return `rejected_admin_capability` by default.

Unsupported mechanics are not faked. Mechanics-specific decomposition, such as workstation recipes, fishing, mounts, elytra flight, sleep, villager trading, and automatic equipment policies, belongs in local strategy files or external planner knowledge. The Java runtime exposes only bounded generic primitives and rejects unavailable adapters or unsafe state transitions truthfully.

Provider-backed parsing accepts only structured `tool`, `chat`, or `unavailable` output. Provider `tool` output is limited to primitives that bridge to the current `CommandIntent` runtime path; facade-only live executor tools validate in AICore but are rejected by provider parsing until a reviewed provider execution route exists. The old internal generic provider shape using `kind` and `instruction` fields is not accepted, and provider plans remain rejected until reviewed queue semantics exist.

## Existing Primitive Bridge

Existing OpenPlayer primitives still work through AICore where practical:

- Observation/status: `observe_self`, `observe_world`, `report_status`.
- Loaded searches and raycasts: `find_loaded_blocks`, `find_loaded_entities`, mineflayer-facing `find_blocks`, `find_block`, `nearest_entity`, `block_at`, `can_see_block`, `block_in_sight`, `block_at_cursor`, `entity_at_cursor`, and `block_at_entity_cursor` through loaded-world seams. Entity cursor queries use the requested entity's view state and do not fall back to the NPC cursor.
- Movement/look/control: `move_to`, bounded coordinate `pathfinder_goto`, non-dynamic `pathfinder_set_goal`, bounded non-node `pathfinder_get_path_to`/`pathfinder_get_path_from_to` loaded-area diagnostics, `look_at`, raw `look` yaw/pitch in the NPC executor as a facade-only tool, `set_control_state`, `get_control_state`, `clear_control_states`, `wait_for_ticks`, and `pathfinder_stop`.
- Block mutation: `can_dig_block`, `dig_time`, `stop_digging`, `dig`, `break_block_at`, `place_block`, `place_block_at`.
- Inventory/equipment: `inventory`, `inventory_query`, `equip`, `equip_item`, facade-only `set_quick_bar_slot`, `unequip`, `move_slot_item`, and `toss_stack`, `toss`, `drop_item`, `pickup_items_nearby`.
- Interaction/use: `activate_block`, `activate_entity`, `activate_entity_at`, `use_on_entity`, and `swing_arm` through reviewed ordinary-player-like NPC adapters where runtime preconditions pass. Generic held-item activation/consumption is intentionally not exposed until a reviewed adapter exists.
- Combat stop/attack bridge: `attack`, `attack_nearest`, `attack_target`, `pvp_stop`, `pvp_force_stop`.
- Control: `stop`, `pause`, `unpause`.
- Recipe queries and sessions: `recipes_for` and `recipes_all` query the server recipe manager. `craft` executes one known crafting recipe id with count 1 to 256, consumes only existing NPC normal-inventory inputs, respects 2x2 versus reachable loaded crafting table requirements, handles ordinary crafting remainders, and rolls back on missing inputs or output capacity failure. Loaded reachable block-entity `open_block`, `open_container`, `window_deposit`, `window_withdraw`, `window_close`, `close_window`, and JSON-option `transfer` are live executor tools with NPC-owned session state and no-loss snapshot/rollback semantics. Transfer JSON options must include a supported direction, item type, and integer count from 1 to 256. Generic `window_deposit`, `window_withdraw`, and `transfer` reject slot-restricted container sessions with `slot_restricted_container_transfer_unsupported` instead of guessing mechanics-specific slot rules.

These bridges preserve current runtime validation and do not reintroduce hidden resource strategy chains.
