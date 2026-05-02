package dev.soffits.openplayer;

import dev.soffits.openplayer.automation.AutomationBackend;
import dev.soffits.openplayer.automation.AutomationBackendStatus;
import dev.soffits.openplayer.automation.AutomationBackendState;
import dev.soffits.openplayer.automation.BaritoneAutomationBackend;
import dev.soffits.openplayer.automation.DisabledAutomationBackend;
import dev.soffits.openplayer.automation.VanillaAutomationBackend;
import java.util.Locale;

public final class OpenPlayerAutomationConfig {
    public static final String BACKEND_KEY = "OPENPLAYER_AUTOMATION_BACKEND";
    public static final String ALLOW_WORLD_ACTIONS_ENV_KEY = "OPENPLAYER_AUTOMATION_ALLOW_WORLD_ACTIONS";
    public static final String ALLOW_WORLD_ACTIONS_PROPERTY_KEY = "openplayer.automation.allowWorldActions";

    private OpenPlayerAutomationConfig() {
    }

    public static AutomationBackend createBackend() {
        String backendName = configuredBackendName();
        if (backendName.equals(DisabledAutomationBackend.NAME)) {
            return new DisabledAutomationBackend();
        }
        if (backendName.equals(BaritoneAutomationBackend.NAME)) {
            return new BaritoneAutomationBackend();
        }
        return new VanillaAutomationBackend();
    }

    public static AutomationBackendStatus status() {
        String backendName = configuredBackendName();
        if (backendName.equals(DisabledAutomationBackend.NAME)) {
            return new DisabledAutomationBackend().status();
        }
        if (backendName.equals(BaritoneAutomationBackend.NAME)) {
            return new BaritoneAutomationBackend().status();
        }
        if (!backendName.equals(VanillaAutomationBackend.NAME)) {
            return new AutomationBackendStatus(
                    backendName,
                    AutomationBackendState.UNAVAILABLE,
                    "Unknown automation backend requested; falling back to vanilla Minecraft navigation"
            );
        }
        return new VanillaAutomationBackend().status();
    }

    public static boolean allowWorldActions() {
        String propertyValue = System.getProperty(ALLOW_WORLD_ACTIONS_PROPERTY_KEY);
        String value = propertyValue == null ? System.getenv(ALLOW_WORLD_ACTIONS_ENV_KEY) : propertyValue;
        return value != null && value.trim().equalsIgnoreCase("true");
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
