package dev.soffits.openplayer.conversation;

import dev.soffits.openplayer.IntentParserRuntimeStatus;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.character.LocalCharacterDefinition;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentParseException;
import dev.soffits.openplayer.intent.IntentParser;
import dev.soffits.openplayer.intent.IntentPriority;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentPolicies;
import java.util.ArrayList;
import java.util.List;

public final class ConversationLoopTest {
    private static final LocalCharacterDefinition CHARACTER = new LocalCharacterDefinition(
            "alex_01",
            "Alex Helper",
            "Local helper",
            null,
            null,
            null,
            "Speak briefly and prefer safe helper actions.",
            "No secrets; local non-sensitive preferences only."
    );

    private ConversationLoopTest() {
    }

    public static void main(String[] args) {
        disabledParserReportsUnavailableWithoutParsing();
        promptAssemblyIncludesCharacterConversationText();
        boundedHistoryKeepsRecentTurns();
        invalidParserOutputRejectsWithoutSubmitting();
        validParserOutputSubmitsConstrainedIntent();
        chatIntentReturnsReplyWithoutSubmittingAutomation();
        unavailableIntentReturnsSafeReplyWithoutSubmittingAutomation();
    }

    private static void disabledParserReportsUnavailableWithoutParsing() {
        CountingParser parser = new CountingParser(new CommandIntent(IntentKind.FOLLOW_OWNER, IntentPriority.NORMAL, ""));
        ConversationLoop loop = new ConversationLoop(parser, () -> status(false));
        CommandSubmissionResult result = loop.submit(CHARACTER, "follow me", List.of(), command -> {
            throw new AssertionError("disabled conversation must not submit commands");
        });
        require(result.status() == CommandSubmissionStatus.UNAVAILABLE,
                "disabled parser must report conversation unavailable");
        require(parser.parseCount == 0, "disabled parser must not be contacted");
    }

    private static void promptAssemblyIncludesCharacterConversationText() {
        String prompt = ConversationPromptAssembler.assemble(
                CHARACTER,
                "follow me",
                List.of(new ConversationTurn("player", "hello")),
                new ConversationContextSnapshot("world: dimension=minecraft:overworld\nnearbyBlocks: minecraft:oak_log x3")
        );
        require(prompt.contains("Speak briefly"), "prompt must include per-character conversationPrompt text");
        require(prompt.contains("No secrets"), "prompt must include per-character conversationSettings text");
        require(prompt.contains("world, inventory, and combat actions are disabled"), "prompt must include selected character action policy");
        for (IntentKind kind : RuntimeIntentPolicies.localWorldOrInventoryActions()) {
            require(prompt.contains(kind.name()), "disabled-world-actions prompt must mention " + kind.name());
        }
        require(prompt.contains("When kind is CHAT, instruction must be the selected character's concise conversational reply"),
                "prompt must tell providers that CHAT instruction is the NPC reply");
        require(prompt.contains("When kind is UNAVAILABLE, instruction may be blank or a short safe reason"),
                "prompt must allow UNAVAILABLE to provide a short safe reason");
        require(prompt.contains("Server context:"), "prompt must include a server context section when available");
        require(prompt.contains("nearbyBlocks: minecraft:oak_log x3"),
                "prompt must include bounded nearby block context");
        require(prompt.contains("choose an actionable intent using available targets"),
                "prompt must guide nearby world actions from context");
        require(prompt.contains("Planned PlayerEngine-style task categories may be unavailable until implemented"),
                "prompt must warn that planned task categories may be unavailable");
        require(prompt.contains("provider and runtime must not pretend success"),
                "prompt must forbid fake success for planned task categories");
        require(prompt.contains("Player: follow me"), "prompt must include the current player message");
    }

    private static void boundedHistoryKeepsRecentTurns() {
        List<ConversationTurn> history = List.of(
                new ConversationTurn("player", "old one"),
                new ConversationTurn("openplayer", "old two"),
                new ConversationTurn("player", "recent one"),
                new ConversationTurn("openplayer", "recent two")
        );
        List<ConversationTurn> trimmed = ConversationHistoryTrimmer.trim(history, 2, 100);
        require(trimmed.size() == 2, "history must be bounded by max turns");
        require("recent one".equals(trimmed.get(0).text()), "history must keep recent turns in order");
        require("recent two".equals(trimmed.get(1).text()), "history must keep most recent turn");
    }

    private static void invalidParserOutputRejectsWithoutSubmitting() {
        ConversationLoop loop = new ConversationLoop(new FailingParser(), () -> status(true));
        CommandSubmissionResult result = loop.submit(CHARACTER, "do something", List.of(), command -> {
            throw new AssertionError("invalid parser output must not submit commands");
        });
        require(result.status() == CommandSubmissionStatus.REJECTED,
                "invalid parser output must reject conversation");
    }

    private static void validParserOutputSubmitsConstrainedIntent() {
        ArrayList<CommandIntent> submitted = new ArrayList<>();
        ConversationLoop loop = new ConversationLoop(
                new CountingParser(new CommandIntent(IntentKind.FOLLOW_OWNER, IntentPriority.HIGH, "")),
                () -> status(true)
        );
        CommandSubmissionResult result = loop.submit(CHARACTER, "follow me", List.of(), command -> {
            submitted.add(command.intent());
            return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED, "accepted");
        });
        require(result.status() == CommandSubmissionStatus.ACCEPTED, "valid parser output must submit");
        require(submitted.size() == 1, "valid parser output must submit exactly one command");
        require(submitted.get(0).kind() == IntentKind.FOLLOW_OWNER, "submitted intent kind must come from parser");
    }

    private static void chatIntentReturnsReplyWithoutSubmittingAutomation() {
        ConversationLoop loop = new ConversationLoop(
                new CountingParser(new CommandIntent(IntentKind.CHAT, IntentPriority.NORMAL, "Hello there")),
                () -> status(true)
        );
        CommandSubmissionResult result = loop.submit(CHARACTER, "hello", List.of(), command -> {
            throw new AssertionError("CHAT conversation intent must not submit automation");
        });
        require(result.status() == CommandSubmissionStatus.ACCEPTED, "CHAT intent must return a visible reply");
        require("Hello there".equals(result.message()), "CHAT reply must use the provider instruction");
    }

    private static void unavailableIntentReturnsSafeReplyWithoutSubmittingAutomation() {
        ConversationLoop loop = new ConversationLoop(
                new CountingParser(new CommandIntent(IntentKind.UNAVAILABLE, IntentPriority.NORMAL, "")),
                () -> status(true)
        );
        CommandSubmissionResult result = loop.submit(CHARACTER, "break a block", List.of(), command -> {
            throw new AssertionError("UNAVAILABLE conversation intent must not submit automation");
        });
        require(result.status() == CommandSubmissionStatus.ACCEPTED, "UNAVAILABLE intent must return a visible reply");
        require(result.message().contains("safely"), "UNAVAILABLE blank instruction must use a safe fallback");
    }

    private static IntentParserRuntimeStatus status(boolean enabled) {
        return new IntentParserRuntimeStatus(enabled, "test", "test", true, "test", true, "test");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class CountingParser implements IntentParser {
        private final CommandIntent intent;
        private int parseCount;

        private CountingParser(CommandIntent intent) {
            this.intent = intent;
        }

        @Override
        public CommandIntent parse(String input) {
            parseCount++;
            return intent;
        }
    }

    private static final class FailingParser implements IntentParser {
        @Override
        public CommandIntent parse(String input) throws IntentParseException {
            throw new IntentParseException("invalid provider output");
        }
    }
}
