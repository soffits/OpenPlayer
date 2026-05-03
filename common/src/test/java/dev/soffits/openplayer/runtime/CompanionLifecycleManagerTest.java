package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.AiPlayerNpcService;
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
import dev.soffits.openplayer.character.LocalCharacterDefinition;
import dev.soffits.openplayer.character.LocalCharacterRepositoryResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CompanionLifecycleManagerTest {
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalCharacterDefinition CHARACTER = new LocalCharacterDefinition(
            "alex_01",
            "Renamed Alex",
            null,
            null,
            null,
            "helper_01",
            null,
            null
    );

    private CompanionLifecycleManagerTest() {
    }

    public static void main(String[] args) {
        matchesByOwnerAndStableCharacterRole();
        lifecycleStatusUsesMatchedSessionStatus();
        lifecycleStatusIsDespawnedWhenOwnerDoesNotMatch();
        selectedActionsRejectUnknownCharacterWithoutSessionLookupIdentity();
    }

    private static void matchesByOwnerAndStableCharacterRole() {
        require(CompanionLifecycleManager.matchesLocalCharacterSession(
                OWNER_ID,
                session(OWNER_ID, OpenPlayerConstants.LOCAL_CHARACTER_SESSION_ROLE_PREFIX + "alex_01", "Original Alex"),
                CHARACTER
        ), "selected companion identity must use owner and stable local character id role");
        require(!CompanionLifecycleManager.matchesLocalCharacterSession(
                OWNER_ID,
                session(OWNER_ID, OpenPlayerConstants.LOCAL_CHARACTER_SESSION_ROLE_PREFIX + "other", "Renamed Alex"),
                CHARACTER
        ), "selected companion identity must reject other stable character ids");
    }

    private static void lifecycleStatusUsesMatchedSessionStatus() {
        TestNpcService service = new TestNpcService();
        service.sessions.add(new TestSession(
                new NpcSessionId(UUID.fromString("00000000-0000-0000-0000-000000000010")),
                spec(OWNER_ID, OpenPlayerConstants.LOCAL_CHARACTER_SESSION_ROLE_PREFIX + "alex_01", "Original Alex"),
                NpcSessionStatus.ACTIVE
        ));
        CompanionLifecycleManager manager = manager(service, CHARACTER);
        require("active".equals(manager.lifecycleStatus(OWNER_ID, CHARACTER)),
                "lifecycle status must come from the matched runtime session");
    }

    private static void lifecycleStatusIsDespawnedWhenOwnerDoesNotMatch() {
        TestNpcService service = new TestNpcService();
        service.sessions.add(new TestSession(
                new NpcSessionId(UUID.fromString("00000000-0000-0000-0000-000000000011")),
                spec(OTHER_OWNER_ID, OpenPlayerConstants.LOCAL_CHARACTER_SESSION_ROLE_PREFIX + "alex_01", "Renamed Alex"),
                NpcSessionStatus.ACTIVE
        ));
        CompanionLifecycleManager manager = manager(service, CHARACTER);
        require("despawned".equals(manager.lifecycleStatus(OWNER_ID, CHARACTER)),
                "lifecycle status must not match another owner's companion");
    }

    private static void selectedActionsRejectUnknownCharacterWithoutSessionLookupIdentity() {
        TestNpcService service = new TestNpcService();
        service.sessions.add(new TestSession(
                new NpcSessionId(UUID.fromString("00000000-0000-0000-0000-000000000012")),
                spec(OWNER_ID, OpenPlayerConstants.LOCAL_CHARACTER_SESSION_ROLE_PREFIX + "missing", "Missing"),
                NpcSessionStatus.ACTIVE
        ));
        CompanionLifecycleManager manager = manager(service, CHARACTER);
        CommandSubmissionResult result = manager.submitSelectedCommandText(OWNER_ID, "../missing", "follow");
        require(result.status() == CommandSubmissionStatus.REJECTED,
                "invalid or unknown selected character ids must reject safely");
        require(service.submittedCommandTextCount == 0,
                "unknown selected character ids must not submit to any runtime session");
    }

    private static CompanionLifecycleManager manager(TestNpcService service, LocalCharacterDefinition character) {
        return new CompanionLifecycleManager(
                () -> service,
                () -> new LocalCharacterRepositoryResult(List.of(character), List.of())
        );
    }

    private static AiPlayerNpcSession session(UUID ownerId, String roleId, String profileName) {
        return new TestSession(
                new NpcSessionId(UUID.fromString("00000000-0000-0000-0000-000000000020")),
                spec(ownerId, roleId, profileName),
                NpcSessionStatus.ACTIVE
        );
    }

    private static AiPlayerNpcSpec spec(UUID ownerId, String roleId, String profileName) {
        return new AiPlayerNpcSpec(
                new NpcRoleId(roleId),
                new NpcOwnerId(ownerId),
                new NpcProfileSpec(profileName),
                new NpcSpawnLocation("minecraft:overworld", 0.0D, 64.0D, 0.0D)
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestNpcService implements AiPlayerNpcService {
        private final List<AiPlayerNpcSession> sessions = new ArrayList<>();
        private int submittedCommandTextCount;

        @Override
        public AiPlayerNpcSession spawn(AiPlayerNpcSpec spec) {
            TestSession session = new TestSession(new NpcSessionId(UUID.randomUUID()), spec, NpcSessionStatus.ACTIVE);
            sessions.add(session);
            return session;
        }

        @Override
        public boolean despawn(NpcSessionId sessionId) {
            return sessions.removeIf(session -> session.sessionId().equals(sessionId));
        }

        @Override
        public List<AiPlayerNpcSession> listSessions() {
            return List.copyOf(sessions);
        }

        @Override
        public CommandSubmissionResult submitCommand(NpcSessionId sessionId, AiPlayerNpcCommand command) {
            return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED, "accepted");
        }

        @Override
        public CommandSubmissionResult submitCommandText(NpcSessionId sessionId, String input) {
            submittedCommandTextCount++;
            return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED, "accepted");
        }

        @Override
        public NpcSessionStatus status(NpcSessionId sessionId) {
            for (AiPlayerNpcSession session : sessions) {
                if (session.sessionId().equals(sessionId)) {
                    return session.status();
                }
            }
            return NpcSessionStatus.DESPAWNED;
        }
    }

    private record TestSession(NpcSessionId sessionId, AiPlayerNpcSpec spec,
                               NpcSessionStatus status) implements AiPlayerNpcSession {
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
