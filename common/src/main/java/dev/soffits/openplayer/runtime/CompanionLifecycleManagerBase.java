package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.AiPlayerNpcService;
import dev.soffits.openplayer.api.AiPlayerNpcSession;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.api.NpcSessionStatus;
import dev.soffits.openplayer.character.LocalAssignmentDefinition;
import dev.soffits.openplayer.character.LocalAssignmentRepositoryResult;
import dev.soffits.openplayer.character.LocalCharacterDefinition;
import dev.soffits.openplayer.character.LocalCharacterRepositoryResult;
import dev.soffits.openplayer.conversation.ActionLikeRequestDiagnostics;
import dev.soffits.openplayer.conversation.ConversationHistoryTrimmer;
import dev.soffits.openplayer.conversation.ConversationContextSnapshot;
import dev.soffits.openplayer.conversation.ConversationLoop;
import dev.soffits.openplayer.conversation.ConversationReplyText;
import dev.soffits.openplayer.conversation.ConversationStatusRepository;
import dev.soffits.openplayer.conversation.ConversationTurn;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.runtime.planner.PlannerPrimitiveProgress;
import dev.soffits.openplayer.intent.IntentParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Consumer;

abstract class CompanionLifecycleManagerBase {
    protected static final int MAX_ACTIVE_ASSIGNMENTS_PER_OWNER = 4;
    protected static final int MAX_CONVERSATION_HISTORY_KEYS = 64;
    protected final Supplier<AiPlayerNpcService> npcServiceSupplier;
    protected final Supplier<LocalAssignmentRepositoryResult> assignmentRepositoryResultSupplier;
    protected final ConversationLoop conversationLoop;
    protected final ConversationStatusRepository conversationStatusRepository = new ConversationStatusRepository();
    protected final Map<ConversationHistoryKey, List<ConversationTurn>> conversationHistory = new LinkedHashMap<>();

    protected CompanionLifecycleManagerBase(Supplier<AiPlayerNpcService> npcServiceSupplier,
                                            Supplier<LocalAssignmentRepositoryResult> assignmentRepositoryResultSupplier,
                                            ConversationLoop conversationLoop) {
        this.npcServiceSupplier = npcServiceSupplier;
        this.assignmentRepositoryResultSupplier = assignmentRepositoryResultSupplier;
        this.conversationLoop = conversationLoop;
    }

    protected CommandSubmissionResult submitPlannedConversation(ConversationSubmissionContext context,
                                                              InteractivePlannerCommandTextService plannerService,
                                                              Consumer<PlannerPrimitiveProgress.Display> progress,
                                                              Consumer<CommandSubmissionResult> completion) {
        List<AiPlayerNpcCommand> submittedCommands = new ArrayList<>();
        CommandIntent[] lastIntent = new CommandIntent[1];
        return plannerService.submitPlannedCommandText(
                new dev.soffits.openplayer.api.NpcSessionId(UUID.fromString(context.sessionId())),
                new InteractivePlannerCommandTextService.PlannerCommandTextRequest(
                        context.commandText(),
                        context.parseRequest().prompt(),
                        context.assignment().id(),
                        context.character().id(),
                        "conversation"
                ),
                new InteractivePlannerCommandTextService.PlannerCommandTextCallbacks(
                        intent -> {
                            lastIntent[0] = intent;
                            OpenPlayerDebugEvents.record("provider_parse", "success", context.assignment().id(),
                                    context.character().id(), context.sessionId(),
                                    "kind=" + intent.kind().name() + " instructionLength=" + intent.instruction().length());
                        },
                        command -> {
                            submittedCommands.add(command);
                            conversationStatusRepository.recordAction(context.ownerId(), context.assignment().id(), command.intent());
                        },
                        progress,
                        result -> completion.accept(finishPlannedConversationSubmission(context, result, lastIntent[0], submittedCommands))
                )
        );
    }

    protected CommandSubmissionResult finishPlannedConversationSubmission(ConversationSubmissionContext context,
                                                                        CommandSubmissionResult result,
                                                                        CommandIntent lastIntent,
                                                                        List<AiPlayerNpcCommand> submittedCommands) {
        if (submittedCommands.isEmpty() && lastIntent != null) {
            ActionLikeRequestDiagnostics.recordChatIfActionLike(context.commandText(), lastIntent, context.assignment().id(),
                    context.character().id(), context.sessionId());
        }
        CommandSubmissionResult displayResult = plannerDisplayResult(result, lastIntent);
        if (!submittedCommands.isEmpty()) {
            appendConversationTurn(context.historyKey(), new ConversationTurn("player", context.commandText()));
            for (AiPlayerNpcCommand command : submittedCommands) {
                appendConversationTurn(context.historyKey(), new ConversationTurn(
                        "openplayer",
                        "Action accepted: " + command.intent().kind().name()
                ));
            }
            if (displayResult.status() == CommandSubmissionStatus.ACCEPTED && lastIntent != null
                    && (lastIntent.kind() == IntentKind.CHAT || lastIntent.kind() == IntentKind.UNAVAILABLE)) {
                appendConversationTurn(context.historyKey(), new ConversationTurn("openplayer", displayResult.message()));
                conversationStatusRepository.recordNpcReply(context.ownerId(), context.assignment().id(), displayResult.message());
            } else if (displayResult.status() != CommandSubmissionStatus.ACCEPTED) {
                conversationStatusRepository.recordFailure(context.ownerId(), context.assignment().id(), displayResult.message());
            }
            return displayResult;
        }
        if (displayResult.status() == CommandSubmissionStatus.ACCEPTED) {
            appendConversationTurn(context.historyKey(), new ConversationTurn("player", context.commandText()));
            appendConversationTurn(context.historyKey(), new ConversationTurn("openplayer", displayResult.message()));
            conversationStatusRepository.recordNpcReply(context.ownerId(), context.assignment().id(), displayResult.message());
        } else {
            conversationStatusRepository.recordFailure(context.ownerId(), context.assignment().id(), displayResult.message());
            OpenPlayerDebugEvents.record("conversation", displayResult.status().name(), context.assignment().id(),
                    context.character().id(), context.sessionId(), displayResult.message());
        }
        return displayResult;
    }

    protected static CommandSubmissionResult plannerDisplayResult(CommandSubmissionResult result, CommandIntent lastIntent) {
        if (result.status() != CommandSubmissionStatus.ACCEPTED || lastIntent == null) {
            return result;
        }
        if (lastIntent.kind() == IntentKind.CHAT) {
            return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED,
                    ConversationReplyText.chatReply(lastIntent.instruction()));
        }
        if (lastIntent.kind() == IntentKind.UNAVAILABLE) {
            return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED,
                    ConversationReplyText.unavailableReply(lastIntent.instruction()));
        }
        return result;
    }

    protected ConversationSubmissionContext prepareConversationSubmission(UUID ownerId, String characterId, String commandText) {
        if (commandText == null) {
            throw new IllegalArgumentException("commandText cannot be null");
        }
        Optional<ResolvedAssignment> resolvedAssignment = findAssignment(characterId);
        if (resolvedAssignment.isEmpty()) {
            OpenPlayerDebugEvents.record("conversation", "unknown_assignment", characterId, null, null, "assignment_not_found");
            return ConversationSubmissionContext.immediate(rejectedUnknownAssignment());
        }
        LocalAssignmentDefinition assignment = resolvedAssignment.get().assignment();
        LocalCharacterDefinition character = resolvedAssignment.get().character();
        if (!hasConversationConfig(character)) {
            OpenPlayerDebugEvents.record("conversation", "unavailable", assignment.id(), character.id(), null,
                    "conversation_config_missing messageLength=" + commandText.trim().length());
            return ConversationSubmissionContext.immediate(new CommandSubmissionResult(CommandSubmissionStatus.UNAVAILABLE,
                    "Conversation unavailable: conversation config missing"));
        }
        conversationStatusRepository.recordPlayerMessage(ownerId, assignment.id(), commandText);
        Optional<AiPlayerNpcSession> session = findActiveSession(ownerId, resolvedAssignment.get());
        if (session.isEmpty()) {
            conversationStatusRepository.recordFailure(ownerId, assignment.id(), "Companion is not spawned");
            OpenPlayerDebugEvents.record("conversation", "unknown_session", assignment.id(), character.id(), null,
                    "companion_not_spawned messageLength=" + commandText.trim().length());
            return ConversationSubmissionContext.immediate(new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION,
                    "Companion is not spawned"));
        }
        ConversationHistoryKey historyKey = new ConversationHistoryKey(ownerId, assignment.id());
        List<ConversationTurn> history = conversationHistory.getOrDefault(historyKey, List.of());
        String sessionId = session.get().sessionId().value().toString();
        ConversationContextSnapshot contextSnapshot = conversationContextSnapshot(session.get());
        OpenPlayerDebugEvents.record("provider_parse", "attempted", assignment.id(), character.id(), sessionId,
                "source=conversation messageLength=" + commandText.trim().length());
        ConversationLoop.ConversationParseRequest parseRequest = conversationLoop.prepare(
                character,
                commandText,
                history,
                contextSnapshot
        );
        if (parseRequest.immediateResult() != null) {
            return ConversationSubmissionContext.immediate(parseRequest.immediateResult());
        }
        return new ConversationSubmissionContext(ownerId, commandText, assignment, character, historyKey, sessionId,
                parseRequest, null);
    }

    protected CommandIntent parseConversation(ConversationLoop.ConversationParseRequest parseRequest) {
        try {
            return conversationLoop.parsePrepared(parseRequest);
        } catch (IntentParseException exception) {
            throw new ConversationParseRuntimeException(exception);
        }
    }

    protected CommandSubmissionResult finishConversationSubmission(ConversationSubmissionContext context, CommandIntent intent) {
        AiPlayerNpcCommand[] submittedCommand = new AiPlayerNpcCommand[1];
        CommandSubmissionResult result = conversationLoop.submitIntent(
                intent,
                command -> {
                    submittedCommand[0] = command;
                    return submitSelectedCommand(context.ownerId(), context.assignment().id(), command);
                },
                acceptedIntent -> OpenPlayerDebugEvents.record("provider_parse", "success", context.assignment().id(),
                        context.character().id(), context.sessionId(),
                        "kind=" + acceptedIntent.kind().name() + " instructionLength=" + acceptedIntent.instruction().length())
        );
        if (submittedCommand[0] == null) {
            ActionLikeRequestDiagnostics.recordChatIfActionLike(context.commandText(), intent, context.assignment().id(),
                    context.character().id(), context.sessionId());
        }
        if (result.status() == CommandSubmissionStatus.ACCEPTED && submittedCommand[0] != null
                && submittedCommand[0].intent().kind() != IntentKind.RESET_MEMORY) {
            appendConversationTurn(context.historyKey(), new ConversationTurn("player", context.commandText()));
            appendConversationTurn(context.historyKey(), new ConversationTurn(
                    "openplayer",
                    "Action accepted: " + submittedCommand[0].intent().kind().name()
            ));
            conversationStatusRepository.recordAction(context.ownerId(), context.assignment().id(), submittedCommand[0].intent());
        } else if (result.status() == CommandSubmissionStatus.ACCEPTED && submittedCommand[0] != null
                && submittedCommand[0].intent().kind() == IntentKind.RESET_MEMORY) {
            return result;
        } else if (result.status() == CommandSubmissionStatus.ACCEPTED) {
            appendConversationTurn(context.historyKey(), new ConversationTurn("player", context.commandText()));
            appendConversationTurn(context.historyKey(), new ConversationTurn("openplayer", result.message()));
            conversationStatusRepository.recordNpcReply(context.ownerId(), context.assignment().id(), result.message());
        } else {
            conversationStatusRepository.recordFailure(context.ownerId(), context.assignment().id(), result.message());
            OpenPlayerDebugEvents.record("conversation", result.status().name(), context.assignment().id(), context.character().id(),
                    context.sessionId(), result.message());
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

    protected Optional<AiPlayerNpcSession> findActiveSession(UUID ownerId, String characterId) {
        Optional<ResolvedAssignment> resolvedAssignment = findAssignment(characterId);
        if (resolvedAssignment.isEmpty()) {
            return Optional.empty();
        }
        return findActiveSession(ownerId, resolvedAssignment.get());
    }

    protected Optional<AiPlayerNpcSession> findActiveSession(UUID ownerId, LocalCharacterDefinition character) {
        AiPlayerNpcService service = npcService();
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (matchesLocalCharacterSession(ownerId, session, character)
                    && service.status(session.sessionId()) != NpcSessionStatus.DESPAWNED) {
                return Optional.of(session);
            }
        }
        return Optional.empty();
    }

    protected Optional<AiPlayerNpcSession> findActiveSession(UUID ownerId, ResolvedAssignment resolvedAssignment) {
        AiPlayerNpcService service = npcService();
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (matchesLocalAssignmentSession(ownerId, session, resolvedAssignment.assignment())
                    && service.status(session.sessionId()) != NpcSessionStatus.DESPAWNED) {
                return Optional.of(session);
            }
        }
        return Optional.empty();
    }

    protected int activeAssignmentCount(UUID ownerId) {
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

    protected static boolean hasConversationConfig(LocalCharacterDefinition character) {
        return character.conversationPrompt() != null || character.conversationSettings() != null;
    }

    protected void appendConversationTurn(ConversationHistoryKey historyKey, ConversationTurn turn) {
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

    protected void trimConversationHistoryKeys() {
        while (conversationHistory.size() > MAX_CONVERSATION_HISTORY_KEYS) {
            ConversationHistoryKey oldestKey = conversationHistory.keySet().iterator().next();
            conversationHistory.remove(oldestKey);
        }
    }

    protected Optional<LocalCharacterDefinition> findCharacter(String characterId) {
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

    protected Optional<ResolvedAssignment> findAssignment(String assignmentId) {
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

    protected AiPlayerNpcService npcService() {
        return npcServiceSupplier.get();
    }

    protected ConversationContextSnapshot conversationContextSnapshot(AiPlayerNpcSession session) {
        AiPlayerNpcService service = npcService();
        if (service instanceof RuntimeAiPlayerNpcService runtimeService) {
            return runtimeService.conversationContextSnapshot(session.sessionId());
        }
        return ConversationContextSnapshot.EMPTY;
    }

    protected CommandSubmissionResult unknownOrMissingSession(String characterId) {
        if (findCharacter(characterId).isEmpty()) {
            return rejectedUnknownAssignment();
        }
        return new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION, "Companion is not spawned");
    }

    protected CommandSubmissionResult unknownOrMissingAssignment(String assignmentId) {
        if (findAssignment(assignmentId).isEmpty()) {
            return rejectedUnknownAssignment();
        }
        return new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION, "Companion is not spawned");
    }

    protected CommandSubmissionResult rejectedUnknownCharacter() {
        return rejectedUnknownAssignment();
    }

    protected CommandSubmissionResult rejectedUnknownAssignment() {
        return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, "Unknown local assignment");
    }

    protected static LocalAssignmentRepositoryResult defaultAssignmentResult(LocalCharacterRepositoryResult characterResult) {
        List<LocalAssignmentDefinition> assignments = new java.util.ArrayList<>();
        for (LocalCharacterDefinition character : characterResult.characters()) {
            assignments.add(LocalAssignmentDefinition.defaultFor(character));
        }
        return new LocalAssignmentRepositoryResult(assignments, characterResult.characters(), characterResult.errors());
    }

    protected static String safeStatusToken(String value) {
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

    protected record ResolvedAssignment(LocalAssignmentDefinition assignment, LocalCharacterDefinition character) {
    }

    protected record ConversationHistoryKey(UUID ownerId, String assignmentId) {
    }

    protected record ConversationSubmissionContext(UUID ownerId, String commandText, LocalAssignmentDefinition assignment,
                                                 LocalCharacterDefinition character, ConversationHistoryKey historyKey,
                                                 String sessionId,
                                                 ConversationLoop.ConversationParseRequest parseRequest,
                                                 CommandSubmissionResult result) {
        protected static ConversationSubmissionContext immediate(CommandSubmissionResult result) {
            return new ConversationSubmissionContext(null, "", null, null, null, "", null, result);
        }
    }

    protected static final class ConversationParseRuntimeException extends RuntimeException {
        protected final IntentParseException parseException;

        private ConversationParseRuntimeException(IntentParseException parseException) {
            super(parseException);
            this.parseException = parseException;
        }

        IntentParseException parseException() {
            return parseException;
        }
    }

    public abstract CommandSubmissionResult submitSelectedCommand(UUID ownerId, String characterId,
                                                                  AiPlayerNpcCommand command);
}
