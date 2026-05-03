package dev.soffits.openplayer.character;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record LocalCharacterListView(List<LocalCharacterListEntry> characters, List<String> errors) {
    public LocalCharacterListView {
        characters = List.copyOf(characters);
        errors = List.copyOf(errors);
    }

    public static LocalCharacterListView fromRepositoryResult(LocalCharacterRepositoryResult result,
                                                              CharacterLifecycleResolver lifecycleResolver) {
        if (result == null) {
            throw new IllegalArgumentException("result cannot be null");
        }
        if (lifecycleResolver == null) {
            throw new IllegalArgumentException("lifecycleResolver cannot be null");
        }
        List<LocalCharacterListEntry> entries = new ArrayList<>();
        for (LocalCharacterDefinition character : result.characters()) {
            entries.add(LocalCharacterListEntry.from(character, lifecycleResolver.lifecycleStatus(character)));
        }
        List<String> safeErrors = new ArrayList<>();
        for (LocalCharacterValidationError error : result.errors()) {
            safeErrors.add(safeError(error));
        }
        return new LocalCharacterListView(entries, safeErrors);
    }

    public static LocalCharacterListView fromAssignmentRepositoryResult(LocalAssignmentRepositoryResult result,
                                                                        AssignmentLifecycleResolver lifecycleResolver,
                                                                        LocalSkinPathResolver localSkinPathResolver,
                                                                        LocalCharacterListEntry.ConversationStatusResolver conversationStatusResolver) {
        if (result == null) {
            throw new IllegalArgumentException("result cannot be null");
        }
        if (lifecycleResolver == null) {
            throw new IllegalArgumentException("lifecycleResolver cannot be null");
        }
        if (conversationStatusResolver == null) {
            throw new IllegalArgumentException("conversationStatusResolver cannot be null");
        }
        List<LocalCharacterListEntry> entries = new ArrayList<>();
        for (LocalAssignmentDefinition assignment : result.assignments()) {
            LocalCharacterDefinition character = findCharacter(result.characters(), assignment.characterId());
            if (character != null) {
                entries.add(LocalCharacterListEntry.from(
                        assignment,
                        character,
                        lifecycleResolver.lifecycleStatus(assignment, character),
                        localSkinPathResolver,
                        conversationStatusResolver
                ));
            }
        }
        List<String> safeErrors = new ArrayList<>();
        for (LocalCharacterValidationError error : result.errors()) {
            safeErrors.add(safeError(error));
        }
        return new LocalCharacterListView(entries, safeErrors);
    }

    private static LocalCharacterDefinition findCharacter(List<LocalCharacterDefinition> characters, String characterId) {
        for (LocalCharacterDefinition character : characters) {
            if (character.id().equals(characterId)) {
                return character;
            }
        }
        return null;
    }

    public static LocalCharacterListView fromRepositoryResult(LocalCharacterRepositoryResult result,
                                                               CharacterLifecycleResolver lifecycleResolver,
                                                               LocalSkinPathResolver localSkinPathResolver) {
        return fromRepositoryResult(result, lifecycleResolver, localSkinPathResolver, ignored -> "unknown");
    }

    public static LocalCharacterListView fromRepositoryResult(LocalCharacterRepositoryResult result,
                                                               CharacterLifecycleResolver lifecycleResolver,
                                                               LocalSkinPathResolver localSkinPathResolver,
                                                               LocalCharacterListEntry.ConversationStatusResolver conversationStatusResolver) {
        if (result == null) {
            throw new IllegalArgumentException("result cannot be null");
        }
        if (lifecycleResolver == null) {
            throw new IllegalArgumentException("lifecycleResolver cannot be null");
        }
        if (conversationStatusResolver == null) {
            throw new IllegalArgumentException("conversationStatusResolver cannot be null");
        }
        List<LocalCharacterListEntry> entries = new ArrayList<>();
        for (LocalCharacterDefinition character : result.characters()) {
            entries.add(LocalCharacterListEntry.from(
                    character,
                    lifecycleResolver.lifecycleStatus(character),
                    localSkinPathResolver,
                    conversationStatusResolver
            ));
        }
        List<String> safeErrors = new ArrayList<>();
        for (LocalCharacterValidationError error : result.errors()) {
            safeErrors.add(safeError(error));
        }
        return new LocalCharacterListView(entries, safeErrors);
    }

    private static String safeError(LocalCharacterValidationError error) {
        Path file = error.file();
        String message = safeMessage(error.message());
        if (file == null || file.getFileName() == null) {
            return message;
        }
        return file.getFileName() + ": " + message;
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unable to load character file";
        }
        String sanitized = message.replace('\\', '/');
        sanitized = sanitized.replaceAll("[A-Za-z]:/[^\\s:]+/([^/\\s:]+)", "$1");
        sanitized = sanitized.replaceAll("/(?:[^/\\s:]+/)+([^/\\s:]+)", "$1");
        return sanitized;
    }

    @FunctionalInterface
    public interface CharacterLifecycleResolver {
        String lifecycleStatus(LocalCharacterDefinition character);
    }

    @FunctionalInterface
    public interface AssignmentLifecycleResolver {
        String lifecycleStatus(LocalAssignmentDefinition assignment, LocalCharacterDefinition character);
    }
}
