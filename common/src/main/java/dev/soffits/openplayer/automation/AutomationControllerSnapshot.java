package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.automation.navigation.NavigationSnapshot;
import dev.soffits.openplayer.intent.IntentKind;
import java.util.List;

public record AutomationControllerSnapshot(
        int health,
        int selectedHotbarSlot,
        boolean active,
        IntentKind activeKind,
        AutomationControllerMonitorStatus monitorStatus,
        String monitorReason,
        int elapsedTicks,
        int maxTicks,
        int queuedCommandCount,
        List<IntentKind> queuedKinds,
        int interactionCooldownTicks,
        NavigationSnapshot navigationSnapshot
) {
    public AutomationControllerSnapshot {
        if (health < 0) {
            throw new IllegalArgumentException("health must be non-negative");
        }
        if (selectedHotbarSlot < 0) {
            throw new IllegalArgumentException("selectedHotbarSlot must be non-negative");
        }
        if (active && activeKind == null) {
            throw new IllegalArgumentException("activeKind cannot be null when active");
        }
        if (monitorStatus == null) {
            throw new IllegalArgumentException("monitorStatus cannot be null");
        }
        if (monitorReason == null) {
            throw new IllegalArgumentException("monitorReason cannot be null");
        }
        if (queuedKinds == null) {
            throw new IllegalArgumentException("queuedKinds cannot be null");
        }
        if (navigationSnapshot == null) {
            throw new IllegalArgumentException("navigationSnapshot cannot be null");
        }
        if (elapsedTicks < 0) {
            throw new IllegalArgumentException("elapsedTicks must be non-negative");
        }
        if (maxTicks < 0) {
            throw new IllegalArgumentException("maxTicks must be non-negative");
        }
        if (queuedCommandCount < 0) {
            throw new IllegalArgumentException("queuedCommandCount must be non-negative");
        }
        if (interactionCooldownTicks < 0) {
            throw new IllegalArgumentException("interactionCooldownTicks must be non-negative");
        }
        queuedKinds = List.copyOf(queuedKinds);
        if (queuedCommandCount != queuedKinds.size()) {
            throw new IllegalArgumentException("queuedCommandCount must equal queuedKinds size");
        }
        monitorReason = AutomationControllerMonitor.bounded(monitorReason);
    }

    public static AutomationControllerSnapshot idle(
            int health,
            int selectedHotbarSlot,
            AutomationControllerMonitorStatus monitorStatus,
            String monitorReason,
            List<IntentKind> queuedKinds,
            int interactionCooldownTicks
    ) {
        return new AutomationControllerSnapshot(
                health,
                selectedHotbarSlot,
                false,
                null,
                monitorStatus,
                monitorReason,
                0,
                0,
                queuedKinds.size(),
                queuedKinds,
                interactionCooldownTicks,
                NavigationSnapshot.idle()
        );
    }

    public static AutomationControllerSnapshot active(
            int health,
            int selectedHotbarSlot,
            IntentKind activeKind,
            AutomationControllerMonitorStatus monitorStatus,
            String monitorReason,
            int elapsedTicks,
            int maxTicks,
            List<IntentKind> queuedKinds,
            int interactionCooldownTicks,
            NavigationSnapshot navigationSnapshot
    ) {
        return new AutomationControllerSnapshot(
                health,
                selectedHotbarSlot,
                true,
                activeKind,
                monitorStatus,
                monitorReason,
                elapsedTicks,
                maxTicks,
                queuedKinds.size(),
                queuedKinds,
                interactionCooldownTicks,
                navigationSnapshot
        );
    }

    public String summary() {
        String activeValue = active ? activeKind.name() : "idle";
        return "hp=" + health
                + ", slot=" + selectedHotbarSlot
                + ", active=" + activeValue
                + ", queued=" + queuedCommandCount
                + ", queuedKinds=" + queuedKindsSummary()
                + ", interactCd=" + interactionCooldownTicks
                + ", ctrl=" + monitorStatus.name().toLowerCase()
                + ", reason=" + monitorReason
                + ", ticks=" + elapsedTicks + "/" + maxTicks
                + ", " + navigationSnapshot.summary();
    }

    private String queuedKindsSummary() {
        if (queuedKinds.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < queuedKinds.size(); index++) {
            if (index > 0) {
                builder.append('>');
            }
            builder.append(queuedKinds.get(index).name());
        }
        return builder.append(']').toString();
    }
}
