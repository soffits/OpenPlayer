package dev.soffits.openplayer.automation.resource;

public final class EndgamePreparationDiagnosticsTest {
    private EndgamePreparationDiagnosticsTest() {
    }

    public static void main(String[] args) {
        blazeHintsNameAvailableAndMissingPrimitives();
        pearlHintsDoNotClaimFakeTradeOrMobDrops();
        foodHintsUseExistingPreparationPrimitives();
        unrelatedItemsHaveNoEndgameHint();
        taskTreeReportsMaterialsAndMissingStrongholdAdapters();
        taskTreeDoesNotClaimEndOrDragonSuccessWhenPrepared();
    }

    private static void blazeHintsNameAvailableAndMissingPrimitives() {
        String hint = EndgamePreparationDiagnostics.hintForItem("minecraft:blaze_rod", "minecraft:the_nether");

        require(hint.contains("endgame_prep=blaze_resource_chain"), "blaze rods need blaze resource hint");
        require(hint.contains("available_primitives=TRAVEL_NETHER,REPORT_STATUS,ATTACK_TARGET,GET_ITEM"),
                "blaze hint must point at existing primitives");
        require(hint.contains("missing_primitives=fortress_or_blaze_spawner_search"),
                "blaze hint must name missing search primitive");
        require(hint.contains("GET_ITEM_cannot_fake_hostile_drops"),
                "blaze hint must not claim fake hostile drops");
    }

    private static void pearlHintsDoNotClaimFakeTradeOrMobDrops() {
        String hint = EndgamePreparationDiagnostics.hintForItem("minecraft:ender_eye", "minecraft:overworld");

        require(hint.contains("endgame_prep=pearl_eye_resource_chain"), "eyes need pearl/eye hint");
        require(hint.contains("missing_primitives=eye_throw_stronghold_estimation"),
                "eye hint must name missing stronghold primitive");
        require(hint.contains("GET_ITEM_cannot_fake_mob_or_trade_drops"),
                "eye hint must not claim fake mob or trade drops");
    }

    private static void foodHintsUseExistingPreparationPrimitives() {
        String hint = EndgamePreparationDiagnostics.hintForItem("minecraft:cooked_beef", "minecraft:the_nether");

        require(hint.contains("endgame_prep=food_safety"), "food needs safety hint");
        require(hint.contains("available_primitives=COLLECT_FOOD,GET_ITEM,SMELT_ITEM,REPORT_STATUS"),
                "food hint must point at existing preparation primitives");
    }

    private static void unrelatedItemsHaveNoEndgameHint() {
        require(EndgamePreparationDiagnostics.hintForItem("minecraft:cobblestone", "minecraft:overworld").isEmpty(),
                "unrelated items should not add endgame noise");
    }

    private static void taskTreeReportsMaterialsAndMissingStrongholdAdapters() {
        EndgamePreparationDiagnostics.InventoryCounts counts = new EndgamePreparationDiagnostics.InventoryCounts(
                2, 1, 0, 2, 4, 0, 32
        );
        String summary = EndgamePreparationDiagnostics.summary("minecraft:overworld", counts);

        require(summary.contains("node=endgame_phase21 status=UNSAFE_OR_UNKNOWN"),
                "task tree root must be diagnostic rather than success");
        require(summary.contains("node=pearl_eye_resources status=AVAILABLE_PRIMITIVE"),
                "task tree should expose craftable eye materials as available primitive");
        require(summary.contains("node=stronghold_estimation_search status=MISSING_PRIMITIVE"),
                "task tree must name missing stronghold adapter when eyes exist");
        require(summary.contains("no_locate_api"), "stronghold diagnostics must reject hidden locate APIs");
    }

    private static void taskTreeDoesNotClaimEndOrDragonSuccessWhenPrepared() {
        EndgamePreparationDiagnostics.InventoryCounts counts = new EndgamePreparationDiagnostics.InventoryCounts(
                12, 0, 0, 0, 16, 4, 64
        );
        String summary = EndgamePreparationDiagnostics.summary("minecraft:the_end", counts);

        require(summary.contains("node=resource_prep status=READY"),
                "task tree should recognize visible preparation materials");
        require(summary.contains("node=end_travel status=MISSING_PRIMITIVE"),
                "task tree must not claim End travel execution");
        require(summary.contains("node=dragon_fight_primitives status=MISSING_PRIMITIVE"),
                "task tree must not claim dragon execution");
        require(!summary.toLowerCase().contains("speedrun_success"),
                "task tree must not claim speedrun success");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
