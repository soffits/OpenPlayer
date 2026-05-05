package dev.soffits.openplayer.automation;

public final class InteractionSafetyPolicyTest {
    private InteractionSafetyPolicyTest() {
    }

    public static void main(String[] args) {
        acceptsOnlyExplicitSafeVanillaInteractionIds();
        acceptsOnlyExplicitHostileAttackEntityIds();
    }

    private static void acceptsOnlyExplicitSafeVanillaInteractionIds() {
        require(InteractionSafetyPolicy.isSafeEmptyHandInteractBlockId("minecraft:lever"),
                "vanilla lever should be safe for empty-hand interaction");
        require(InteractionSafetyPolicy.isSafeEmptyHandInteractBlockId("minecraft:oak_trapdoor"),
                "wooden trapdoor should be safe for empty-hand interaction");
        require(InteractionSafetyPolicy.isSafeEmptyHandInteractBlockId("minecraft:spruce_fence_gate"),
                "wooden fence gate should be safe for empty-hand interaction");
        require(!InteractionSafetyPolicy.isSafeEmptyHandInteractBlockId("minecraft:iron_trapdoor"),
                "iron trapdoor must not bypass vanilla powered-only semantics");
        require(!InteractionSafetyPolicy.isSafeEmptyHandInteractBlockId("minecraft:oak_door"),
                "doors remain unsupported until reviewed interaction semantics exist");
        require(!InteractionSafetyPolicy.isSafeEmptyHandInteractBlockId("example:oak_trapdoor"),
                "modded/custom block ids must not be accepted by family name");
    }

    private static void acceptsOnlyExplicitHostileAttackEntityIds() {
        require(InteractionSafetyPolicy.isSafeExplicitAttackEntityTypeId("minecraft:zombie"),
                "zombie should be safe for explicit hostile attack selection");
        require(InteractionSafetyPolicy.isSafeExplicitAttackEntityTypeId("minecraft:skeleton"),
                "skeleton should be safe for explicit hostile attack selection");
        require(!InteractionSafetyPolicy.isSafeExplicitAttackEntityTypeId("minecraft:enderman"),
                "enderman is neutral and must not be an explicit attack target");
        require(!InteractionSafetyPolicy.isSafeExplicitAttackEntityTypeId("minecraft:zombified_piglin"),
                "zombified piglin is neutral and must not be an explicit attack target");
        require(!InteractionSafetyPolicy.isSafeExplicitAttackEntityTypeId("minecraft:villager"),
                "friendly entities must not be explicit attack targets");
        require(!InteractionSafetyPolicy.isSafeExplicitAttackEntityTypeId("example:zombie"),
                "modded/custom hostile-looking ids must not be accepted by suffix");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
