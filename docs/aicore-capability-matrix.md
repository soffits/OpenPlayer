# AICore Capability Matrix

Statuses: `implemented`, `implemented_with_server_side_semantics`, `policy_rejected`, `unsupported_missing_adapter`, `not_applicable_server_side_npc`.

## Core State And Data Views

| Mineflayer surface | AICore surface | Status | Notes |
| --- | --- | --- | --- |
| `bot.registry`, `bot.world`, `bot.entity`, `bot.entities`, `bot.username`, `bot.spawnPoint`, `bot.heldItem`, `bot.usingHeldItem`, `bot.player`, `bot.players`, `bot.tablist`, `bot.settings`, `bot.experience`, `bot.health`, `bot.food`, `bot.foodSaturation`, `bot.oxygenLevel`, `bot.physics`, `bot.time`, `bot.quickBarSlot`, `bot.inventory`, `bot.targetDigBlock`, `bot.isSleeping`, `bot.scoreboards`, `bot.scoreboard`, `bot.teams`, `bot.teamMap`, `bot.controlState`, `bot.currentWindow` | `AICoreBotFacade`, `AICoreBotState`, `observe_self`, `observe_world`, `inventory`, `held_item` | `implemented_with_server_side_semantics` | Server-side NPC state is exposed as immutable snapshots and stable resource IDs. Client-only pieces are absent or marked separately. |
| `Vec3`, `Location`, `Entity`, `Block`, `Biome`, `Item`, `Window`, `Recipe`, `Container`, `Furnace`, `EnchantmentTable`, `Anvil`, `Villager`, `ScoreBoard`, `Team`, `BossBar`, `Particle` | `AICoreVec3`, `AICoreBlockSnapshot`, `AICoreEntitySnapshot`, `AICoreItemSnapshot`, planned adapter snapshots | `implemented_with_server_side_semantics` | Core immutable Java shapes are present; workstation/window recipe specifics remain adapter-gated. |
| `bot.game.*`, `bot.isRaining`, `bot.rainState`, `bot.thunderState` | `AICoreBotState` fields and observation tools | `implemented_with_server_side_semantics` | Snapshot contract exists; Minecraft runtime adapters fill live values later. |

## World Query API

| Mineflayer surface | AICore tools | Status | Notes |
| --- | --- | --- | --- |
| `blockAt`, `canSeeBlock` | `block_at`, `can_see_block` | `implemented_with_server_side_semantics` | Loaded-world only. Live NPC executor uses `ServerLevel` loaded-chunk state and collider LOS; pure seam covers deterministic snapshots. |
| `waitForChunksToLoad` | `wait_for_chunks_to_load` | `implemented_with_server_side_semantics` | Server-side check only; no chunk generation. |
| `blockInSight`, `blockAtCursor`, `entityAtCursor` | `block_in_sight`, `block_at_cursor`, `entity_at_cursor` | `implemented_with_server_side_semantics` | Bounded loaded-world raycasts from NPC/view vector or pure snapshot seams; no chunk generation. These are facade/live executor tools, not provider-executable `CommandIntent` tools yet. |
| `blockAtEntityCursor` | `block_at_entity_cursor` | `unsupported_missing_adapter` | Requires an entity-specific cursor/view adapter in both live and pure seams; it is not aliased to the NPC cursor. |
| `findBlocks`, `findBlock`, `nearestEntity` | `find_blocks`, `find_block`, `nearest_entity`, legacy `find_loaded_blocks`, `find_loaded_entities` | `implemented_with_server_side_semantics` | Bounded loaded-world searches; radius/count required for mineflayer-facing tools. |

## Control, Movement, And Pathfinder

| Mineflayer surface | AICore tools | Status | Notes |
| --- | --- | --- | --- |
| `setControlState`, `clearControlStates` | `set_control_state`, `clear_control_states` | `unsupported_missing_adapter` | Surface exists; no fake control state mutation. |
| `getControlState` | `get_control_state` | `implemented` | Facade state query. |
| `lookAt`, `look`, `waitForTicks` | `look_at`, `look`, `wait_for_ticks` | `implemented_with_server_side_semantics` / `unsupported_missing_adapter` | `look_at` bridges existing explicit coordinate primitive. Raw yaw/pitch is implemented by the live NPC executor as facade-only and is not provider-executable yet. `wait_for_ticks` remains adapter-gated. |
| `pathfinder.goto`, `getPathTo`, `getPathFromTo`, `setGoal`, `setMovements`, `stop` | `pathfinder_goto`, `pathfinder_get_path_to`, `pathfinder_get_path_from_to`, `pathfinder_set_goal`, `pathfinder_set_movements`, `pathfinder_stop` | `implemented_with_server_side_semantics` / `unsupported_missing_adapter` | `pathfinder_goto` bridges coordinate goal objects to existing loaded-area vanilla navigation. Path computation, persistent dynamic goals, and movement profile configuration still report missing adapters. |
| `GoalBlock`, `GoalNear`, `GoalXZ`, `GoalNearXZ`, `GoalY`, `GoalGetToBlock`, `GoalFollow`, `GoalPlaceBlock`, `GoalLookAtBlock`, `GoalBreakBlock` | `AICoreGoal` | `implemented` | `GoalBreakBlock` is only a deprecated precondition alias shape, not a hidden dig macro. |

## Block Mutation API

| Mineflayer surface | AICore tools | Status | Notes |
| --- | --- | --- | --- |
| `canDigBlock`, `stopDigging`, `digTime` | `can_dig_block`, `stop_digging`, `dig_time` | `unsupported_missing_adapter` | Real hardness/tool/state adapters are required. |
| `dig`, `placeBlock` | `dig`, `break_block_at`, `place_block`, `place_block_at` | `implemented_with_server_side_semantics` | Bridges existing explicit block primitives and policy validation. |
| `placeEntity`, `activateBlock`, `updateSign` | `place_entity`, `activate_block`, `update_sign` | `unsupported_missing_adapter` / `implemented_with_server_side_semantics` | `activate_block` bridges the reviewed loaded, reachable, LOS block interaction primitive. Entity placement and sign update remain missing adapters. |

## Item Use, Entity Interaction, Combat, Mounts, Vehicles

| Mineflayer surface | AICore tools | Status | Notes |
| --- | --- | --- | --- |
| `activateItem`, `deactivateItem`, `consume`, `useOn`, `activateEntity`, `activateEntityAt`, `swingArm` | `activate_item`, `deactivate_item`, `consume`, `use_on_entity`, `activate_entity`, `activate_entity_at`, `swing_arm` | `implemented_with_server_side_semantics` / `unsupported_missing_adapter` | Safe selected edible use, reviewed entity interaction bridges, and main-hand swing are implemented. Long-running item-use deactivation remains missing because there is no held-use session adapter. |
| `fish` | `fish` | `unsupported_missing_adapter` | Returns `unsupported_missing_npc_fishing_hook_adapter`; no fake fishing macro. |
| `attack` | `attack`, `attack_nearest`, `attack_target` | `implemented_with_server_side_semantics` | Existing combat bridge remains hostile-policy gated. |
| `mount`, `dismount`, `moveVehicle`, `elytraFly` | `mount`, `dismount`, `move_vehicle`, `elytra_fly` | `unsupported_missing_adapter` | Elytra returns `unsupported_missing_server_side_elytra_physics_adapter`. |

## Inventory, Equipment, And Windows

| Mineflayer surface | AICore tools | Status | Notes |
| --- | --- | --- | --- |
| `inventory`, `heldItem`, `quickBarSlot`, `updateHeldItem`, `getEquipmentDestSlot` | `inventory`, `held_item`, `set_quick_bar_slot`, `update_held_item`, `get_equipment_dest_slot` | `implemented_with_server_side_semantics` | Snapshot, destination resolution, and live hotbar selection are present. `set_quick_bar_slot` is facade-only and is not provider-executable yet. |
| `equip`, `unequip`, `tossStack`, `toss` | `equip`, `equip_item`, `unequip`, `toss_stack`, `toss`, `drop_item` | `implemented_with_server_side_semantics` / `unsupported_missing_adapter` | Existing equip/drop bridge remains. `toss_stack` uses selected-stack no-loss spawn commit checks as a facade-only live executor tool and is not provider-executable yet. Unequip still needs an equipment transaction adapter. |
| `simpleClick`, `clickWindow`, `putSelectedItemRange`, `putAway`, `closeWindow`, `transfer`, `openBlock`, `openEntity`, `moveSlotItem` | Matching `simple_click_*`, `click_window`, `put_selected_item_range`, `put_away`, `close_window`, `transfer`, `open_block`, `open_entity`, `move_slot_item` | `unsupported_missing_adapter` | Present but rejected as missing window/session adapter to preserve no-loss semantics. |

## Recipes, Crafting, Containers, Workstations

| Mineflayer surface | AICore tools | Status | Notes |
| --- | --- | --- | --- |
| `recipesFor`, `recipesAll`, `craft` | `recipes_for`, `recipes_all`, `craft` | `unsupported_missing_adapter` | Recipe primitives exist; no resource acquisition chains. |
| `window.deposit`, `window.withdraw`, `window.close`, `openContainer`, `openChest` | `window_deposit`, `window_withdraw`, `window_close`, `open_container`, `open_chest` | `unsupported_missing_adapter` | Requires no-loss window session adapter. |
| Furnace, enchantment table, anvil, villager APIs | `open_furnace`, `furnace_*`, `open_enchantment_table`, `enchantment_*`, `open_anvil`, `anvil_combine`, `open_villager`, `villager_*` | `unsupported_missing_adapter` | Registered with truthful missing workstation adapter results. |

## Chat, Settings, Bed, Respawn, Resource Packs

| Mineflayer surface | AICore tools | Status | Notes |
| --- | --- | --- | --- |
| `chat` | `chat` | `implemented_with_server_side_semantics` | Maps to companion speech/local chat semantics. |
| `whisper`, `tabComplete`, chat patterns, `awaitMessage`, `setSettings` | `whisper`, `tab_complete`, `chat_add_pattern`, `add_chat_pattern`, `add_chat_pattern_set`, `remove_chat_pattern`, `await_message`, `set_settings` | `unsupported_missing_adapter` | Requires safe permission-aware and bounded session adapters. |
| `sleep`, `isABed`, `wake`, `respawn` | `sleep`, `is_bed`, `wake`, `respawn` | `unsupported_missing_adapter` | Requires real NPC bed/lifecycle adapters. |
| `acceptResourcePack`, `denyResourcePack` | `accept_resource_pack`, `deny_resource_pack` | `not_applicable_server_side_npc` | Server-side NPC has no client resource-pack flow. |

## Events And Plugins

| Mineflayer surface | AICore tools/classes | Status | Notes |
| --- | --- | --- | --- |
| chat, message, login, spawn, weather, health, entity, player, block, chunk, sound, movement, window, scoreboard, team, boss bar, held-item, physics, particle events | `AICoreEventBus`, `AgentEvent`, `EventType`, `EventCursor`, `EventSubscription`, `observe_events`, `wait_for_event` | `implemented` | Bounded sanitized in-memory ring buffer per session facade. |
| `loadPlugin`, `loadPlugins`, `hasPlugin` | `AICorePlugin`, `AICorePluginRegistry`, `CapabilityModule`, `has_plugin`, `list_capabilities`, `capability_status` | `implemented` | Java modules only; provider-origin plugin loading is rejected. |

## Creative, Admin, Ecosystem Plugins

| Mineflayer surface | AICore tools | Status | Notes |
| --- | --- | --- | --- |
| Creative inventory/flying and command block APIs | `creative_set_inventory_slot`, `creative_clear_slot`, `creative_clear_inventory`, `creative_fly_to`, `creative_start_flying`, `creative_stop_flying`, `set_command_block` | `policy_rejected` | Default result is `rejected_admin_capability`. |
| mineflayer-collectblock | `collectblock_collect`, `collectblock_cancel`, `collectblock_status` | `unsupported_missing_adapter` | Transparent diagnostics surface only; not a hidden `GET_ITEM` macro. |
| mineflayer-pvp | `pvp_attack`, `pvp_stop`, `pvp_force_stop`, `pvp_status` | `policy_rejected` / `implemented_with_server_side_semantics` | Player/owner/friendly/neutral targets excluded by default. |
| auto-eat and armor-manager | `auto_eat_enable`, `auto_eat_disable`, `auto_eat_status`, `armor_manager_equip_best`, `armor_manager_status` | `unsupported_missing_adapter` | Modes are visible in the surface but need reviewed bounded adapters. |
