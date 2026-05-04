package dev.soffits.openplayer.conversation;

import dev.soffits.openplayer.IntentParserRuntimeStatus;
import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.character.LocalCharacterDefinition;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentParseException;
import dev.soffits.openplayer.intent.IntentParser;
import dev.soffits.openplayer.intent.IntentProviderException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ConversationLoop {
    public static final int MAX_HISTORY_TURNS = 8;
    public static final int MAX_HISTORY_CHARACTERS = 4096;

    private final Supplier<IntentParser> intentParserSupplier;
    private final Supplier<IntentParserRuntimeStatus> statusSupplier;

    public ConversationLoop(IntentParser intentParser, Supplier<IntentParserRuntimeStatus> statusSupplier) {
        if (intentParser == null) {
            throw new IllegalArgumentException("intentParser cannot be null");
        }
        this.intentParserSupplier = () -> intentParser;
        if (statusSupplier == null) {
            throw new IllegalArgumentException("statusSupplier cannot be null");
        }
        this.statusSupplier = statusSupplier;
    }

    public ConversationLoop(Supplier<IntentParser> intentParserSupplier,
                            Supplier<IntentParserRuntimeStatus> statusSupplier) {
        if (intentParserSupplier == null) {
            throw new IllegalArgumentException("intentParserSupplier cannot be null");
        }
        if (statusSupplier == null) {
            throw new IllegalArgumentException("statusSupplier cannot be null");
        }
        this.intentParserSupplier = intentParserSupplier;
        this.statusSupplier = statusSupplier;
    }

    public CommandSubmissionResult submit(LocalCharacterDefinition character, String playerMessage,
                                           List<ConversationTurn> history, CommandSubmitter submitter) {
        return submit(character, playerMessage, history, ConversationContextSnapshot.EMPTY, submitter, ignored -> {
        });
    }

    public CommandSubmissionResult submit(LocalCharacterDefinition character, String playerMessage,
                                           List<ConversationTurn> history, CommandSubmitter submitter,
                                           Consumer<CommandIntent> acceptedIntentRecorder) {
        return submit(character, playerMessage, history, ConversationContextSnapshot.EMPTY, submitter, acceptedIntentRecorder);
    }

    public CommandSubmissionResult submit(LocalCharacterDefinition character, String playerMessage,
                                           List<ConversationTurn> history, ConversationContextSnapshot contextSnapshot,
                                           CommandSubmitter submitter,
                                           Consumer<CommandIntent> acceptedIntentRecorder) {
        if (character == null) {
            throw new IllegalArgumentException("character cannot be null");
        }
        if (contextSnapshot == null) {
            throw new IllegalArgumentException("contextSnapshot cannot be null");
        }
        if (submitter == null) {
            throw new IllegalArgumentException("submitter cannot be null");
        }
        if (acceptedIntentRecorder == null) {
            throw new IllegalArgumentException("acceptedIntentRecorder cannot be null");
        }
        ConversationParseRequest request = prepare(character, playerMessage, history, contextSnapshot);
        if (request.immediateResult() != null) {
            return request.immediateResult();
        }

        CommandIntent intent;
        try {
            IntentParser intentParser = intentParserSupplier.get();
            if (intentParser == null) {
                return new CommandSubmissionResult(CommandSubmissionStatus.UNAVAILABLE,
                        "Conversation unavailable: intent parser disabled");
            }
            intent = ConversationIntentValidator.requireConversationIntent(intentParser.parse(request.prompt()));
        } catch (IntentParseException exception) {
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, conversationFailureMessage(exception));
        }
        return submitIntent(intent, submitter, acceptedIntentRecorder);
    }

    public ConversationParseRequest prepare(LocalCharacterDefinition character, String playerMessage,
                                            List<ConversationTurn> history,
                                            ConversationContextSnapshot contextSnapshot) {
        if (character == null) {
            throw new IllegalArgumentException("character cannot be null");
        }
        if (contextSnapshot == null) {
            throw new IllegalArgumentException("contextSnapshot cannot be null");
        }
        IntentParserRuntimeStatus status = statusSupplier.get();
        if (status == null || !status.enabled()) {
            return new ConversationParseRequest("", new CommandSubmissionResult(CommandSubmissionStatus.UNAVAILABLE,
                    "Conversation unavailable: intent parser disabled"));
        }
        try {
            return new ConversationParseRequest(ConversationPromptAssembler.assemble(
                    character,
                    playerMessage,
                    ConversationHistoryTrimmer.trim(history, MAX_HISTORY_TURNS, MAX_HISTORY_CHARACTERS),
                    contextSnapshot
            ), null);
        } catch (IllegalArgumentException exception) {
            return new ConversationParseRequest("", new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, exception.getMessage()));
        }
    }

    public CommandSubmissionResult submitIntent(CommandIntent intent, CommandSubmitter submitter,
                                                Consumer<CommandIntent> acceptedIntentRecorder) {
        if (intent == null) {
            throw new IllegalArgumentException("intent cannot be null");
        }
        if (submitter == null) {
            throw new IllegalArgumentException("submitter cannot be null");
        }
        if (acceptedIntentRecorder == null) {
            throw new IllegalArgumentException("acceptedIntentRecorder cannot be null");
        }
        acceptedIntentRecorder.accept(intent);
        if (intent.kind() == IntentKind.CHAT) {
            return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED,
                    ConversationReplyText.chatReply(intent.instruction()));
        }
        if (intent.kind() == IntentKind.UNAVAILABLE) {
            return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED,
                    ConversationReplyText.unavailableReply(intent.instruction()));
        }
        return submitter.submit(new AiPlayerNpcCommand(UUID.randomUUID(), intent));
    }

    public CommandIntent parsePrepared(ConversationParseRequest request) throws IntentParseException {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        if (request.immediateResult() != null) {
            throw new IllegalArgumentException("request has an immediate result");
        }
        IntentParser intentParser = intentParserSupplier.get();
        if (intentParser == null) {
            throw new IntentParseException("intent parser disabled");
        }
        return ConversationIntentValidator.requireConversationIntent(intentParser.parse(request.prompt()));
    }

    public static String conversationFailureMessage(IntentParseException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof IntentProviderException providerException) {
            String message = providerException.getMessage();
            if (message != null && message.contains("status ")) {
                String status = message.substring(message.lastIndexOf(' ') + 1);
                if (status.matches("[0-9]{3}")) {
                    return "Conversation provider request failed: HTTP " + status;
                }
            }
            if (message != null && message.contains("timed out")) {
                return "Conversation provider request timed out";
            }
            if (message != null && message.contains("interrupted")) {
                return "Conversation provider request interrupted";
            }
            return "Conversation provider request failed";
        }
        return "Conversation provider response could not be parsed";
    }

    @FunctionalInterface
    public interface CommandSubmitter {
        CommandSubmissionResult submit(AiPlayerNpcCommand command);
    }

    public record ConversationParseRequest(String prompt, CommandSubmissionResult immediateResult) {
        public ConversationParseRequest {
            if (prompt == null) {
                throw new IllegalArgumentException("prompt cannot be null");
            }
        }
    }
}
