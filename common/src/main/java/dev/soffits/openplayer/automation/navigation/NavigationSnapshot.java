package dev.soffits.openplayer.automation.navigation;

import java.util.Locale;

public record NavigationSnapshot(
        NavigationState state,
        NavigationTargetKind targetKind,
        String targetSummary,
        double distanceSquared,
        int replanCount,
        int recoveryCount,
        String lastReason,
        NavigationTargetStatus loadedStatus,
        NavigationTargetStatus reachableStatus
) {
    private static final int MAX_STATUS_LENGTH = 96;
    private static final int MAX_TARGET_LENGTH = 80;

    public NavigationSnapshot {
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        if (targetKind == null) {
            throw new IllegalArgumentException("targetKind cannot be null");
        }
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0D) {
            distanceSquared = 0.0D;
        }
        if (replanCount < 0) {
            throw new IllegalArgumentException("replanCount must be non-negative");
        }
        if (recoveryCount < 0) {
            throw new IllegalArgumentException("recoveryCount must be non-negative");
        }
        if (loadedStatus == null) {
            throw new IllegalArgumentException("loadedStatus cannot be null");
        }
        if (reachableStatus == null) {
            throw new IllegalArgumentException("reachableStatus cannot be null");
        }
        targetSummary = boundedTarget(targetSummary);
        lastReason = boundedStatus(lastReason);
    }

    public static NavigationSnapshot idle() {
        return new NavigationSnapshot(
                NavigationState.IDLE,
                NavigationTargetKind.NONE,
                "none",
                0.0D,
                0,
                0,
                "idle",
                NavigationTargetStatus.UNKNOWN,
                NavigationTargetStatus.UNKNOWN
        );
    }

    public String summary() {
        return "nav=" + state.name().toLowerCase(Locale.ROOT)
                + ", navTarget=" + targetKind.name().toLowerCase(Locale.ROOT) + ':' + targetSummary
                + ", navDistSq=" + rounded(distanceSquared)
                + ", navReplans=" + replanCount
                + ", navRecoveries=" + recoveryCount
                + ", navLoaded=" + loadedStatus.name().toLowerCase(Locale.ROOT)
                + ", navReachable=" + reachableStatus.name().toLowerCase(Locale.ROOT)
                + ", navReason=" + lastReason;
    }

    public static String boundedStatus(String value) {
        return bounded(value, MAX_STATUS_LENGTH);
    }

    public static String boundedTarget(String value) {
        return bounded(value, MAX_TARGET_LENGTH);
    }

    private static String bounded(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length() && builder.length() < maxLength; index++) {
            char character = value.charAt(index);
            if (character == '\n' || character == '\r' || character == '\t') {
                builder.append(' ');
            } else if (character >= 32 && character < 127) {
                builder.append(character);
            } else {
                builder.append('?');
            }
        }
        String normalized = builder.toString().trim();
        return normalized.isEmpty() ? "none" : normalized;
    }

    private static String rounded(double value) {
        return String.valueOf(Math.round(value * 10.0D) / 10.0D);
    }
}
