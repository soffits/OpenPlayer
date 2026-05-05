package dev.soffits.openplayer.automation.navigation;

public final class ReplanThrottler {
    private static final int DEFAULT_MIN_INTERVAL_TICKS = 10;
    private static final int DEFAULT_MIN_PROGRESS_BLOCKS = 1;
    private static final int DEFAULT_MAX_REPLAN_COUNT = 5;

    private final int minIntervalTicks;
    private final int minProgressBlocks;
    private final int maxReplanCount;
    private int lastTick;
    private double lastDistanceSquared;
    private int replanCount;

    public ReplanThrottler() {
        this(DEFAULT_MIN_INTERVAL_TICKS, DEFAULT_MIN_PROGRESS_BLOCKS, DEFAULT_MAX_REPLAN_COUNT);
    }

    public ReplanThrottler(int minIntervalTicks, int minProgressBlocks, int maxReplanCount) {
        if (minIntervalTicks < 1) {
            minIntervalTicks = DEFAULT_MIN_INTERVAL_TICKS;
        }
        if (minProgressBlocks < 1) {
            minProgressBlocks = DEFAULT_MIN_PROGRESS_BLOCKS;
        }
        if (maxReplanCount < 0) {
            maxReplanCount = DEFAULT_MAX_REPLAN_COUNT;
        }
        this.minIntervalTicks = minIntervalTicks;
        this.minProgressBlocks = minProgressBlocks;
        this.maxReplanCount = maxReplanCount;
        this.lastTick = 0;
        this.lastDistanceSquared = Double.MAX_VALUE;
        this.replanCount = 0;
    }

    public boolean shouldReplan(int currentTick, double currentDistanceSquared) {
        if (currentTick - lastTick < minIntervalTicks) {
            return false;
        }
        if (replanCount >= maxReplanCount) {
            return false;
        }
        double progress = lastDistanceSquared - currentDistanceSquared;
        if (progress < 0) {
            progress = 0;
        }
        if (progress < minProgressBlocks * minProgressBlocks) {
            return false;
        }
        return true;
    }

    public void noteReplan(int currentTick, double currentDistanceSquared) {
        lastTick = currentTick;
        lastDistanceSquared = currentDistanceSquared;
        replanCount++;
    }

    public void reset() {
        lastTick = 0;
        lastDistanceSquared = Double.MAX_VALUE;
        replanCount = 0;
    }

    public int replanCount() {
        return replanCount;
    }

    public static ReplanThrottler forLongRange() {
        return new ReplanThrottler(15, 2, 3);
    }

    public static ReplanThrottler forPreciseTasks() {
        return new ReplanThrottler(5, 1, 10);
    }

    public static ReplanThrottler forSurvival() {
        return new ReplanThrottler(8, 2, 5);
    }
}
