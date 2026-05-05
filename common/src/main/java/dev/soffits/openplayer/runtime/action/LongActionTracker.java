package dev.soffits.openplayer.runtime.action;

import java.util.Optional;

public final class LongActionTracker {
    private ActionLifecycleState state = ActionLifecycleState.IDLE;
    private String label = "idle";
    private int timeoutTicks;
    private long startedTick;
    private long lastProgressTick;
    private String cancellationReason = "none";
    private ActionResultSummary lastResult = new ActionResultSummary(false, "none");
    private int retriesRemaining;
    private Optional<String> resumeToken = Optional.empty();

    public void startPlanning(String label, long tick, int timeoutTicks, int retryBudget) {
        begin(ActionLifecycleState.PLANNING, label, tick, timeoutTicks, retryBudget, Optional.empty());
    }

    public void startExecuting(String label, long tick, int timeoutTicks, int retryBudget, Optional<String> resumeToken) {
        begin(ActionLifecycleState.EXECUTING, label, tick, timeoutTicks, retryBudget, resumeToken);
    }

    public void noteProgress(long tick, ActionResultSummary result) {
        requireMutable("record progress");
        if (tick < lastProgressTick) {
            throw new IllegalArgumentException("progress tick cannot move backward");
        }
        lastProgressTick = tick;
        lastResult = result == null ? lastResult : result;
    }

    public void pause(String reason) {
        requireMutable("pause");
        state = ActionLifecycleState.PAUSED;
        cancellationReason = sanitize(reason);
    }

    public boolean retry(String reason) {
        requireMutable("retry");
        if (retriesRemaining <= 0) {
            state = ActionLifecycleState.BLOCKED;
            lastResult = new ActionResultSummary(false, sanitize(reason));
            return false;
        }
        retriesRemaining--;
        state = ActionLifecycleState.RETRYING;
        lastResult = new ActionResultSummary(false, sanitize(reason));
        return true;
    }

    public void resumeExecuting(long tick) {
        if (state != ActionLifecycleState.PAUSED && state != ActionLifecycleState.RETRYING) {
            throw new IllegalStateException("only paused or retrying actions can resume");
        }
        state = ActionLifecycleState.EXECUTING;
        lastProgressTick = Math.max(lastProgressTick, tick);
        cancellationReason = "none";
    }

    public void complete(ActionResultSummary result) {
        requireMutable("complete");
        state = ActionLifecycleState.COMPLETED;
        lastResult = result == null ? new ActionResultSummary(true, "completed") : result;
    }

    public void fail(ActionResultSummary result) {
        requireMutable("fail");
        state = ActionLifecycleState.FAILED;
        lastResult = result == null ? new ActionResultSummary(false, "failed") : result;
    }

    public void cancel(String reason) {
        if (terminal()) {
            return;
        }
        state = ActionLifecycleState.CANCELLED;
        cancellationReason = sanitize(reason);
        lastResult = new ActionResultSummary(false, cancellationReason);
    }

    public LongActionStatus status() {
        return new LongActionStatus(state, label, timeoutTicks, startedTick, lastProgressTick,
                cancellationReason, lastResult, retriesRemaining, resumeToken);
    }

    private void begin(ActionLifecycleState newState, String newLabel, long tick, int newTimeoutTicks,
                       int retryBudget, Optional<String> newResumeToken) {
        if (tick < 0L) {
            throw new IllegalArgumentException("tick must be non-negative");
        }
        if (newTimeoutTicks <= 0) {
            throw new IllegalArgumentException("timeoutTicks must be positive");
        }
        if (retryBudget < 0) {
            throw new IllegalArgumentException("retryBudget must be non-negative");
        }
        state = newState;
        label = sanitize(newLabel);
        timeoutTicks = newTimeoutTicks;
        startedTick = tick;
        lastProgressTick = tick;
        cancellationReason = "none";
        lastResult = new ActionResultSummary(false, "started");
        retriesRemaining = retryBudget;
        resumeToken = newResumeToken == null ? Optional.empty() : newResumeToken.map(String::trim).filter(value -> !value.isBlank());
    }

    private void requireMutable(String operation) {
        if (terminal()) {
            throw new IllegalStateException("cannot " + operation + " after terminal state " + state.name().toLowerCase());
        }
    }

    private boolean terminal() {
        return state == ActionLifecycleState.COMPLETED
                || state == ActionLifecycleState.FAILED
                || state == ActionLifecycleState.CANCELLED;
    }

    private static String sanitize(String value) {
        String source = value == null || value.isBlank() ? "none" : value.trim();
        source = source.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return source.length() <= 96 ? source : source.substring(0, 96);
    }
}
