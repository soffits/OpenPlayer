package dev.soffits.openplayer.runtime.objective;

import java.util.List;
import java.util.Map;

public final class BuildObjectiveValidator {
    private BuildObjectiveValidator() {
    }

    public static ObjectiveProgress unsupported() {
        return new ObjectiveProgress(false, false, Map.of(), List.of("build objective diff adapter is unavailable"),
                "report missing build adapter");
    }
}
