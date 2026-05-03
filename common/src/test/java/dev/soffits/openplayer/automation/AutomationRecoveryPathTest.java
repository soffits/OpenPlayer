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
