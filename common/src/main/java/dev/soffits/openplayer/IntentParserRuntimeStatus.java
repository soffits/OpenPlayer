package dev.soffits.openplayer;

public record IntentParserRuntimeStatus(
        boolean enabled,
        String endpointStatus,
        String endpointSource,
        boolean modelConfigured,
        String modelSource,
        boolean apiKeyPresent,
        String apiKeySource
) {
    public IntentParserRuntimeStatus {
        if (endpointStatus == null || endpointStatus.isBlank()) {
            throw new IllegalArgumentException("endpointStatus cannot be blank");
        }
        if (endpointSource == null || endpointSource.isBlank()) {
            throw new IllegalArgumentException("endpointSource cannot be blank");
        }
        if (modelSource == null || modelSource.isBlank()) {
            throw new IllegalArgumentException("modelSource cannot be blank");
        }
        if (apiKeySource == null || apiKeySource.isBlank()) {
            throw new IllegalArgumentException("apiKeySource cannot be blank");
        }
    }
}
