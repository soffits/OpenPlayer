package dev.soffits.openplayer.automation.advanced;

public final class AdvancedTaskInstructionParserTest {
    private AdvancedTaskInstructionParserTest() {
    }

    public static void main(String[] args) {
        parsesStrictLoadedSearchInstruction();
        rejectsInvalidLoadedSearchInstruction();
        parsesExploreChunksInstruction();
        rejectsInvalidExploreChunksInstruction();
        parsesLocateStructureInstruction();
        rejectsInvalidLocateStructureInstruction();
        parsesPortalTravelInstructions();
        rejectsInvalidPortalTravelInstructions();
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

    private static void parsesLocateStructureInstruction() {
        AdvancedTaskInstructionParser.LocateStructureInstruction defaultRadius =
                AdvancedTaskInstructionParser.parseLocateStructureOrNull("minecraft:village");
        require(defaultRadius != null, "Locate structure should accept exact structure id");
        require(defaultRadius.structureId().equals("minecraft:village"), "Locate structure should preserve structure id");
        require(defaultRadius.radius() == AdvancedTaskInstructionParser.STRUCTURE_DEFAULT_RADIUS,
                "Locate structure should use default radius");

        AdvancedTaskInstructionParser.LocateStructureInstruction cappedRadius =
                AdvancedTaskInstructionParser.parseLocateStructureOrNull("minecraft:village 128 source=loaded");
        require(cappedRadius != null, "Locate structure should accept source=loaded and positive radius");
        require(cappedRadius.radius() == AdvancedTaskInstructionParser.STRUCTURE_MAX_RADIUS,
                "Locate structure radius should be capped to loaded-only max");

        AdvancedTaskInstructionParser.LocateStructureInstruction reorderedSource =
                AdvancedTaskInstructionParser.parseLocateStructureOrNull("minecraft:village source=loaded 16");
        require(reorderedSource != null && reorderedSource.radius() == 16.0D,
                "Locate structure should accept source=loaded before radius");
    }

    private static void rejectsInvalidLocateStructureInstruction() {
        require(AdvancedTaskInstructionParser.parseLocateStructureOrNull("") == null,
                "Locate structure should reject blank instruction");
        require(AdvancedTaskInstructionParser.parseLocateStructureOrNull("village") == null,
                "Locate structure should reject non-namespaced ids");
        require(AdvancedTaskInstructionParser.parseLocateStructureOrNull("minecraft:village 0") == null,
                "Locate structure should reject zero radius");
        require(AdvancedTaskInstructionParser.parseLocateStructureOrNull("minecraft:village near") == null,
                "Locate structure should reject non-numeric radius");
        require(AdvancedTaskInstructionParser.parseLocateStructureOrNull("minecraft:village 16 source=generated") == null,
                "Locate structure should reject unsupported sources");
        require(AdvancedTaskInstructionParser.parseLocateStructureOrNull("minecraft:village 16 8") == null,
                "Locate structure should reject duplicate radii");
    }

    private static void parsesPortalTravelInstructions() {
        AdvancedTaskInstructionParser.PortalTravelInstruction blankUse =
                AdvancedTaskInstructionParser.parseUsePortalOrNull("");
        require(blankUse != null, "USE_PORTAL should accept blank instruction");
        require(blankUse.radius() == AdvancedTaskInstructionParser.PORTAL_DEFAULT_RADIUS,
                "USE_PORTAL should use default portal radius");
        require(blankUse.targetDimensionId() == null && !blankUse.explicitTarget(),
                "USE_PORTAL blank target should mean any portal transition");
        require(Boolean.FALSE.equals(blankUse.build()) && !blankUse.explicitBuild(),
                "USE_PORTAL should default build=false");

        AdvancedTaskInstructionParser.PortalTravelInstruction explicitUse =
                AdvancedTaskInstructionParser.parseUsePortalOrNull(
                        "radius=12 target=minecraft:the_nether build=true"
                );
        require(explicitUse != null, "USE_PORTAL should accept strict key/value syntax");
        require(explicitUse.radius() == 12.0D, "USE_PORTAL should preserve bounded radius");
        require(explicitUse.targetDimensionId().equals("minecraft:the_nether") && explicitUse.explicitTarget(),
                "USE_PORTAL should preserve supported target dimension");
        require(Boolean.TRUE.equals(explicitUse.build()) && explicitUse.explicitBuild(),
                "USE_PORTAL should preserve explicit build=true");

        AdvancedTaskInstructionParser.PortalTravelInstruction moddedUse =
                AdvancedTaskInstructionParser.parseUsePortalOrNull(
                        "radius=12 target=example:moon build=false"
                );
        require(moddedUse != null, "USE_PORTAL should accept arbitrary ResourceLocation target dimensions");
        require(moddedUse.targetDimensionId().equals("example:moon") && moddedUse.explicitTarget(),
                "USE_PORTAL should preserve modded target dimension ids for observed portal travel");

        AdvancedTaskInstructionParser.PortalTravelInstruction blankTravel =
                AdvancedTaskInstructionParser.parseTravelNetherOrNull(" ");
        require(blankTravel != null, "TRAVEL_NETHER should accept blank instruction");
        require(blankTravel.targetDimensionId().equals("minecraft:the_nether"),
                "TRAVEL_NETHER parser should default target to Nether");
        require(blankTravel.build() == null && !blankTravel.explicitBuild(),
                "TRAVEL_NETHER omitted build should remain inventory-dependent");

        AdvancedTaskInstructionParser.PortalTravelInstruction explicitTravel =
                AdvancedTaskInstructionParser.parseTravelNetherOrNull("build=false radius=24");
        require(explicitTravel != null && explicitTravel.radius() == 24.0D,
                "TRAVEL_NETHER should accept bounded key/value radius");
        require(Boolean.FALSE.equals(explicitTravel.build()), "TRAVEL_NETHER should preserve explicit build=false");
    }

    private static void rejectsInvalidPortalTravelInstructions() {
        require(AdvancedTaskInstructionParser.parseUsePortalOrNull("go to the nether") == null,
                "USE_PORTAL should reject free-form prose");
        require(AdvancedTaskInstructionParser.parseUsePortalOrNull("radius=25") == null,
                "USE_PORTAL should reject huge radius instead of capping");
        require(AdvancedTaskInstructionParser.parseUsePortalOrNull("radius=-1") == null,
                "USE_PORTAL should reject negative radius");
        require(AdvancedTaskInstructionParser.parseUsePortalOrNull("target=not_a_resource_location") == null,
                "USE_PORTAL should reject invalid dimension ids");
        require(AdvancedTaskInstructionParser.parseUsePortalOrNull("build=yes") == null,
                "USE_PORTAL should reject non-boolean build");
        require(AdvancedTaskInstructionParser.parseUsePortalOrNull("radius=12 speed=fast") == null,
                "USE_PORTAL should reject unknown keys");
        require(AdvancedTaskInstructionParser.parseTravelNetherOrNull("target=minecraft:overworld") == null,
                "TRAVEL_NETHER should reject target key");
        require(AdvancedTaskInstructionParser.parseTravelNetherOrNull("radius=0") == null,
                "TRAVEL_NETHER should reject zero radius");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
