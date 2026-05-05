package dev.soffits.openplayer.automation.policy;

import java.util.Set;

public record BlockSafetyPolicy(
        Set<String> neverBreak,
        Set<String> avoid,
        Set<String> lowRiskBreakable
) {
    public BlockSafetyPolicy {
        neverBreak = Set.copyOf(neverBreak == null ? Set.of() : neverBreak);
        avoid = Set.copyOf(avoid == null ? Set.of() : avoid);
        lowRiskBreakable = Set.copyOf(lowRiskBreakable == null ? Set.of() : lowRiskBreakable);
    }

    public static BlockSafetyPolicy boundedDefault() {
        return new BlockSafetyPolicy(
                Set.of("minecraft:chest", "minecraft:furnace", "minecraft:crafting_table", "minecraft:bed"),
                Set.of("minecraft:lava", "minecraft:fire", "minecraft:cactus", "minecraft:magma_block"),
                Set.of("minecraft:snow", "minecraft:short_grass", "minecraft:tall_grass", "#minecraft:leaves")
        );
    }

    BlockSafetyPolicy boundBy(BlockSafetyPolicy caps) {
        return new BlockSafetyPolicy(
                union(neverBreak, caps.neverBreak),
                union(avoid, caps.avoid),
                intersection(lowRiskBreakable, caps.lowRiskBreakable)
        );
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>(first);
        values.addAll(second);
        return Set.copyOf(values);
    }

    private static Set<String> intersection(Set<String> first, Set<String> second) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>(first);
        values.retainAll(second);
        return Set.copyOf(values);
    }
}
