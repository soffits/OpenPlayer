package dev.soffits.openplayer.runtime.planner;

import java.util.UUID;

public record PlannerJobSnapshot(UUID jobId, String ownerKey, PlannerJobStatus status, String detail) {
    public PlannerJobSnapshot {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId cannot be null");
        }
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("ownerKey cannot be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        detail = detail == null || detail.isBlank() ? "none" : detail.trim();
    }
}
