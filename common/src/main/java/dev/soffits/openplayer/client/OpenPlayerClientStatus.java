package dev.soffits.openplayer.client;

import dev.soffits.openplayer.character.LocalCharacterListEntry;
import dev.soffits.openplayer.character.LocalCharacterListView;
import java.util.List;

public final class OpenPlayerClientStatus {
    private static String parserStatus = "unknown";
    private static boolean parserAvailable;
    private static String endpointStatus = "unknown";
    private static String modelStatus = "unknown";
    private static String apiKeyStatus = "unknown";
    private static String automationStatus = "unknown";
    private static String characterListStatus = "loading";
    private static String characterFileOperationStatus = "none";
    private static LocalCharacterListView characterList = new LocalCharacterListView(List.of(), List.of());
    private static List<String> importFileNames = List.of();

    private OpenPlayerClientStatus() {
    }

    public static void update(
            boolean parserAvailable,
            String endpoint,
            String endpointSource,
            boolean modelConfigured,
            String modelSource,
            boolean apiKeyPresent,
            String apiKeySource,
            String automationName,
            String automationState
    ) {
        OpenPlayerClientStatus.parserAvailable = parserAvailable;
        parserStatus = parserAvailable ? "enabled" : "disabled";
        endpointStatus = endpoint + " [" + endpointSource + "]";
        modelStatus = (modelConfigured ? "configured" : "not configured") + " [" + modelSource + "]";
        apiKeyStatus = (apiKeyPresent ? "present" : "not present") + " [" + apiKeySource + "]";
        automationStatus = automationName + " (" + automationState.toLowerCase(java.util.Locale.ROOT) + ")";
    }

    public static String parserStatus() {
        return parserStatus;
    }

    public static boolean parserAvailable() {
        return parserAvailable;
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

    public static void updateCharacters(LocalCharacterListView value) {
        characterList = value;
        if (!value.errors().isEmpty()) {
            characterListStatus = "validation errors";
        } else if (value.characters().isEmpty()) {
            characterListStatus = "empty";
        } else {
            characterListStatus = "loaded";
        }
    }

    public static void updateImportFileNames(List<String> values) {
        importFileNames = values == null ? List.of() : List.copyOf(values);
    }

    public static void updateCharacterFileOperationStatus(String value) {
        characterFileOperationStatus = value == null || value.isBlank() ? "none" : value;
    }

    public static String characterListStatus() {
        return characterListStatus;
    }

    public static List<LocalCharacterListEntry> characters() {
        return characterList.characters();
    }

    public static List<String> characterErrors() {
        return characterList.errors();
    }

    public static String characterFileOperationStatus() {
        return characterFileOperationStatus;
    }

    public static List<String> importFileNames() {
        return importFileNames;
    }
}
