package dev.soffits.openplayer.automation.policy;

import java.util.Set;

public record EntitySafetyPolicy(
        Set<String> avoid,
        Set<String> defendAgainst,
        Set<String> neverAttack
) {
    public EntitySafetyPolicy {
        avoid = Set.copyOf(avoid == null ? Set.of() : avoid);
        defendAgainst = Set.copyOf(defendAgainst == null ? Set.of() : defendAgainst);
        neverAttack = Set.copyOf(neverAttack == null ? Set.of() : neverAttack);
    }

    public static EntitySafetyPolicy boundedDefault() {
        return new EntitySafetyPolicy(
                Set.of("minecraft:creeper", "minecraft:skeleton"),
                Set.of("minecraft:zombie", "minecraft:spider", "minecraft:skeleton"),
                Set.of("minecraft:villager", "minecraft:iron_golem", "minecraft:player")
        );
    }

    EntitySafetyPolicy boundBy(EntitySafetyPolicy caps) {
        java.util.LinkedHashSet<String> boundedNeverAttack = new java.util.LinkedHashSet<>(neverAttack);
        boundedNeverAttack.addAll(caps.neverAttack);
        java.util.LinkedHashSet<String> boundedDefendAgainst = new java.util.LinkedHashSet<>(defendAgainst);
        boundedDefendAgainst.retainAll(caps.defendAgainst);
        return new EntitySafetyPolicy(avoid, boundedDefendAgainst, boundedNeverAttack);
    }
}
