package dev.soffits.openplayer.client;

public final class OpenPlayerClientStatus {
    private static String parserStatus = "unknown";
    private static String endpointStatus = "unknown";
    private static String modelStatus = "unknown";
    private static String apiKeyStatus = "unknown";
    private static String automationStatus = "unknown";

    private OpenPlayerClientStatus() {
    }

    public static void update(
            boolean parserEnabled,
            String endpoint,
            boolean modelConfigured,
            boolean apiKeyPresent,
            String automationName,
            String automationState
    ) {
        parserStatus = parserEnabled ? "enabled" : "disabled";
        endpointStatus = endpoint;
        modelStatus = modelConfigured ? "configured" : "not configured";
        apiKeyStatus = apiKeyPresent ? "present" : "not present";
        automationStatus = automationName + " (" + automationState.toLowerCase(java.util.Locale.ROOT) + ")";
    }

    public static String parserStatus() {
        return parserStatus;
    }

    public static String endpointStatus() {
        return endpointStatus;
    }

    public static String modelStatus() {
        return modelStatus;
    }

    public static String apiKeyStatus() {
        return apiKeyStatus;
    }

    public static String automationStatus() {
        return automationStatus;
    }
}
