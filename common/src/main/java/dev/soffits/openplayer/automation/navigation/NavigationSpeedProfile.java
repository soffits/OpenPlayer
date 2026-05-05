package dev.soffits.openplayer.automation.navigation;

import dev.soffits.openplayer.automation.policy.MovementProfile;

public final class NavigationSpeedProfile {
    public static final double CAREFUL_SPEED = 0.85D;
    public static final double WALK_SPEED = 1.0D;
    public static final double JOG_SPEED = 1.25D;
    public static final double RUN_SPEED = 1.55D;

    public static double computeEffectiveSpeed(int distanceBlocks, boolean movingTarget,
                                                boolean requiresSafety, boolean requiresPrecision,
                                                MovementProfile policy) {
        double baseSpeed = WALK_SPEED;
        if (requiresPrecision || distanceBlocks <= 3) {
            baseSpeed = CAREFUL_SPEED;
        } else if (requiresSafety && movingTarget) {
            baseSpeed = RUN_SPEED;
        } else if (requiresSafety) {
            baseSpeed = JOG_SPEED;
        } else if (movingTarget) {
            baseSpeed = distanceBlocks >= 10 ? RUN_SPEED : JOG_SPEED;
        } else if (distanceBlocks >= 16) {
            baseSpeed = RUN_SPEED;
        } else if (distanceBlocks >= 6) {
            baseSpeed = JOG_SPEED;
        }

        double maxFromPolicy = policy.canBreakObstacles() ? RUN_SPEED : 1.4D;
        if (policy.avoidHostiles()) {
            maxFromPolicy = Math.min(maxFromPolicy, requiresSafety ? RUN_SPEED : 1.45D);
        }
        if (!policy.canPlaceScaffold()) {
            maxFromPolicy = Math.min(maxFromPolicy, 1.35D);
        }

        baseSpeed = Math.min(baseSpeed, maxFromPolicy);
        return Math.max(0.65D, Math.min(RUN_SPEED, baseSpeed));
    }

    private NavigationSpeedProfile() {
    }
}
