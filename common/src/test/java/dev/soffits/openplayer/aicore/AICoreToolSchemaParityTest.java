package dev.soffits.openplayer.aicore;

public final class AICoreToolSchemaParityTest {
    private AICoreToolSchemaParityTest() {
    }

    public static void main(String[] args) {
        String[] required = {
                "block_at", "wait_for_chunks_to_load", "block_in_sight", "block_at_cursor", "entity_at_cursor",
                "find_blocks", "find_block", "nearest_entity", "set_control_state", "pathfinder_goto",
                "can_dig_block", "dig", "place_block", "activate_item", "attack", "inventory", "set_quick_bar_slot",
                "recipes_for", "craft", "open_container", "open_block", "window_deposit", "window_withdraw",
                "chat", "observe_events", "has_plugin", "creative_set_inventory_slot", "set_command_block",
                "pvp_attack"
        };
        for (String tool : required) {
            AICoreTestSupport.requireTool(tool);
        }
        for (String tool : new String[] {
                "open_chest", "open_furnace", "furnace_status", "is_bed", "sleep", "armor_manager_status",
                "collectblock_collect", "auto_eat_status", "open_anvil", "villager_trade", "fish", "mount", "elytra_fly"
        }) {
            AICoreTestSupport.requireNoTool(tool);
        }
    }
}
