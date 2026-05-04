package dev.soffits.openplayer.runtime.planner;

public enum PlannerObservationStatus {
    ACCEPTED,
    QUEUED,
    ACTIVE,
    COMPLETED,
    REJECTED,
    FAILED,
    TIMED_OUT,
    UNAVAILABLE,
    CANCELLED
}
