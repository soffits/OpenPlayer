package dev.soffits.openplayer.runtime.planner;

import java.time.Duration;

public record InteractivePlannerConfig(
        int maxIterations,
        int maxProviderCalls,
        int maxToolSteps,
        int maxObservationCharacters,
        int maxPromptCharacters,
        int maxNoProgressCount,
        Duration maxWallTime,
        Duration pollDelay,
        int maxPollsPerTool
) {
    public InteractivePlannerConfig {
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be positive");
        }
        if (maxProviderCalls < 1) {
            throw new IllegalArgumentException("maxProviderCalls must be positive");
        }
        if (maxToolSteps < 1) {
            throw new IllegalArgumentException("maxToolSteps must be positive");
        }
        if (maxObservationCharacters < 64) {
            throw new IllegalArgumentException("maxObservationCharacters must be at least 64");
        }
        if (maxPromptCharacters < 512) {
            throw new IllegalArgumentException("maxPromptCharacters must be at least 512");
        }
        if (maxNoProgressCount < 1) {
            throw new IllegalArgumentException("maxNoProgressCount must be positive");
        }
        if (maxWallTime == null || maxWallTime.isNegative() || maxWallTime.isZero()) {
            throw new IllegalArgumentException("maxWallTime must be positive");
        }
        if (pollDelay == null || pollDelay.isNegative() || pollDelay.isZero()) {
            throw new IllegalArgumentException("pollDelay must be positive");
        }
        if (maxPollsPerTool < 0) {
            throw new IllegalArgumentException("maxPollsPerTool must be non-negative");
        }
    }

    public static InteractivePlannerConfig defaults() {
        return new InteractivePlannerConfig(6, 6, 8, 1600, 12000, 2,
                Duration.ofSeconds(45L), Duration.ofMillis(250L), 12);
    }
}
