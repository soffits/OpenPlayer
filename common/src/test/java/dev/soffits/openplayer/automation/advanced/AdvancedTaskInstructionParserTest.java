package dev.soffits.openplayer.automation.advanced;

public final class AdvancedTaskInstructionParserTest {
    private AdvancedTaskInstructionParserTest() {
    }

    public static void main(String[] args) {
        parsesStrictLoadedSearchInstruction();
        rejectsInvalidLoadedSearchInstruction();
    }

    private static void parsesStrictLoadedSearchInstruction() {
        AdvancedTaskInstructionParser.LoadedSearchInstruction defaultRadius =
                AdvancedTaskInstructionParser.parseLoadedSearchOrNull("minecraft:oak_log");
        require(defaultRadius != null, "Loaded search should accept exact resource id");
        require(defaultRadius.resourceId().equals("minecraft:oak_log"), "Loaded search should preserve resource id");
        require(defaultRadius.radius() == AdvancedTaskInstructionParser.DEFAULT_RADIUS,
                "Loaded search should use default radius");

        AdvancedTaskInstructionParser.LoadedSearchInstruction cappedRadius =
                AdvancedTaskInstructionParser.parseLoadedSearchOrNull("minecraft:zombie 64");
        require(cappedRadius != null, "Loaded search should accept positive radius");
        require(cappedRadius.radius() == AdvancedTaskInstructionParser.MAX_RADIUS,
                "Loaded search should cap radius to max loaded reconnaissance radius");
    }

    private static void rejectsInvalidLoadedSearchInstruction() {
        require(AdvancedTaskInstructionParser.parseLoadedSearchOrNull("") == null,
                "Loaded search should reject blank instruction");
        require(AdvancedTaskInstructionParser.parseLoadedSearchOrNull("oak_log") == null,
                "Loaded search should reject non-namespaced ids");
        require(AdvancedTaskInstructionParser.parseLoadedSearchOrNull("minecraft:oak_log 0") == null,
                "Loaded search should reject zero radius");
        require(AdvancedTaskInstructionParser.parseLoadedSearchOrNull("minecraft:oak_log near") == null,
                "Loaded search should reject non-numeric radius");
        require(AdvancedTaskInstructionParser.parseLoadedSearchOrNull("minecraft:oak_log 8 extra") == null,
                "Loaded search should reject extra tokens");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
