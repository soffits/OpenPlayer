package dev.soffits.openplayer.automation.navigation;

public final class NavigationRuntimeTest {
    private NavigationRuntimeTest() {
    }

    public static void main(String[] args) {
        targetAndReasonAreSanitizedAndBounded();
        recoveryBudgetCannotExceedConfiguredMax();
        replanCountIsTrackedDeterministically();
        failureKeepsRejectedPathUnreachable();
        suspendPreservesActiveTelemetry();
    }

    private static void targetAndReasonAreSanitizedAndBounded() {
        NavigationSnapshot snapshot = new NavigationSnapshot(
                NavigationState.ACTIVE,
                NavigationTargetKind.ENTITY,
                "safe-prefix" + "a".repeat(100) + "\nsecret-overflow",
                8.0D,
                0,
                0,
                "reason-prefix" + "b".repeat(140) + "\tsecret-overflow",
                NavigationTargetStatus.YES,
                NavigationTargetStatus.UNKNOWN
        );
        String summary = snapshot.summary();

        require(snapshot.targetSummary().length() == 80, "target summary must be bounded");
        require(snapshot.lastReason().length() == 96, "reason must be bounded");
        require(!summary.contains("secret-overflow"), "overflow text must not be exposed");
        require(!summary.contains("\n"), "newlines must be normalized");
        require(!summary.contains("\t"), "tabs must be normalized");
    }

    private static void recoveryBudgetCannotExceedConfiguredMax() {
        NavigationRuntime runtime = new NavigationRuntime(2);
        runtime.start(NavigationTarget.position(1.0D, 2.0D, 3.0D), 14.0D, true);

        require(runtime.tryRecover("stuck"), "first recovery must be allowed");
        require(runtime.tryRecover("stuck"), "second recovery must be allowed");
        require(!runtime.tryRecover("stuck"), "third recovery must be rejected");
        NavigationSnapshot snapshot = runtime.snapshot();

        require(snapshot.recoveryCount() == 2, "recovery count must not exceed max");
        require(snapshot.state() == NavigationState.FAILED, "runtime must fail when recovery budget is exhausted");
        require("recovery_budget_exhausted".equals(snapshot.lastReason()), "failure reason must be truthful");
    }

    private static void replanCountIsTrackedDeterministically() {
        NavigationRuntime runtime = new NavigationRuntime(1);
        runtime.start(NavigationTarget.block(1, 64, 1), 9.0D, true);
        runtime.replan(NavigationTarget.block(2, 64, 2), 4.0D, true);
        runtime.replan(NavigationTarget.block(3, 64, 3), 1.0D, false);
        NavigationSnapshot snapshot = runtime.snapshot();

        require(snapshot.state() == NavigationState.REPLANNING, "runtime must expose replanning state");
        require(snapshot.replanCount() == 2, "runtime must count replans");
        require(snapshot.loadedStatus() == NavigationTargetStatus.NO, "runtime must expose unloaded target status");
        require(snapshot.distanceSquared() == 1.0D, "runtime must expose latest distance");
    }

    private static void failureKeepsRejectedPathUnreachable() {
        NavigationRuntime runtime = new NavigationRuntime(1);
        runtime.start(NavigationTarget.position(1.0D, 64.0D, 1.0D), 4.0D, true);
        runtime.markReachable(false);
        runtime.fail("navigation_position_rejected");
        NavigationSnapshot snapshot = runtime.snapshot();

        require(snapshot.state() == NavigationState.FAILED, "rejected path must fail navigation");
        require("navigation_position_rejected".equals(snapshot.lastReason()), "rejection reason must be deterministic");
        require(snapshot.loadedStatus() == NavigationTargetStatus.YES, "loaded target status must be preserved");
        require(snapshot.reachableStatus() == NavigationTargetStatus.NO, "rejected path must stay unreachable");
    }

    private static void suspendPreservesActiveTelemetry() {
        NavigationRuntime runtime = new NavigationRuntime(1);
        runtime.start(NavigationTarget.block(4, 65, 6), 25.0D, true);
        runtime.markReachable(true);
        NavigationSnapshot before = runtime.snapshot();

        runtime.suspend();
        NavigationSnapshot after = runtime.snapshot();

        require(after.state() == NavigationState.ACTIVE, "suspend must not complete active navigation");
        require(after.targetKind() == before.targetKind(), "suspend must preserve target kind");
        require(after.targetSummary().equals(before.targetSummary()), "suspend must preserve target summary");
        require(after.reachableStatus() == NavigationTargetStatus.YES, "suspend must preserve reachable telemetry");
        require(after.lastReason().equals(before.lastReason()), "suspend must preserve navigation reason");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
