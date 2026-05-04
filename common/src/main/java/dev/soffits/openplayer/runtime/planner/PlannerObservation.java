package dev.soffits.openplayer.runtime.planner;

public record PlannerObservation(
        PlannerObservationStatus status,
        String detail,
        boolean activeOrQueued
) {
    public PlannerObservation {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        if (detail == null) {
            throw new IllegalArgumentException("detail cannot be null");
        }
    }

    public static PlannerObservation of(PlannerObservationStatus status, String detail, boolean activeOrQueued) {
        return new PlannerObservation(status, detail, activeOrQueued);
    }
}
