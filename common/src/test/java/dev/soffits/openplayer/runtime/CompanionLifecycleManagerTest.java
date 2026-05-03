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
import dev.soffits.openplayer.character.LocalAssignmentDefinition;
import dev.soffits.openplayer.character.LocalAssignmentRepositoryResult;
import dev.soffits.openplayer.character.LocalCharacterDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final LocalAssignmentDefinition DEFAULT_ASSIGNMENT = LocalAssignmentDefinition.defaultFor(CHARACTER);

    private CompanionLifecycleManagerTest() {
    }

    public static void main(String[] args) {
        matchesByOwnerAndStableCharacterRole();
        lifecycleStatusUsesMatchedSessionStatus();
        lifecycleStatusIsDespawnedWhenOwnerDoesNotMatch();
        selectedActionsRejectUnknownCharacterWithoutSessionLookupIdentity();
        twoAssignmentsForOneCharacterSpawnIndependently();
        sameAssignmentSpawnReusesRuntimeIdentity();
        rejectsOverActiveAssignmentLimit();
        despawnedAssignmentsDoNotConsumeActiveAssignmentLimit();
        matchingDespawnedAssignmentDoesNotBypassActiveAssignmentLimit();
    }

    private static void matchesByOwnerAndStableCharacterRole() {
        require(CompanionLifecycleManager.matchesLocalCharacterSession(
                OWNER_ID,
                session(OWNER_ID, OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + "alex_01", "Original Alex"),
                CHARACTER
        ), "default assignment identity must use owner and stable assignment id role");
        require(!CompanionLifecycleManager.matchesLocalCharacterSession(
                OWNER_ID,
                session(OWNER_ID, OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + "other", "Renamed Alex"),
                CHARACTER
        ), "selected companion identity must reject other stable assignment ids");
    }

    private static void lifecycleStatusUsesMatchedSessionStatus() {
        TestNpcService service = new TestNpcService();
        service.sessions.add(new TestSession(
                new NpcSessionId(UUID.fromString("00000000-0000-0000-0000-000000000010")),
                spec(OWNER_ID, OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + "alex_01", "Original Alex"),
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
                spec(OTHER_OWNER_ID, OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + "alex_01", "Renamed Alex"),
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
                spec(OWNER_ID, OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + "missing", "Missing"),
                NpcSessionStatus.ACTIVE
        ));
        CompanionLifecycleManager manager = manager(service, CHARACTER);
        CommandSubmissionResult result = manager.submitSelectedCommandText(OWNER_ID, "../missing", "follow");
        require(result.status() == CommandSubmissionStatus.REJECTED,
                "invalid or unknown selected character ids must reject safely");
        require(service.submittedCommandTextCount == 0,
                "unknown selected character ids must not submit to any runtime session");
    }

    private static void twoAssignmentsForOneCharacterSpawnIndependently() {
        TestNpcService service = new TestNpcService();
        LocalAssignmentDefinition first = new LocalAssignmentDefinition("alex_left", "alex_01", "Alex Left");
        LocalAssignmentDefinition second = new LocalAssignmentDefinition("alex_right", "alex_01", "Alex Right");
        CompanionLifecycleManager manager = manager(service, List.of(first, second), CHARACTER);
        NpcSpawnLocation location = new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D);

        manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID), location, first.id());
        manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID), location, second.id());

        require(service.sessions.size() == 2, "two assignments for one character must spawn independently");
        require(service.containsRole(OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + first.id()),
                "first assignment role must be active");
        require(service.containsRole(OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + second.id()),
                "second assignment role must be active");
    }

    private static void sameAssignmentSpawnReusesRuntimeIdentity() {
        TestNpcService service = new TestNpcService();
        CompanionLifecycleManager manager = manager(service, List.of(DEFAULT_ASSIGNMENT), CHARACTER);
        manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID),
                new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D), DEFAULT_ASSIGNMENT.id());
        manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID),
                new NpcSpawnLocation("minecraft:overworld", 2.0D, 64.0D, 2.0D), DEFAULT_ASSIGNMENT.id());

        require(service.sessions.size() == 1, "same assignment spawn must reuse the runtime identity");
        require(service.spawnCount == 2, "same assignment should still submit a relocate/update spawn request");
    }

    private static void rejectsOverActiveAssignmentLimit() {
        TestNpcService service = new TestNpcService();
        List<LocalAssignmentDefinition> assignments = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            assignments.add(new LocalAssignmentDefinition("alex_0" + index, "alex_01", "Alex " + index));
        }
        CompanionLifecycleManager manager = manager(service, assignments, CHARACTER);
        NpcSpawnLocation location = new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D);
        for (int index = 0; index < 4; index++) {
            CommandSubmissionResult result = manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID), location, assignments.get(index).id());
            require(result.status() == CommandSubmissionStatus.ACCEPTED, "first four active assignments must be accepted");
        }
        CommandSubmissionResult result = manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID), location, assignments.get(4).id());
        require(result.status() == CommandSubmissionStatus.REJECTED, "fifth active assignment must be rejected");
        require(result.message().contains("limit"), "over-limit rejection must be safe and actionable");
    }

    private static void despawnedAssignmentsDoNotConsumeActiveAssignmentLimit() {
        TestNpcService service = new TestNpcService();
        List<LocalAssignmentDefinition> assignments = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            assignments.add(new LocalAssignmentDefinition("alex_1" + index, "alex_01", "Alex " + index));
        }
        for (int index = 0; index < 4; index++) {
            service.sessions.add(new TestSession(
                    new NpcSessionId(UUID.randomUUID()),
                    spec(OWNER_ID, OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + assignments.get(index).id(), "Alex"),
                    NpcSessionStatus.DESPAWNED
            ));
        }
        CompanionLifecycleManager manager = manager(service, assignments, CHARACTER);
        CommandSubmissionResult result = manager.spawnSelectedAssignment(
                new NpcOwnerId(OWNER_ID),
                new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D),
                assignments.get(4).id()
        );

        require(result.status() == CommandSubmissionStatus.ACCEPTED,
                "despawned assignment sessions must not consume the active assignment limit");
    }

    private static void matchingDespawnedAssignmentDoesNotBypassActiveAssignmentLimit() {
        TestNpcService service = new TestNpcService();
        List<LocalAssignmentDefinition> assignments = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            assignments.add(new LocalAssignmentDefinition("alex_2" + index, "alex_01", "Alex " + index));
        }
        for (int index = 0; index < 4; index++) {
            service.sessions.add(new TestSession(
                    new NpcSessionId(UUID.randomUUID()),
                    spec(OWNER_ID, OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + assignments.get(index).id(), "Alex"),
                    NpcSessionStatus.ACTIVE
            ));
        }
        service.sessions.add(new TestSession(
                new NpcSessionId(UUID.randomUUID()),
                spec(OWNER_ID, OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + assignments.get(4).id(), "Alex"),
                NpcSessionStatus.DESPAWNED
        ));
        CompanionLifecycleManager manager = manager(service, assignments, CHARACTER);
        CommandSubmissionResult result = manager.spawnSelectedAssignment(
                new NpcOwnerId(OWNER_ID),
                new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D),
                assignments.get(4).id()
        );

        require(result.status() == CommandSubmissionStatus.REJECTED,
                "matching despawned assignment session must not bypass the active assignment limit");
        require(service.spawnCount == 0,
                "over-limit spawn with only a matching despawned session must not reach the runtime service");
    }

    private static CompanionLifecycleManager manager(TestNpcService service, LocalCharacterDefinition character) {
        return manager(service, List.of(LocalAssignmentDefinition.defaultFor(character)), character);
    }

    private static CompanionLifecycleManager manager(TestNpcService service, List<LocalAssignmentDefinition> assignments,
                                                     LocalCharacterDefinition character) {
        LocalAssignmentRepositoryResult result = new LocalAssignmentRepositoryResult(assignments, List.of(character), List.of());
        return CompanionLifecycleManager.withAssignments(
                () -> service,
                () -> result,
                dev.soffits.openplayer.intent.DisabledIntentParser::new
        );
    }

    private static CompanionLifecycleManager oldCharacterManager(TestNpcService service, LocalCharacterDefinition character) {
        return new CompanionLifecycleManager(
                () -> service,
                () -> new dev.soffits.openplayer.character.LocalCharacterRepositoryResult(List.of(character), List.of())
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
        private final Map<String, TestSession> sessionsByIdentity = new LinkedHashMap<>();
        private int submittedCommandTextCount;
        private int spawnCount;

        @Override
        public AiPlayerNpcSession spawn(AiPlayerNpcSpec spec) {
            spawnCount++;
            String identity = spec.ownerId().value() + ":" + spec.roleId().value();
            TestSession existing = sessionsByIdentity.get(identity);
            if (existing != null) {
                existing.spec = spec;
                return existing;
            }
            TestSession session = new TestSession(new NpcSessionId(UUID.randomUUID()), spec, NpcSessionStatus.ACTIVE);
            sessions.add(session);
            sessionsByIdentity.put(identity, session);
            return session;
        }

        @Override
        public boolean despawn(NpcSessionId sessionId) {
            boolean removed = sessions.removeIf(session -> session.sessionId().equals(sessionId));
            sessionsByIdentity.values().removeIf(session -> session.sessionId().equals(sessionId));
            return removed;
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

        private boolean containsRole(String roleId) {
            for (AiPlayerNpcSession session : sessions) {
                if (session.spec().roleId().value().equals(roleId)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class TestSession implements AiPlayerNpcSession {
        private final NpcSessionId sessionId;
        private AiPlayerNpcSpec spec;
        private final NpcSessionStatus status;

        private TestSession(NpcSessionId sessionId, AiPlayerNpcSpec spec, NpcSessionStatus status) {
            this.sessionId = sessionId;
            this.spec = spec;
            this.status = status;
        }

        @Override
        public NpcSessionId sessionId() {
            return sessionId;
        }

        @Override
        public AiPlayerNpcSpec spec() {
            return spec;
        }

        @Override
        public NpcSessionStatus status() {
            return status;
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
