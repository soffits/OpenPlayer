package dev.soffits.openplayer.runtime.planner;

import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Optional;

final class ResourceAcquisitionObservations {
    private static final int MAX_RECENT_BREAK_TARGETS = 16;

    private final Deque<String> recentTerminalBreakTargets = new ArrayDeque<>();
    private PendingDroppedItem pendingDroppedItem;

    Optional<String> guard(CommandIntent intent) {
        if (intent.kind() == IntentKind.COLLECT_ITEMS) {
            return Optional.empty();
        }
        if (intent.kind() != IntentKind.BREAK_BLOCK) {
            return Optional.empty();
        }
        Optional<String> target = instructionTarget(intent.instruction());
        if (target.isPresent() && recentTerminalBreakTargets.contains(target.get())) {
            return Optional.of("stale_break_target rejected: target=" + target.get()
                    + " was already processed or terminal in this objective; choose a fresh observed block"
                    + pendingPickupSuffix());
        }
        if (pendingDroppedItem != null) {
            return Optional.of("resource_pickup_pending rejected: previous break of " + pendingDroppedItem.blockId()
                    + " at " + pendingDroppedItem.target()
                    + " produced nearby dropped item " + pendingDroppedItem.itemId()
                    + " without inventory gain; call pickup_items_nearby matching="
                    + pendingDroppedItem.itemId() + " before more digging");
        }
        return Optional.empty();
    }

    void record(PlannerObservationStatus status, String detail) {
        String normalized = normalize(detail);
        if (status == PlannerObservationStatus.COMPLETED) {
            recordCompletedBreak(detail, normalized);
        }
        if (isTerminalBreakFailure(status, normalized)) {
            field(detail, "target").ifPresent(this::rememberTerminalBreakTarget);
        }
        if (status == PlannerObservationStatus.COMPLETED && normalized.contains("picked up inventory delta=")) {
            pendingDroppedItem = null;
        }
    }

    void recordIntentObservation(CommandIntent intent, PlannerObservation observation) {
        if (intent.kind() != IntentKind.BREAK_BLOCK) {
            return;
        }
        if (!isTerminalBreakFailure(observation.status(), normalize(observation.detail()))) {
            return;
        }
        instructionTarget(intent.instruction()).ifPresent(this::rememberTerminalBreakTarget);
    }

    String promptHint() {
        if (pendingDroppedItem == null) {
            return "";
        }
        return "Resource acquisition observation: previous break of " + pendingDroppedItem.blockId()
                + " at " + pendingDroppedItem.target()
                + " changed the world but did not verify item acquisition; nearby dropped item "
                + pendingDroppedItem.itemId()
                + " is pending. Prefer pickup_items_nearby matching=" + pendingDroppedItem.itemId()
                + " and verify inventory before more digging or claiming success.";
    }

    private void recordCompletedBreak(String detail, String normalized) {
        Optional<String> target = field(detail, "target");
        if (target.isEmpty() || !normalized.contains("block=") || !normalized.contains("nearby drop delta=")) {
            return;
        }
        rememberTerminalBreakTarget(target.get());
        if (!normalized.contains("inventory delta=none")) {
            return;
        }
        Optional<String> nearbyDropDelta = field(detail, "nearby_drop_delta");
        if (nearbyDropDelta.isEmpty() || nearbyDropDelta.get().equals("none")) {
            return;
        }
        String itemId = firstItemId(nearbyDropDelta.get());
        if (itemId.isBlank()) {
            return;
        }
        pendingDroppedItem = new PendingDroppedItem(
                field(detail, "block").orElse("unknown_block"),
                target.get(),
                itemId
        );
    }

    private boolean isTerminalBreakFailure(PlannerObservationStatus status, String normalized) {
        if (status != PlannerObservationStatus.REJECTED && status != PlannerObservationStatus.FAILED
                && status != PlannerObservationStatus.CANCELLED) {
            return false;
        }
        return normalized.contains("block not breakable") || normalized.contains("block target unavailable")
                || normalized.contains("block policy never break") || normalized.contains("missing required harvest tool");
    }

    private String pendingPickupSuffix() {
        if (pendingDroppedItem == null) {
            return "";
        }
        return "; dropped item pending, call pickup_items_nearby matching=" + pendingDroppedItem.itemId();
    }

    private void rememberTerminalBreakTarget(String target) {
        recentTerminalBreakTargets.remove(target);
        recentTerminalBreakTargets.addLast(target);
        while (recentTerminalBreakTargets.size() > MAX_RECENT_BREAK_TARGETS) {
            recentTerminalBreakTargets.removeFirst();
        }
    }

    private static Optional<String> instructionTarget(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return Optional.empty();
        }
        String[] parts = instruction.trim().split("\\s+");
        if (parts.length != 3) {
            return Optional.empty();
        }
        return Optional.of(parts[0] + "," + parts[1] + "," + parts[2]);
    }

    private static Optional<String> field(String detail, String fieldName) {
        String marker = fieldName + "=";
        int start = detail.indexOf(marker);
        if (start < 0) {
            return Optional.empty();
        }
        int valueStart = start + marker.length();
        int valueEnd = detail.indexOf(' ', valueStart);
        return Optional.of((valueEnd < 0 ? detail.substring(valueStart) : detail.substring(valueStart, valueEnd)).trim());
    }

    private static String firstItemId(String nearbyDropDelta) {
        String firstEntry = firstDropEntry(nearbyDropDelta);
        int countSeparator = firstEntry.indexOf(" x");
        if (countSeparator > 0) {
            return firstEntry.substring(0, countSeparator).trim();
        }
        return firstEntry.trim();
    }

    private static String firstDropEntry(String nearbyDropDelta) {
        String trimmed = nearbyDropDelta.trim();
        int comma = trimmed.indexOf(',');
        return comma > 0 ? trimmed.substring(0, comma) : trimmed;
    }

    private static String normalize(String detail) {
        return detail == null ? "" : detail.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
    }

    private record PendingDroppedItem(String blockId, String target, String itemId) {
    }
}
