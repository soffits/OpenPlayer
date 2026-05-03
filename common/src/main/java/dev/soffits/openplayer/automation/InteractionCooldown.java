package dev.soffits.openplayer.automation;

public final class InteractionCooldown {
    private final int cooldownTicks;
    private int remainingTicks;

    public InteractionCooldown(int cooldownTicks) {
        if (cooldownTicks <= 0) {
            throw new IllegalArgumentException("cooldownTicks must be positive");
        }
        this.cooldownTicks = cooldownTicks;
    }

    public boolean tryAcquire() {
        if (remainingTicks > 0) {
            return false;
        }
        remainingTicks = cooldownTicks;
        return true;
    }

    public boolean canAcquire() {
        return remainingTicks == 0;
    }

    public void rollbackAcquire() {
        remainingTicks = 0;
    }

    public void tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    public void reset() {
        remainingTicks = 0;
    }

    public int remainingTicks() {
        return remainingTicks;
    }
}
