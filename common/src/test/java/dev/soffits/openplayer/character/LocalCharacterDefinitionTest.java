package dev.soffits.openplayer.character;

import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.api.NpcSpawnLocation;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class LocalCharacterDefinitionTest {
    private LocalCharacterDefinitionTest() {
    }

    public static void main(String[] args) {
        rejectsUnsafeId();
        rejectsUnsafeSkinPath();
        rejectsSecretMarkers();
        acceptsReadmeSample();
        convertsToNpcSpec();
        createsSafeListView();
        sanitizesExceptionPathsInListView();
    }

    private static void rejectsUnsafeId() {
        List<String> errors = LocalCharacterDefinition.validate("../bad", "Alex", null, null, null, null, null, null);
        require(!errors.isEmpty(), "unsafe ids must be rejected");
    }

    private static void rejectsUnsafeSkinPath() {
        List<String> errors = LocalCharacterDefinition.validate("alex_01", "Alex", null, null, "../skins/alex.png", null, null, null);
        require(!errors.isEmpty(), "parent traversal skin paths must be rejected");
    }

    private static void rejectsSecretMarkers() {
        for (String marker : List.of(
                "api key",
                "api-key",
                "api_key",
                "apikey",
                "token",
                "access token",
                "access-token",
                "access_token",
                "bearer",
                "password",
                "passwd",
                "cookie",
                "secret",
                "credential",
                "client secret"
        )) {
            requireContainsSecretError(LocalCharacterDefinition.validate(
                    "alex_01",
                    "Alex",
                    "Contains " + marker + " marker",
                    null,
                    null,
                    null,
                    null,
                    null
            ), marker + " must be rejected");
        }
        requireContainsSecretError(LocalCharacterDefinition.validate("token", "Alex", null, null, null, null, null, null), "id marker must be rejected");
        requireContainsSecretError(LocalCharacterDefinition.validate("alex_01", "Token", null, null, null, null, null, null), "displayName marker must be rejected");
        requireContainsSecretError(LocalCharacterDefinition.validate("alex_01", "Alex", null, "openplayer:token", null, null, null, null), "skinTexture marker must be rejected");
        requireContainsSecretError(LocalCharacterDefinition.validate("alex_01", "Alex", null, null, "skins/token.png", null, null, null), "localSkinFile marker must be rejected");
        requireContainsSecretError(LocalCharacterDefinition.validate("alex_01", "Alex", null, null, null, "token", null, null), "defaultRoleId marker must be rejected");
        requireContainsSecretError(LocalCharacterDefinition.validate("alex_01", "Alex", null, null, null, null, "Use token marker", null), "conversationPrompt marker must be rejected");
        requireContainsSecretError(LocalCharacterDefinition.validate("alex_01", "Alex", null, null, null, null, null, "Use token marker"), "conversationSettings marker must be rejected");
    }

    private static void acceptsReadmeSample() {
        List<String> errors = LocalCharacterDefinition.validate(
                "alex_helper",
                "Alex Helper",
                "Optional short description shown by future UI.",
                "openplayer:skins/alex_helper",
                "skins/alex_helper.png",
                "helper",
                "Reserved for later role instructions.",
                "Reserved for later non-sensitive preferences."
        );
        require(errors.isEmpty(), "README local character sample must remain valid: " + errors);
    }

    private static void convertsToNpcSpec() {
        LocalCharacterDefinition character = new LocalCharacterDefinition(
                "alex_01",
                "Alex",
                "Local helper",
                "openplayer:skins/alex",
                "skins/alex.png",
                "helper_01",
                null,
                null
        );
        require("Alex".equals(character.toProfileSpec().name()), "profile name must use displayName");
        require("openplayer:skins/alex".equals(character.toProfileSpec().skinTexture()), "profile skin must use skinTexture");
        require("openplayer-local-character-alex_01".equals(character.toSessionRoleId().value()), "session role id must use stable character id");
        require("helper_01".equals(character.optionalDefaultRoleId().orElseThrow()), "defaultRoleId must remain optional metadata");
        require("openplayer-local-character-alex_01".equals(character.toNpcSpec(
                new NpcOwnerId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                new NpcSpawnLocation("minecraft:overworld", 0.0D, 64.0D, 0.0D)
        ).roleId().value()), "AiPlayerNpcSpec role id must use stable local character session identity");
    }

    private static void createsSafeListView() {
        LocalCharacterDefinition character = new LocalCharacterDefinition(
                "alex_01",
                "Alex",
                "Local helper",
                null,
                null,
                null,
                null,
                null
        );
        LocalCharacterRepositoryResult result = new LocalCharacterRepositoryResult(
                List.of(character),
                List.of(new LocalCharacterValidationError(Path.of("/tmp/openplayer/characters/bad.properties"), "displayName is required"))
        );
        LocalCharacterListView view = LocalCharacterListView.fromRepositoryResult(result, ignored -> "active");
        require(view.characters().size() == 1, "list view must expose valid characters");
        require("default".equals(view.characters().get(0).skinStatus()), "missing skins must report default status");
        require("active".equals(view.characters().get(0).lifecycleStatus()), "lifecycle resolver must be used");
        require("bad.properties: displayName is required".equals(view.errors().get(0)), "UI errors must not expose absolute paths: " + view.errors());
    }

    private static void sanitizesExceptionPathsInListView() {
        LocalCharacterRepositoryResult result = new LocalCharacterRepositoryResult(
                List.of(),
                List.of(new LocalCharacterValidationError(
                        Path.of("/tmp/openplayer/characters/bad.properties"),
                        "Unable to read character file: /tmp/openplayer/characters/bad.properties"
                ))
        );
        LocalCharacterListView view = LocalCharacterListView.fromRepositoryResult(result, ignored -> "despawned");
        require("bad.properties: Unable to read character file: bad.properties".equals(view.errors().get(0)), "UI errors must sanitize exception paths: " + view.errors());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireContainsSecretError(List<String> errors, String message) {
        for (String error : errors) {
            if (error.contains("secret-like labels or credentials")) {
                return;
            }
        }
        throw new AssertionError(message + ": " + errors);
    }
}
