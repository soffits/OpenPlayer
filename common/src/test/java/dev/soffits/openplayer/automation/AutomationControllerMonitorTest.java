package dev.soffits.openplayer.automation;

public final class AutomationControllerMonitorTest {
    private AutomationControllerMonitorTest() {
    }

    public static void main(String[] args) {
        startsActiveAndTracksElapsedTicks();
        timesOutDeterministically();
        detectsRepeatedMissingProgressAsStuck();
        progressResetsStuckChecks();
        idleTicksWithoutRequiredProgressDoNotMarkStuck();
        resetClearsTerminalStatus();
        boundsStatusReasons();
    }

    private static void startsActiveAndTracksElapsedTicks() {
        AutomationControllerMonitor monitor = new AutomationControllerMonitor(10, 2, 0.5D, 2);
        monitor.start(0.0D, 0.0D, 0.0D);

        require(monitor.status() == AutomationControllerMonitorStatus.ACTIVE, "monitor must start active");
        require(monitor.tick(0.0D, 0.0D, 0.0D, false) == AutomationControllerMonitorStatus.ACTIVE,
                "first non-progress tick must remain active");
        require(monitor.elapsedTicks() == 1, "elapsed ticks must increment");
    }

    private static void timesOutDeterministically() {
        AutomationControllerMonitor monitor = new AutomationControllerMonitor(2, 3, 0.5D, 2);
        monitor.start(0.0D, 0.0D, 0.0D);

        monitor.tick(0.0D, 0.0D, 0.0D, false);
        AutomationControllerMonitorStatus status = monitor.tick(0.0D, 0.0D, 0.0D, false);

        require(status == AutomationControllerMonitorStatus.TIMED_OUT, "monitor must time out at max ticks");
        require("timed_out".equals(monitor.boundedReason()), "timeout reason must be deterministic");
    }

    private static void detectsRepeatedMissingProgressAsStuck() {
        AutomationControllerMonitor monitor = new AutomationControllerMonitor(20, 2, 0.5D, 2);
        monitor.start(0.0D, 0.0D, 0.0D);

        monitor.tick(0.1D, 0.0D, 0.0D, true);
        monitor.tick(0.1D, 0.0D, 0.0D, true);
        monitor.tick(0.1D, 0.0D, 0.0D, true);
        AutomationControllerMonitorStatus status = monitor.tick(0.1D, 0.0D, 0.0D, true);

        require(status == AutomationControllerMonitorStatus.STUCK, "monitor must detect repeated missing progress");
        require("stuck".equals(monitor.boundedReason()), "stuck reason must be deterministic");
    }

    private static void progressResetsStuckChecks() {
        AutomationControllerMonitor monitor = new AutomationControllerMonitor(20, 2, 0.5D, 2);
        monitor.start(0.0D, 0.0D, 0.0D);

        monitor.tick(0.1D, 0.0D, 0.0D, true);
        monitor.tick(0.1D, 0.0D, 0.0D, true);
        require(monitor.stuckChecks() == 1, "first missing progress window must increment stuck checks");
        monitor.tick(1.0D, 0.0D, 0.0D, true);
        monitor.tick(1.0D, 0.0D, 0.0D, true);

        require(monitor.status() == AutomationControllerMonitorStatus.ACTIVE, "progress must keep monitor active");
        require(monitor.stuckChecks() == 0, "progress must reset stuck checks");
    }

    private static void idleTicksWithoutRequiredProgressDoNotMarkStuck() {
        AutomationControllerMonitor monitor = new AutomationControllerMonitor(20, 1, 0.5D, 1);
        monitor.start(0.0D, 0.0D, 0.0D);

        monitor.tick(0.0D, 0.0D, 0.0D, false);
        monitor.tick(0.0D, 0.0D, 0.0D, false);

        require(monitor.status() == AutomationControllerMonitorStatus.ACTIVE,
                "idle non-navigation ticks must not mark the task stuck");
    }

    private static void resetClearsTerminalStatus() {
        AutomationControllerMonitor monitor = new AutomationControllerMonitor(2, 1, 0.5D, 1);
        monitor.start(0.0D, 0.0D, 0.0D);
        monitor.cancel("target_unavailable");

        require(monitor.status() == AutomationControllerMonitorStatus.CANCELLED, "cancel must set cancelled status");
        require("target_unavailable".equals(monitor.boundedReason()), "cancel reason must be retained");
        monitor.reset();

        require(monitor.status() == AutomationControllerMonitorStatus.IDLE, "reset must return to idle");
        require("idle".equals(monitor.boundedReason()), "reset must clear reason");
    }

    private static void boundsStatusReasons() {
        String longReason = "safe".repeat(40);
        String bounded = AutomationControllerMonitor.bounded(longReason + "\nsecret-looking overflow");

        require(bounded.length() == 96, "bounded status must be capped");
        require(!bounded.contains("\n"), "bounded status must normalize newlines");
        require("none".equals(AutomationControllerMonitor.bounded("   ")), "blank status must become none");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
