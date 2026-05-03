package dev.soffits.openplayer.network;

import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.AiPlayerNpcSession;
import dev.soffits.openplayer.api.AiPlayerNpcSpec;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.api.NpcProfileSpec;
import dev.soffits.openplayer.api.NpcRoleId;
import dev.soffits.openplayer.api.NpcSessionId;
import dev.soffits.openplayer.api.NpcSessionStatus;
import dev.soffits.openplayer.api.NpcSpawnLocation;
import dev.soffits.openplayer.intent.IntentParseException;
import dev.soffits.openplayer.intent.IntentProviderException;
import java.util.UUID;

public final class OpenPlayerNetworkingTest {
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private OpenPlayerNetworkingTest() {
    }

    public static void main(String[] args) {
        matchesLegacyDefaultNetworkNpc();
        rejectsCharacterSessionUsingDefaultRole();
        rejectsOtherOwnerLegacyDefaultNetworkNpc();
        acceptsSingleplayerOwnerProviderConfigSave();
        acceptsPermittedProviderConfigSave();
        rejectsUnauthorizedProviderConfigSave();
        acceptsSingleplayerOwnerLocalProfileManagement();
        acceptsPermittedLocalProfileManagement();
        rejectsUnauthorizedLocalProfileManagement();
        classifiesProviderHttpFailure();
    }

    private static void matchesLegacyDefaultNetworkNpc() {
        require(OpenPlayerNetworking.isLegacyDefaultNetworkNpcSession(
                OWNER_ID,
                "Alex",
                session(OWNER_ID, OpenPlayerConstants.DEFAULT_NETWORK_NPC_ROLE_ID, "Alex OpenPlayer NPC")
        ), "absent character id must target the legacy default network NPC");
    }

    private static void rejectsCharacterSessionUsingDefaultRole() {
        require(!OpenPlayerNetworking.isLegacyDefaultNetworkNpcSession(
                OWNER_ID,
                "Alex",
                session(OWNER_ID, OpenPlayerConstants.DEFAULT_NETWORK_NPC_ROLE_ID, "Alex Helper")
        ), "absent character id must not target a local character session using the default role");
    }

    private static void rejectsOtherOwnerLegacyDefaultNetworkNpc() {
        require(!OpenPlayerNetworking.isLegacyDefaultNetworkNpcSession(
                OWNER_ID,
                "Alex",
                session(OTHER_OWNER_ID, OpenPlayerConstants.DEFAULT_NETWORK_NPC_ROLE_ID, "Alex OpenPlayer NPC")
        ), "absent character id must not target another player's legacy default network NPC");
    }

    private static void acceptsSingleplayerOwnerProviderConfigSave() {
        require(OpenPlayerNetworking.maySaveProviderConfig(true, false), "singleplayer owner must be allowed to save provider config");
    }

    private static void acceptsPermittedProviderConfigSave() {
        require(OpenPlayerNetworking.maySaveProviderConfig(false, true), "permitted player must be allowed to save provider config");
    }

    private static void rejectsUnauthorizedProviderConfigSave() {
        require(!OpenPlayerNetworking.maySaveProviderConfig(false, false), "unauthorized player must not save provider config");
    }

    private static void acceptsSingleplayerOwnerLocalProfileManagement() {
        require(OpenPlayerNetworking.mayManageLocalProfiles(true, false), "singleplayer owner must be allowed to manage local profiles");
    }

    private static void acceptsPermittedLocalProfileManagement() {
        require(OpenPlayerNetworking.mayManageLocalProfiles(false, true), "permitted player must be allowed to manage local profiles");
    }

    private static void rejectsUnauthorizedLocalProfileManagement() {
        require(!OpenPlayerNetworking.mayManageLocalProfiles(false, false), "unauthorized player must not manage local profiles");
    }

    private static void classifiesProviderHttpFailure() {
        IntentParseException exception = new IntentParseException(
                "intent provider failed",
                new IntentProviderException("intent provider request failed with status 401")
        );
        require("http_status".equals(OpenPlayerNetworking.providerFailureCode(exception)), "HTTP provider failure must be classified");
        require("401".equals(OpenPlayerNetworking.providerFailureDetail(exception)), "HTTP status must be preserved without response body");
    }

    private static AiPlayerNpcSession session(UUID ownerId, String roleId, String profileName) {
        return new TestSession(new AiPlayerNpcSpec(
                new NpcRoleId(roleId),
                new NpcOwnerId(ownerId),
                new NpcProfileSpec(profileName),
                new NpcSpawnLocation("minecraft:overworld", 0.0D, 64.0D, 0.0D)
        ));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record TestSession(AiPlayerNpcSpec spec) implements AiPlayerNpcSession {
        @Override
        public NpcSessionId sessionId() {
            return new NpcSessionId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        }

        @Override
        public NpcSessionStatus status() {
            return NpcSessionStatus.ACTIVE;
        }

        @Override
        public CommandSubmissionResult submitCommand(AiPlayerNpcCommand command) {
            return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED, "accepted");
        }

        @Override
        public CommandSubmissionResult submitCommandText(String input) {
            return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED, "accepted");
        }

        @Override
        public boolean despawn() {
            return true;
        }
    }
}
