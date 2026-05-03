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
        if (lowHealth && safeFoodAvailable) {
            return SurvivalIdleAction.EAT_SAFE_FOOD;
        }
        if (selfDangerTarget) {
            return SurvivalIdleAction.SELF_DEFENSE;
        }
        if (ownerDangerTarget) {
            return SurvivalIdleAction.DEFEND_OWNER;
        }
        if (armorUpgradeAvailable) {
            return SurvivalIdleAction.EQUIP_ARMOR;
        }
        return SurvivalIdleAction.NONE;
    }
}
