package dev.soffits.openplayer.automation.survival;

public final class SurvivalDangerPolicy {
    private SurvivalDangerPolicy() {
    }

    public static SurvivalDangerKind immediateDanger(boolean onFire, boolean inLava, boolean projectileNearby) {
        if (inLava) {
            return SurvivalDangerKind.LAVA;
        }
        if (onFire) {
            return SurvivalDangerKind.FIRE;
        }
        if (projectileNearby) {
            return SurvivalDangerKind.PROJECTILE;
        }
        return SurvivalDangerKind.NONE;
    }
}
