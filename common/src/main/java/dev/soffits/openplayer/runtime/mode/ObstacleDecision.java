package dev.soffits.openplayer.runtime.mode;

public record ObstacleDecision(ObstacleDecisionKind kind, String reason) {
    public ObstacleDecision {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        reason = reason == null || reason.isBlank() ? "none" : reason.trim();
    }
}
