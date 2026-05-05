package dev.soffits.openplayer.runtime.action;

import java.util.Optional;

public record LongActionStatus(
        ActionLifecycleState state,
        String label,
        int timeoutTicks,
        long startedTick,
        long lastProgressTick,
        String cancellationReason,
        ActionResultSummary lastResult,
        int retriesRemaining,
        Optional<String> resumeToken
) {
    public LongActionStatus {
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        label = label == null || label.isBlank() ? "idle" : label.trim();
        if (timeoutTicks < 0) {
            throw new IllegalArgumentException("timeoutTicks must be non-negative");
        }
        if (startedTick < 0L || lastProgressTick < 0L) {
            throw new IllegalArgumentException("ticks must be non-negative");
        }
        cancellationReason = cancellationReason == null || cancellationReason.isBlank() ? "none" : cancellationReason.trim();
        lastResult = lastResult == null ? new ActionResultSummary(false, "none") : lastResult;
        if (retriesRemaining < 0) {
            throw new IllegalArgumentException("retriesRemaining must be non-negative");
        }
        resumeToken = resumeToken == null ? Optional.empty() : resumeToken.map(String::trim).filter(value -> !value.isBlank());
    }
}
