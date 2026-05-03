package dev.soffits.openplayer.character;

import java.util.List;

public record LocalCharacterRepositoryResult(List<LocalCharacterDefinition> characters,
                                             List<LocalCharacterValidationError> errors) {
    public LocalCharacterRepositoryResult {
        characters = List.copyOf(characters);
        errors = List.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
