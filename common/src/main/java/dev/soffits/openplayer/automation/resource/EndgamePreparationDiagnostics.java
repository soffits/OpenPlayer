package dev.soffits.openplayer.automation.resource;

import java.util.List;

public final class EndgamePreparationDiagnostics {
    public static final int RECOMMENDED_EYE_COUNT = 12;
    public static final int RECOMMENDED_FOOD_COUNT = 16;
    public static final int RECOMMENDED_BLOCK_COUNT = 64;

    private EndgamePreparationDiagnostics() {
    }

    public static EndgameTaskTree taskTree(String dimensionId, InventoryCounts inventoryCounts) {
        if (inventoryCounts == null) {
            throw new IllegalArgumentException("inventoryCounts cannot be null");
        }
        String normalizedDimension = normalizedDimension(dimensionId);
        EndgameTaskNode resourcePrep = new EndgameTaskNode(
                "resource_prep",
                resourcePrepStatus(inventoryCounts),
                "eyes=" + inventoryCounts.eyesOfEnder() + "/" + RECOMMENDED_EYE_COUNT
                        + " food=" + inventoryCounts.food() + "/" + RECOMMENDED_FOOD_COUNT
                        + " blocks=" + inventoryCounts.blocks() + "/" + RECOMMENDED_BLOCK_COUNT,
                List.of(
                        EndgameTaskNode.leaf("blaze_resources", blazeStatus(inventoryCounts),
                                "blaze_rods=" + inventoryCounts.blazeRods()
                                        + " blaze_powder=" + inventoryCounts.blazePowder()
                                        + " primitive=GET_ITEM_or_ATTACK_TARGET_loaded_blaze"),
                        EndgameTaskNode.leaf("pearl_eye_resources", pearlEyeStatus(inventoryCounts),
                                "ender_pearls=" + inventoryCounts.enderPearls()
                                        + " eyes=" + inventoryCounts.eyesOfEnder()
                                        + " primitive=GET_ITEM_recipe_if_materials_available"),
                        EndgameTaskNode.leaf("food_and_blocks", safetyMaterialStatus(inventoryCounts),
                                "food=" + inventoryCounts.food() + " beds=" + inventoryCounts.beds()
                                        + " blocks=" + inventoryCounts.blocks()
                                        + " primitive=COLLECT_FOOD_SMELT_ITEM_GET_ITEM")
                )
        );
        EndgameTaskNode netherPrep = new EndgameTaskNode(
                "vanilla_nether_prep",
                normalizedDimension.equals("minecraft:the_nether") ? EndgameTaskStatus.READY : EndgameTaskStatus.AVAILABLE_PRIMITIVE,
                "current_dimension=" + normalizedDimension
                        + " vanilla_adapter=TRAVEL_NETHER_OR_USE_PORTAL vanilla_nether_portal_build_adapter",
                List.of(
                        EndgameTaskNode.leaf("fortress_or_blaze_search", EndgameTaskStatus.MISSING_PRIMITIVE,
                                "no_fortress_search_adapter_no_hidden_locate_api"),
                        EndgameTaskNode.leaf("loaded_blaze_combat", EndgameTaskStatus.AVAILABLE_PRIMITIVE,
                                "ATTACK_TARGET_loaded_reviewed_hostiles_only_no_fake_drops")
                )
        );
        EndgameTaskNode stronghold = new EndgameTaskNode(
                "stronghold_estimation_search",
                inventoryCounts.eyesOfEnder() > 0 ? EndgameTaskStatus.MISSING_PRIMITIVE : EndgameTaskStatus.BLOCKED_BY_MATERIALS,
                "eye_throw_triangulation_adapter_missing loaded_world_diagnostics_only no_locate_api eyes="
                        + inventoryCounts.eyesOfEnder(),
                List.of(
                        EndgameTaskNode.leaf("eye_throw_observation", EndgameTaskStatus.MISSING_PRIMITIVE,
                                "needs_reviewed_use_item_and_observation_adapter"),
                        EndgameTaskNode.leaf("loaded_world_diagnostics", EndgameTaskStatus.MISSING_PRIMITIVE,
                                "no_reviewed_stronghold_evidence_scanner_yet")
                )
        );
        EndgameTaskNode portalPrep = new EndgameTaskNode(
                "end_portal_prep",
                inventoryCounts.eyesOfEnder() >= RECOMMENDED_EYE_COUNT
                        ? EndgameTaskStatus.MISSING_PRIMITIVE : EndgameTaskStatus.BLOCKED_BY_MATERIALS,
                "requires_visible_portal_frame_state_and_inventory_safety eyes=" + inventoryCounts.eyesOfEnder(),
                List.of(
                        EndgameTaskNode.leaf("portal_frame_state", EndgameTaskStatus.MISSING_PRIMITIVE,
                                "needs_loaded_end_portal_room_adapter"),
                        EndgameTaskNode.leaf("insert_eyes", EndgameTaskStatus.MISSING_PRIMITIVE,
                                "needs_reviewed_block_interaction_adapter_for_portal_frames")
                )
        );
        EndgameTaskNode endTravel = EndgameTaskNode.leaf(
                "end_travel",
                EndgameTaskStatus.MISSING_PRIMITIVE,
                "no_forced_dimension_change requires_visible_completed_portal_and_recovery_plan"
        );
        EndgameTaskNode dragon = new EndgameTaskNode(
                "dragon_fight_primitives",
                EndgameTaskStatus.MISSING_PRIMITIVE,
                "cancellable_task_tree_only no_dragon_completion_claim",
                List.of(
                        EndgameTaskNode.leaf("crystal_handling", EndgameTaskStatus.MISSING_PRIMITIVE,
                                "needs_reviewed_movement_ranged_combat_or_block_adapter"),
                        EndgameTaskNode.leaf("dragon_combat", EndgameTaskStatus.MISSING_PRIMITIVE,
                                "needs_reviewed_combat_positioning_and_safety_adapter"),
                        EndgameTaskNode.leaf("bed_pearl_block_tactics", EndgameTaskStatus.MISSING_PRIMITIVE,
                                "needs_safe_npc_specific_bed_pearl_block_placement_adapters")
                )
        );
        EndgameTaskNode recovery = EndgameTaskNode.leaf(
                "current_dimension_recovery",
                recoveryStatus(normalizedDimension, inventoryCounts),
                "current_dimension=" + normalizedDimension + " environment=observed_loaded_world food="
                        + inventoryCounts.food() + " blocks=" + inventoryCounts.blocks()
                        + " generic_dimension_recovery=loaded_portal_or_explore_or_owner_path_if_available"
                        + " stop_cancel_available inventory_safety_prep no_fake_return_or_respawn_claim"
        );
        EndgameTaskNode root = new EndgameTaskNode(
                "endgame_phase21",
                EndgameTaskStatus.UNSAFE_OR_UNKNOWN,
                "vanilla_endgame_diagnostic_task_tree_only no_speedrun_completion_claim dimension=" + normalizedDimension,
                List.of(resourcePrep, netherPrep, stronghold, portalPrep, endTravel, dragon, recovery)
        );
        return new EndgameTaskTree(root);
    }

    public static String summary(String dimensionId, InventoryCounts inventoryCounts) {
        return taskTree(dimensionId, inventoryCounts).boundedSummary();
    }

    public static List<String> visibleStatusLines(String dimensionId, InventoryCounts inventoryCounts) {
        return EndgameTaskTreeStatusFormatter.visibleLines(taskTree(dimensionId, inventoryCounts));
    }

    public static List<String> visibleViewerStatusLines(String dimensionId, InventoryCounts inventoryCounts) {
        return EndgameTaskTreeStatusFormatter.visibleViewerDiagnosticLines(taskTree(dimensionId, inventoryCounts));
    }

    public static String hintForItem(String itemId, String dimensionId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId cannot be blank");
        }
        String normalizedDimension = normalizedDimension(dimensionId);
        return switch (itemId) {
            case "minecraft:blaze_rod", "minecraft:blaze_powder" ->
                    "endgame_prep=blaze_resource_chain current_dimension=" + normalizedDimension
                            + " available_primitives=TRAVEL_NETHER,REPORT_STATUS,ATTACK_TARGET,GET_ITEM "
                            + "missing_primitives=fortress_or_blaze_spawner_search,fire_resistance_prep,task_tree_orchestration "
                            + "truth=GET_ITEM_cannot_fake_hostile_drops_without_visible_drop_or_recipe";
            case "minecraft:ender_pearl", "minecraft:ender_eye" ->
                    "endgame_prep=pearl_eye_resource_chain current_dimension=" + normalizedDimension
                            + " available_primitives=ATTACK_TARGET,GET_ITEM,REPORT_STATUS,SMELT_ITEM "
                            + "missing_primitives=eye_throw_stronghold_estimation,barter_or_trade_adapters,task_tree_orchestration "
                            + "truth=GET_ITEM_cannot_fake_mob_or_trade_drops_without_visible_drop_or_recipe";
            case "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken",
                    "minecraft:cooked_mutton", "minecraft:bread", "minecraft:golden_carrot" ->
                    "endgame_prep=food_safety current_dimension=" + normalizedDimension
                            + " available_primitives=COLLECT_FOOD,GET_ITEM,SMELT_ITEM,REPORT_STATUS "
                            + "missing_primitives=long_range_food_route_planning,trade_adapters "
                            + "truth=preparation_is_visible_inventory_or_loaded_world_only";
            default -> "";
        };
    }

    private static String normalizedDimension(String dimensionId) {
        return dimensionId == null || dimensionId.isBlank() ? "unknown" : dimensionId.trim();
    }

    private static EndgameTaskStatus resourcePrepStatus(InventoryCounts counts) {
        if (counts.eyesOfEnder() >= RECOMMENDED_EYE_COUNT
                && counts.food() >= RECOMMENDED_FOOD_COUNT
                && counts.blocks() >= RECOMMENDED_BLOCK_COUNT) {
            return EndgameTaskStatus.READY;
        }
        return EndgameTaskStatus.BLOCKED_BY_MATERIALS;
    }

    private static EndgameTaskStatus blazeStatus(InventoryCounts counts) {
        if (counts.blazePowder() > 0 || counts.blazeRods() > 0) {
            return EndgameTaskStatus.AVAILABLE_PRIMITIVE;
        }
        return EndgameTaskStatus.BLOCKED_BY_MATERIALS;
    }

    private static EndgameTaskStatus pearlEyeStatus(InventoryCounts counts) {
        if (counts.eyesOfEnder() >= RECOMMENDED_EYE_COUNT) {
            return EndgameTaskStatus.READY;
        }
        if (counts.enderPearls() > 0 && (counts.blazePowder() > 0 || counts.blazeRods() > 0)) {
            return EndgameTaskStatus.AVAILABLE_PRIMITIVE;
        }
        return EndgameTaskStatus.BLOCKED_BY_MATERIALS;
    }

    private static EndgameTaskStatus safetyMaterialStatus(InventoryCounts counts) {
        if (counts.food() >= RECOMMENDED_FOOD_COUNT && counts.blocks() >= RECOMMENDED_BLOCK_COUNT) {
            return EndgameTaskStatus.READY;
        }
        return EndgameTaskStatus.BLOCKED_BY_MATERIALS;
    }

    private static EndgameTaskStatus recoveryStatus(String dimensionId, InventoryCounts counts) {
        if (counts.food() > 0 && counts.blocks() > 0) {
            return EndgameTaskStatus.AVAILABLE_PRIMITIVE;
        }
        return EndgameTaskStatus.UNSAFE_OR_UNKNOWN;
    }

    public record InventoryCounts(
            int eyesOfEnder,
            int blazePowder,
            int blazeRods,
            int enderPearls,
            int food,
            int beds,
            int blocks
    ) {
        public InventoryCounts {
            if (eyesOfEnder < 0 || blazePowder < 0 || blazeRods < 0 || enderPearls < 0
                    || food < 0 || beds < 0 || blocks < 0) {
                throw new IllegalArgumentException("inventory counts cannot be negative");
            }
        }
    }
}
