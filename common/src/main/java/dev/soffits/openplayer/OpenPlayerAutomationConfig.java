package dev.soffits.openplayer;

import dev.soffits.openplayer.automation.AutomationBackend;
import dev.soffits.openplayer.automation.AutomationBackendStatus;
import dev.soffits.openplayer.automation.AutomationBackendState;
import dev.soffits.openplayer.automation.DisabledAutomationBackend;
import dev.soffits.openplayer.automation.VanillaAutomationBackend;
import java.util.Locale;

public final class OpenPlayerAutomationConfig {
    public static final String BACKEND_KEY = "OPENPLAYER_AUTOMATION_BACKEND";

    private OpenPlayerAutomationConfig() {
    }

    public static AutomationBackend createBackend() {
        String backendName = configuredBackendName();
        if (backendName.equals(DisabledAutomationBackend.NAME)) {
            return new DisabledAutomationBackend();
        }
        return new VanillaAutomationBackend();
    }

    public static AutomationBackendStatus status() {
        String backendName = configuredBackendName();
        if (backendName.equals(DisabledAutomationBackend.NAME)) {
            return new DisabledAutomationBackend().status();
        }
        if (!backendName.equals(VanillaAutomationBackend.NAME)) {
            return new AutomationBackendStatus(
                    VanillaAutomationBackend.NAME,
                    AutomationBackendState.AVAILABLE,
                    "Unknown backend requested; using vanilla Minecraft navigation"
            );
        }
        return new VanillaAutomationBackend().status();
    }

    private static String configuredBackendName() {
        String value = configValue(BACKEND_KEY);
        if (value == null || value.isBlank()) {
            return VanillaAutomationBackend.NAME;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String configValue(String key) {
        String propertyValue = System.getProperty(key);
        if (propertyValue != null) {
            return propertyValue;
        }
        return System.getenv(key);
    }
}
