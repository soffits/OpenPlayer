package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.AiPlayerNpcService;
import dev.soffits.openplayer.api.AiPlayerNpcSession;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.api.NpcSessionStatus;
import dev.soffits.openplayer.api.NpcSpawnLocation;
import dev.soffits.openplayer.character.LocalAssignmentDefinition;
import dev.soffits.openplayer.character.LocalAssignmentRepositoryResult;
import dev.soffits.openplayer.character.LocalCharacterDefinition;
import dev.soffits.openplayer.character.LocalCharacterRepositoryResult;
import dev.soffits.openplayer.conversation.ConversationHistoryTrimmer;
import dev.soffits.openplayer.conversation.ConversationContextSnapshot;
import dev.soffits.openplayer.conversation.ConversationLoop;
import dev.soffits.openplayer.conversation.ConversationStatusRepository;
import dev.soffits.openplayer.conversation.ConversationTurn;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentParser;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.OpenPlayerIntentParserConfig;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidationResult;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidator;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class CompanionLifecycleManager {
    private static final int MAX_ACTIVE_ASSIGNMENTS_PER_OWNER = 4;
    private static final int MAX_CONVERSATION_HISTORY_KEYS = 64;
    private final Supplier<AiPlayerNpcService> npcServiceSupplier;
    private final Supplier<LocalAssignmentRepositoryResult> assignmentRepositoryResultSupplier;
    private final ConversationLoop conversationLoop;
    private final ConversationStatusRepository conversationStatusRepository = new ConversationStatusRepository();
    private final Map<ConversationHistoryKey, List<ConversationTurn>> conversationHistory = new LinkedHashMap<>();

    public CompanionLifecycleManager(Supplier<AiPlayerNpcService> npcServiceSupplier,
                                      Supplier<LocalCharacterRepositoryResult> characterRepositoryResultSupplier) {
        this(npcServiceSupplier, characterRepositoryResultSupplier, new dev.soffits.openplayer.intent.DisabledIntentParser());
    }

    public CompanionLifecycleManager(Supplier<AiPlayerNpcService> npcServiceSupplier,
                                     Supplier<LocalCharacterRepositoryResult> characterRepositoryResultSupplier,
                                     IntentParser intentParser) {
        this(npcServiceSupplier, characterRepositoryResultSupplier, () -> intentParser);
    }

    public CompanionLifecycleManager(Supplier<AiPlayerNpcService> npcServiceSupplier,
                                     Supplier<LocalCharacterRepositoryResult> characterRepositoryResultSupplier,
                                     Supplier<IntentParser> intentParserSupplier) {
        if (npcServiceSupplier == null) {
            throw new IllegalArgumentException("npcServiceSupplier cannot be null");
        }
        if (characterRepositoryResultSupplier == null) {
            throw new IllegalArgumentException("characterRepositoryResultSupplier cannot be null");
        }
        this.npcServiceSupplier = npcServiceSupplier;
        this.assignmentRepositoryResultSupplier = () -> defaultAssignmentResult(characterRepositoryResultSupplier.get());
        this.conversationLoop = new ConversationLoop(intentParserSupplier, OpenPlayerIntentParserConfig::status);
    }

    public static CompanionLifecycleManager withAssignments(Supplier<AiPlayerNpcService> npcServiceSupplier,
                                                            Supplier<LocalAssignmentRepositoryResult> assignmentRepositoryResultSupplier,
                                                            Supplier<IntentParser> intentParserSupplier) {
        return new CompanionLifecycleManager(npcServiceSupplier, assignmentRepositoryResultSupplier, intentParserSupplier, true);
    }

    private CompanionLifecycleManager(Supplier<AiPlayerNpcService> npcServiceSupplier,
                                      Supplier<LocalAssignmentRepositoryResult> assignmentRepositoryResultSupplier,
                                      Supplier<IntentParser> intentParserSupplier,
                                      boolean ignored) {
        if (npcServiceSupplier == null) {
            throw new IllegalArgumentException("npcServiceSupplier cannot be null");
        }
        if (assignmentRepositoryResultSupplier == null) {
            throw new IllegalArgumentException("assignmentRepositoryResultSupplier cannot be null");
        }
        this.npcServiceSupplier = npcServiceSupplier;
        this.assignmentRepositoryResultSupplier = assignmentRepositoryResultSupplier;
        this.conversationLoop = new ConversationLoop(intentParserSupplier, OpenPlayerIntentParserConfig::status);
    }

    public CommandSubmissionResult spawnSelected(NpcOwnerId ownerId, NpcSpawnLocation spawnLocation,
                                                 String characterId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId cannot be null");
        }
        if (spawnLocation == null) {
            throw new IllegalArgumentException("spawnLocation cannot be null");
        }
        return spawnSelectedAssignment(ownerId, spawnLocation, characterId);
    }

    public CommandSubmissionResult spawnSelectedAssignment(NpcOwnerId ownerId, NpcSpawnLocation spawnLocation,
                                                           String assignmentId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId cannot be null");
        }
        if (spawnLocation == null) {
            throw new IllegalArgumentException("spawnLocation cannot be null");
        }
        Optional<ResolvedAssignment> resolvedAssignment = findAssignment(assignmentId);
        if (resolvedAssignment.isEmpty()) {
            return rejectedUnknownAssignment();
        }
        if (findActiveSession(ownerId.value(), resolvedAssignment.get()).isEmpty()
                && activeAssignmentCount(ownerId.value()) >= MAX_ACTIVE_ASSIGNMENTS_PER_OWNER) {
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED,
                    "Active companion assignment limit reached");
        }
        LocalAssignmentDefinition assignment = resolvedAssignment.get().assignment();
        LocalCharacterDefinition character = resolvedAssignment.get().character();
        npcService().spawn(assignment.toNpcSpec(
                character,
                ownerId,
                spawnLocation
        ));
        if (hasConversationConfig(character)) {
            conversationStatusRepository.recordGreeting(
                    ownerId.value(),
                    assignment.id(),
                    assignment.resolvedDisplayName(character)
            );
        }
        return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED, "Companion spawned");
    }

    public CommandSubmissionResult despawnSelected(UUID ownerId, String characterId) {
        return despawnSelectedAssignment(ownerId, characterId);
    }

    public CommandSubmissionResult despawnSelectedAssignment(UUID ownerId, String assignmentId) {
        Optional<ResolvedAssignment> resolvedAssignment = findAssignment(assignmentId);
        if (resolvedAssignment.isEmpty()) {
            return rejectedUnknownAssignment();
        }
        AiPlayerNpcService service = npcService();
        boolean despawned = false;
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (matchesLocalAssignmentSession(ownerId, session, resolvedAssignment.get().assignment())) {
                despawned |= service.despawn(session.sessionId());
            }
        }
        if (!despawned) {
            return new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION, "Companion is not spawned");
        }
        return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED, "Companion despawned");
    }

    public CommandSubmissionResult submitSelectedCommand(UUID ownerId, String characterId, AiPlayerNpcCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        if (command.intent().kind() == IntentKind.RESET_MEMORY) {
            return resetSelectedMemory(ownerId, characterId, command.intent());
        }
        Optional<AiPlayerNpcSession> session = findActiveSession(ownerId, characterId);
        if (session.isEmpty()) {
            OpenPlayerDebugEvents.record("companion", "unknown_session", characterId, null, null, "submit_command");
            return unknownOrMissingAssignment(characterId);
        }
        return npcService().submitCommand(session.get().sessionId(), command);
    }

    private CommandSubmissionResult resetSelectedMemory(UUID ownerId, String characterId, CommandIntent intent) {
        RuntimeIntentValidationResult validation = RuntimeIntentValidator.validate(intent, true);
        if (!validation.isAccepted()) {
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, validation.message());
        }
        Optional<ResolvedAssignment> resolvedAssignment = findAssignment(characterId);
        if (resolvedAssignment.isEmpty()) {
            OpenPlayerDebugEvents.record("memory", "unknown_assignment", characterId, null, null, "assignment_not_found");
            return rejectedUnknownAssignment();
        }
        LocalAssignmentDefinition assignment = resolvedAssignment.get().assignment();
        ConversationHistoryKey historyKey = new ConversationHistoryKey(ownerId, assignment.id());
        boolean clearedConversationHistory = conversationHistory.remove(historyKey) != null;
        conversationStatusRepository.recordAction(ownerId, assignment.id(), intent);
        OpenPlayerDebugEvents.record("memory", "reset", assignment.id(), resolvedAssignment.get().character().id(), null,
                "conversationHistory=" + clearedConversationHistory + " automationLocalMemory=false");
        return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED,
                "RESET_MEMORY accepted: cleared conversationHistory=" + clearedConversationHistory
                        + ", automationLocalMemory=false");
    }

    public CommandSubmissionResult submitSelectedCommandText(UUID ownerId, String characterId, String commandText) {
        if (commandText == null) {
            throw new IllegalArgumentException("commandText cannot be null");
        }
        Optional<ResolvedAssignment> resolvedAssignment = findAssignment(characterId);
        if (resolvedAssignment.isEmpty()) {
            OpenPlayerDebugEvents.record("conversation", "unknown_assignment", characterId, null, null, "assignment_not_found");
            return rejectedUnknownAssignment();
        }
        LocalAssignmentDefinition assignment = resolvedAssignment.get().assignment();
        LocalCharacterDefinition character = resolvedAssignment.get().character();
        boolean conversationConfigured = hasConversationConfig(character);
        if (!conversationConfigured) {
            OpenPlayerDebugEvents.record("conversation", "unavailable", assignment.id(), character.id(), null,
                    "conversation_config_missing messageLength=" + commandText.trim().length());
            return new CommandSubmissionResult(CommandSubmissionStatus.UNAVAILABLE,
                    "Conversation unavailable: conversation config missing");
        }
        conversationStatusRepository.recordPlayerMessage(ownerId, assignment.id(), commandText);
        Optional<AiPlayerNpcSession> session = findActiveSession(ownerId, resolvedAssignment.get());
        if (session.isEmpty()) {
            conversationStatusRepository.recordFailure(ownerId, assignment.id(), "Companion is not spawned");
            OpenPlayerDebugEvents.record("conversation", "unknown_session", assignment.id(), character.id(), null,
                    "companion_not_spawned messageLength=" + commandText.trim().length());
            return new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION, "Companion is not spawned");
        }
        ConversationHistoryKey historyKey = new ConversationHistoryKey(ownerId, assignment.id());
        List<ConversationTurn> history = conversationHistory.getOrDefault(historyKey, List.of());
        AiPlayerNpcCommand[] submittedCommand = new AiPlayerNpcCommand[1];
        String sessionId = session.get().sessionId().value().toString();
        ConversationContextSnapshot contextSnapshot = conversationContextSnapshot(session.get());
        OpenPlayerDebugEvents.record("provider_parse", "attempted", assignment.id(), character.id(), sessionId,
                "source=conversation messageLength=" + commandText.trim().length());
        CommandSubmissionResult result = conversationLoop.submit(
                character,
                commandText,
                history,
                contextSnapshot,
                command -> {
                    submittedCommand[0] = command;
                    return submitSelectedCommand(ownerId, assignment.id(), command);
                },
                intent -> OpenPlayerDebugEvents.record("provider_parse", "success", assignment.id(), character.id(), sessionId,
                        "kind=" + intent.kind().name() + " instructionLength=" + intent.instruction().length())
        );
        if (result.status() == CommandSubmissionStatus.ACCEPTED && submittedCommand[0] != null
                && submittedCommand[0].intent().kind() != IntentKind.RESET_MEMORY) {
            appendConversationTurn(historyKey, new ConversationTurn("player", commandText));
            appendConversationTurn(historyKey, new ConversationTurn(
                    "openplayer",
                    "Action accepted: " + submittedCommand[0].intent().kind().name()
            ));
            conversationStatusRepository.recordAction(ownerId, assignment.id(), submittedCommand[0].intent());
        } else if (result.status() == CommandSubmissionStatus.ACCEPTED && submittedCommand[0] != null
                && submittedCommand[0].intent().kind() == IntentKind.RESET_MEMORY) {
            return result;
        } else if (result.status() == CommandSubmissionStatus.ACCEPTED) {
            appendConversationTurn(historyKey, new ConversationTurn("player", commandText));
            appendConversationTurn(historyKey, new ConversationTurn("openplayer", result.message()));
            conversationStatusRepository.recordNpcReply(ownerId, assignment.id(), result.message());
        } else if (result.status() != CommandSubmissionStatus.ACCEPTED) {
            conversationStatusRepository.recordFailure(ownerId, assignment.id(), result.message());
            OpenPlayerDebugEvents.record("conversation", result.status().name(), assignment.id(), character.id(), sessionId,
                    result.message());
        }
        return result;
    }

    public List<String> conversationEventLines(UUID ownerId, LocalAssignmentDefinition assignment) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId cannot be null");
        }
        if (assignment == null) {
            throw new IllegalArgumentException("assignment cannot be null");
        }
        return conversationStatusRepository.eventLines(ownerId, assignment.id());
    }

    public List<String> selectedRuntimeStatusLines(UUID ownerId, String assignmentId) {
        Optional<ResolvedAssignment> resolvedAssignment = findAssignment(assignmentId);
        if (resolvedAssignment.isEmpty()) {
            return List.of("selected_assignment=unknown source=selected_npc status=unknown_assignment active=idle queued=0");
        }
        String safeAssignmentId = safeStatusToken(resolvedAssignment.get().assignment().id());
        Optional<AiPlayerNpcSession> session = findActiveSession(ownerId, resolvedAssignment.get());
        if (session.isEmpty()) {
            return List.of("selected_assignment=" + safeAssignmentId
                    + " source=selected_npc status=despawned active=idle queued=0");
        }
        AiPlayerNpcService service = npcService();
        if (service instanceof RuntimeAiPlayerNpcService runtimeService) {
            return runtimeService.selectedRuntimeStatusLines(session.get().sessionId(), safeAssignmentId);
        }
        return List.of("selected_assignment=" + safeAssignmentId
                + " source=selected_npc status=runtime_snapshot_unavailable active=unknown queued=unknown");
    }

    public String lifecycleStatus(UUID ownerId, LocalCharacterDefinition character) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId cannot be null");
        }
        if (character == null) {
            throw new IllegalArgumentException("character cannot be null");
        }
        AiPlayerNpcService service = npcService();
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (matchesLocalCharacterSession(ownerId, session, character)) {
                NpcSessionStatus status = service.status(session.sessionId());
                return status.name().toLowerCase(Locale.ROOT);
            }
        }
        return "despawned";
    }

    static boolean matchesLocalCharacterSession(UUID ownerId, AiPlayerNpcSession session,
                                                 LocalCharacterDefinition character) {
        if (ownerId == null || session == null || character == null) {
            return false;
        }
        return session.spec().ownerId().value().equals(ownerId)
                && (session.spec().roleId().value().equals(character.toSessionRoleId().value())
                || session.spec().roleId().value().equals(LocalAssignmentDefinition.defaultFor(character).toSessionRoleId().value()));
    }

    public String lifecycleStatus(UUID ownerId, LocalAssignmentDefinition assignment) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId cannot be null");
        }
        if (assignment == null) {
            throw new IllegalArgumentException("assignment cannot be null");
        }
        AiPlayerNpcService service = npcService();
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (matchesLocalAssignmentSession(ownerId, session, assignment)) {
                NpcSessionStatus status = service.status(session.sessionId());
                return status.name().toLowerCase(Locale.ROOT);
            }
        }
        return "despawned";
    }

    static boolean matchesLocalAssignmentSession(UUID ownerId, AiPlayerNpcSession session,
                                                 LocalAssignmentDefinition assignment) {
        if (ownerId == null || session == null || assignment == null) {
            return false;
        }
        return session.spec().ownerId().value().equals(ownerId)
                && session.spec().roleId().value().equals(assignment.toSessionRoleId().value());
    }

    private Optional<AiPlayerNpcSession> findActiveSession(UUID ownerId, String characterId) {
        Optional<ResolvedAssignment> resolvedAssignment = findAssignment(characterId);
        if (resolvedAssignment.isEmpty()) {
            return Optional.empty();
        }
        return findActiveSession(ownerId, resolvedAssignment.get());
    }

    private Optional<AiPlayerNpcSession> findActiveSession(UUID ownerId, LocalCharacterDefinition character) {
        AiPlayerNpcService service = npcService();
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (matchesLocalCharacterSession(ownerId, session, character)
                    && service.status(session.sessionId()) != NpcSessionStatus.DESPAWNED) {
                return Optional.of(session);
            }
        }
        return Optional.empty();
    }

    private Optional<AiPlayerNpcSession> findActiveSession(UUID ownerId, ResolvedAssignment resolvedAssignment) {
        AiPlayerNpcService service = npcService();
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (matchesLocalAssignmentSession(ownerId, session, resolvedAssignment.assignment())
                    && service.status(session.sessionId()) != NpcSessionStatus.DESPAWNED) {
                return Optional.of(session);
            }
        }
        return Optional.empty();
    }

    private int activeAssignmentCount(UUID ownerId) {
        AiPlayerNpcService service = npcService();
        int count = 0;
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (session.spec().ownerId().value().equals(ownerId)
                    && session.spec().roleId().value().startsWith(OpenPlayerConstants.LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX)
                    && service.status(session.sessionId()) != NpcSessionStatus.DESPAWNED) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasConversationConfig(LocalCharacterDefinition character) {
        return character.conversationPrompt() != null || character.conversationSettings() != null;
    }

    private void appendConversationTurn(ConversationHistoryKey historyKey, ConversationTurn turn) {
        List<ConversationTurn> currentHistory = conversationHistory.getOrDefault(historyKey, List.of());
        java.util.ArrayList<ConversationTurn> updatedHistory = new java.util.ArrayList<>(currentHistory);
        updatedHistory.add(turn);
        conversationHistory.put(historyKey, ConversationHistoryTrimmer.trim(
                updatedHistory,
                ConversationLoop.MAX_HISTORY_TURNS,
                ConversationLoop.MAX_HISTORY_CHARACTERS
        ));
        trimConversationHistoryKeys();
    }

    private void trimConversationHistoryKeys() {
        while (conversationHistory.size() > MAX_CONVERSATION_HISTORY_KEYS) {
            ConversationHistoryKey oldestKey = conversationHistory.keySet().iterator().next();
            conversationHistory.remove(oldestKey);
        }
    }

    private Optional<LocalCharacterDefinition> findCharacter(String characterId) {
        if (characterId == null || characterId.isBlank()) {
            return Optional.empty();
        }
        String normalizedCharacterId = characterId.trim();
        LocalAssignmentRepositoryResult result = assignmentRepositoryResultSupplier.get();
        for (LocalCharacterDefinition character : result.characters()) {
            if (character.id().equals(normalizedCharacterId)) {
                return Optional.of(character);
            }
        }
        return Optional.empty();
    }

    private Optional<ResolvedAssignment> findAssignment(String assignmentId) {
        if (assignmentId == null || assignmentId.isBlank()) {
            return Optional.empty();
        }
        String normalizedAssignmentId = assignmentId.trim();
        LocalAssignmentRepositoryResult result = assignmentRepositoryResultSupplier.get();
        for (LocalAssignmentDefinition assignment : result.assignments()) {
            if (assignment.id().equals(normalizedAssignmentId)) {
                for (LocalCharacterDefinition character : result.characters()) {
                    if (character.id().equals(assignment.characterId())) {
                        return Optional.of(new ResolvedAssignment(assignment, character));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private AiPlayerNpcService npcService() {
        return npcServiceSupplier.get();
    }

    private ConversationContextSnapshot conversationContextSnapshot(AiPlayerNpcSession session) {
        AiPlayerNpcService service = npcService();
        if (service instanceof RuntimeAiPlayerNpcService runtimeService) {
            return runtimeService.conversationContextSnapshot(session.sessionId());
        }
        return ConversationContextSnapshot.EMPTY;
    }

    private CommandSubmissionResult unknownOrMissingSession(String characterId) {
        if (findCharacter(characterId).isEmpty()) {
            return rejectedUnknownAssignment();
        }
        return new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION, "Companion is not spawned");
    }

    private CommandSubmissionResult unknownOrMissingAssignment(String assignmentId) {
        if (findAssignment(assignmentId).isEmpty()) {
            return rejectedUnknownAssignment();
        }
        return new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION, "Companion is not spawned");
    }

    private CommandSubmissionResult rejectedUnknownCharacter() {
        return rejectedUnknownAssignment();
    }

    private CommandSubmissionResult rejectedUnknownAssignment() {
        return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, "Unknown local assignment");
    }

    private static LocalAssignmentRepositoryResult defaultAssignmentResult(LocalCharacterRepositoryResult characterResult) {
        List<LocalAssignmentDefinition> assignments = new java.util.ArrayList<>();
        for (LocalCharacterDefinition character : characterResult.characters()) {
            assignments.add(LocalAssignmentDefinition.defaultFor(character));
        }
        return new LocalAssignmentRepositoryResult(assignments, characterResult.characters(), characterResult.errors());
    }

    private static String safeStatusToken(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        StringBuilder builder = new StringBuilder();
        String trimmed = value.trim();
        for (int index = 0; index < trimmed.length() && builder.length() < 48; index++) {
            char character = trimmed.charAt(index);
            if ((character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9') || character == '_' || character == '-'
                    || character == ':' || character == '.') {
                builder.append(character);
            } else {
                builder.append('_');
            }
        }
        return builder.isEmpty() ? "unknown" : builder.toString();
    }

    private record ResolvedAssignment(LocalAssignmentDefinition assignment, LocalCharacterDefinition character) {
    }

    private record ConversationHistoryKey(UUID ownerId, String assignmentId) {
    }
}
