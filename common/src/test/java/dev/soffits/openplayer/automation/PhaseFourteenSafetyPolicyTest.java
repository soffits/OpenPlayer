package dev.soffits.openplayer.automation;

public final class PhaseFourteenSafetyPolicyTest {
    private PhaseFourteenSafetyPolicyTest() {
    }

    public static void main(String[] args) {
        acceptsOnlyExplicitSafeVanillaInteractionIds();
        acceptsOnlyExplicitHostileAttackEntityIds();
    }

    private static void acceptsOnlyExplicitSafeVanillaInteractionIds() {
        require(PhaseFourteenSafetyPolicy.isSafeEmptyHandInteractBlockId("minecraft:lever"),
                "vanilla lever should be safe for empty-hand interaction");
        require(PhaseFourteenSafetyPolicy.isSafeEmptyHandInteractBlockId("minecraft:oak_trapdoor"),
                "wooden trapdoor should be safe for empty-hand interaction");
        require(PhaseFourteenSafetyPolicy.isSafeEmptyHandInteractBlockId("minecraft:spruce_fence_gate"),
                "wooden fence gate should be safe for empty-hand interaction");
        require(!PhaseFourteenSafetyPolicy.isSafeEmptyHandInteractBlockId("minecraft:iron_trapdoor"),
                "iron trapdoor must not bypass vanilla powered-only semantics");
        require(!PhaseFourteenSafetyPolicy.isSafeEmptyHandInteractBlockId("minecraft:oak_door"),
                "doors are out of scope for Phase 14 interaction");
        require(!PhaseFourteenSafetyPolicy.isSafeEmptyHandInteractBlockId("example:oak_trapdoor"),
                "modded/custom block ids must not be accepted by family name");
    }

    private static void acceptsOnlyExplicitHostileAttackEntityIds() {
        require(PhaseFourteenSafetyPolicy.isSafeExplicitAttackEntityTypeId("minecraft:zombie"),
                "zombie should be safe for explicit hostile attack selection");
        require(PhaseFourteenSafetyPolicy.isSafeExplicitAttackEntityTypeId("minecraft:skeleton"),
                "skeleton should be safe for explicit hostile attack selection");
        require(!PhaseFourteenSafetyPolicy.isSafeExplicitAttackEntityTypeId("minecraft:enderman"),
                "enderman is neutral and must not be an explicit attack target");
        require(!PhaseFourteenSafetyPolicy.isSafeExplicitAttackEntityTypeId("minecraft:zombified_piglin"),
                "zombified piglin is neutral and must not be an explicit attack target");
        require(!PhaseFourteenSafetyPolicy.isSafeExplicitAttackEntityTypeId("minecraft:villager"),
                "friendly entities must not be explicit attack targets");
        require(!PhaseFourteenSafetyPolicy.isSafeExplicitAttackEntityTypeId("example:zombie"),
                "modded/custom hostile-looking ids must not be accepted by suffix");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
