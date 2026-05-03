package dev.soffits.openplayer.automation.workstation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WorkstationDiagnostics {
    private static final int MAX_SUMMARY_TARGETS = 4;

    private WorkstationDiagnostics() {
    }

    public static String adapterUnavailable(WorkstationKind kind, String recipeType) {
        return kind.id() + " adapter unavailable for " + recipeType
                + "; add a safe workstation adapter before execution";
    }

    public static String noLoadedTarget(WorkstationCapability capability) {
        return "no loaded nearby " + capability.kind().id() + " workstation with "
                + capability.adapterId() + " adapter";
    }

    public static String targetSummary(List<WorkstationTarget> targets) {
        if (targets.isEmpty()) {
            return "none";
        }
        List<WorkstationTarget> ordered = new ArrayList<>(targets);
        ordered.sort(Comparator
                .comparing((WorkstationTarget target) -> target.capability().kind().id())
                .thenComparingInt(target -> target.blockPos().getX())
                .thenComparingInt(target -> target.blockPos().getY())
                .thenComparingInt(target -> target.blockPos().getZ()));

        List<String> entries = new ArrayList<>();
        int limit = Math.min(MAX_SUMMARY_TARGETS, ordered.size());
        for (int index = 0; index < limit; index++) {
            WorkstationTarget target = ordered.get(index);
            entries.add(target.capability().kind().id() + "@" + target.blockPos().toShortString());
        }
        if (ordered.size() > limit) {
            entries.add("+" + (ordered.size() - limit) + " more");
        }
        return String.join(", ", entries);
    }
}
