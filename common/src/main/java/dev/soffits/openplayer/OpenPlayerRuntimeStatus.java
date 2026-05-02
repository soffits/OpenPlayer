package dev.soffits.openplayer;

import dev.soffits.openplayer.automation.AutomationBackendStatus;

public record OpenPlayerRuntimeStatus(
        IntentParserRuntimeStatus intentParser,
        AutomationBackendStatus automationBackend
) {
    public OpenPlayerRuntimeStatus {
        if (intentParser == null) {
            throw new IllegalArgumentException("intentParser cannot be null");
        }
        if (automationBackend == null) {
            throw new IllegalArgumentException("automationBackend cannot be null");
        }
    }
}
