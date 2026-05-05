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

public record LocalCharacterDefinition(String id, String displayName, String description, String skinTexture,
                                         String localSkinFile, String defaultRoleId, String conversationPrompt,
                                         String conversationSettings, boolean allowWorldActions, String movementPolicy) {
    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_DISPLAY_NAME_LENGTH = 32;
    private static final int MAX_DESCRIPTION_LENGTH = 1024;
    private static final int MAX_PROMPT_LENGTH = 4096;
    private static final int MAX_SETTINGS_LENGTH = 2048;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{1,63}");
    private static final Pattern RESOURCE_LOCATION_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern SAFE_SKIN_PATH_PATTERN = Pattern.compile("[A-Za-z0-9._/-]+\\.png");
    private static final Pattern SECRET_MARKER_PATTERN = Pattern.compile(
            "(^|[^a-z0-9])(?:api[ _-]?key|access[ _-]?token|client[ _-]?secret|token|bearer|password|passwd|cookie|secret|credential)(?=$|[^a-z0-9])",
            Pattern.CASE_INSENSITIVE
    );

    public LocalCharacterDefinition(String id, String displayName, String description, String skinTexture,
                                    String localSkinFile, String defaultRoleId, String conversationPrompt,
                                    String conversationSettings) {
        this(id, displayName, description, skinTexture, localSkinFile, defaultRoleId, conversationPrompt,
                conversationSettings, false, null);
    }

    public LocalCharacterDefinition(String id, String displayName, String description, String skinTexture,
                                    String localSkinFile, String defaultRoleId, String conversationPrompt,
                                    String conversationSettings, boolean allowWorldActions) {
        this(id, displayName, description, skinTexture, localSkinFile, defaultRoleId, conversationPrompt,
                conversationSettings, allowWorldActions, null);
    }

    public LocalCharacterDefinition {
        id = normalizeRequired(id, "id");
        displayName = normalizeRequired(displayName, "displayName");
        description = normalizeOptional(description);
        skinTexture = normalizeOptional(skinTexture);
        localSkinFile = normalizeOptional(localSkinFile);
        defaultRoleId = normalizeOptional(defaultRoleId);
        conversationPrompt = normalizeOptional(conversationPrompt);
        conversationSettings = normalizeOptional(conversationSettings);
        movementPolicy = normalizeOptional(movementPolicy);
        List<String> errors = validate(id, displayName, description, skinTexture, localSkinFile, defaultRoleId,
                conversationPrompt, conversationSettings, movementPolicy);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }

    public Optional<String> optionalDescription() {
        return Optional.ofNullable(description);
    }

    public Optional<String> optionalSkinTexture() {
        return Optional.ofNullable(skinTexture);
    }

    public Optional<String> optionalLocalSkinFile() {
        return Optional.ofNullable(localSkinFile);
    }

    public Optional<String> optionalDefaultRoleId() {
        return Optional.ofNullable(defaultRoleId);
    }

    public Optional<String> optionalConversationPrompt() {
        return Optional.ofNullable(conversationPrompt);
    }

    public Optional<String> optionalConversationSettings() {
        return Optional.ofNullable(conversationSettings);
    }

    public Optional<String> optionalMovementPolicy() {
        return Optional.ofNullable(movementPolicy);
    }

    public NpcProfileSpec toProfileSpec() {
        return new NpcProfileSpec(displayName, skinTexture, movementPolicy);
    }

    public AiPlayerNpcSpec toNpcSpec(NpcOwnerId ownerId, NpcSpawnLocation spawnLocation) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId cannot be null");
        }
        if (spawnLocation == null) {
            throw new IllegalArgumentException("spawnLocation cannot be null");
        }
        return new AiPlayerNpcSpec(toSessionRoleId(), ownerId, toProfileSpec(), spawnLocation, allowWorldActions);
    }

    public NpcRoleId toSessionRoleId() {
        return new NpcRoleId(OpenPlayerConstants.LOCAL_CHARACTER_SESSION_ROLE_PREFIX + id);
    }

    public static List<String> validate(String id, String displayName, String description, String skinTexture,
                                         String localSkinFile, String defaultRoleId, String conversationPrompt,
                                         String conversationSettings) {
        return validate(id, displayName, description, skinTexture, localSkinFile, defaultRoleId, conversationPrompt,
                conversationSettings, null);
    }

    public static List<String> validate(String id, String displayName, String description, String skinTexture,
                                         String localSkinFile, String defaultRoleId, String conversationPrompt,
                                         String conversationSettings, String movementPolicy) {
        List<String> errors = new ArrayList<>();
        validateId(errors, "id", id);
        validateDisplayName(errors, displayName);
        validateOptionalText(errors, "description", description, MAX_DESCRIPTION_LENGTH);
        validateOptionalResourceLocation(errors, "skinTexture", skinTexture);
        errors.addAll(validateLocalSkinFile(localSkinFile));
        validateOptionalId(errors, "defaultRoleId", defaultRoleId);
        validateOptionalText(errors, "conversationPrompt", conversationPrompt, MAX_PROMPT_LENGTH);
        validateOptionalText(errors, "conversationSettings", conversationSettings, MAX_SETTINGS_LENGTH);
        validateOptionalResourceLocation(errors, "movementPolicy", movementPolicy);
        return List.copyOf(errors);
    }

    static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static List<String> validateLocalSkinFile(String value) {
        List<String> errors = new ArrayList<>();
        validateOptionalSkinPath(errors, value);
        return List.copyOf(errors);
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
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

    private static void validateOptionalId(List<String> errors, String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        validateId(errors, fieldName, value);
    }

    private static void validateDisplayName(List<String> errors, String value) {
        if (value == null || value.isBlank()) {
            errors.add("displayName is required");
            return;
        }
        if (value.length() > MAX_DISPLAY_NAME_LENGTH) {
            errors.add("displayName must be 1-32 characters");
        }
        if (containsControlCharacter(value)) {
            errors.add("displayName must not contain control characters");
        }
        validateNoSecretMarkers(errors, "displayName", value);
    }

    private static void validateOptionalText(List<String> errors, String fieldName, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (value.length() > maxLength) {
            errors.add(fieldName + " must be at most " + maxLength + " characters");
        }
        if (containsControlCharacter(value)) {
            errors.add(fieldName + " must not contain control characters");
        }
        validateNoSecretMarkers(errors, fieldName, value);
    }

    private static void validateOptionalResourceLocation(List<String> errors, String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!RESOURCE_LOCATION_PATTERN.matcher(value).matches()) {
            errors.add(fieldName + " must be a lowercase resource id in namespace:path form");
        }
        validateNoSecretMarkers(errors, fieldName, value);
    }

    private static void validateOptionalSkinPath(List<String> errors, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (value.startsWith("/") || value.startsWith("\\")) {
            errors.add("localSkinFile must be a relative PNG path");
        }
        if (value.contains("\\")) {
            errors.add("localSkinFile must use forward slashes");
        }
        if (value.contains(":") || value.contains("//")) {
            errors.add("localSkinFile must not contain a drive prefix or empty path segment");
        }
        if (value.equals(".") || value.contains("../") || value.startsWith("../") || value.endsWith("/..") || value.contains("/../")) {
            errors.add("localSkinFile must not contain parent traversal");
        }
        if (!value.startsWith("skins/")) {
            errors.add("localSkinFile must point under the skins/ directory");
        }
        if (!SAFE_SKIN_PATH_PATTERN.matcher(value).matches()) {
            errors.add("localSkinFile must be a safe relative .png path using ASCII letters, digits, dot, underscore, hyphen, or slash");
        }
        validateNoSecretMarkers(errors, "localSkinFile", value);
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
