package dev.soffits.openplayer.automation.survival;

public final class SurvivalIdlePolicy {
    private SurvivalIdlePolicy() {
    }

    public static SurvivalIdleAction choose(
            boolean allowWorldActions,
            boolean activeCommand,
            boolean queueEmpty,
            boolean cooldownReady,
            SurvivalDangerKind dangerKind,
            boolean lowHealth,
            boolean safeFoodAvailable,
            boolean selfDangerTarget,
            boolean ownerDangerTarget,
            boolean armorUpgradeAvailable
    ) {
        if (!allowWorldActions || activeCommand || !queueEmpty || !cooldownReady) {
            return SurvivalIdleAction.NONE;
        }
        if (dangerKind != null && dangerKind != SurvivalDangerKind.NONE) {
            return SurvivalIdleAction.AVOID_DANGER;
        }
        if (selfDangerTarget) {
            return SurvivalIdleAction.SELF_DEFENSE;
        }
        return SurvivalIdleAction.NONE;
    }
}
