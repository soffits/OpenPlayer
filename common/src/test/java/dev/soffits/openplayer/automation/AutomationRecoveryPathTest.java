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
