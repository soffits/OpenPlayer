package dev.soffits.openplayer.automation.navigation;

public final class NavigationRuntime {
    private final int maxRecoveryCount;

    private NavigationState state = NavigationState.IDLE;
    private NavigationTarget target = NavigationTarget.none();
    private double distanceSquared;
    private int replanCount;
    private int recoveryCount;
    private String lastReason = "idle";
    private NavigationTargetStatus loadedStatus = NavigationTargetStatus.UNKNOWN;
    private NavigationTargetStatus reachableStatus = NavigationTargetStatus.UNKNOWN;

    public NavigationRuntime(int maxRecoveryCount) {
        if (maxRecoveryCount < 0) {
            throw new IllegalArgumentException("maxRecoveryCount must be non-negative");
        }
        this.maxRecoveryCount = maxRecoveryCount;
    }

    public void start(NavigationTarget target, double distanceSquared, boolean loaded) {
        this.state = NavigationState.ACTIVE;
        this.target = target == null ? NavigationTarget.none() : target;
        this.distanceSquared = safeDistance(distanceSquared);
        this.replanCount = 0;
        this.recoveryCount = 0;
        this.lastReason = "started";
        this.loadedStatus = loaded ? NavigationTargetStatus.YES : NavigationTargetStatus.NO;
        this.reachableStatus = NavigationTargetStatus.UNKNOWN;
    }

    public void replan(NavigationTarget target, double distanceSquared, boolean loaded) {
        this.state = NavigationState.REPLANNING;
        this.target = target == null ? NavigationTarget.none() : target;
        this.distanceSquared = safeDistance(distanceSquared);
        this.replanCount++;
        this.lastReason = "replanned";
        this.loadedStatus = loaded ? NavigationTargetStatus.YES : NavigationTargetStatus.NO;
    }

    public void plan(NavigationTarget target, double distanceSquared, boolean loaded) {
        if (state == NavigationState.IDLE || state == NavigationState.COMPLETED || state == NavigationState.FAILED) {
            start(target, distanceSquared, loaded);
        } else {
            replan(target, distanceSquared, loaded);
        }
    }

    public boolean tryRecover(String reason) {
        if (recoveryCount >= maxRecoveryCount) {
            fail("recovery_budget_exhausted");
            return false;
        }
        recoveryCount++;
        state = NavigationState.RECOVERING;
        lastReason = NavigationSnapshot.boundedStatus(reason);
        reachableStatus = NavigationTargetStatus.UNKNOWN;
        return true;
    }

    public void markActive(String reason) {
        state = NavigationState.ACTIVE;
        lastReason = NavigationSnapshot.boundedStatus(reason);
    }

    public void updateDistance(double distanceSquared) {
        this.distanceSquared = safeDistance(distanceSquared);
    }

    public void markReachable(boolean reachable) {
        reachableStatus = reachable ? NavigationTargetStatus.YES : NavigationTargetStatus.NO;
    }

    public void complete() {
        state = NavigationState.COMPLETED;
        lastReason = "completed";
        reachableStatus = NavigationTargetStatus.YES;
    }

    public void fail(String reason) {
        state = NavigationState.FAILED;
        lastReason = NavigationSnapshot.boundedStatus(reason);
        reachableStatus = NavigationTargetStatus.NO;
    }

    public void reset() {
        state = NavigationState.IDLE;
        target = NavigationTarget.none();
        distanceSquared = 0.0D;
        replanCount = 0;
        recoveryCount = 0;
        lastReason = "idle";
        loadedStatus = NavigationTargetStatus.UNKNOWN;
        reachableStatus = NavigationTargetStatus.UNKNOWN;
    }

    public NavigationSnapshot snapshot() {
        return new NavigationSnapshot(
                state,
                target.kind(),
                target.summary(),
                distanceSquared,
                replanCount,
                recoveryCount,
                lastReason,
                loadedStatus,
                reachableStatus
        );
    }

    public int recoveryCount() {
        return recoveryCount;
    }

    private static double safeDistance(double value) {
        if (!Double.isFinite(value) || value < 0.0D) {
            return 0.0D;
        }
        return value;
    }
}
