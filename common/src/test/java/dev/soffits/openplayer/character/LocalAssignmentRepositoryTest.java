package dev.soffits.openplayer.character;

import dev.soffits.openplayer.OpenPlayerConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LocalAssignmentRepositoryTest {
    private static final LocalCharacterDefinition ALEX = new LocalCharacterDefinition(
            "alex_01",
            "Alex",
            null,
            null,
            null,
            null,
            null,
            null
    );
    private static final LocalCharacterDefinition BOB = new LocalCharacterDefinition(
            "bob_01",
            "Bob",
            null,
            null,
            null,
            null,
            null,
            null
    );

    private LocalAssignmentRepositoryTest() {
    }

    public static void main(String[] args) {
        validatesAssignmentFields();
        missingDirectoryReturnsDefaultAssignments();
        loadsExplicitAssignments();
        rejectsDuplicateExplicitIds();
        rejectsUnknownAssignmentCharacter();
        rejectsUnknownFields();
        rejectsDefaultAssignmentIdCollision();
    }

    private static void validatesAssignmentFields() {
        require(!LocalAssignmentDefinition.validate("../bad", "alex_01", null).isEmpty(),
                "unsafe assignment ids must be rejected");
        require(!LocalAssignmentDefinition.validate("left", "../bad", null).isEmpty(),
                "unsafe character ids must be rejected");
        require(!LocalAssignmentDefinition.validate("left", "alex_01", "Token").isEmpty(),
                "secret-like display names must be rejected");
        require(!LocalAssignmentDefinition.validate("left", "alex_01", "/tmp/alex").isEmpty(),
                "absolute path display names must be rejected");
        LocalAssignmentDefinition assignment = new LocalAssignmentDefinition("left", "alex_01", "Left Alex");
        require((OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + "left").equals(assignment.toSessionRoleId().value()),
                "assignment role id must be derived from assignment id");
    }

    private static void missingDirectoryReturnsDefaultAssignments() {
        LocalAssignmentRepository repository = new LocalAssignmentRepository(createTempDirectory().resolve("missing"));
        LocalAssignmentRepositoryResult result = repository.loadAll(characterResult(ALEX, BOB));
        require(result.assignments().size() == 2, "missing assignment directory must expose default assignments");
        require(containsAssignment(result, "alex_01", "alex_01"), "default assignment id must equal character id");
        require(result.errors().isEmpty(), "missing assignment directory must not be an error");
    }

    private static void loadsExplicitAssignments() {
        Path directory = createTempDirectory();
        write(directory.resolve("left.properties"), "id=left\ncharacterId=alex_01\ndisplayName=Left Alex\n");
        LocalAssignmentRepositoryResult result = new LocalAssignmentRepository(directory).loadAll(characterResult(ALEX));
        require(containsAssignment(result, "left", "alex_01"), "explicit assignments must load");
        require(containsAssignment(result, "alex_01", "alex_01"), "explicit non-default assignments must keep default assignment");
    }

    private static void rejectsDuplicateExplicitIds() {
        Path directory = createTempDirectory();
        write(directory.resolve("a.properties"), "id=left\ncharacterId=alex_01\n");
        write(directory.resolve("b.properties"), "id=left\ncharacterId=alex_01\n");
        LocalAssignmentRepositoryResult result = new LocalAssignmentRepository(directory).loadAll(characterResult(ALEX));
        require(hasError(result, "Duplicate assignment id: left"), "duplicate explicit assignment ids must be validation errors");
    }

    private static void rejectsUnknownAssignmentCharacter() {
        Path directory = createTempDirectory();
        write(directory.resolve("missing.properties"), "id=missing\ncharacterId=missing_01\n");
        LocalAssignmentRepositoryResult result = new LocalAssignmentRepository(directory).loadAll(characterResult(ALEX));
        require(hasError(result, "Unknown assignment characterId: missing_01"),
                "assignments must target loaded local characters");
    }

    private static void rejectsUnknownFields() {
        Path directory = createTempDirectory();
        write(directory.resolve("left.properties"), "id=left\ncharacterId=alex_01\nextra=value\n");
        LocalAssignmentRepositoryResult result = new LocalAssignmentRepository(directory).loadAll(characterResult(ALEX));
        require(hasError(result, "Unknown assignment field: extra"), "unknown assignment fields must be invalid");
    }

    private static void rejectsDefaultAssignmentIdCollision() {
        Path directory = createTempDirectory();
        write(directory.resolve("bad.properties"), "id=alex_01\ncharacterId=bob_01\n");
        LocalAssignmentRepositoryResult result = new LocalAssignmentRepository(directory).loadAll(characterResult(ALEX, BOB));
        require(hasError(result, "Duplicate assignment id: alex_01"),
                "explicit ids must not hijack another character's default assignment id");
        require(containsAssignment(result, "alex_01", "alex_01"),
                "default assignment must remain available after a rejected collision");
    }

    private static LocalCharacterRepositoryResult characterResult(LocalCharacterDefinition... characters) {
        return new LocalCharacterRepositoryResult(List.of(characters), List.of());
    }

    private static boolean containsAssignment(LocalAssignmentRepositoryResult result, String id, String characterId) {
        for (LocalAssignmentDefinition assignment : result.assignments()) {
            if (assignment.id().equals(id) && assignment.characterId().equals(characterId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasError(LocalAssignmentRepositoryResult result, String message) {
        for (LocalCharacterValidationError error : result.errors()) {
            if (error.message().contains(message) && error.file().getFileName() != null) {
                return true;
            }
        }
        return false;
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("openplayer-assignment-test");
        } catch (IOException exception) {
            throw new AssertionError("unable to create temp directory", exception);
        }
    }

    private static void write(Path file, String value) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, value);
        } catch (IOException exception) {
            throw new AssertionError("unable to write assignment test file", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
