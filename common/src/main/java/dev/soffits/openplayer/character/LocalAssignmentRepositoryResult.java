package dev.soffits.openplayer.character;

import java.util.List;

public record LocalAssignmentRepositoryResult(List<LocalAssignmentDefinition> assignments,
                                              List<LocalCharacterDefinition> characters,
                                              List<LocalCharacterValidationError> errors) {
    public LocalAssignmentRepositoryResult {
        assignments = List.copyOf(assignments);
        characters = List.copyOf(characters);
        errors = List.copyOf(errors);
    }
}
