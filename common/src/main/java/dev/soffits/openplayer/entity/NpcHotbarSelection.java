package dev.soffits.openplayer.entity;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class NpcHotbarSelection {
    public static final int HOTBAR_SIZE = 9;

    private NpcHotbarSelection() {
    }

    public static boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot < HOTBAR_SIZE;
    }

    public static int validatedHotbarSlot(int slot) {
        if (isHotbarSlot(slot)) {
            return slot;
        }
        return 0;
    }

    public static <T> int firstMatchingSlot(List<T> hotbarItems, Predicate<T> predicate) {
        if (hotbarItems == null) {
            throw new IllegalArgumentException("hotbarItems cannot be null");
        }
        if (predicate == null) {
            throw new IllegalArgumentException("predicate cannot be null");
        }
        int slots = Math.min(HOTBAR_SIZE, hotbarItems.size());
        for (int slot = 0; slot < slots; slot++) {
            if (predicate.test(hotbarItems.get(slot))) {
                return slot;
            }
        }
        return -1;
    }

    public static <T> int bestScoredSlot(List<T> hotbarItems, ToDoubleFunction<T> scorer) {
        if (hotbarItems == null) {
            throw new IllegalArgumentException("hotbarItems cannot be null");
        }
        if (scorer == null) {
            throw new IllegalArgumentException("scorer cannot be null");
        }
        int bestSlot = -1;
        double bestScore = 0.0D;
        int slots = Math.min(HOTBAR_SIZE, hotbarItems.size());
        for (int slot = 0; slot < slots; slot++) {
            double score = scorer.applyAsDouble(hotbarItems.get(slot));
            if (Double.isFinite(score) && score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }
}
