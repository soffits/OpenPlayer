package dev.soffits.openplayer.character;

public record LocalCharacterListEntry(String id, String displayName, String description, String skinStatus,
                                      String lifecycleStatus, String conversationStatus) {
    public LocalCharacterListEntry {
        id = requireText(id, "id");
        displayName = requireText(displayName, "displayName");
        description = normalize(description);
        skinStatus = requireText(skinStatus, "skinStatus");
        lifecycleStatus = requireText(lifecycleStatus, "lifecycleStatus");
        conversationStatus = requireText(conversationStatus, "conversationStatus");
    }

    public static LocalCharacterListEntry from(LocalCharacterDefinition character, String lifecycleStatus) {
        if (character == null) {
            throw new IllegalArgumentException("character cannot be null");
        }
        String skinStatus = character.localSkinFile() == null && character.skinTexture() == null
                ? "default"
                : "configured";
        String conversationStatus = character.conversationPrompt() == null && character.conversationSettings() == null
                ? "not configured"
                : "configured for later phases";
        return new LocalCharacterListEntry(
                character.id(),
                character.displayName(),
                character.description(),
                skinStatus,
                lifecycleStatus,
                conversationStatus
        );
    }

    private static String requireText(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
