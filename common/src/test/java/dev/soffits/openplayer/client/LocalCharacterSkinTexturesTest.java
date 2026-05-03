package dev.soffits.openplayer.client;

import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.character.LocalAssignmentDefinition;
import dev.soffits.openplayer.character.LocalAssignmentRepositoryResult;
import dev.soffits.openplayer.character.LocalCharacterDefinition;
import java.util.List;

public final class LocalCharacterSkinTexturesTest {
    private LocalCharacterSkinTexturesTest() {
    }

    public static void main(String[] args) {
        assignmentRoleResolvesTargetCharacterLocalSkinFile();
        missingAssignmentRoleDoesNotResolveLocalSkinFile();
    }

    private static void assignmentRoleResolvesTargetCharacterLocalSkinFile() {
        LocalAssignmentRepositoryResult result = new LocalAssignmentRepositoryResult(
                List.of(new LocalAssignmentDefinition("left_guard", "alex_01", "Left Guard")),
                List.of(new LocalCharacterDefinition(
                        "alex_01",
                        "Alex",
                        null,
                        null,
                        "skins/alex.png",
                        null,
                        null,
                        null
                )),
                List.of()
        );

        require(LocalCharacterSkinTextures.localSkinFileForRoleId(
                OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + "left_guard",
                result
        ).orElseThrow().equals("skins/alex.png"), "assignment roles must use the target character localSkinFile");
    }

    private static void missingAssignmentRoleDoesNotResolveLocalSkinFile() {
        LocalAssignmentRepositoryResult result = new LocalAssignmentRepositoryResult(List.of(), List.of(), List.of());
        require(LocalCharacterSkinTextures.localSkinFileForRoleId(
                OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + "missing",
                result
        ).isEmpty(), "missing assignment roles must not resolve a local skin file");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
