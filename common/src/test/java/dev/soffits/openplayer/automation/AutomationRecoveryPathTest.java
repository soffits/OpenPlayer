package dev.soffits.openplayer.automation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AutomationRecoveryPathTest {
    private static final String SOURCE_PATH = "src/main/java/dev/soffits/openplayer/automation/VanillaAutomationBackend.java";

    private AutomationRecoveryPathTest() {
    }

    public static void main(String[] args) throws IOException {
        smeltRecoveryUsesLiveNpcInventory();
        pauseSuspendsNavigationWithoutCompletingRuntime();
        resetMemoryDoesNotClearExplorationMemory();
        exploreChunksRechecksWorldActionsBeforeNavigationExecution();
    }

    private static void smeltRecoveryUsesLiveNpcInventory() throws IOException {
        String source = Files.readString(sourcePath());
        int methodStart = source.indexOf("private void recoverActiveSmeltResources()");
        require(methodStart >= 0, "recoverActiveSmeltResources must exist");
        int methodEnd = source.indexOf("private boolean requiresNavigationProgress", methodStart);
        require(methodEnd > methodStart, "recoverActiveSmeltResources boundary must be detectable");

        String methodSource = source.substring(methodStart, methodEnd);
        require(methodSource.contains("entity.recoverFurnaceSmeltResources("),
                "smelt recovery must use the live NPC inventory method");
        require(!methodSource.contains("inventorySnapshot()"),
                "smelt recovery must not recover into an inventory snapshot");
    }

    private static void pauseSuspendsNavigationWithoutCompletingRuntime() throws IOException {
        String source = Files.readString(sourcePath());
        int pauseStart = source.indexOf("if (kind == IntentKind.PAUSE)");
        require(pauseStart >= 0, "PAUSE branch must exist");
        int pauseEnd = source.indexOf("if (kind == IntentKind.UNPAUSE)", pauseStart);
        require(pauseEnd > pauseStart, "PAUSE branch boundary must be detectable");
        String pauseSource = source.substring(pauseStart, pauseEnd);

        int suspendStart = source.indexOf("private void suspendNavigation()");
        require(suspendStart >= 0, "suspendNavigation must exist");
        int suspendEnd = source.indexOf("private void cancelNavigation", suspendStart);
        require(suspendEnd > suspendStart, "suspendNavigation boundary must be detectable");
        String suspendSource = source.substring(suspendStart, suspendEnd);

        require(pauseSource.contains("suspendNavigation();"), "PAUSE must suspend navigation");
        require(!pauseSource.contains("stopNavigation();"), "PAUSE must not complete navigation runtime");
        require(suspendSource.contains("entity.getNavigation().stop();"), "suspendNavigation must stop live path motion");
        require(suspendSource.contains("navigationRuntime.suspend();"), "suspendNavigation must preserve runtime telemetry");
        require(!suspendSource.contains("navigationRuntime.complete();"),
                "suspendNavigation must not mark navigation completed");
    }

    private static void resetMemoryDoesNotClearExplorationMemory() throws IOException {
        String source = Files.readString(sourcePath());
        int resetStart = source.indexOf("if (kind == IntentKind.RESET_MEMORY)");
        require(resetStart >= 0, "RESET_MEMORY branch must exist");
        int resetEnd = source.indexOf("if (kind == IntentKind.REPORT_STATUS)", resetStart);
        require(resetEnd > resetStart, "RESET_MEMORY branch boundary must be detectable");
        String resetSource = source.substring(resetStart, resetEnd);

        require(!resetSource.contains("loadedChunkExplorationMemory.clear();"),
                "RESET_MEMORY must not clear automation-local loaded chunk exploration memory");
        require(resetSource.contains("no automation-local memory was cleared"),
                "RESET_MEMORY must report that automation-local memory was not cleared");
    }

    private static void exploreChunksRechecksWorldActionsBeforeNavigationExecution() throws IOException {
        String source = Files.readString(sourcePath());
        int startExploreStart = source.indexOf("private void startExploreChunks(QueuedCommand command)");
        require(startExploreStart >= 0, "startExploreChunks must exist");
        int startExploreEnd = source.indexOf("private void continueExploreChunks", startExploreStart);
        require(startExploreEnd > startExploreStart, "startExploreChunks boundary must be detectable");
        String startExploreSource = source.substring(startExploreStart, startExploreEnd);

        int continueExploreStart = source.indexOf("private void continueExploreChunks(QueuedCommand command)");
        require(continueExploreStart >= 0, "continueExploreChunks must exist");
        int continueExploreEnd = source.indexOf("private boolean moveToExploreTarget", continueExploreStart);
        require(continueExploreEnd > continueExploreStart, "continueExploreChunks boundary must be detectable");
        String continueExploreSource = source.substring(continueExploreStart, continueExploreEnd);

        int reissueStart = source.indexOf("private void reissueActiveNavigation()");
        require(reissueStart >= 0, "reissueActiveNavigation must exist");
        int reissueEnd = source.indexOf("if (kind == IntentKind.PATROL)", reissueStart);
        require(reissueEnd > reissueStart, "EXPLORE_CHUNKS reissue boundary must be detectable");
        String reissueSource = source.substring(reissueStart, reissueEnd);

        requireOccursBefore(startExploreSource,
                "if (!entity.allowWorldActions())",
                "moveToExploreTarget(target.targetPos());",
                "startExploreChunks must re-check allowWorldActions before starting navigation");
        require(startExploreSource.contains("failActiveCommand(\"world_actions_disabled_before_explore\")"),
                "startExploreChunks must fail deterministically when world actions are disabled");
        requireOccursBefore(continueExploreSource,
                "if (!entity.allowWorldActions())",
                "navigationRuntime.updateDistance(distanceTo(target));",
                "continueExploreChunks must re-check allowWorldActions before continuing navigation");
        require(continueExploreSource.contains("failActiveCommand(\"world_actions_disabled_during_explore\")"),
                "continueExploreChunks must fail deterministically when world actions are disabled");
        requireOccursBefore(reissueSource,
                "if (!entity.allowWorldActions())",
                "moveToExploreTarget(activeCommand.explorationTarget());",
                "reissueActiveNavigation must re-check allowWorldActions before reissuing EXPLORE_CHUNKS navigation");
        require(reissueSource.contains("failActiveCommand(\"world_actions_disabled_before_explore\")"),
                "reissueActiveNavigation must fail deterministically when EXPLORE_CHUNKS reissue is disabled");
    }

    private static Path sourcePath() {
        Path projectDir = Path.of(System.getProperty("user.dir"));
        Path commonProjectSource = projectDir.resolve(SOURCE_PATH);
        if (Files.exists(commonProjectSource)) {
            return commonProjectSource;
        }
        return projectDir.resolve("common").resolve(SOURCE_PATH);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireOccursBefore(String source, String first, String second, String message) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        require(firstIndex >= 0, message + ": missing " + first);
        require(secondIndex >= 0, message + ": missing " + second);
        require(firstIndex < secondIndex, message);
    }
}
