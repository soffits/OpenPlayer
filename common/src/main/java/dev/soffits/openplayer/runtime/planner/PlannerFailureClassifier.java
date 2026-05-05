package dev.soffits.openplayer.runtime.planner;

import java.util.Locale;

public final class PlannerFailureClassifier {
    private PlannerFailureClassifier() {
    }

    public static PlannerFailureKind classify(PlannerObservationStatus status, String detail) {
        if (status == PlannerObservationStatus.COMPLETED
                || status == PlannerObservationStatus.ACTIVE
                || status == PlannerObservationStatus.QUEUED) {
            return PlannerFailureKind.PROGRESS;
        }
        String normalized = normalize(detail);
        if (status == PlannerObservationStatus.CANCELLED) {
            return PlannerFailureKind.TERMINAL;
        }
        if (containsAny(normalized,
                "policy", "permission", "world actions", "world-actions", "unsafe", "unsupported",
                "missing adapter", "missing-adapter", "adapter absent", "capability adapter", "not provider tools",
                "non-primitive", "requires a blank instruction", "must not", "cannot be null", "cannot be blank")) {
            return PlannerFailureKind.TERMINAL;
        }
        if (containsAny(normalized,
                "unloaded", "not loaded", "path", "navigation", "stuck", "target lost", "not found",
                "no loaded", "no safe", "out of range", "outside radius", "line of sight", "line-of-sight", "blocked",
                "inventory full", "item not picked up", "rejected")) {
            return PlannerFailureKind.RETRYABLE;
        }
        if (status == PlannerObservationStatus.REJECTED
                || status == PlannerObservationStatus.FAILED
                || status == PlannerObservationStatus.UNAVAILABLE
                || status == PlannerObservationStatus.TIMED_OUT) {
            return PlannerFailureKind.RETRYABLE;
        }
        return PlannerFailureKind.PROGRESS;
    }

    private static boolean containsAny(String normalized, String... needles) {
        for (String needle : needles) {
            if (normalized.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String detail) {
        return detail == null ? "" : detail.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
    }

    public enum PlannerFailureKind {
        PROGRESS,
        RETRYABLE,
        TERMINAL
    }
}
