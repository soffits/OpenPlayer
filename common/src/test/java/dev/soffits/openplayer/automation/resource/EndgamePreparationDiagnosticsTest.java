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
        taskTreeReportsGenericRecoveryForModdedDimensions();
        taskTreeStatusLinesAreBoundedReadableAndTruthful();
        viewerStatusLinesLabelServerPlayerSources();
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

    private static void taskTreeReportsGenericRecoveryForModdedDimensions() {
        EndgamePreparationDiagnostics.InventoryCounts counts = new EndgamePreparationDiagnostics.InventoryCounts(
                0, 0, 0, 0, 4, 0, 16
        );
        String summary = EndgamePreparationDiagnostics.summary("example:moon", counts);

        require(summary.contains("current_dimension=example:moon"),
                "task tree must preserve arbitrary current dimension ids");
        require(summary.contains("environment=observed_loaded_world"),
                "task tree must frame unknown dimensions as observed loaded world state");
        require(summary.contains("generic_dimension_recovery=loaded_portal_or_explore_or_owner_path_if_available"),
                "task tree must expose generic player-like recovery affordances");
        require(!summary.contains("unsupported_dimension_" + "report_status_only"),
                "task tree must not mark modded dimensions globally unsupported");
    }

    private static void taskTreeStatusLinesAreBoundedReadableAndTruthful() {
        EndgamePreparationDiagnostics.InventoryCounts counts = new EndgamePreparationDiagnostics.InventoryCounts(
                12, 2, 0, 4, 16, 2, 64
        );
        java.util.List<String> lines = EndgamePreparationDiagnostics.visibleStatusLines("minecraft:overworld", counts);

        require(lines.size() <= EndgameTaskTreeStatusFormatter.DEFAULT_MAX_LINES,
                "task-tree UI status must cap line count");
        require(lines.get(0).contains("active_task=diagnostic_snapshot"),
                "task-tree UI status must identify snapshots as diagnostics");
        require(lines.get(0).contains("status=not_queued"),
                "task-tree UI status must not pretend diagnostic snapshots are queued execution");
        boolean namesMissingPrimitive = false;
        boolean namesRecovery = false;
        for (String line : lines) {
            require(line.length() <= EndgameTaskTreeStatusFormatter.DEFAULT_MAX_LINE_LENGTH,
                    "task-tree UI status lines must be length-bounded");
            require(line.contains("=") && !line.contains(";"),
                    "task-tree UI status lines must be parseable key/value text");
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            require(!lower.contains("speedrun_success") && !lower.contains("dragon_success")
                            && !lower.contains("completed_dragon"),
                    "task-tree UI status must not overclaim speedrun, End, or dragon success");
            require(!lower.contains("/locate") && !lower.contains("/tp") && !lower.contains("/give")
                            && !lower.contains("gamerule") && !lower.contains("teleport"),
                    "task-tree UI status must not introduce admin command vocabulary");
            namesMissingPrimitive = namesMissingPrimitive || line.contains("MISSING_PRIMITIVE");
            namesRecovery = namesRecovery || line.contains("recovery") || line.contains("truncated=true");
        }
        require(namesMissingPrimitive, "task-tree UI status must expose missing primitives");
        require(namesRecovery, "task-tree UI status must expose recovery state or bounded truncation");
    }

    private static void viewerStatusLinesLabelServerPlayerSources() {
        EndgamePreparationDiagnostics.InventoryCounts counts = new EndgamePreparationDiagnostics.InventoryCounts(
                12, 2, 0, 4, 16, 2, 64
        );
        java.util.List<String> lines = EndgamePreparationDiagnostics.visibleViewerStatusLines("example:moon", counts);

        boolean namesViewerInventory = false;
        boolean namesCurrentViewerDimension = false;
        for (String line : lines) {
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            namesViewerInventory = namesViewerInventory || line.contains("source=viewer_inventory");
            namesCurrentViewerDimension = namesCurrentViewerDimension || line.contains("source=current_viewer_dimension");
            require(!lower.contains("selected_npc") && !lower.contains("npc_inventory")
                            && !lower.contains("queued_execution") && !lower.contains("active_npc"),
                    "viewer status lines must not claim selected NPC inventory or execution state");
        }
        require(namesViewerInventory, "viewer material counts must be labeled source=viewer_inventory");
        require(namesCurrentViewerDimension, "viewer dimensions must be labeled source=current_viewer_dimension");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
