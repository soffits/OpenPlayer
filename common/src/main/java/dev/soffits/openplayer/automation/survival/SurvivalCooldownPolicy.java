package dev.soffits.openplayer.automation.survival;

public final class SurvivalCooldownPolicy {
    private final int actionCooldownTicks;
    private final int diagnosticCooldownTicks;
    private int remainingTicks;

    public SurvivalCooldownPolicy(int actionCooldownTicks, int diagnosticCooldownTicks) {
        if (actionCooldownTicks < 1 || diagnosticCooldownTicks < 1) {
            throw new IllegalArgumentException("cooldowns must be positive");
        }
        this.actionCooldownTicks = actionCooldownTicks;
        this.diagnosticCooldownTicks = diagnosticCooldownTicks;
    }

    public void tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    public boolean ready() {
        return remainingTicks == 0;
    }

    public int remainingTicks() {
        return remainingTicks;
    }

    public void backoffAfterAction() {
        remainingTicks = actionCooldownTicks;
    }

    public void backoffAfterDiagnostic() {
        remainingTicks = diagnosticCooldownTicks;
    }

    public void reset() {
        remainingTicks = 0;
    }
}
