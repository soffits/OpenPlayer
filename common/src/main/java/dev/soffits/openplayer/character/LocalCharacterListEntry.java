package dev.soffits.openplayer.character;

import java.util.ArrayList;
import java.util.List;

public record LocalCharacterListEntry(String id, String assignmentId, String characterId, String displayName,
                                       String description, String localSkinFile, String defaultRoleId,
                                       String conversationPrompt, String conversationSettings,
                                       boolean allowWorldActions, String skinStatus, String lifecycleStatus,
                                       String conversationStatus, List<String> conversationEvents) {
    public LocalCharacterListEntry {
        id = requireText(id, "id");
        assignmentId = requireText(assignmentId, "assignmentId");
        characterId = requireText(characterId, "characterId");
        displayName = requireText(displayName, "displayName");
        description = normalize(description);
        localSkinFile = normalize(localSkinFile);
        defaultRoleId = normalize(defaultRoleId);
        conversationPrompt = normalize(conversationPrompt);
        conversationSettings = normalize(conversationSettings);
        skinStatus = requireText(skinStatus, "skinStatus");
        lifecycleStatus = requireText(lifecycleStatus, "lifecycleStatus");
        conversationStatus = requireText(conversationStatus, "conversationStatus");
        conversationEvents = safeEvents(conversationEvents);
    }

    public LocalCharacterListEntry(String id, String assignmentId, String characterId, String displayName,
                                    String description, String skinStatus, String lifecycleStatus,
                                    String conversationStatus) {
        this(id, assignmentId, characterId, displayName, description, "", "", "", "", false, skinStatus, lifecycleStatus,
                conversationStatus, List.of());
    }

    public LocalCharacterListEntry(String id, String displayName, String description, String skinStatus,
                                     String lifecycleStatus, String conversationStatus) {
        this(id, id, id, displayName, description, "", "", "", "", false, skinStatus, lifecycleStatus,
                conversationStatus, List.of());
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
                character.id(),
                character.id(),
                character.displayName(),
                character.description(),
                character.localSkinFile(),
                character.defaultRoleId(),
                character.conversationPrompt(),
                character.conversationSettings(),
                character.allowWorldActions(),
                skinStatus,
                lifecycleStatus,
                conversationStatus,
                List.of()
        );
    }

    public static LocalCharacterListEntry from(LocalAssignmentDefinition assignment,
                                               LocalCharacterDefinition character,
                                               String lifecycleStatus,
                                               LocalSkinPathResolver localSkinPathResolver,
                                               ConversationStatusResolver conversationStatusResolver) {
        return from(assignment, character, lifecycleStatus, localSkinPathResolver, conversationStatusResolver,
                (ignoredAssignment, ignoredCharacter) -> List.of());
    }

    public static LocalCharacterListEntry from(LocalAssignmentDefinition assignment,
                                               LocalCharacterDefinition character,
                                               String lifecycleStatus,
                                               LocalSkinPathResolver localSkinPathResolver,
                                               ConversationStatusResolver conversationStatusResolver,
                                               ConversationEventResolver conversationEventResolver) {
        if (assignment == null) {
            throw new IllegalArgumentException("assignment cannot be null");
        }
        if (character == null) {
            throw new IllegalArgumentException("character cannot be null");
        }
        if (conversationStatusResolver == null) {
            throw new IllegalArgumentException("conversationStatusResolver cannot be null");
        }
        if (conversationEventResolver == null) {
            throw new IllegalArgumentException("conversationEventResolver cannot be null");
        }
        String skinStatus = skinStatus(character, localSkinPathResolver);
        String conversationStatus = character.conversationPrompt() == null && character.conversationSettings() == null
                ? "not configured"
                : conversationStatusResolver.conversationStatus(character);
        List<String> conversationEvents = character.conversationPrompt() == null && character.conversationSettings() == null
                ? List.of()
                : conversationEventResolver.conversationEvents(assignment, character);
        return new LocalCharacterListEntry(
                character.id(),
                assignment.id(),
                assignment.characterId(),
                assignment.resolvedDisplayName(character),
                character.description(),
                character.localSkinFile(),
                character.defaultRoleId(),
                character.conversationPrompt(),
                character.conversationSettings(),
                character.allowWorldActions(),
                skinStatus,
                lifecycleStatus,
                conversationStatus,
                conversationEvents
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

    private static List<String> safeEvents(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> safeValues = new ArrayList<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isEmpty()) {
                safeValues.add(normalized);
            }
        }
        return List.copyOf(safeValues);
    }

    @FunctionalInterface
    public interface ConversationStatusResolver {
        String conversationStatus(LocalCharacterDefinition character);
    }

    @FunctionalInterface
    public interface ConversationEventResolver {
        List<String> conversationEvents(LocalAssignmentDefinition assignment, LocalCharacterDefinition character);
    }
}
