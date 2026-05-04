package dev.soffits.openplayer.automation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AutomationRecoveryPathTest {
    private static final String SOURCE_PATH = "src/main/java/dev/soffits/openplayer/automation/VanillaAutomationBackend.java";

    private AutomationRecoveryPathTest() {
    }

    public static void main(String[] args) throws IOException {
        pauseSuspendsNavigationWithoutCompletingRuntime();
        resetMemoryDoesNotClearExplorationMemory();
        primitiveToolSubmitUsesLiveNpcExecutorWithoutRuntimeRecursion();
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

    private static void primitiveToolSubmitUsesLiveNpcExecutorWithoutRuntimeRecursion() throws IOException {
        String source = Files.readString(sourcePath());
        int submitStart = source.indexOf("public AutomationCommandResult submit(CommandIntent intent)");
        require(submitStart >= 0, "submit branch must exist");
        int submitEnd = source.indexOf("private AutomationCommandResult submitPrimitiveIntent", submitStart);
        require(submitEnd > submitStart, "submit branch boundary must be detectable");
        String submitSource = source.substring(submitStart, submitEnd);

        require(submitSource.contains("new AICoreNpcToolExecutor(entity, aicoreEventBus,"),
                "primitive tool submit must route through the reviewed live NPC executor");
        require(submitSource.contains("submitPrimitiveIntent(primitiveIntent)"),
                "live NPC executor command fallback must submit directly to primitive intent handling");
        require(!submitSource.contains("new MinecraftPrimitiveToolExecutor"),
                "primitive tool submit must not use the facade-only primitive executor in the live backend");
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

}
