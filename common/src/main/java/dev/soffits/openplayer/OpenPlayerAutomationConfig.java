package dev.soffits.openplayer;

import dev.soffits.openplayer.automation.AutomationBackend;
import dev.soffits.openplayer.automation.AutomationBackendStatus;
import dev.soffits.openplayer.automation.VanillaAutomationBackend;

public final class OpenPlayerAutomationConfig {
    private OpenPlayerAutomationConfig() {
    }

    public static AutomationBackend createBackend() {
        return new VanillaAutomationBackend();
    }

    public static AutomationBackendStatus status() {
        return new VanillaAutomationBackend().status();
    }
}
