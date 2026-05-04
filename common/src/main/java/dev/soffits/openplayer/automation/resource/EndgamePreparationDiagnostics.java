package dev.soffits.openplayer.automation.resource;

public final class EndgamePreparationDiagnostics {
    private EndgamePreparationDiagnostics() {
    }

    public static String hintForItem(String itemId, String dimensionId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId cannot be blank");
        }
        String normalizedDimension = dimensionId == null || dimensionId.isBlank() ? "unknown" : dimensionId.trim();
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
}
