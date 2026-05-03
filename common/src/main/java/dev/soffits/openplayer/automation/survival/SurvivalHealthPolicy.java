package dev.soffits.openplayer.automation.survival;

public final class SurvivalHealthPolicy {
    private static final float LOW_HEALTH_FRACTION = 0.60F;
    private static final float DANGEROUS_HEALTH_FRACTION = 0.25F;

    private SurvivalHealthPolicy() {
    }

    public static boolean isLowHealth(float health, float maxHealth) {
        return maxHealth > 0.0F && health > 0.0F && health <= maxHealth * LOW_HEALTH_FRACTION;
    }

    public static boolean isDangerouslyLowHealth(float health, float maxHealth) {
        return maxHealth > 0.0F && health > 0.0F && health <= maxHealth * DANGEROUS_HEALTH_FRACTION;
    }
}
