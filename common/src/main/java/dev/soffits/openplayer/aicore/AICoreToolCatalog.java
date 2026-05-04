package dev.soffits.openplayer.aicore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AICoreToolCatalog {
    private static final List<AICoreToolDefinition> DEFINITIONS = buildDefinitions();
    private static final ToolRegistry REGISTRY = new ToolRegistry(DEFINITIONS.stream().map(AICoreToolDefinition::schema).toList());
    private static final Map<ToolName, AICoreToolDefinition> BY_NAME = byName(DEFINITIONS);

    private AICoreToolCatalog() {
    }

    public static ToolRegistry registry() {
        return REGISTRY;
    }

    public static Collection<AICoreToolDefinition> definitions() {
        return DEFINITIONS;
    }

    public static Optional<AICoreToolDefinition> definition(ToolName name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    public static List<CapabilityModule> defaultModules() {
        return List.of(
                new CapabilityModule("minecraft-core", "Server-side Minecraft state, query, and primitive action surface.", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS),
                new CapabilityModule("pathfinder", "Loaded-area navigation goal facade with truthful path diagnostics.", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS),
                new CapabilityModule("inventory", "Inventory and equipment primitives with no-loss validation contracts.", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS),
                new CapabilityModule("containers", "Loaded block-entity container sessions with no-loss transfer boundaries.", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS),
                new CapabilityModule("crafting", "Datapack-aware recipe query surface without resource acquisition chains.", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS),
                new CapabilityModule("combat", "Combat primitives gated by hostile-only default policy.", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS),
                new CapabilityModule("events", "Bounded sanitized session event ring buffer.", CapabilityStatus.IMPLEMENTED),
                new CapabilityModule("creative-policy", "Creative and admin parity surface rejected by default.", CapabilityStatus.POLICY_REJECTED),
                new CapabilityModule("openplayer-companion", "Bridge from OpenPlayer NPC commands into AICore validation and execution.", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS)
        );
    }

    private static List<AICoreToolDefinition> buildDefinitions() {
        ArrayList<AICoreToolDefinition> defs = new ArrayList<>();

        add(defs, "observe_self", "core_state", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Report the NPC facade state snapshot.");
        add(defs, "observe_world", "core_state", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Report bounded loaded-world status.");
        add(defs, "inventory_query", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Legacy inventory query bridge.");
        add(defs, "report_status", "openplayer_bridge", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Report active OpenPlayer automation status.");
        add(defs, "stop", "openplayer_bridge", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Stop active and queued OpenPlayer automation.");
        add(defs, "pause", "openplayer_bridge", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Pause automation ticks.");
        add(defs, "unpause", "openplayer_bridge", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Resume paused automation ticks.");

        add(defs, "block_at", "world_query", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Return a loaded block snapshot at explicit coordinates.", integer("x"), integer("y"), integer("z"), bool("extraInfos", false));
        add(defs, "wait_for_chunks_to_load", "world_query", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Verify currently needed chunks are already loaded without generating chunks.", integer("timeoutTicks", false));
        add(defs, "block_in_sight", "world_query", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Raycast for the block in sight using bounded steps.", integer("maxSteps"), number("vectorLength"));
        add(defs, "block_at_cursor", "world_query", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Raycast for the cursor block within maxDistance.", number("maxDistance"));
        add(defs, "entity_at_cursor", "world_query", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Raycast for the cursor entity within maxDistance.", number("maxDistance"));
        add(defs, "block_at_entity_cursor", "world_query", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Raycast from an entity cursor within maxDistance.", text("entityId"), number("maxDistance"));
        add(defs, "can_see_block", "world_query", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Return whether the NPC can see a loaded block.", integer("x"), integer("y"), integer("z"));
        add(defs, "find_blocks", "world_query", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Find loaded blocks by resource id with mandatory radius and count.", text("matching"), integer("maxDistance"), integer("count"));
        add(defs, "find_block", "world_query", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Find one loaded block by resource id with mandatory radius.", text("matching"), integer("maxDistance"));
        add(defs, "nearest_entity", "world_query", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Find the nearest loaded entity matching bounded criteria.", text("matching", false), integer("maxDistance"));
        add(defs, "find_loaded_blocks", "world_query", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Legacy loaded block search bridge.", text("matching"), integer("maxDistance", false));
        add(defs, "find_loaded_entities", "world_query", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Legacy loaded entity search bridge.", text("matching"), integer("maxDistance", false));

        add(defs, "set_control_state", "movement_pathfinder", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Set one visible player-like control state without applying fake motion.", text("control"), bool("state"));
        add(defs, "get_control_state", "movement_pathfinder", CapabilityStatus.IMPLEMENTED, false, "", "Read one control state from the facade.", text("control"));
        add(defs, "clear_control_states", "movement_pathfinder", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Clear all visible control states.");
        add(defs, "look_at", "movement_pathfinder", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Look at an explicit coordinate.", integer("x"), integer("y"), integer("z"), bool("force", false));
        add(defs, "look", "movement_pathfinder", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Set yaw and pitch.", number("yaw"), number("pitch"), bool("force", false));
        add(defs, "wait_for_ticks", "movement_pathfinder", CapabilityStatus.IMPLEMENTED, false, "", "Return a bounded wait request for the runtime tick scheduler.", integer("ticks"));
        add(defs, "move_to", "movement_pathfinder", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Legacy explicit coordinate movement bridge.", integer("x"), integer("y"), integer("z"));
        add(defs, "pathfinder_goto", "movement_pathfinder", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Request loaded-area pathfinder movement to a structured goal.", object("goal"));
        add(defs, "pathfinder_get_path_to", "movement_pathfinder", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Return bounded non-node loaded-area path diagnostics from current position to a goal.", object("goal"), integer("timeoutTicks"));
        add(defs, "pathfinder_get_path_from_to", "movement_pathfinder", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Return bounded non-node loaded-area path diagnostics from a start position to a goal.", object("start"), object("goal"), integer("timeoutTicks"));
        add(defs, "pathfinder_set_goal", "movement_pathfinder", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Set a non-dynamic loaded-area pathfinder goal through the reviewed goto bridge.", object("goal"), bool("dynamic", false));
        add(defs, "pathfinder_set_movements", "movement_pathfinder", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER, false, "unsupported_movement_profile_not_applied", "Reject movement profiles until they affect reviewed navigation.", object("movements"));
        add(defs, "pathfinder_stop", "movement_pathfinder", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Stop active pathfinder or bridged automation movement.");

        add(defs, "can_dig_block", "block_mutation", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Validate loaded block dig preconditions.", integer("x"), integer("y"), integer("z"));
        add(defs, "dig", "block_mutation", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Dig a loaded reachable block and verify state change where adapter exists.", integer("x"), integer("y"), integer("z"), bool("forceLook", false), text("digFace", false));
        add(defs, "break_block_at", "block_mutation", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Legacy explicit block breaking bridge.", integer("x"), integer("y"), integer("z"));
        add(defs, "stop_digging", "block_mutation", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Stop active digging when the NPC has a held-use dig session.");
        add(defs, "dig_time", "block_mutation", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Estimate dig time using loaded block and selected tool state.", integer("x"), integer("y"), integer("z"));
        add(defs, "place_block", "block_mutation", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Place selected block item against a loaded reference block.", integer("x"), integer("y"), integer("z"), object("faceVector"));
        add(defs, "place_block_at", "block_mutation", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Legacy explicit block placement bridge.", integer("x"), integer("y"), integer("z"));
        add(defs, "place_entity", "block_mutation", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER, true, "unsupported_missing_place_entity_adapter", "Place an entity from the held item against a block.", integer("x"), integer("y"), integer("z"), object("faceVector"));
        add(defs, "activate_block", "block_mutation", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Activate a loaded block with optional cursor position.", integer("x"), integer("y"), integer("z"), object("direction", false), object("cursorPos", false));
        add(defs, "update_sign", "block_mutation", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER, true, "unsupported_missing_sign_update_adapter", "Update sign text with bounded sanitized content.", integer("x"), integer("y"), integer("z"), text("text"), bool("back", false));

        add(defs, "use_on_entity", "item_entity_combat", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Use held item on a target entity through the reviewed interaction primitive.", text("entityId"));
        add(defs, "activate_entity", "item_entity_combat", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Activate an entity through the reviewed interaction primitive.", text("entityId"));
        add(defs, "activate_entity_at", "item_entity_combat", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Activate an entity at a relative position through the reviewed interaction primitive.", text("entityId"), object("position"));
        add(defs, "attack", "item_entity_combat", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Attack an explicit hostile allowlisted entity.", text("entityId"), bool("swing", false));
        add(defs, "attack_nearest", "item_entity_combat", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Legacy nearest hostile attack bridge.", integer("maxDistance", false));
        add(defs, "attack_target", "item_entity_combat", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Legacy explicit hostile attack bridge.", text("entityId"));
        add(defs, "swing_arm", "item_entity_combat", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Swing the NPC main hand for visible ordinary-player-like feedback.", text("hand", false), bool("showHand", false));
        addInventory(defs);
        addCraftingAndContainers(defs);
        addChatEventsPluginsAndAdmin(defs);
        addEcosystem(defs);

        return Collections.unmodifiableList(defs);
    }

    private static void addInventory(List<AICoreToolDefinition> defs) {
        add(defs, "inventory", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Return an inventory snapshot.");
        add(defs, "held_item", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Return the currently held item snapshot.");
        add(defs, "set_quick_bar_slot", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Select hotbar slot 0 through 8.", integer("slot"));
        add(defs, "equip", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Equip an exact inventory item id.", text("item"), text("destination"));
        add(defs, "equip_item", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Legacy equip item bridge.", text("item"));
        add(defs, "unequip", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Unequip a destination safely into normal inventory.", text("destination"));
        add(defs, "toss_stack", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Toss the selected stack with no-loss spawn commit checks.", text("item", false));
        add(defs, "toss", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Toss an exact item stack count.", text("itemType"), text("metadata", false), integer("count", false));
        add(defs, "drop_item", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Legacy drop item bridge.", text("item", false), integer("count", false));
        add(defs, "simple_click_left", "inventory_window", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER, true, "unsupported_missing_window_adapter", "Left click a window slot after session validation.", integer("slot"));
        add(defs, "simple_click_right", "inventory_window", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER, true, "unsupported_missing_window_adapter", "Right click a window slot after session validation.", integer("slot"));
        add(defs, "click_window", "inventory_window", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER, true, "unsupported_missing_window_adapter", "Click a validated window slot.", integer("slot"), integer("mouseButton"), integer("mode"));
        add(defs, "put_selected_item_range", "inventory_window", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER, true, "unsupported_missing_window_adapter", "Put selected item into a validated slot range.", integer("start"), integer("end"), text("window"), integer("slot"));
        add(defs, "put_away", "inventory_window", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER, true, "unsupported_missing_window_adapter", "Put away a slot with no-loss semantics.", integer("slot"));
        add(defs, "close_window", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Close the current bounded NPC-owned container session.", text("window", false));
        add(defs, "transfer", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Transfer items with no-loss transaction semantics.", object("options"));
        add(defs, "open_block", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Open a loaded reachable block-entity container session.", integer("x"), integer("y"), integer("z"));
        add(defs, "open_entity", "inventory_window", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER, true, "unsupported_missing_window_adapter", "Open an entity window after reach and type validation.", text("entityId"));
        add(defs, "move_slot_item", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Move an NPC inventory item between slots with no-loss checks.", integer("sourceSlot"), integer("destSlot"));
        add(defs, "update_held_item", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Refresh held item snapshot from runtime state.");
        add(defs, "get_equipment_dest_slot", "inventory_window", CapabilityStatus.IMPLEMENTED, false, "", "Resolve a mineflayer equipment destination to a slot id.", text("destination"));
        add(defs, "pickup_items_nearby", "inventory_window", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Collect already dropped nearby items only.", text("matching", false), integer("maxDistance", false));
    }

    private static void addCraftingAndContainers(List<AICoreToolDefinition> defs) {
        add(defs, "recipes_for", "recipes_crafting", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Query server recipes for one output item.", text("itemType"), text("metadata", false), integer("minResultCount", false), object("craftingTable", false));
        add(defs, "recipes_all", "recipes_crafting", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Query all server recipes for one output item.", text("itemType"), text("metadata", false), object("craftingTable", false));
        add(defs, "craft", "recipes_crafting", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER, true, "unsupported_missing_no_loss_crafting_adapter", "Craft a known recipe without acquiring missing resources.", text("recipe"), integer("count"), object("craftingTable", false));

        add(defs, "open_container", "containers", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Open a loaded reachable block-entity container session.", object("target"));
        add(defs, "window_deposit", "containers", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Deposit items into current generic block-entity container with no-loss semantics.", text("itemType"), text("metadata", false), integer("count"));
        add(defs, "window_withdraw", "containers", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, true, "", "Withdraw items from current generic block-entity container with no-loss semantics.", text("itemType"), text("metadata", false), integer("count"));
        add(defs, "window_close", "containers", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Close current bounded container session.");
    }

    private static void addChatEventsPluginsAndAdmin(List<AICoreToolDefinition> defs) {
        add(defs, "chat", "chat_settings", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Emit companion speech through the OpenPlayer chat/speech layer.", text("message"));
        add(defs, "whisper", "chat_settings", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER, false, "unsupported_missing_safe_whisper_adapter", "Send direct message only when a safe adapter exists.", text("username"), text("message"));
        add(defs, "tab_complete", "chat_settings", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER, false, "unsupported_missing_permission_aware_tab_complete_adapter", "Permission-aware read-only tab completion.", text("input"), bool("assumeCommand", false));
        for (String name : List.of("chat_add_pattern", "add_chat_pattern", "add_chat_pattern_set", "remove_chat_pattern", "await_message", "set_settings")) {
            add(defs, name, "chat_settings", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER, false, "unsupported_missing_bounded_chat_pattern_adapter", "Bounded session-scoped chat pattern or setting parity surface.", text("name", false), text("pattern", false));
        }

        add(defs, "accept_resource_pack", "resource_pack", CapabilityStatus.NOT_APPLICABLE_SERVER_SIDE_NPC, false, "unsupported_server_side_npc_no_client_resource_pack_flow", "Resource pack client flow is not applicable to server-side NPCs.");
        add(defs, "deny_resource_pack", "resource_pack", CapabilityStatus.NOT_APPLICABLE_SERVER_SIDE_NPC, false, "unsupported_server_side_npc_no_client_resource_pack_flow", "Resource pack client flow is not applicable to server-side NPCs.");

        add(defs, "observe_events", "events", CapabilityStatus.IMPLEMENTED, false, "", "Read sanitized bounded events after a cursor.", text("cursor", false), integer("limit", false));
        add(defs, "wait_for_event", "events", CapabilityStatus.IMPLEMENTED, false, "", "Wait for a bounded event type and timeout.", text("eventType"), integer("timeoutTicks"));
        add(defs, "has_plugin", "plugins_capabilities", CapabilityStatus.IMPLEMENTED, false, "", "Return whether a Java-registered AICore module exists.", text("module"));
        add(defs, "list_capabilities", "plugins_capabilities", CapabilityStatus.IMPLEMENTED, false, "", "List registered AICore capability modules.");
        add(defs, "capability_status", "plugins_capabilities", CapabilityStatus.IMPLEMENTED, false, "", "Return one capability module status.", text("module"));

        for (String name : List.of("creative_set_inventory_slot", "creative_clear_slot", "creative_clear_inventory", "creative_fly_to", "creative_start_flying", "creative_stop_flying", "set_command_block")) {
            add(defs, name, "creative_admin", CapabilityStatus.POLICY_REJECTED, true, "rejected_admin_capability", "Creative/admin mineflayer parity surface rejected for normal companions.", text("target", false));
        }
    }

    private static void addEcosystem(List<AICoreToolDefinition> defs) {
        add(defs, "pvp_attack", "ecosystem_pvp", CapabilityStatus.POLICY_REJECTED, true, "policy_rejected_hostile_allowlist_required", "PVP facade attack requires explicit hostile policy and never targets players by default.", text("entityId"));
        add(defs, "pvp_stop", "ecosystem_pvp", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Stop PVP facade or bridged attack automation.");
        add(defs, "pvp_force_stop", "ecosystem_pvp", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS, false, "", "Force stop PVP facade or bridged attack automation.");
        add(defs, "pvp_status", "ecosystem_pvp", CapabilityStatus.IMPLEMENTED, false, "", "Return PVP facade status.");
    }

    private static void add(List<AICoreToolDefinition> defs, String name, String group, CapabilityStatus status, boolean mutatesWorld, String reason, String description, ToolParameter... parameters) {
        defs.add(new AICoreToolDefinition(new ToolSchema(ToolName.of(name), description, List.of(parameters), mutatesWorld), group, status, reason));
    }

    private static ToolParameter text(String name) {
        return text(name, true);
    }

    private static ToolParameter text(String name, boolean required) {
        return new ToolParameter(name, "string", required, required ? "required string" : "optional string");
    }

    private static ToolParameter integer(String name) {
        return integer(name, true);
    }

    private static ToolParameter integer(String name, boolean required) {
        return new ToolParameter(name, "integer", required, required ? "required integer" : "optional integer");
    }

    private static ToolParameter number(String name) {
        return number(name, true);
    }

    private static ToolParameter number(String name, boolean required) {
        return new ToolParameter(name, "number", required, required ? "required number" : "optional number");
    }

    private static ToolParameter bool(String name) {
        return bool(name, true);
    }

    private static ToolParameter bool(String name, boolean required) {
        return new ToolParameter(name, "boolean", required, required ? "required boolean" : "optional boolean");
    }

    private static ToolParameter object(String name) {
        return object(name, true);
    }

    private static ToolParameter object(String name, boolean required) {
        return new ToolParameter(name, "object", required, required ? "required JSON object" : "optional JSON object");
    }

    private static Map<ToolName, AICoreToolDefinition> byName(List<AICoreToolDefinition> definitions) {
        LinkedHashMap<ToolName, AICoreToolDefinition> map = new LinkedHashMap<>();
        for (AICoreToolDefinition definition : definitions) {
            if (map.put(definition.schema().name(), definition) != null) {
                throw new IllegalArgumentException("duplicate AICore tool definition: " + definition.schema().name().value());
            }
        }
        return Map.copyOf(map);
    }
}
