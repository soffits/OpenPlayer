package dev.soffits.openplayer.automation.advanced;

public final class AdvancedTaskInstructionParserTest {
    private AdvancedTaskInstructionParserTest() {
    }

    public static void main(String[] args) {
        parsesStrictLoadedSearchInstruction();
        rejectsInvalidLoadedSearchInstruction();
        parsesExploreChunksInstruction();
        rejectsInvalidExploreChunksInstruction();
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

    private static void parsesExploreChunksInstruction() {
        AdvancedTaskInstructionParser.ExploreChunksInstruction defaultInstruction =
                AdvancedTaskInstructionParser.parseExploreChunksOrNull("");
        require(defaultInstruction != null, "Explore chunks should accept blank instruction");
        require(defaultInstruction.radius() == AdvancedTaskInstructionParser.EXPLORE_DEFAULT_RADIUS,
                "Explore chunks should use default radius");
        require(defaultInstruction.steps() == AdvancedTaskInstructionParser.EXPLORE_DEFAULT_STEPS,
                "Explore chunks should use default steps");
        require(!defaultInstruction.resetOnly(), "Blank explore chunks instruction should navigate");

        AdvancedTaskInstructionParser.ExploreChunksInstruction keyValueInstruction =
                AdvancedTaskInstructionParser.parseExploreChunksOrNull("radius=64 steps=12");
        require(keyValueInstruction != null, "Explore chunks should accept key/value syntax");
        require(keyValueInstruction.radius() == AdvancedTaskInstructionParser.EXPLORE_MAX_RADIUS,
                "Explore chunks radius should be bounded");
        require(keyValueInstruction.steps() == AdvancedTaskInstructionParser.EXPLORE_MAX_STEPS,
                "Explore chunks steps should be bounded");

        AdvancedTaskInstructionParser.ExploreChunksInstruction resetInstruction =
                AdvancedTaskInstructionParser.parseExploreChunksOrNull("reset");
        require(resetInstruction != null && resetInstruction.resetOnly(), "Explore chunks should accept reset");
    }

    private static void rejectsInvalidExploreChunksInstruction() {
        require(AdvancedTaskInstructionParser.parseExploreChunksOrNull("north") == null,
                "Explore chunks should reject free-form directions");
        require(AdvancedTaskInstructionParser.parseExploreChunksOrNull("radius=0") == null,
                "Explore chunks should reject zero radius");
        require(AdvancedTaskInstructionParser.parseExploreChunksOrNull("steps=0") == null,
                "Explore chunks should reject zero steps");
        require(AdvancedTaskInstructionParser.parseExploreChunksOrNull("radius=16 direction=north") == null,
                "Explore chunks should reject unsupported keys");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
