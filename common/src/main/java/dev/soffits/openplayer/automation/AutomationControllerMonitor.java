package dev.soffits.openplayer.automation;

public final class AutomationControllerMonitor {
    private static final int MAX_STATUS_LENGTH = 96;

    private final int maxTicks;
    private final int stuckCheckIntervalTicks;
    private final double minProgressDistanceSquared;
    private final int maxStuckChecks;

    private int elapsedTicks;
    private int ticksSinceProgressCheck;
    private int stuckChecks;
    private double progressX;
    private double progressY;
    private double progressZ;
    private AutomationControllerMonitorStatus status = AutomationControllerMonitorStatus.IDLE;
    private String reason = "idle";

    public AutomationControllerMonitor(
            int maxTicks,
            int stuckCheckIntervalTicks,
            double minProgressDistance,
            int maxStuckChecks
    ) {
        if (maxTicks <= 0) {
            throw new IllegalArgumentException("maxTicks must be positive");
        }
        if (stuckCheckIntervalTicks <= 0) {
            throw new IllegalArgumentException("stuckCheckIntervalTicks must be positive");
        }
        if (!Double.isFinite(minProgressDistance) || minProgressDistance < 0.0D) {
            throw new IllegalArgumentException("minProgressDistance must be finite and non-negative");
        }
        if (maxStuckChecks <= 0) {
            throw new IllegalArgumentException("maxStuckChecks must be positive");
        }
        this.maxTicks = maxTicks;
        this.stuckCheckIntervalTicks = stuckCheckIntervalTicks;
        this.minProgressDistanceSquared = minProgressDistance * minProgressDistance;
        this.maxStuckChecks = maxStuckChecks;
    }

    public void start(double x, double y, double z) {
        elapsedTicks = 0;
        ticksSinceProgressCheck = 0;
        stuckChecks = 0;
        progressX = x;
        progressY = y;
        progressZ = z;
        status = AutomationControllerMonitorStatus.ACTIVE;
        reason = "active";
    }

    public AutomationControllerMonitorStatus tick(double x, double y, double z, boolean progressRequired) {
        if (status != AutomationControllerMonitorStatus.ACTIVE) {
            return status;
        }
        elapsedTicks++;
        if (elapsedTicks >= maxTicks) {
            timeout();
            return status;
        }
        if (!progressRequired) {
            recordProgressPoint(x, y, z);
            return status;
        }
        ticksSinceProgressCheck++;
        if (ticksSinceProgressCheck < stuckCheckIntervalTicks) {
            return status;
        }
        ticksSinceProgressCheck = 0;
        double distanceSquared = distanceSquared(progressX, progressY, progressZ, x, y, z);
        if (distanceSquared >= minProgressDistanceSquared) {
            recordProgressPoint(x, y, z);
            stuckChecks = 0;
            return status;
        }
        stuckChecks++;
        if (stuckChecks >= maxStuckChecks) {
            stuck();
        }
        return status;
    }

    public void complete() {
        complete("completed");
    }

    public void complete(String reason) {
        status = AutomationControllerMonitorStatus.COMPLETED;
        this.reason = bounded(reason);
    }

    public void cancel() {
        cancel("cancelled");
    }

    public void cancel(String reason) {
        status = AutomationControllerMonitorStatus.CANCELLED;
        this.reason = bounded(reason);
    }

    public void reset() {
        status = AutomationControllerMonitorStatus.IDLE;
        reason = "idle";
        elapsedTicks = 0;
        ticksSinceProgressCheck = 0;
        stuckChecks = 0;
    }

    public AutomationControllerMonitorStatus status() {
        return status;
    }

    public int elapsedTicks() {
        return elapsedTicks;
    }

    public int maxTicks() {
        return maxTicks;
    }

    public int stuckChecks() {
        return stuckChecks;
    }

    public String boundedReason() {
        return bounded(reason);
    }

    public static String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        if (normalized.length() <= MAX_STATUS_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_STATUS_LENGTH);
    }

    private void timeout() {
        status = AutomationControllerMonitorStatus.TIMED_OUT;
        reason = "timed_out";
    }

    private void stuck() {
        status = AutomationControllerMonitorStatus.STUCK;
        reason = "stuck";
    }

    private void recordProgressPoint(double x, double y, double z) {
        progressX = x;
        progressY = y;
        progressZ = z;
        ticksSinceProgressCheck = 0;
    }

    private static double distanceSquared(double startX, double startY, double startZ, double endX, double endY, double endZ) {
        double x = startX - endX;
        double y = startY - endY;
        double z = startZ - endZ;
        return x * x + y * y + z * z;
    }
}
