package dev.soffits.openplayer.character;

import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.api.NpcSpawnLocation;
import dev.soffits.openplayer.client.LocalSkinImageValidator;
import java.io.IOException;
import java.nio.file.Files;
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
        mergesAssignedAndUnassignedProfilesInAssignmentListView();
        avoidsDuplicateAssignedProfilesInAssignmentListView();
        createsSafeConversationStatusInListView();
        sanitizesExceptionPathsInListView();
        resolvesSafeLocalSkinPaths();
        rejectsUnsafeLocalSkinPaths();
        rejectsSymlinkEscapedLocalSkinPaths();
        rejectsSymlinkedSkinDirectory();
        validatesPlayerSkinImageDimensions();
    }

    private static void rejectsUnsafeId() {
        List<String> errors = LocalCharacterDefinition.validate("../bad", "Alex", null, null, null, null, null, null);
        require(!errors.isEmpty(), "unsafe ids must be rejected");
    }

    private static void rejectsUnsafeSkinPath() {
        List<String> errors = LocalCharacterDefinition.validate("alex_01", "Alex", null, null, "../skins/alex.png", null, null, null);
        require(!errors.isEmpty(), "parent traversal skin paths must be rejected");
        errors = LocalCharacterDefinition.validate("alex_01", "Alex", null, null, "alex.png", null, null, null);
        require(!errors.isEmpty(), "skin paths outside skins/ must be rejected");
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
        require(!character.toNpcSpec(
                new NpcOwnerId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                new NpcSpawnLocation("minecraft:overworld", 0.0D, 64.0D, 0.0D)
        ).allowWorldActions(), "allowWorldActions must default false");
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

    private static void mergesAssignedAndUnassignedProfilesInAssignmentListView() {
        LocalCharacterDefinition assigned = character("alex_01", "Alex");
        LocalCharacterDefinition unassigned = character("openplayer_default", "Default Profile");
        LocalAssignmentDefinition assignment = new LocalAssignmentDefinition("left_slot", "alex_01", "Left Alex");
        LocalCharacterListView view = LocalCharacterListView.fromAssignmentRepositoryResult(
                new LocalAssignmentRepositoryResult(List.of(assignment), List.of(assigned, unassigned), List.of()),
                (candidateAssignment, ignoredCharacter) -> "lifecycle:" + candidateAssignment.id(),
                null,
                ignored -> "available"
        );
        require(view.characters().size() == 2, "assigned and unassigned profiles must both appear");
        require("left_slot".equals(view.characters().get(0).assignmentId()), "assigned profile must preserve assignment id");
        require("alex_01".equals(view.characters().get(0).characterId()), "assigned profile must preserve character id");
        require("openplayer_default".equals(view.characters().get(1).assignmentId()), "unassigned profile must use profile id as stable assignment id");
        require("openplayer_default".equals(view.characters().get(1).characterId()), "default profile must remain selectable as a profile");
    }

    private static void avoidsDuplicateAssignedProfilesInAssignmentListView() {
        LocalCharacterDefinition character = character("alex_01", "Alex");
        LocalCharacterListView view = LocalCharacterListView.fromAssignmentRepositoryResult(
                new LocalAssignmentRepositoryResult(List.of(
                        new LocalAssignmentDefinition("left_slot", "alex_01", "Left Alex"),
                        LocalAssignmentDefinition.defaultFor(character)
                ), List.of(character), List.of()),
                (ignoredAssignment, ignoredCharacter) -> "despawned",
                null,
                ignored -> "available"
        );
        require(view.characters().size() == 1, "assigned profiles must not duplicate default profile rows");
        require("left_slot".equals(view.characters().get(0).assignmentId()), "first assignment row must be preserved");
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

    private static void createsSafeConversationStatusInListView() {
        LocalCharacterDefinition character = new LocalCharacterDefinition(
                "alex_01",
                "Alex",
                null,
                null,
                null,
                null,
                "Friendly helper instructions.",
                null
        );
        LocalCharacterRepositoryResult result = new LocalCharacterRepositoryResult(List.of(character), List.of());
        LocalCharacterListView view = LocalCharacterListView.fromRepositoryResult(
                result,
                ignored -> "despawned",
                null,
                ignored -> "unavailable: parser disabled"
        );
        require("unavailable: parser disabled".equals(view.characters().get(0).conversationStatus()),
                "configured conversation must report safe resolver status");
    }

    private static void resolvesSafeLocalSkinPaths() {
        Path directory = createTempDirectory();
        try {
            Path skin = directory.resolve("skins").resolve("alex.png");
            Files.createDirectories(skin.getParent());
            Files.write(skin, new byte[]{1, 2, 3});
            LocalSkinPathResolution resolution = new LocalSkinPathResolver(directory).resolve("skins/alex.png");
            require(resolution.isResolved(), "safe existing local skin must resolve: " + resolution.rejectionReason());
            require(skin.toAbsolutePath().normalize().equals(resolution.path()), "resolved skin path must be normalized under skins/");
        } catch (IOException exception) {
            throw new AssertionError("unable to create local skin test file", exception);
        }
    }

    private static void rejectsUnsafeLocalSkinPaths() {
        Path directory = createTempDirectory();
        LocalSkinPathResolver resolver = new LocalSkinPathResolver(directory);
        require(!resolver.resolve("../alex.png").isResolved(), "parent traversal must be rejected");
        require(!resolver.resolve(directory.resolve("skins/alex.png").toString()).isResolved(), "absolute paths must be rejected");
        require(!resolver.resolve("skins/alex.txt").isResolved(), "non-PNG paths must be rejected");
        require(!resolver.resolve("skins/missing.png").isResolved(), "missing PNG files must be rejected");
        require(!resolver.resolve("other/alex.png").isResolved(), "paths outside skins/ must be rejected");
    }

    private static void rejectsSymlinkEscapedLocalSkinPaths() {
        Path directory = createTempDirectory();
        Path outsideSkin = createTempDirectory().resolve("escaped.png");
        Path link = directory.resolve("skins").resolve("escaped.png");
        try {
            Files.write(outsideSkin, new byte[]{1, 2, 3});
            Files.createDirectories(link.getParent());
        } catch (IOException exception) {
            throw new AssertionError("unable to create symlink escape test files", exception);
        }
        try {
            Files.createSymbolicLink(link, outsideSkin);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            return;
        }
        LocalSkinPathResolution resolution = new LocalSkinPathResolver(directory).resolve("skins/escaped.png");
        require(!resolution.isResolved(), "symlink escape must be rejected");
    }

    private static void rejectsSymlinkedSkinDirectory() {
        Path directory = createTempDirectory();
        Path outsideDirectory = createTempDirectory();
        Path skinDirectory = directory.resolve("skins");
        try {
            Files.write(outsideDirectory.resolve("alex.png"), new byte[]{1, 2, 3});
        } catch (IOException exception) {
            throw new AssertionError("unable to create symlinked skin directory test file", exception);
        }
        try {
            Files.createSymbolicLink(skinDirectory, outsideDirectory);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            return;
        }
        LocalSkinPathResolution resolution = new LocalSkinPathResolver(directory).resolve("skins/alex.png");
        require(!resolution.isResolved(), "symlinked skins directory must be rejected");
        require(!resolution.rejectionReason().contains(directory.toAbsolutePath().normalize().toString()), "rejection reason must not expose config path");
        require(!resolution.rejectionReason().contains(outsideDirectory.toAbsolutePath().normalize().toString()), "rejection reason must not expose external path");
    }

    private static void validatesPlayerSkinImageDimensions() {
        require(LocalSkinImageValidator.isSupportedPlayerSkinSize(64, 32), "classic 64x32 player skins must be accepted");
        require(LocalSkinImageValidator.isSupportedPlayerSkinSize(64, 64), "modern player skins must be accepted");
        require(!LocalSkinImageValidator.isSupportedPlayerSkinSize(32, 32), "non-player skin dimensions must be rejected");
        require(!LocalSkinImageValidator.isSupportedPlayerSkinSize(128, 128), "non-standard skin dimensions must be rejected");
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("openplayer-skin-test");
        } catch (IOException exception) {
            throw new AssertionError("unable to create temp directory", exception);
        }
    }

    private static LocalCharacterDefinition character(String id, String displayName) {
        return new LocalCharacterDefinition(
                id,
                displayName,
                null,
                null,
                null,
                null,
                null,
                null
        );
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
