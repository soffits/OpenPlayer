package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.automation.navigation.NavigationSnapshot;
import dev.soffits.openplayer.automation.navigation.NavigationState;
import dev.soffits.openplayer.automation.navigation.NavigationTargetKind;
import dev.soffits.openplayer.automation.navigation.NavigationTargetStatus;
import dev.soffits.openplayer.intent.IntentKind;
import java.util.ArrayList;
import java.util.List;

public final class AutomationControllerSnapshotTest {
    private AutomationControllerSnapshotTest() {
    }

    public static void main(String[] args) {
        idleSnapshotSummaryIsStable();
        activeSnapshotSummaryIncludesQueueOrder();
        queuedKindsAreDefensivelyCopiedAndUnmodifiable();
        nullFieldsThrowIllegalArgumentException();
        negativeValuesThrowIllegalArgumentException();
        monitorReasonIsNormalizedAndBounded();
        summaryIncludesBoundedNavigationTelemetry();
        pausedSnapshotPreservesActiveAndQueuedState();
    }

    private static void idleSnapshotSummaryIsStable() {
        AutomationControllerSnapshot snapshot = AutomationControllerSnapshot.idle(
                20,
                2,
                AutomationControllerMonitorStatus.IDLE,
                "idle",
                List.of(),
                3
        );

        require(!snapshot.active(), "idle snapshot must not be active");
        require(snapshot.activeKind() == null, "idle snapshot must not expose active kind");
        require(("hp=20, slot=2, active=idle, queued=0, queuedKinds=[], paused=false, interactCd=3, ctrl=idle, reason=idle, "
                + "ticks=0/0, nav=idle, navTarget=none:none, navDistSq=0.0, navReplans=0, navRecoveries=0, "
                + "navLoaded=unknown, navReachable=unknown, navReason=idle")
                .equals(snapshot.summary()), "idle summary must be deterministic");
    }

    private static void activeSnapshotSummaryIncludesQueueOrder() {
        AutomationControllerSnapshot snapshot = AutomationControllerSnapshot.active(
                17,
                4,
                IntentKind.MOVE,
                AutomationControllerMonitorStatus.ACTIVE,
                "active",
                12,
                1200,
                List.of(IntentKind.LOOK, IntentKind.COLLECT_ITEMS, IntentKind.PATROL),
                5,
                NavigationSnapshot.idle()
        );

        require(snapshot.active(), "active snapshot must be active");
        require(snapshot.activeKind() == IntentKind.MOVE, "active snapshot must expose active kind");
        require(("hp=17, slot=4, active=MOVE, queued=3, queuedKinds=[LOOK>COLLECT_ITEMS>PATROL], "
                + "paused=false, interactCd=5, ctrl=active, reason=active, ticks=12/1200, nav=idle, navTarget=none:none, "
                + "navDistSq=0.0, navReplans=0, navRecoveries=0, navLoaded=unknown, navReachable=unknown, "
                + "navReason=idle")
                .equals(snapshot.summary()), "active summary must include queue order and ticks");
    }

    private static void pausedSnapshotPreservesActiveAndQueuedState() {
        AutomationControllerSnapshot snapshot = AutomationControllerSnapshot.active(
                18,
                1,
                IntentKind.PATROL,
                AutomationControllerMonitorStatus.ACTIVE,
                "active",
                7,
                1200,
                List.of(IntentKind.LOOK, IntentKind.FOLLOW_OWNER),
                2,
                true,
                NavigationSnapshot.idle()
        );

        require(snapshot.paused(), "paused snapshot must expose paused state");
        require(snapshot.active(), "paused snapshot must preserve active task state");
        require(snapshot.activeKind() == IntentKind.PATROL, "paused snapshot must preserve active task kind");
        require(snapshot.queuedKinds().equals(List.of(IntentKind.LOOK, IntentKind.FOLLOW_OWNER)),
                "paused snapshot must preserve queued task order");
        require(snapshot.summary().contains("paused=true"), "paused summary must be deterministic");
    }

    private static void queuedKindsAreDefensivelyCopiedAndUnmodifiable() {
        List<IntentKind> queuedKinds = new ArrayList<>();
        queuedKinds.add(IntentKind.MOVE);
        AutomationControllerSnapshot snapshot = AutomationControllerSnapshot.idle(
                20,
                0,
                AutomationControllerMonitorStatus.IDLE,
                "idle",
                queuedKinds,
                0
        );

        queuedKinds.add(IntentKind.LOOK);

        require(snapshot.queuedKinds().equals(List.of(IntentKind.MOVE)), "snapshot must defensively copy queued kinds");
        expectUnsupportedOperation(() -> snapshot.queuedKinds().add(IntentKind.PATROL),
                "queued kinds must be unmodifiable");
    }

    private static void nullFieldsThrowIllegalArgumentException() {
        expectIllegalArgumentException(() -> new AutomationControllerSnapshot(
                20,
                0,
                true,
                null,
                AutomationControllerMonitorStatus.ACTIVE,
                "active",
                0,
                1,
                0,
                List.of(),
                0,
                NavigationSnapshot.idle()
        ), "active snapshot must reject null active kind");
        expectIllegalArgumentException(() -> new AutomationControllerSnapshot(
                20,
                0,
                false,
                null,
                null,
                "idle",
                0,
                0,
                0,
                List.of(),
                0,
                NavigationSnapshot.idle()
        ), "snapshot must reject null monitor status");
        expectIllegalArgumentException(() -> new AutomationControllerSnapshot(
                20,
                0,
                false,
                null,
                AutomationControllerMonitorStatus.IDLE,
                null,
                0,
                0,
                0,
                List.of(),
                0,
                NavigationSnapshot.idle()
        ), "snapshot must reject null monitor reason");
        expectIllegalArgumentException(() -> new AutomationControllerSnapshot(
                20,
                0,
                false,
                null,
                AutomationControllerMonitorStatus.IDLE,
                "idle",
                0,
                0,
                0,
                null,
                0,
                NavigationSnapshot.idle()
        ), "snapshot must reject null queued kinds");
        expectIllegalArgumentException(() -> new AutomationControllerSnapshot(
                20,
                0,
                false,
                null,
                AutomationControllerMonitorStatus.IDLE,
                "idle",
                0,
                0,
                0,
                List.of(),
                0,
                null
        ), "snapshot must reject null navigation snapshot");
    }

    private static void negativeValuesThrowIllegalArgumentException() {
        expectIllegalArgumentException(() -> snapshotWithValues(-1, 0, 0, 0, 0, 0),
                "snapshot must reject negative health");
        expectIllegalArgumentException(() -> snapshotWithValues(20, -1, 0, 0, 0, 0),
                "snapshot must reject negative selected hotbar slot");
        expectIllegalArgumentException(() -> snapshotWithCounts(-1, 0, 0, 0),
                "snapshot must reject negative elapsed ticks");
        expectIllegalArgumentException(() -> snapshotWithCounts(0, -1, 0, 0),
                "snapshot must reject negative max ticks");
        expectIllegalArgumentException(() -> snapshotWithCounts(0, 0, -1, 0),
                "snapshot must reject negative queued count");
        expectIllegalArgumentException(() -> snapshotWithCounts(0, 0, 0, -1),
                "snapshot must reject negative interaction cooldown");
        expectIllegalArgumentException(() -> snapshotWithCounts(0, 0, 2, 0),
                "snapshot must reject mismatched queued count");
    }

    private static void monitorReasonIsNormalizedAndBounded() {
        AutomationControllerSnapshot blankSnapshot = AutomationControllerSnapshot.idle(
                20,
                0,
                AutomationControllerMonitorStatus.IDLE,
                " \t ",
                List.of(),
                0
        );
        String longReason = "safe".repeat(40) + "\nsecret-looking overflow";
        AutomationControllerSnapshot longSnapshot = AutomationControllerSnapshot.idle(
                20,
                0,
                AutomationControllerMonitorStatus.CANCELLED,
                longReason,
                List.of(),
                0
        );

        require("none".equals(blankSnapshot.monitorReason()), "blank reason must be normalized");
        require(longSnapshot.monitorReason().length() == 96, "long reason must be bounded");
        require(!longSnapshot.monitorReason().contains("\n"), "long reason must normalize newlines");
    }

    private static void summaryIncludesBoundedNavigationTelemetry() {
        String target = "target-with-safe-prefix" + "x".repeat(120) + "\nraw-user-overflow";
        String reason = "reason-with-safe-prefix" + "y".repeat(140) + "\nraw-user-overflow";
        NavigationSnapshot navigationSnapshot = new NavigationSnapshot(
                NavigationState.RECOVERING,
                NavigationTargetKind.BLOCK,
                target,
                12.34D,
                2,
                1,
                reason,
                NavigationTargetStatus.YES,
                NavigationTargetStatus.UNKNOWN
        );
        AutomationControllerSnapshot snapshot = AutomationControllerSnapshot.active(
                19,
                1,
                IntentKind.BREAK_BLOCK,
                AutomationControllerMonitorStatus.ACTIVE,
                "active",
                40,
                600,
                List.of(),
                0,
                navigationSnapshot
        );
        String summary = snapshot.summary();

        require(summary.contains("hp=19"), "summary must preserve hp field");
        require(summary.contains("slot=1"), "summary must preserve slot field");
        require(summary.contains("active=BREAK_BLOCK"), "summary must preserve active field");
        require(summary.contains("queued=0"), "summary must preserve queued field");
        require(summary.contains("ctrl=active"), "summary must preserve ctrl field");
        require(summary.contains("reason=active"), "summary must preserve monitor reason field");
        require(summary.contains("ticks=40/600"), "summary must preserve ticks field");
        require(summary.contains("nav=recovering"), "summary must include navigation state");
        require(summary.contains("navTarget=block:target-with-safe-prefix"), "summary must include navigation target");
        require(summary.contains("navReplans=2"), "summary must include replan count");
        require(summary.contains("navRecoveries=1"), "summary must include recovery count");
        require(!summary.contains("raw-user-overflow"), "summary must bound navigation overflow");
        require(!summary.contains("\n"), "summary must normalize navigation newlines");
    }

    private static AutomationControllerSnapshot snapshotWithCounts(
            int elapsedTicks,
            int maxTicks,
            int queuedCommandCount,
            int interactionCooldownTicks
    ) {
        return snapshotWithValues(20, 0, elapsedTicks, maxTicks, queuedCommandCount, interactionCooldownTicks);
    }

    private static AutomationControllerSnapshot snapshotWithValues(
            int health,
            int selectedHotbarSlot,
            int elapsedTicks,
            int maxTicks,
            int queuedCommandCount,
            int interactionCooldownTicks
    ) {
        return new AutomationControllerSnapshot(
                health,
                selectedHotbarSlot,
                false,
                null,
                AutomationControllerMonitorStatus.IDLE,
                "idle",
                elapsedTicks,
                maxTicks,
                queuedCommandCount,
                List.of(),
                interactionCooldownTicks,
                NavigationSnapshot.idle()
        );
    }

    private static void expectIllegalArgumentException(Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (IllegalArgumentException exception) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void expectUnsupportedOperation(Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (UnsupportedOperationException exception) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
