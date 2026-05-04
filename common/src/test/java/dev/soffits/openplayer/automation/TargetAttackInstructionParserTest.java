package dev.soffits.openplayer.automation;

public final class TargetAttackInstructionParserTest {
    private TargetAttackInstructionParserTest() {
    }

    public static void main(String[] args) {
        parsesTypeAndUuidTargets();
        capsRadius();
        rejectsUnsafeSyntax();
    }

    private static void parsesTypeAndUuidTargets() {
        TargetAttackInstruction byType = TargetAttackInstructionParser.parseOrNull("minecraft:zombie 12");
        require(byType != null, "entity type target should parse");
        require(!byType.targetsUuid(), "entity type target should not be UUID classified");
        TargetAttackInstruction withPrefix = TargetAttackInstructionParser.parseOrNull("entity minecraft:skeleton");
        require(withPrefix != null, "entity-prefixed type target should parse");
        require(withPrefix.radius() == TargetAttackInstructionParser.DEFAULT_RADIUS,
                "blank radius should use default radius");
        TargetAttackInstruction byUuid = TargetAttackInstructionParser.parseOrNull(
                "entity 123e4567-e89b-12d3-a456-426614174000 8"
        );
        require(byUuid != null, "UUID target should parse");
        require(byUuid.targetsUuid(), "UUID target should be UUID classified");
    }

    private static void capsRadius() {
        TargetAttackInstruction instruction = TargetAttackInstructionParser.parseOrNull("minecraft:zombie 200");
        require(instruction != null, "large finite radius should parse");
        require(instruction.radius() == TargetAttackInstructionParser.MAX_RADIUS, "radius should be capped");
    }

    private static void rejectsUnsafeSyntax() {
        require(TargetAttackInstructionParser.parseOrNull("") == null, "blank target should reject");
        require(TargetAttackInstructionParser.parseOrNull("zombie") == null,
                "non-namespaced target should reject");
        require(TargetAttackInstructionParser.parseOrNull("minecraft:zombie 0") == null,
                "zero radius should reject");
        require(TargetAttackInstructionParser.parseOrNull("minecraft:zombie 12 extra") == null,
                "extra text should reject");
        require(TargetAttackInstructionParser.parseOrNull("attack nearest zombie") == null,
                "free-form attack text should reject");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
