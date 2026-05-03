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
        legacyIdentityIncludesProfileName();
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

    private static void legacyIdentityIncludesProfileName() {
        RuntimeNpcIdentityKey first = RuntimeNpcIdentityKey.from(spec(
                OpenPlayerConstants.DEFAULT_NETWORK_NPC_ROLE_ID,
                "Alex OpenPlayer NPC"
        ));
        RuntimeNpcIdentityKey renamed = RuntimeNpcIdentityKey.from(spec(
                OpenPlayerConstants.DEFAULT_NETWORK_NPC_ROLE_ID,
                "Other OpenPlayer NPC"
        ));
        require(!first.equals(renamed), "legacy runtime identity must keep profile name matching behavior");
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
