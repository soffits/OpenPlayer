package dev.soffits.openplayer.entity;

import java.util.List;
import java.util.function.ToDoubleFunction;

public final class NpcEquipmentSelection {
    private NpcEquipmentSelection() {
    }

    public static boolean shouldReplaceArmor(double candidateScore, double currentScore) {
        return Double.isFinite(candidateScore)
                && Double.isFinite(currentScore)
                && candidateScore > 0.0D
                && candidateScore > currentScore;
    }

    public static <T> int bestReplacementSlot(List<T> items, ToDoubleFunction<T> scorer, double currentScore) {
        if (items == null) {
            throw new IllegalArgumentException("items cannot be null");
        }
        if (scorer == null) {
            throw new IllegalArgumentException("scorer cannot be null");
        }
        int bestSlot = -1;
        double bestScore = currentScore;
        for (int slot = 0; slot < items.size(); slot++) {
            double score = scorer.applyAsDouble(items.get(slot));
            if (shouldReplaceArmor(score, bestScore)) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }
}
