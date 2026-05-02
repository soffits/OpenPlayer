package dev.soffits.openplayer;

public record IntentParserRuntimeStatus(
        boolean enabled,
        String endpointStatus,
        boolean modelConfigured,
        boolean apiKeyPresent
) {
    public IntentParserRuntimeStatus {
        if (endpointStatus == null || endpointStatus.isBlank()) {
            throw new IllegalArgumentException("endpointStatus cannot be blank");
        }
    }
}
