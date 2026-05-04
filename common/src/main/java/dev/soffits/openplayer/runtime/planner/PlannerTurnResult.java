package dev.soffits.openplayer.runtime.planner;

public record PlannerTurnResult(PlannerTurnStatus status, String message) {
    public PlannerTurnResult {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
    }
}
