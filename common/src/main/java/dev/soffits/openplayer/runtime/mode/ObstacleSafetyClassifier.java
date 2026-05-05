package dev.soffits.openplayer.runtime.mode;

import dev.soffits.openplayer.automation.policy.MovementProfile;

public final class ObstacleSafetyClassifier {
    private ObstacleSafetyClassifier() {
    }

    public static ObstacleDecision classify(String blockId, MovementProfile profile,
                                            boolean allowWorldActions, boolean taskAllowsObstacleClearing) {
        if (blockId == null || blockId.isBlank()) {
            return new ObstacleDecision(ObstacleDecisionKind.OBSERVE_DANGEROUS, "missing block id");
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile cannot be null");
        }
        String id = blockId.trim();
        if (profile.blocks().neverBreak().contains(id)) {
            return new ObstacleDecision(ObstacleDecisionKind.OBSERVE_DANGEROUS, "block is never-break by policy");
        }
        if (!allowWorldActions || !taskAllowsObstacleClearing || !profile.canBreakObstacles()) {
            return new ObstacleDecision(ObstacleDecisionKind.OBSERVE_BLOCKED, "obstacle clearing not permitted");
        }
        if (profile.blocks().lowRiskBreakable().contains(id)) {
            return new ObstacleDecision(ObstacleDecisionKind.CLEAR_LOW_RISK, "low-risk policy match");
        }
        return new ObstacleDecision(ObstacleDecisionKind.OBSERVE_DANGEROUS, "obstacle is ambiguous or valuable");
    }
}
