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

    EntitySafetyPolicy boundBy(EntitySafetyPolicy caps) {
        java.util.LinkedHashSet<String> boundedNeverAttack = new java.util.LinkedHashSet<>(neverAttack);
        boundedNeverAttack.addAll(caps.neverAttack);
        java.util.LinkedHashSet<String> boundedDefendAgainst = new java.util.LinkedHashSet<>(defendAgainst);
        boundedDefendAgainst.retainAll(caps.defendAgainst);
        return new EntitySafetyPolicy(avoid, boundedDefendAgainst, boundedNeverAttack);
    }
}
