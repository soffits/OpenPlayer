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
import dev.soffits.openplayer.conversation.ActionLikeRequestDiagnostics;
import dev.soffits.openplayer.conversation.ConversationHistoryTrimmer;
import dev.soffits.openplayer.conversation.ConversationContextSnapshot;
import dev.soffits.openplayer.conversation.ConversationLoop;
import dev.soffits.openplayer.conversation.ConversationReplyText;
import dev.soffits.openplayer.conversation.ConversationStatusRepository;
import dev.soffits.openplayer.conversation.ConversationTurn;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentParser;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentParseException;
import dev.soffits.openplayer.OpenPlayerIntentParserConfig;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidationResult;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidator;
import java.util.ArrayList;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;


public final class CompanionLifecycleManager extends CompanionLifecycleManagerBase {
    public CompanionLifecycleManager(Supplier<AiPlayerNpcService> npcServiceSupplier,
                                      Supplier<LocalCharacterRepositoryResult> characterRepositoryResultSupplier) {
        this(npcServiceSupplier, characterRepositoryResultSupplier, () -> new dev.soffits.openplayer.intent.DisabledIntentParser());
    }

    public CompanionLifecycleManager(Supplier<AiPlayerNpcService> npcServiceSupplier,
                                     Supplier<LocalCharacterRepositoryResult> characterRepositoryResultSupplier,
                                     IntentParser intentParser) {
        this(npcServiceSupplier, characterRepositoryResultSupplier, () -> intentParser);
    }

    public CompanionLifecycleManager(Supplier<AiPlayerNpcService> npcServiceSupplier,
                                     Supplier<LocalCharacterRepositoryResult> characterRepositoryResultSupplier,
                                     Supplier<IntentParser> intentParserSupplier) {
        super(npcServiceSupplier, () -> defaultAssignmentResult(characterRepositoryResultSupplier.get()),
                new ConversationLoop(intentParserSupplier, OpenPlayerIntentParserConfig::status));
    }

    public static CompanionLifecycleManager withAssignments(Supplier<AiPlayerNpcService> npcServiceSupplier,
                                                            Supplier<LocalAssignmentRepositoryResult> assignmentRepositoryResultSupplier,
                                                            Supplier<IntentParser> intentParserSupplier) {
        return new CompanionLifecycleManager(npcServiceSupplier, assignmentRepositoryResultSupplier,
                new ConversationLoop(intentParserSupplier, OpenPlayerIntentParserConfig::status));
    }

    private CompanionLifecycleManager(Supplier<AiPlayerNpcService> npcServiceSupplier,
                                      Supplier<LocalAssignmentRepositoryResult> assignmentRepositoryResultSupplier,
                                      ConversationLoop conversationLoop) {
        super(npcServiceSupplier, assignmentRepositoryResultSupplier, conversationLoop);
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
        CommandIntent[] parsedIntent = new CommandIntent[1];
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
                intent -> {
                    parsedIntent[0] = intent;
                    OpenPlayerDebugEvents.record("provider_parse", "success", assignment.id(), character.id(), sessionId,
                            "kind=" + intent.kind().name() + " instructionLength=" + intent.instruction().length());
                }
        );
        if (submittedCommand[0] == null && parsedIntent[0] != null) {
            ActionLikeRequestDiagnostics.recordChatIfActionLike(commandText, parsedIntent[0], assignment.id(), character.id(), sessionId);
        }
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

    public CommandSubmissionResult submitSelectedCommandTextAsync(MinecraftServer server, UUID ownerId, String characterId,
                                                                 String commandText,
                                                                 Consumer<CommandSubmissionResult> completion) {
        if (completion == null) {
            throw new IllegalArgumentException("completion cannot be null");
        }
        ConversationSubmissionContext context = prepareConversationSubmission(ownerId, characterId, commandText);
        if (context.result() != null) {
            return context.result();
        }
        AiPlayerNpcService service = npcService();
        if (service instanceof InteractivePlannerCommandTextService plannerService) {
            return submitPlannedConversation(context, plannerService, completion);
        }
        if (server == null) {
            throw new IllegalArgumentException("server cannot be null");
        }
        RuntimeAgentExecutor.submit(server, () -> parseConversation(context.parseRequest()), intent -> {
            CommandSubmissionResult result = finishConversationSubmission(context, intent);
            completion.accept(result);
        }, exception -> {
            String message = exception instanceof ConversationParseRuntimeException parseException
                    ? ConversationLoop.conversationFailureMessage(parseException.parseException())
                    : "Conversation provider response could not be parsed";
            conversationStatusRepository.recordFailure(ownerId, context.assignment().id(), message);
            OpenPlayerDebugEvents.record("conversation", CommandSubmissionStatus.REJECTED.name(), context.assignment().id(),
                    context.character().id(), context.sessionId(), message);
            completion.accept(new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, message));
        });
        return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED, "Conversation queued for provider parsing");
    }

}
