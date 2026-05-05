package dev.soffits.openplayer.runtime.mode;

import dev.soffits.openplayer.automation.policy.MovementProfile;

public record ModeSchedulerConfig(
        boolean autonomousPlannerEnabled,
        boolean allowWorldActions,
        boolean taskAllowsItemCollection,
        boolean taskAllowsObstacleClearing,
        boolean taskAllowsCombat,
        MovementProfile movementProfile
) {
    public ModeSchedulerConfig {
        if (movementProfile == null) {
            throw new IllegalArgumentException("movementProfile cannot be null");
        }
    }
}
