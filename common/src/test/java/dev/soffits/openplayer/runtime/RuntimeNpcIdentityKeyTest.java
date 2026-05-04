package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.api.AiPlayerNpcSpec;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.api.NpcProfileSpec;
import dev.soffits.openplayer.api.NpcRoleId;
import dev.soffits.openplayer.api.NpcSpawnLocation;
import java.util.UUID;

public final class RuntimeNpcIdentityKeyTest {
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private RuntimeNpcIdentityKeyTest() {
    }

    public static void main(String[] args) {
        localCharacterIdentityIgnoresDisplayName();
        localAssignmentIdentityIgnoresProfileName();
    }

    private static void localCharacterIdentityIgnoresDisplayName() {
        RuntimeNpcIdentityKey first = RuntimeNpcIdentityKey.from(spec(
                OpenPlayerConstants.LOCAL_CHARACTER_SESSION_ROLE_PREFIX + "alex_01",
                "Alex"
        ));
        RuntimeNpcIdentityKey renamed = RuntimeNpcIdentityKey.from(spec(
                OpenPlayerConstants.LOCAL_CHARACTER_SESSION_ROLE_PREFIX + "alex_01",
                "Renamed Alex"
        ));
        require(first.equals(renamed), "local character runtime identity must be stable across displayName changes");
    }

    private static void localAssignmentIdentityIgnoresProfileName() {
        RuntimeNpcIdentityKey first = RuntimeNpcIdentityKey.from(spec(
                OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + "left_guard",
                "Alex"
        ));
        RuntimeNpcIdentityKey renamed = RuntimeNpcIdentityKey.from(spec(
                OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + "left_guard",
                "Renamed Alex"
        ));
        require(first.equals(renamed), "local assignment runtime identity must be stable across profileName changes");
    }

    private static AiPlayerNpcSpec spec(String roleId, String profileName) {
        return new AiPlayerNpcSpec(
                new NpcRoleId(roleId),
                new NpcOwnerId(OWNER_ID),
                new NpcProfileSpec(profileName),
                new NpcSpawnLocation("minecraft:overworld", 0.0D, 64.0D, 0.0D)
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
