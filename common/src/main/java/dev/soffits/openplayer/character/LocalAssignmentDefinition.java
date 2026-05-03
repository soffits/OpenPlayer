package dev.soffits.openplayer.character;

import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.api.AiPlayerNpcSpec;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.api.NpcProfileSpec;
import dev.soffits.openplayer.api.NpcRoleId;
import dev.soffits.openplayer.api.NpcSpawnLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public record LocalAssignmentDefinition(String id, String characterId, String displayName) {
    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_DISPLAY_NAME_LENGTH = 32;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{1,63}");
    private static final Pattern SECRET_MARKER_PATTERN = Pattern.compile(
            "(^|[^a-z0-9])(?:api[ _-]?key|access[ _-]?token|client[ _-]?secret|token|bearer|password|passwd|cookie|secret|credential)(?=$|[^a-z0-9])",
            Pattern.CASE_INSENSITIVE
    );

    public LocalAssignmentDefinition {
        id = normalizeRequired(id, "id");
        characterId = normalizeRequired(characterId, "characterId");
        displayName = normalizeOptional(displayName);
        List<String> errors = validate(id, characterId, displayName);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }

    public static LocalAssignmentDefinition defaultFor(LocalCharacterDefinition character) {
        if (character == null) {
            throw new IllegalArgumentException("character cannot be null");
        }
        return new LocalAssignmentDefinition(character.id(), character.id(), null);
    }

    public Optional<String> optionalDisplayName() {
        return Optional.ofNullable(displayName);
    }

    public String resolvedDisplayName(LocalCharacterDefinition character) {
        return displayName == null ? character.displayName() : displayName;
    }

    public NpcRoleId toSessionRoleId() {
        return new NpcRoleId(OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + id);
    }

    public AiPlayerNpcSpec toNpcSpec(LocalCharacterDefinition character, NpcOwnerId ownerId,
                                     NpcSpawnLocation spawnLocation) {
        if (character == null) {
            throw new IllegalArgumentException("character cannot be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId cannot be null");
        }
        if (spawnLocation == null) {
            throw new IllegalArgumentException("spawnLocation cannot be null");
        }
        return new AiPlayerNpcSpec(
                toSessionRoleId(),
                ownerId,
                new NpcProfileSpec(resolvedDisplayName(character), character.skinTexture()),
                spawnLocation,
                character.allowWorldActions()
        );
    }

    public static List<String> validate(String id, String characterId, String displayName) {
        List<String> errors = new ArrayList<>();
        validateId(errors, "id", id);
        validateId(errors, "characterId", characterId);
        validateOptionalDisplayName(errors, displayName);
        return List.copyOf(errors);
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void validateId(List<String> errors, String fieldName, String value) {
        if (value == null || value.isBlank()) {
            errors.add(fieldName + " is required");
            return;
        }
        if (value.length() > MAX_ID_LENGTH || !ID_PATTERN.matcher(value).matches()) {
            errors.add(fieldName + " must be 2-64 lowercase ASCII characters using letters, digits, underscore, or hyphen, and must start with a letter or digit");
        }
        validateNoSecretMarkers(errors, fieldName, value);
    }

    private static void validateOptionalDisplayName(List<String> errors, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (value.length() > MAX_DISPLAY_NAME_LENGTH) {
            errors.add("displayName must be 1-32 characters");
        }
        if (containsControlCharacter(value)) {
            errors.add("displayName must not contain control characters");
        }
        if (value.startsWith("/") || value.startsWith("\\") || value.matches("^[A-Za-z]:.*")) {
            errors.add("displayName must not contain absolute paths");
        }
        validateNoSecretMarkers(errors, "displayName", value);
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static void validateNoSecretMarkers(List<String> errors, String fieldName, String value) {
        if (SECRET_MARKER_PATTERN.matcher(value).find()) {
            errors.add(fieldName + " must not contain secret-like labels or credentials");
        }
    }
}
