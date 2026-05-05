package dev.soffits.openplayer.runtime.planner;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class PlannerJob<T> {
    private final UUID id;
    private final String ownerKey;
    private final CompletableFuture<T> future;
    private final AtomicReference<String> cancellationReason = new AtomicReference<>("none");

    PlannerJob(UUID id, String ownerKey, CompletableFuture<T> future) {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("ownerKey cannot be blank");
        }
        if (future == null) {
            throw new IllegalArgumentException("future cannot be null");
        }
        this.id = id;
        this.ownerKey = ownerKey.trim();
        this.future = future;
    }

    public UUID id() {
        return id;
    }

    public String ownerKey() {
        return ownerKey;
    }

    public CompletableFuture<T> future() {
        return future;
    }

    public void cancel(String reason) {
        cancellationReason.set(reason == null || reason.isBlank() ? "cancelled" : reason.trim());
        future.cancel(true);
    }

    public PlannerJobSnapshot snapshot() {
        PlannerJobStatus status;
        String detail;
        if (future.isCancelled()) {
            status = PlannerJobStatus.CANCELLED;
            detail = cancellationReason.get();
        } else if (!future.isDone()) {
            status = PlannerJobStatus.ACTIVE;
            detail = "planning";
        } else if (future.isCompletedExceptionally()) {
            status = PlannerJobStatus.FAILED;
            detail = "planning future failed";
        } else {
            status = PlannerJobStatus.COMPLETED;
            detail = "planning future completed";
        }
        return new PlannerJobSnapshot(id, ownerKey, status, detail);
    }
}
