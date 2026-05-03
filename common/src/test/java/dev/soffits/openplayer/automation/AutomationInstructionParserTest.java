package dev.soffits.openplayer.automation;

public final class AutomationInstructionParserTest {
    private AutomationInstructionParserTest() {
    }

    public static void main(String[] args) {
        parsesFiniteCoordinates();
        rejectsMalformedCoordinates();
        rejectsTooFarPatrolCoordinates();
        clampsOptionalRadius();
        rejectsInvalidRadius();
        identifiesBlankInstructions();
        rejectsNonBlankInstructions();
        parsesGotoInstructions();
        rejectsInvalidGotoInstructions();
    }

    private static void parsesFiniteCoordinates() {
        AutomationInstructionParser.Coordinate coordinate = AutomationInstructionParser.parseCoordinate("1 64 -2.5");
        require(coordinate.x() == 1.0D, "x coordinate must parse");
        require(coordinate.y() == 64.0D, "y coordinate must parse");
        require(coordinate.z() == -2.5D, "z coordinate must parse");
    }

    private static void rejectsMalformedCoordinates() {
        require(AutomationInstructionParser.parseCoordinateOrNull("1 2") == null,
                "coordinates must require exactly three parts");
        require(AutomationInstructionParser.parseCoordinateOrNull("1 NaN 3") == null,
                "coordinates must reject non-finite values");
        require(AutomationInstructionParser.parseCoordinateOrNull("1 two 3") == null,
                "coordinates must reject non-numeric values");
    }

    private static void rejectsTooFarPatrolCoordinates() {
        require(AutomationInstructionParser.parseBoundedCoordinateOrNull("3 4 0", 0.0D, 0.0D, 0.0D, 5.0D) != null,
                "bounded coordinate at max range must be accepted");
        require(AutomationInstructionParser.parseBoundedCoordinateOrNull("6 0 0", 0.0D, 0.0D, 0.0D, 5.0D) == null,
                "bounded coordinate beyond max range must be rejected");
    }

    private static void clampsOptionalRadius() {
        require(AutomationInstructionParser.parseOptionalRadiusOrNegative("", 12.0D, 16.0D) == 12.0D,
                "blank radius must use default");
        require(AutomationInstructionParser.parseOptionalRadiusOrNegative("30", 12.0D, 16.0D) == 16.0D,
                "radius must clamp to max");
    }

    private static void rejectsInvalidRadius() {
        require(AutomationInstructionParser.parseOptionalRadiusOrNegative("0", 12.0D, 16.0D) < 0.0D,
                "zero radius must reject");
        require(AutomationInstructionParser.parseOptionalRadiusOrNegative("wide", 12.0D, 16.0D) < 0.0D,
                "non-numeric radius must reject");
    }

    private static void identifiesBlankInstructions() {
        require(AutomationInstructionParser.isBlankInstruction(null),
                "null instruction must count as blank for strict blank-only intents");
        require(AutomationInstructionParser.isBlankInstruction(""),
                "empty instruction must count as blank");
        require(AutomationInstructionParser.isBlankInstruction("   \t\n"),
                "whitespace instruction must count as blank");
    }

    private static void rejectsNonBlankInstructions() {
        require(!AutomationInstructionParser.isBlankInstruction("status"),
                "nonblank instruction must reject for strict blank-only intents");
        require(!AutomationInstructionParser.isBlankInstruction("  status  "),
                "trimmed nonblank instruction must reject for strict blank-only intents");
    }

    private static void parsesGotoInstructions() {
        AutomationInstructionParser.GotoInstruction coordinate = AutomationInstructionParser.parseGotoInstructionOrNull(
                "1 64 -2", 16.0D, 32.0D
        );
        require(coordinate != null && coordinate.kind() == AutomationInstructionParser.GotoTargetKind.COORDINATE,
                "GOTO coordinate syntax must parse");
        AutomationInstructionParser.GotoInstruction owner = AutomationInstructionParser.parseGotoInstructionOrNull(
                "owner", 16.0D, 32.0D
        );
        require(owner != null && owner.kind() == AutomationInstructionParser.GotoTargetKind.OWNER,
                "GOTO owner syntax must parse");
        AutomationInstructionParser.GotoInstruction block = AutomationInstructionParser.parseGotoInstructionOrNull(
                "block minecraft:oak_log 24", 16.0D, 32.0D
        );
        require(block != null && block.kind() == AutomationInstructionParser.GotoTargetKind.BLOCK,
                "GOTO block syntax must parse");
        require(block.radius() == 24.0D, "GOTO block radius must parse");
        AutomationInstructionParser.GotoInstruction entity = AutomationInstructionParser.parseGotoInstructionOrNull(
                "entity minecraft:zombie", 16.0D, 32.0D
        );
        require(entity != null && entity.kind() == AutomationInstructionParser.GotoTargetKind.ENTITY,
                "GOTO entity syntax must parse");
        require(entity.radius() == 16.0D, "GOTO entity blank radius must use default");
    }

    private static void rejectsInvalidGotoInstructions() {
        require(AutomationInstructionParser.parseGotoInstructionOrNull("", 16.0D, 32.0D) == null,
                "GOTO must reject blank instruction");
        require(AutomationInstructionParser.parseGotoInstructionOrNull("home", 16.0D, 32.0D) == null,
                "GOTO must reject fuzzy target names");
        require(AutomationInstructionParser.parseGotoInstructionOrNull("block oak log", 16.0D, 32.0D) == null,
                "GOTO block must reject non-id target");
        require(AutomationInstructionParser.parseGotoInstructionOrNull("entity minecraft:zombie far", 16.0D, 32.0D) == null,
                "GOTO entity must reject nonnumeric radius");
        require(AutomationInstructionParser.parseGotoInstructionOrNull("entity minecraft:zombie 0", 16.0D, 32.0D) == null,
                "GOTO entity must reject nonpositive radius");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
