package dev.soffits.openplayer.automation.navigation;

import java.util.List;
import net.minecraft.core.BlockPos;

public final class LoadedAreaNavigatorTest {
    private LoadedAreaNavigatorTest() {
    }

    public static void main(String[] args) {
        selectsNearestCandidateDeterministically();
        handlesEmptyCandidateList();
        boundsDiagnosticsSummary();
    }

    private static void selectsNearestCandidateDeterministically() {
        LoadedAreaNavigator.SearchCandidate<String> selected = LoadedAreaNavigator.nearestCandidate(List.of(
                new LoadedAreaNavigator.SearchCandidate<>("far", 9.0D, new BlockPos(0, 64, 3)),
                new LoadedAreaNavigator.SearchCandidate<>("tie-z", 4.0D, new BlockPos(0, 64, 2)),
                new LoadedAreaNavigator.SearchCandidate<>("tie-x", 4.0D, new BlockPos(1, 64, 0)),
                new LoadedAreaNavigator.SearchCandidate<>("tie-y", 4.0D, new BlockPos(0, 63, 2))
        ));
        require(selected != null, "nearest candidate must be selected");
        require(selected.value().equals("tie-y"), "equal-distance candidates must sort by y, then x, then z");
    }

    private static void handlesEmptyCandidateList() {
        require(LoadedAreaNavigator.nearestCandidate(List.of()) == null, "empty candidate list must return null");
        require(LoadedAreaNavigator.nearestCandidate(null) == null, "null candidate list must return null");
    }

    private static void boundsDiagnosticsSummary() {
        LoadedAreaNavigator.SearchDiagnostics diagnostics = new LoadedAreaNavigator.SearchDiagnostics(
                16, 32768, 12, 3, true
        );
        require(diagnostics.summary().equals("radius=16 scanned=32768 skippedUnloaded=12 matched=3 capped=true reason=none"),
                "diagnostics summary must be deterministic");
        LoadedAreaNavigator.SearchDiagnostics invalid = LoadedAreaNavigator.SearchDiagnostics.invalid(
                "missing target with a deliberately long diagnostic string that must remain bounded for status output"
        );
        require(invalid.summary().contains("radius=0 scanned=0 skippedUnloaded=0 matched=0 capped=false reason="),
                "invalid diagnostics must include deterministic zero counts");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
