package dev.soffits.openplayer.automation;

public final class InteractionInstructionParserTest {
    private InteractionInstructionParserTest() {
    }

    public static void main(String[] args) {
        parsesSafeBlockSyntax();
        parsesEntitySyntaxForCapabilityAdapters();
        rejectsUnsupportedFreeFormSyntax();
    }

    private static void parsesSafeBlockSyntax() {
        InteractionInstruction instruction = InteractionInstructionParser.parseOrNull("block 1 64 -2");
        require(instruction != null, "block syntax should parse");
        require(instruction.kind() == InteractionInstruction.InteractionTargetKind.BLOCK,
                "block syntax should produce a block target");
        require(instruction.coordinate().x() == 1.0D, "block x coordinate should parse");
        require(InteractionInstructionParser.parseOrNull("block 1 64 -2 lever") == null,
                "block action suffix must stay unsupported until an adapter needs it");
    }

    private static void parsesEntitySyntaxForCapabilityAdapters() {
        InteractionInstruction byType = InteractionInstructionParser.parseOrNull("entity minecraft:villager 4");
        require(byType != null, "entity type syntax should parse for capability adapters");
        require(byType.kind() == InteractionInstruction.InteractionTargetKind.ENTITY,
                "entity type syntax should produce an entity target");
        require(!byType.targetsUuid(), "entity type syntax should not be treated as UUID targeting");
        InteractionInstruction byUuid = InteractionInstructionParser.parseOrNull(
                "entity 123e4567-e89b-12d3-a456-426614174000"
        );
        require(byUuid != null, "entity UUID syntax should parse for capability adapters");
        require(byUuid.targetsUuid(), "entity UUID syntax should expose UUID targeting");
    }

    private static void rejectsUnsupportedFreeFormSyntax() {
        require(InteractionInstructionParser.parseOrNull("") == null, "blank interaction should reject");
        require(InteractionInstructionParser.parseOrNull("right_click door") == null,
                "free-form right-click text must reject");
        require(InteractionInstructionParser.parseOrNull("block 1 64") == null, "short coordinates should reject");
        require(InteractionInstructionParser.parseOrNull("entity villager") == null,
                "non-namespaced entity ids should reject");
        require(InteractionInstructionParser.parseOrNull("entity minecraft:villager 0") == null,
                "zero radius should reject");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
