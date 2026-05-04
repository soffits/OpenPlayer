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
import dev.soffits.openplayer.debug.OpenPlayerDebugEvent;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentParser;
import dev.soffits.openplayer.intent.IntentPriority;
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
        spawnRecordsDeterministicGreetingForConversationAssignment();
        disabledParserRecordsFailureWithoutProviderCall();
        missingConversationConfigIsUnavailableAndDoesNotUseRawParserPath();
        acceptedConversationCommandRecordsSafeActionSummary();
        chatConversationRecordsNpcReplyWithoutAutomation();
        unavailableConversationRecordsSafeNpcReplyWithoutAutomation();
        asyncConversationUsesPlannerSeamAndSurfacesFinalChat();
        asyncConversationPlannerRecordsPrimitiveAndFinalChat();
        acceptedConversationHistoryDoesNotRetainProviderInstruction();
        resetMemoryClearsOnlySelectedConversationHistory();
        resetMemoryTextPathDoesNotReappendClearedTurns();
        conversationHistoryEvictsOldOwnerKeysFromLaterPrompts();
        conversationHistoryEvictsOldAssignmentKeysFromLaterPrompts();
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

    private static void spawnRecordsDeterministicGreetingForConversationAssignment() {
        TestNpcService service = new TestNpcService();
        LocalCharacterDefinition character = conversationCharacter();
        CompanionLifecycleManager manager = manager(service, character);

        manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID),
                new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D), character.id());

        List<String> events = manager.conversationEventLines(OWNER_ID, LocalAssignmentDefinition.defaultFor(character));
        require(events.size() == 1, "conversation spawn must record one local greeting");
        require(events.get(0).contains("Hello, I am"), "conversation greeting must be deterministic local text");
        require(events.get(0).contains(character.displayName()), "conversation greeting must use the resolved display name");
    }

    private static void disabledParserRecordsFailureWithoutProviderCall() {
        TestNpcService service = new TestNpcService();
        LocalCharacterDefinition character = conversationCharacter();
        CountingIntentParser parser = new CountingIntentParser();
        CompanionLifecycleManager manager = CompanionLifecycleManager.withAssignments(
                () -> service,
                () -> new LocalAssignmentRepositoryResult(List.of(LocalAssignmentDefinition.defaultFor(character)), List.of(character), List.of()),
                () -> parser
        );
        manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID),
                new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D), character.id());

        CommandSubmissionResult result = manager.submitSelectedCommandText(OWNER_ID, character.id(), "follow me");

        require(result.status() == CommandSubmissionStatus.UNAVAILABLE, "disabled parser must reject conversation command safely");
        require(parser.parseCount == 0, "disabled parser status must prevent provider calls");
        List<String> events = manager.conversationEventLines(OWNER_ID, LocalAssignmentDefinition.defaultFor(character));
        require(events.stream().anyMatch(event -> event.contains("You: follow me")),
                "conversation command must record sanitized player text");
        require(events.stream().anyMatch(event -> event.contains("parser disabled")),
                "disabled parser must record a safe failure event");
    }

    private static void missingConversationConfigIsUnavailableAndDoesNotUseRawParserPath() {
        TestNpcService service = new TestNpcService();
        CompanionLifecycleManager manager = manager(service, CHARACTER);
        manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID),
                new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D), CHARACTER.id());

        CommandSubmissionResult result = manager.submitSelectedCommandText(OWNER_ID, CHARACTER.id(), "follow me");

        require(result.status() == CommandSubmissionStatus.UNAVAILABLE,
                "missing conversation config must be reported explicitly");
        require(service.submittedCommandTextCount == 0,
                "missing conversation config must not silently fall back to raw command text parsing");
    }

    private static void acceptedConversationCommandRecordsSafeActionSummary() {
        String previousEndpoint = System.getProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT");
        String previousModel = System.getProperty("OPENPLAYER_INTENT_PROVIDER_MODEL");
        String previousApiKey = System.getProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", "https://example.invalid/v1/chat/completions");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", "test-model");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", "test-key");
        try {
            OpenPlayerDebugEvents.clearForTests();
            TestNpcService service = new TestNpcService();
            LocalCharacterDefinition character = conversationCharacter();
            CountingIntentParser parser = new CountingIntentParser();
            CompanionLifecycleManager manager = CompanionLifecycleManager.withAssignments(
                    () -> service,
                    () -> new LocalAssignmentRepositoryResult(List.of(LocalAssignmentDefinition.defaultFor(character)), List.of(character), List.of()),
                    () -> parser
            );
            manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID),
                    new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D), character.id());

            CommandSubmissionResult result = manager.submitSelectedCommandText(OWNER_ID, character.id(), "please follow me");

            require(result.status() == CommandSubmissionStatus.ACCEPTED, "enabled parser must allow accepted command submission");
            require(service.submittedCommandCount == 1, "accepted conversation intent must submit a command to the runtime service");
            List<String> events = manager.conversationEventLines(OWNER_ID, LocalAssignmentDefinition.defaultFor(character));
            require(events.stream().anyMatch(event -> event.contains("FOLLOW_OWNER")),
                    "accepted command must record a safe intent summary");
            require(events.stream().noneMatch(event -> event.length() > 128),
                    "accepted command summary must stay bounded for client display");
        } finally {
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", previousEndpoint);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", previousModel);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", previousApiKey);
        }
    }

    private static void chatConversationRecordsNpcReplyWithoutAutomation() {
        String previousEndpoint = System.getProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT");
        String previousModel = System.getProperty("OPENPLAYER_INTENT_PROVIDER_MODEL");
        String previousApiKey = System.getProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", "https://example.invalid/v1/chat/completions");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", "test-model");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", "test-key");
        try {
            OpenPlayerDebugEvents.clearForTests();
            TestNpcService service = new TestNpcService();
            LocalCharacterDefinition character = conversationCharacter();
            CompanionLifecycleManager manager = CompanionLifecycleManager.withAssignments(
                    () -> service,
                    () -> new LocalAssignmentRepositoryResult(List.of(LocalAssignmentDefinition.defaultFor(character)), List.of(character), List.of()),
                    () -> input -> new CommandIntent(IntentKind.CHAT, IntentPriority.NORMAL, "Hello there")
            );
            manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID),
                    new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D), character.id());

            CommandSubmissionResult result = manager.submitSelectedCommandText(OWNER_ID, character.id(), "hello");

            require(result.status() == CommandSubmissionStatus.ACCEPTED, "CHAT conversation must be accepted as a reply");
            require("Hello there".equals(result.message()), "CHAT conversation result must expose the NPC reply");
            require(service.submittedCommandCount == 0, "CHAT conversation must not submit automation");
            requireProviderParseSuccess(IntentKind.CHAT, "CHAT conversation must record provider_parse success");
            List<String> events = manager.conversationEventLines(OWNER_ID, LocalAssignmentDefinition.defaultFor(character));
            require(events.stream().anyMatch(event -> event.contains("Hello there")),
                    "CHAT conversation must record the NPC reply for UI display");
        } finally {
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", previousEndpoint);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", previousModel);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", previousApiKey);
        }
    }

    private static void unavailableConversationRecordsSafeNpcReplyWithoutAutomation() {
        String previousEndpoint = System.getProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT");
        String previousModel = System.getProperty("OPENPLAYER_INTENT_PROVIDER_MODEL");
        String previousApiKey = System.getProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", "https://example.invalid/v1/chat/completions");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", "test-model");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", "test-key");
        try {
            OpenPlayerDebugEvents.clearForTests();
            TestNpcService service = new TestNpcService();
            LocalCharacterDefinition character = conversationCharacter();
            CompanionLifecycleManager manager = CompanionLifecycleManager.withAssignments(
                    () -> service,
                    () -> new LocalAssignmentRepositoryResult(List.of(LocalAssignmentDefinition.defaultFor(character)), List.of(character), List.of()),
                    () -> input -> new CommandIntent(IntentKind.UNAVAILABLE, IntentPriority.NORMAL, "")
            );
            manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID),
                    new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D), character.id());

            CommandSubmissionResult result = manager.submitSelectedCommandText(OWNER_ID, character.id(), "cut trees");

            require(result.status() == CommandSubmissionStatus.ACCEPTED, "UNAVAILABLE conversation must be accepted as a reply");
            require(result.message().contains("safely"), "UNAVAILABLE blank response must use a safe fallback reply");
            require(service.submittedCommandCount == 0, "UNAVAILABLE conversation must not submit automation");
            requireProviderParseSuccess(IntentKind.UNAVAILABLE, "UNAVAILABLE conversation must record provider_parse success");
            List<String> events = manager.conversationEventLines(OWNER_ID, LocalAssignmentDefinition.defaultFor(character));
            require(events.stream().anyMatch(event -> event.contains("safely")),
                    "UNAVAILABLE conversation must record a safe NPC reply for UI display");
        } finally {
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", previousEndpoint);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", previousModel);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", previousApiKey);
        }
    }

    private static void asyncConversationUsesPlannerSeamAndSurfacesFinalChat() {
        String previousEndpoint = System.getProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT");
        String previousModel = System.getProperty("OPENPLAYER_INTENT_PROVIDER_MODEL");
        String previousApiKey = System.getProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", "https://example.invalid/v1/chat/completions");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", "test-model");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", "test-key");
        try {
            OpenPlayerDebugEvents.clearForTests();
            PlannerTestNpcService service = new PlannerTestNpcService(PlannerTestMode.FINAL_CHAT_ONLY);
            LocalCharacterDefinition character = conversationCharacter();
            CompanionLifecycleManager manager = CompanionLifecycleManager.withAssignments(
                    () -> service,
                    () -> new LocalAssignmentRepositoryResult(List.of(LocalAssignmentDefinition.defaultFor(character)), List.of(character), List.of()),
                    CountingIntentParser::new
            );
            manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID),
                    new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D), character.id());
            List<CommandSubmissionResult> completions = new ArrayList<>();

            CommandSubmissionResult queued = manager.submitSelectedCommandTextAsync(null, OWNER_ID, character.id(),
                    "please cut one tree", completions::add);

            require(queued.status() == CommandSubmissionStatus.ACCEPTED, "planner conversation must queue successfully");
            require(service.plannerRequestCount == 1, "async selected conversation must use the planner service seam");
            require(service.lastPlannerRequest.providerPrompt().contains("please cut one tree"),
                    "planner request must preserve the assembled companion conversation prompt");
            require(completions.size() == 1, "test planner service must complete through the manager callback");
            require("Planner reply".equals(completions.get(0).message()),
                    "final planner CHAT must be surfaced as the companion chat reply");
            List<String> events = manager.conversationEventLines(OWNER_ID, LocalAssignmentDefinition.defaultFor(character));
            require(events.stream().anyMatch(event -> event.contains("Planner reply")),
                    "final planner CHAT must be recorded for UI display");
        } finally {
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", previousEndpoint);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", previousModel);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", previousApiKey);
        }
    }

    private static void asyncConversationPlannerRecordsPrimitiveAndFinalChat() {
        String previousEndpoint = System.getProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT");
        String previousModel = System.getProperty("OPENPLAYER_INTENT_PROVIDER_MODEL");
        String previousApiKey = System.getProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", "https://example.invalid/v1/chat/completions");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", "test-model");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", "test-key");
        try {
            PlannerTestNpcService service = new PlannerTestNpcService(PlannerTestMode.PRIMITIVE_THEN_CHAT);
            LocalCharacterDefinition character = conversationCharacter();
            CompanionLifecycleManager manager = CompanionLifecycleManager.withAssignments(
                    () -> service,
                    () -> new LocalAssignmentRepositoryResult(List.of(LocalAssignmentDefinition.defaultFor(character)), List.of(character), List.of()),
                    CountingIntentParser::new
            );
            manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID),
                    new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D), character.id());
            List<CommandSubmissionResult> completions = new ArrayList<>();

            CommandSubmissionResult queued = manager.submitSelectedCommandTextAsync(null, OWNER_ID, character.id(),
                    "follow me then report", completions::add);

            require(queued.status() == CommandSubmissionStatus.ACCEPTED, "planner primitive conversation must queue successfully");
            require(service.submittedCommandCount() == 1, "planner primitive must use normal runtime command submission");
            require(completions.size() == 1 && "Ready".equals(completions.get(0).message()),
                    "planner final CHAT after a primitive must be surfaced");
            List<String> events = manager.conversationEventLines(OWNER_ID, LocalAssignmentDefinition.defaultFor(character));
            require(events.stream().anyMatch(event -> event.contains("FOLLOW_OWNER")),
                    "planner primitive must record a truthful action summary");
            require(events.stream().anyMatch(event -> event.contains("Ready")),
                    "planner final CHAT after a primitive must be recorded for UI display");
        } finally {
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", previousEndpoint);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", previousModel);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", previousApiKey);
        }
    }

    private static void acceptedConversationHistoryDoesNotRetainProviderInstruction() {
        String previousEndpoint = System.getProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT");
        String previousModel = System.getProperty("OPENPLAYER_INTENT_PROVIDER_MODEL");
        String previousApiKey = System.getProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", "https://example.invalid/v1/chat/completions");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", "test-model");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", "test-key");
        try {
            TestNpcService service = new TestNpcService();
            LocalCharacterDefinition character = conversationCharacter();
            HistoryInspectingIntentParser parser = new HistoryInspectingIntentParser();
            CompanionLifecycleManager manager = CompanionLifecycleManager.withAssignments(
                    () -> service,
                    () -> new LocalAssignmentRepositoryResult(List.of(LocalAssignmentDefinition.defaultFor(character)), List.of(character), List.of()),
                    () -> parser
            );
            manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID),
                    new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D), character.id());

            CommandSubmissionResult first = manager.submitSelectedCommandText(OWNER_ID, character.id(), "please follow me");
            CommandSubmissionResult second = manager.submitSelectedCommandText(OWNER_ID, character.id(), "keep following");

            require(first.status() == CommandSubmissionStatus.ACCEPTED, "first accepted command must submit successfully");
            require(second.status() == CommandSubmissionStatus.ACCEPTED, "second accepted command must submit successfully");
            require(parser.prompts.size() == 2, "both conversation submits must call the parser");
            String secondPrompt = parser.prompts.get(1);
            require(!secondPrompt.contains(HistoryInspectingIntentParser.SECRET_TOKEN),
                    "conversation history must not retain provider-derived tokens");
            require(!secondPrompt.contains(HistoryInspectingIntentParser.SECRET_PATH),
                    "conversation history must not retain provider-derived paths");
            require(!secondPrompt.contains(HistoryInspectingIntentParser.RAW_TEXT),
                    "conversation history must not retain raw provider text");
            require(secondPrompt.contains("Action accepted: FOLLOW_OWNER"),
                    "conversation history must retain only deterministic server-authored action summaries");
        } finally {
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", previousEndpoint);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", previousModel);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", previousApiKey);
        }
    }

    private static void resetMemoryClearsOnlySelectedConversationHistory() {
        String previousEndpoint = System.getProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT");
        String previousModel = System.getProperty("OPENPLAYER_INTENT_PROVIDER_MODEL");
        String previousApiKey = System.getProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", "https://example.invalid/v1/chat/completions");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", "test-model");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", "test-key");
        try {
            TestNpcService service = new TestNpcService();
            LocalCharacterDefinition character = conversationCharacter();
            HistoryInspectingIntentParser parser = new HistoryInspectingIntentParser();
            CompanionLifecycleManager manager = CompanionLifecycleManager.withAssignments(
                    () -> service,
                    () -> new LocalAssignmentRepositoryResult(List.of(LocalAssignmentDefinition.defaultFor(character)), List.of(character), List.of()),
                    () -> parser
            );
            manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID),
                    new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D), character.id());
            CommandSubmissionResult first = manager.submitSelectedCommandText(OWNER_ID, character.id(), "please follow me");
            require(first.status() == CommandSubmissionStatus.ACCEPTED, "first command must create bounded history");

            CommandSubmissionResult reset = manager.submitSelectedCommand(
                    OWNER_ID,
                    character.id(),
                    new AiPlayerNpcCommand(UUID.randomUUID(),
                            new CommandIntent(IntentKind.RESET_MEMORY, IntentPriority.NORMAL, ""))
            );
            require(reset.status() == CommandSubmissionStatus.ACCEPTED, "RESET_MEMORY must be accepted at lifecycle layer");
            require(reset.message().contains("conversationHistory=true"),
                    "RESET_MEMORY must report cleared conversation history truthfully");
            require(reset.message().contains("automationLocalMemory=false"),
                    "RESET_MEMORY must not claim unavailable local memory cleanup");

            CommandSubmissionResult second = manager.submitSelectedCommandText(OWNER_ID, character.id(), "after reset");
            require(second.status() == CommandSubmissionStatus.ACCEPTED, "conversation must continue after reset");
            String promptAfterReset = parser.prompts.get(1);
            require(!promptAfterReset.contains("Action accepted: FOLLOW_OWNER"),
                    "RESET_MEMORY must clear selected owner and assignment conversation history");
            require(service.submittedCommandCount == 2,
                    "RESET_MEMORY must not submit an automation command to the runtime service");
        } finally {
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", previousEndpoint);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", previousModel);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", previousApiKey);
        }
    }

    private static void resetMemoryTextPathDoesNotReappendClearedTurns() {
        String previousEndpoint = System.getProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT");
        String previousModel = System.getProperty("OPENPLAYER_INTENT_PROVIDER_MODEL");
        String previousApiKey = System.getProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", "https://example.invalid/v1/chat/completions");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", "test-model");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", "test-key");
        try {
            TestNpcService service = new TestNpcService();
            LocalCharacterDefinition character = conversationCharacter();
            ResetMemoryTextPathParser parser = new ResetMemoryTextPathParser();
            CompanionLifecycleManager manager = CompanionLifecycleManager.withAssignments(
                    () -> service,
                    () -> new LocalAssignmentRepositoryResult(List.of(LocalAssignmentDefinition.defaultFor(character)), List.of(character), List.of()),
                    () -> parser
            );
            manager.spawnSelectedAssignment(new NpcOwnerId(OWNER_ID),
                    new NpcSpawnLocation("minecraft:overworld", 1.0D, 64.0D, 1.0D), character.id());

            CommandSubmissionResult first = manager.submitSelectedCommandText(OWNER_ID, character.id(), "previous history");
            CommandSubmissionResult reset = manager.submitSelectedCommandText(OWNER_ID, character.id(), "reset everything");
            CommandSubmissionResult afterReset = manager.submitSelectedCommandText(OWNER_ID, character.id(), "fresh prompt");

            require(first.status() == CommandSubmissionStatus.ACCEPTED, "first text command must create history");
            require(reset.status() == CommandSubmissionStatus.ACCEPTED, "RESET_MEMORY text command must be accepted");
            require(afterReset.status() == CommandSubmissionStatus.ACCEPTED, "conversation must continue after reset text command");
            require(parser.prompts.size() == 3, "reset text test must inspect the next prompt");
            String nextPrompt = parser.prompts.get(2);
            require(!nextPrompt.contains("previous history"), "RESET_MEMORY text path must clear previous player history");
            require(!nextPrompt.contains("Action accepted: FOLLOW_OWNER"), "RESET_MEMORY text path must clear prior action summary");
            require(!nextPrompt.contains("reset everything"), "RESET_MEMORY text path must not reappend reset player text");
            require(!nextPrompt.contains("RESET_MEMORY accepted"), "RESET_MEMORY text path must not reappend reset result");
            require(service.submittedCommandCount == 2, "RESET_MEMORY text path must not submit automation to runtime");
        } finally {
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", previousEndpoint);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", previousModel);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", previousApiKey);
        }
    }

    private static void conversationHistoryEvictsOldOwnerKeysFromLaterPrompts() {
        String previousEndpoint = System.getProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT");
        String previousModel = System.getProperty("OPENPLAYER_INTENT_PROVIDER_MODEL");
        String previousApiKey = System.getProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", "https://example.invalid/v1/chat/completions");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", "test-model");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", "test-key");
        try {
            TestNpcService service = new TestNpcService();
            LocalCharacterDefinition character = conversationCharacter();
            LocalAssignmentDefinition assignment = LocalAssignmentDefinition.defaultFor(character);
            HistoryInspectingIntentParser parser = new HistoryInspectingIntentParser();
            CompanionLifecycleManager manager = CompanionLifecycleManager.withAssignments(
                    () -> service,
                    () -> new LocalAssignmentRepositoryResult(List.of(assignment), List.of(character), List.of()),
                    () -> parser
            );
            List<UUID> owners = new ArrayList<>();
            for (int index = 0; index < 65; index++) {
                UUID ownerId = UUID.fromString(String.format("00000000-0000-0000-0000-%012d", index + 100));
                owners.add(ownerId);
                service.sessions.add(new TestSession(
                        new NpcSessionId(UUID.randomUUID()),
                        spec(ownerId, OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + assignment.id(), "Talk Alex"),
                        NpcSessionStatus.ACTIVE
                ));
            }

            CommandSubmissionResult first = manager.submitSelectedCommandText(owners.get(0), assignment.id(), "evicted owner history");
            for (int index = 1; index < owners.size(); index++) {
                CommandSubmissionResult result = manager.submitSelectedCommandText(owners.get(index), assignment.id(), "owner filler " + index);
                require(result.status() == CommandSubmissionStatus.ACCEPTED, "owner filler history must submit successfully");
            }
            CommandSubmissionResult later = manager.submitSelectedCommandText(owners.get(0), assignment.id(), "fresh owner request");

            require(first.status() == CommandSubmissionStatus.ACCEPTED, "first owner history must submit successfully");
            require(later.status() == CommandSubmissionStatus.ACCEPTED, "evicted owner key must accept a fresh request");
            String laterPrompt = parser.prompts.get(parser.prompts.size() - 1);
            require(!laterPrompt.contains("evicted owner history"),
                    "old owner conversation history key must be evicted from later prompts");
        } finally {
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", previousEndpoint);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", previousModel);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", previousApiKey);
        }
    }

    private static void conversationHistoryEvictsOldAssignmentKeysFromLaterPrompts() {
        String previousEndpoint = System.getProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT");
        String previousModel = System.getProperty("OPENPLAYER_INTENT_PROVIDER_MODEL");
        String previousApiKey = System.getProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", "https://example.invalid/v1/chat/completions");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", "test-model");
        System.setProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", "test-key");
        try {
            TestNpcService service = new TestNpcService();
            LocalCharacterDefinition character = conversationCharacter();
            List<LocalAssignmentDefinition> assignments = new ArrayList<>();
            for (int index = 0; index < 65; index++) {
                LocalAssignmentDefinition assignment = new LocalAssignmentDefinition("talk_" + index, character.id(), "Talk " + index);
                assignments.add(assignment);
                service.sessions.add(new TestSession(
                        new NpcSessionId(UUID.randomUUID()),
                        spec(OWNER_ID, OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX + assignment.id(), "Talk Alex"),
                        NpcSessionStatus.ACTIVE
                ));
            }
            HistoryInspectingIntentParser parser = new HistoryInspectingIntentParser();
            CompanionLifecycleManager manager = CompanionLifecycleManager.withAssignments(
                    () -> service,
                    () -> new LocalAssignmentRepositoryResult(assignments, List.of(character), List.of()),
                    () -> parser
            );

            CommandSubmissionResult first = manager.submitSelectedCommandText(OWNER_ID, assignments.get(0).id(), "evicted assignment history");
            for (int index = 1; index < assignments.size(); index++) {
                CommandSubmissionResult result = manager.submitSelectedCommandText(OWNER_ID, assignments.get(index).id(), "assignment filler " + index);
                require(result.status() == CommandSubmissionStatus.ACCEPTED, "assignment filler history must submit successfully");
            }
            CommandSubmissionResult later = manager.submitSelectedCommandText(OWNER_ID, assignments.get(0).id(), "fresh assignment request");

            require(first.status() == CommandSubmissionStatus.ACCEPTED, "first assignment history must submit successfully");
            require(later.status() == CommandSubmissionStatus.ACCEPTED, "evicted assignment key must accept a fresh request");
            String laterPrompt = parser.prompts.get(parser.prompts.size() - 1);
            require(!laterPrompt.contains("evicted assignment history"),
                    "old assignment conversation history key must be evicted from later prompts");
        } finally {
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_ENDPOINT", previousEndpoint);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_MODEL", previousModel);
            restoreProperty("OPENPLAYER_INTENT_PROVIDER_API_KEY", previousApiKey);
        }
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

    private static LocalCharacterDefinition conversationCharacter() {
        return new LocalCharacterDefinition(
                "alex_talk",
                "Talk Alex",
                null,
                null,
                null,
                null,
                "You are a local helper.",
                null
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

    private static void requireProviderParseSuccess(IntentKind kind, String message) {
        List<OpenPlayerDebugEvent> events = OpenPlayerDebugEvents.recent(OpenPlayerDebugEvents.MAX_MEMORY_EVENTS);
        require(events.stream().anyMatch(event -> "provider_parse".equals(event.category())
                        && "success".equals(event.status())
                        && event.detail().contains("kind=" + kind.name())
                        && event.detail().contains("instructionLength=")),
                message);
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static class TestNpcService implements AiPlayerNpcService {
        private final List<AiPlayerNpcSession> sessions = new ArrayList<>();
        private final Map<String, TestSession> sessionsByIdentity = new LinkedHashMap<>();
        private int submittedCommandTextCount;
        private int submittedCommandCount;
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
            submittedCommandCount++;
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

        int submittedCommandCount() {
            return submittedCommandCount;
        }
    }

    private enum PlannerTestMode {
        FINAL_CHAT_ONLY,
        PRIMITIVE_THEN_CHAT
    }

    private static final class PlannerTestNpcService extends TestNpcService implements InteractivePlannerCommandTextService {
        private final PlannerTestMode mode;
        private int plannerRequestCount;
        private PlannerCommandTextRequest lastPlannerRequest;

        private PlannerTestNpcService(PlannerTestMode mode) {
            this.mode = mode;
        }

        @Override
        public CommandSubmissionResult submitPlannedCommandText(NpcSessionId sessionId, PlannerCommandTextRequest request,
                                                                PlannerCommandTextCallbacks callbacks) {
            plannerRequestCount++;
            lastPlannerRequest = request;
            if (mode == PlannerTestMode.PRIMITIVE_THEN_CHAT) {
                CommandIntent primitive = new CommandIntent(IntentKind.FOLLOW_OWNER, IntentPriority.HIGH, "follow owner safely");
                callbacks.acceptedIntentRecorder().accept(primitive);
                AiPlayerNpcCommand command = new AiPlayerNpcCommand(UUID.randomUUID(), primitive);
                submitCommand(sessionId, command);
                callbacks.submittedCommandRecorder().accept(command);
                CommandIntent chat = new CommandIntent(IntentKind.CHAT, IntentPriority.NORMAL, "Ready");
                callbacks.acceptedIntentRecorder().accept(chat);
                callbacks.completion().accept(new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED, "Ready"));
                return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED,
                        "Command text queued for interactive provider planning");
            }
            CommandIntent chat = new CommandIntent(IntentKind.CHAT, IntentPriority.NORMAL, "Planner reply");
            callbacks.acceptedIntentRecorder().accept(chat);
            callbacks.completion().accept(new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED, "Planner reply"));
            return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED,
                    "Command text queued for interactive provider planning");
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

    private static final class CountingIntentParser implements IntentParser {
        private int parseCount;

        @Override
        public CommandIntent parse(String input) {
            parseCount++;
            return new CommandIntent(IntentKind.FOLLOW_OWNER, IntentPriority.HIGH,
                    "follow owner safely");
        }
    }

    private static final class HistoryInspectingIntentParser implements IntentParser {
        private static final String SECRET_TOKEN = "sk-live-history-secret";
        private static final String SECRET_PATH = "/home/alex/.ssh/id_rsa";
        private static final String RAW_TEXT = "raw provider content";
        private final List<String> prompts = new ArrayList<>();

        @Override
        public CommandIntent parse(String input) {
            prompts.add(input);
            String instruction = prompts.size() == 1
                    ? "follow using token " + SECRET_TOKEN + " from " + SECRET_PATH + " " + RAW_TEXT.repeat(10)
                    : "follow owner safely";
            return new CommandIntent(IntentKind.FOLLOW_OWNER, IntentPriority.HIGH, instruction);
        }
    }

    private static final class ResetMemoryTextPathParser implements IntentParser {
        private final List<String> prompts = new ArrayList<>();

        @Override
        public CommandIntent parse(String input) {
            prompts.add(input);
            if (prompts.size() == 2) {
                return new CommandIntent(IntentKind.RESET_MEMORY, IntentPriority.NORMAL, "");
            }
            return new CommandIntent(IntentKind.FOLLOW_OWNER, IntentPriority.HIGH, "follow owner safely");
        }
    }
}
