package dev.soffits.openplayer.automation.navigation;

public record NavigationSpeedContext(
        double speed,
        double distanceBlocks,
        boolean movingTarget,
        boolean requiresSafety,
        boolean requiresPrecision
) {
    public NavigationSpeedContext {
        if (speed < 0.0D) {
            speed = 0.0D;
        }
        if (distanceBlocks < 0.0D) {
            distanceBlocks = 0.0D;
        }
    }
}
