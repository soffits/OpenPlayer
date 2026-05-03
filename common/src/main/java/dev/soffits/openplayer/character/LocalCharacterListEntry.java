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
        return from(character, lifecycleStatus, null);
    }

    public static LocalCharacterListEntry from(LocalCharacterDefinition character, String lifecycleStatus,
                                               LocalSkinPathResolver localSkinPathResolver) {
        return from(character, lifecycleStatus, localSkinPathResolver, ignored -> "unknown");
    }

    public static LocalCharacterListEntry from(LocalCharacterDefinition character, String lifecycleStatus,
                                               LocalSkinPathResolver localSkinPathResolver,
                                               ConversationStatusResolver conversationStatusResolver) {
        if (character == null) {
            throw new IllegalArgumentException("character cannot be null");
        }
        if (conversationStatusResolver == null) {
            throw new IllegalArgumentException("conversationStatusResolver cannot be null");
        }
        String skinStatus = skinStatus(character, localSkinPathResolver);
        String conversationStatus = character.conversationPrompt() == null && character.conversationSettings() == null
                ? "not configured"
                : conversationStatusResolver.conversationStatus(character);
        return new LocalCharacterListEntry(
                character.id(),
                character.displayName(),
                character.description(),
                skinStatus,
                lifecycleStatus,
                conversationStatus
        );
    }

    private static String skinStatus(LocalCharacterDefinition character, LocalSkinPathResolver localSkinPathResolver) {
        if (character.localSkinFile() != null) {
            if (localSkinPathResolver != null) {
                LocalSkinPathResolution resolution = localSkinPathResolver.resolve(character.localSkinFile());
                return resolution.isResolved() ? "local file" : "local file unavailable";
            }
            return "local file";
        }
        if (character.skinTexture() != null) {
            return "resource";
        }
        return "default";
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

    @FunctionalInterface
    public interface ConversationStatusResolver {
        String conversationStatus(LocalCharacterDefinition character);
    }
}
