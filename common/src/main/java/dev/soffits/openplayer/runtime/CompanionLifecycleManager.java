package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.AiPlayerNpcService;
import dev.soffits.openplayer.api.AiPlayerNpcSession;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.api.NpcSessionStatus;
import dev.soffits.openplayer.api.NpcSpawnLocation;
import dev.soffits.openplayer.character.LocalCharacterDefinition;
import dev.soffits.openplayer.character.LocalCharacterRepositoryResult;
import dev.soffits.openplayer.conversation.ConversationHistoryTrimmer;
import dev.soffits.openplayer.conversation.ConversationLoop;
import dev.soffits.openplayer.conversation.ConversationTurn;
import dev.soffits.openplayer.intent.IntentParser;
import dev.soffits.openplayer.OpenPlayerIntentParserConfig;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class CompanionLifecycleManager {
    private final Supplier<AiPlayerNpcService> npcServiceSupplier;
    private final Supplier<LocalCharacterRepositoryResult> characterRepositoryResultSupplier;
    private final ConversationLoop conversationLoop;
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
        this.characterRepositoryResultSupplier = characterRepositoryResultSupplier;
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
        Optional<LocalCharacterDefinition> character = findCharacter(characterId);
        if (character.isEmpty()) {
            return rejectedUnknownCharacter();
        }
        npcService().spawn(character.get().toNpcSpec(ownerId, spawnLocation));
        return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED, "Companion spawned");
    }

    public CommandSubmissionResult despawnSelected(UUID ownerId, String characterId) {
        Optional<LocalCharacterDefinition> character = findCharacter(characterId);
        if (character.isEmpty()) {
            return rejectedUnknownCharacter();
        }
        AiPlayerNpcService service = npcService();
        boolean despawned = false;
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (matchesLocalCharacterSession(ownerId, session, character.get())) {
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
        Optional<AiPlayerNpcSession> session = findActiveSession(ownerId, characterId);
        if (session.isEmpty()) {
            return unknownOrMissingSession(characterId);
        }
        return npcService().submitCommand(session.get().sessionId(), command);
    }

    public CommandSubmissionResult submitSelectedCommandText(UUID ownerId, String characterId, String commandText) {
        if (commandText == null) {
            throw new IllegalArgumentException("commandText cannot be null");
        }
        Optional<LocalCharacterDefinition> character = findCharacter(characterId);
        if (character.isEmpty()) {
            return rejectedUnknownCharacter();
        }
        Optional<AiPlayerNpcSession> session = findActiveSession(ownerId, character.get());
        if (session.isEmpty()) {
            return new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION, "Companion is not spawned");
        }
        if (hasConversationConfig(character.get())) {
            ConversationHistoryKey historyKey = new ConversationHistoryKey(ownerId, character.get().id());
            List<ConversationTurn> history = conversationHistory.getOrDefault(historyKey, List.of());
            AiPlayerNpcCommand[] submittedCommand = new AiPlayerNpcCommand[1];
            CommandSubmissionResult result = conversationLoop.submit(
                    character.get(),
                    commandText,
                    history,
                    command -> {
                        submittedCommand[0] = command;
                        return submitSelectedCommand(ownerId, character.get().id(), command);
                    }
            );
            if (result.status() == CommandSubmissionStatus.ACCEPTED && submittedCommand[0] != null) {
                appendConversationTurn(historyKey, new ConversationTurn("player", commandText));
                appendConversationTurn(historyKey, new ConversationTurn(
                        "openplayer",
                        submittedCommand[0].intent().kind().name() + " " + submittedCommand[0].intent().instruction()
                ));
            }
            return result;
        }
        return npcService().submitCommandText(session.get().sessionId(), commandText);
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
                && session.spec().roleId().value().equals(character.toSessionRoleId().value());
    }

    private Optional<AiPlayerNpcSession> findActiveSession(UUID ownerId, String characterId) {
        Optional<LocalCharacterDefinition> character = findCharacter(characterId);
        if (character.isEmpty()) {
            return Optional.empty();
        }
        return findActiveSession(ownerId, character.get());
    }

    private Optional<AiPlayerNpcSession> findActiveSession(UUID ownerId, LocalCharacterDefinition character) {
        for (AiPlayerNpcSession session : npcService().listSessions()) {
            if (matchesLocalCharacterSession(ownerId, session, character)) {
                return Optional.of(session);
            }
        }
        return Optional.empty();
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
    }

    private Optional<LocalCharacterDefinition> findCharacter(String characterId) {
        if (characterId == null || characterId.isBlank()) {
            return Optional.empty();
        }
        String normalizedCharacterId = characterId.trim();
        LocalCharacterRepositoryResult result = characterRepositoryResultSupplier.get();
        for (LocalCharacterDefinition character : result.characters()) {
            if (character.id().equals(normalizedCharacterId)) {
                return Optional.of(character);
            }
        }
        return Optional.empty();
    }

    private AiPlayerNpcService npcService() {
        return npcServiceSupplier.get();
    }

    private CommandSubmissionResult unknownOrMissingSession(String characterId) {
        if (findCharacter(characterId).isEmpty()) {
            return rejectedUnknownCharacter();
        }
        return new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION, "Companion is not spawned");
    }

    private CommandSubmissionResult rejectedUnknownCharacter() {
        return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, "Unknown local character");
    }

    private record ConversationHistoryKey(UUID ownerId, String characterId) {
    }
}
