package dev.soffits.openplayer.aicore;

import java.util.Set;

public final class MineflayerApiSurfaceMatrixTest {
    private MineflayerApiSurfaceMatrixTest() {
    }

    public static void main(String[] args) {
        Set<String> groups = AICoreToolCatalog.definitions().stream().map(AICoreToolDefinition::group).collect(java.util.stream.Collectors.toSet());
        for (String group : Set.of("core_state", "world_query", "movement_pathfinder", "block_mutation", "item_entity_combat",
                "inventory_window", "recipes_crafting", "containers_workstations", "chat_settings", "resource_pack",
                "events", "plugins_capabilities", "creative_admin", "ecosystem_pvp")) {
            AICoreTestSupport.require(groups.contains(group), "missing matrix group: " + group);
        }
        AICoreTestSupport.requireStatus("accept_resource_pack", CapabilityStatus.NOT_APPLICABLE_SERVER_SIDE_NPC);
        AICoreTestSupport.requireStatus("creative_fly_to", CapabilityStatus.POLICY_REJECTED);
        AICoreTestSupport.requireStatus("can_dig_block", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("dig_time", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("deactivate_item", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireNoTool("fish");
        AICoreTestSupport.requireNoTool("is_bed");
        AICoreTestSupport.requireNoTool("armor_manager_equip_best");
        AICoreTestSupport.requireNoTool("collectblock_collect");
    }
}
