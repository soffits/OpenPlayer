package dev.soffits.openplayer.runtime.planner;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlannerMailbox {
    private final Map<String, PlannerJob<?>> jobsByOwner = new LinkedHashMap<>();

    public synchronized <T> PlannerJob<T> submit(String ownerKey, CompletableFuture<T> future) {
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("ownerKey cannot be blank");
        }
        String key = ownerKey.trim();
        PlannerJob<?> existing = jobsByOwner.get(key);
        if (existing != null && existing.snapshot().status() == PlannerJobStatus.ACTIVE) {
            throw new IllegalStateException("planner job already active for owner");
        }
        PlannerJob<T> job = new PlannerJob<>(UUID.randomUUID(), key, future);
        jobsByOwner.put(key, job);
        return job;
    }

    public synchronized Optional<PlannerJobSnapshot> snapshot(String ownerKey) {
        PlannerJob<?> job = jobsByOwner.get(ownerKey);
        return job == null ? Optional.empty() : Optional.of(job.snapshot());
    }

    public synchronized void cancel(String ownerKey, String reason) {
        PlannerJob<?> job = jobsByOwner.get(ownerKey);
        if (job != null) {
            job.cancel(reason);
        }
    }

    public synchronized void reapFinished() {
        jobsByOwner.entrySet().removeIf(entry -> entry.getValue().snapshot().status() != PlannerJobStatus.ACTIVE);
    }
}
