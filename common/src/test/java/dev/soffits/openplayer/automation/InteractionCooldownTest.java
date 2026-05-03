package dev.soffits.openplayer.automation;

public final class InteractionCooldownTest {
    private InteractionCooldownTest() {
    }

    public static void main(String[] args) {
        rejectsUntilCooldownElapses();
        resetAllowsImmediateAcquire();
        rollbackClearsOnlyAcquiredCooldown();
    }

    private static void rejectsUntilCooldownElapses() {
        InteractionCooldown cooldown = new InteractionCooldown(3);
        require(cooldown.tryAcquire(), "first interaction must acquire cooldown");
        require(!cooldown.tryAcquire(), "same-tick interaction must reject");
        cooldown.tick();
        cooldown.tick();
        require(!cooldown.tryAcquire(), "cooldown must reject before final tick elapses");
        cooldown.tick();
        require(cooldown.tryAcquire(), "interaction must acquire after cooldown elapses");
    }

    private static void resetAllowsImmediateAcquire() {
        InteractionCooldown cooldown = new InteractionCooldown(2);
        require(cooldown.tryAcquire(), "initial acquire must succeed");
        cooldown.reset();
        require(cooldown.remainingTicks() == 0, "reset must clear remaining ticks");
        require(cooldown.tryAcquire(), "reset must allow immediate acquire");
    }

    private static void rollbackClearsOnlyAcquiredCooldown() {
        InteractionCooldown cooldown = new InteractionCooldown(2);
        require(cooldown.canAcquire(), "fresh cooldown must be acquirable");
        require(cooldown.tryAcquire(), "interaction must acquire before rollback");
        cooldown.rollbackAcquire();
        require(cooldown.remainingTicks() == 0, "rollback must clear an acquired cooldown");
        require(cooldown.tryAcquire(), "rollback must allow later valid interaction");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
