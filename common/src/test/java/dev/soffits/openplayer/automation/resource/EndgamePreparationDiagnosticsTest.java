package dev.soffits.openplayer.automation.resource;

public final class EndgamePreparationDiagnosticsTest {
    private EndgamePreparationDiagnosticsTest() {
    }

    public static void main(String[] args) {
        blazeHintsNameAvailableAndMissingPrimitives();
        pearlHintsDoNotClaimFakeTradeOrMobDrops();
        foodHintsUseExistingPreparationPrimitives();
        unrelatedItemsHaveNoEndgameHint();
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
