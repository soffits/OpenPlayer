package dev.soffits.openplayer.automation.policy;

public final class MovementPolicyLoaderTest {
    private MovementPolicyLoaderTest() {
    }

    public static void main(String[] args) {
        bundledPolicyParsesIndependentBlockAndEntitySections();
        invalidPolicyFallsBackToDefault();
        profileCapsCannotEnableUnsafePermissions();
    }

    private static void bundledPolicyParsesIndependentBlockAndEntitySections() {
        MovementProfile policy = MovementPolicyLoader.effectivePolicy("openplayer:companion_safe");

        require(policy.id().toString().equals("openplayer:companion_safe"), "default policy id must be stable");
        require(policy.blocks().avoid().contains("minecraft:lava"), "block avoid list must include lava");
        require(!policy.blocks().avoid().contains("minecraft:creeper"), "block avoid list must not read entity avoid values");
        require(policy.entities().avoid().contains("minecraft:creeper"), "entity avoid list must include creeper");
        require(!policy.entities().avoid().contains("minecraft:lava"), "entity avoid list must not read block avoid values");
        require(policy.entities().neverAttack().contains("minecraft:player"), "entity neverAttack must include players");
    }

    private static void invalidPolicyFallsBackToDefault() {
        MovementProfile policy = MovementPolicyLoader.effectivePolicy("not a resource location");
        require(policy.id().toString().equals("openplayer:companion_safe"), "invalid policy ids must fall back to default");
    }

    private static void profileCapsCannotEnableUnsafePermissions() {
        MovementProfile requested = new MovementProfile(
                MovementPolicyLoader.DEFAULT_POLICY_ID,
                true,
                true,
                64,
                false,
                false,
                new BlockSafetyPolicy(
                        java.util.Set.of(),
                        java.util.Set.of(),
                        java.util.Set.of("minecraft:diamond_block")
                ),
                new EntitySafetyPolicy(
                        java.util.Set.of(),
                        java.util.Set.of("minecraft:villager"),
                        java.util.Set.of()
                )
        );

        MovementProfile bounded = requested.boundBy(MovementPolicyLoader.defaultPolicy());
        require(!bounded.canPlaceScaffold(), "server caps must prevent scaffold placement when disabled");
        require(bounded.maxFallDistance() == 3, "server caps must bound fall distance");
        require(bounded.avoidLiquids(), "server caps must preserve liquid avoidance");
        require(bounded.avoidHostiles(), "server caps must preserve hostile avoidance");
        require(bounded.blocks().neverBreak().contains("minecraft:chest"), "server caps must preserve protected blocks");
        require(!bounded.blocks().lowRiskBreakable().contains("minecraft:diamond_block"),
                "server caps must not allow arbitrary low-risk breakables");
        require(!bounded.entities().defendAgainst().contains("minecraft:villager"),
                "server caps must not allow arbitrary defense targets");
        require(bounded.entities().neverAttack().contains("minecraft:player"),
                "server caps must preserve never-attack player rule");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
