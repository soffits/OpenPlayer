package dev.soffits.openplayer.conversation;

import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentPriority;
import java.util.List;
import java.util.UUID;

public final class ConversationStatusRepositoryTest {
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private ConversationStatusRepositoryTest() {
    }

    public static void main(String[] args) {
        storesOnlyBoundedEvents();
        evictsOldAssignmentKeysAfterRepositoryCap();
        sanitizesAndTruncatesPublicText();
        storesNetworkSafeEventLinesForLongCjkReplies();
        splitsLongCjkRepliesIntoSafeChatChunks();
        recordsDeterministicActionSummaryWithoutProviderText();
    }

    private static void storesOnlyBoundedEvents() {
        ConversationStatusRepository repository = new ConversationStatusRepository();
        for (int index = 0; index < ConversationStatusRepository.MAX_EVENTS + 2; index++) {
            repository.recordPlayerMessage(OWNER_ID, "alex_01", "message " + index);
        }

        List<String> events = repository.eventLines(OWNER_ID, "alex_01");
        require(events.size() == ConversationStatusRepository.MAX_EVENTS, "repository must keep only bounded events");
        require(!events.get(0).contains("message 0"), "oldest events must be trimmed first");
    }

    private static void evictsOldAssignmentKeysAfterRepositoryCap() {
        ConversationStatusRepository repository = new ConversationStatusRepository();
        for (int index = 0; index < ConversationStatusRepository.MAX_ASSIGNMENTS + 1; index++) {
            repository.recordPlayerMessage(OWNER_ID, "alex_" + index, "message " + index);
        }

        require(repository.eventLines(OWNER_ID, "alex_0").isEmpty(),
                "oldest assignment key must be evicted after repository cap is exceeded");
        require(repository.eventLines(OWNER_ID, "alex_1").size() == 1,
                "repository must retain non-evicted assignment keys");
        require(repository.eventLines(OWNER_ID, "alex_" + ConversationStatusRepository.MAX_ASSIGNMENTS).size() == 1,
                "repository must retain newest assignment key");
    }

    private static void sanitizesAndTruncatesPublicText() {
        String input = "follow\n\t" + "x".repeat(ConversationStatusRepository.MAX_EVENT_TEXT_LENGTH + 20);
        String sanitized = ConversationStatusRepository.sanitize(input);

        require(!sanitized.contains("\n"), "sanitized text must not include newlines");
        require(!sanitized.contains("\t"), "sanitized text must not include tabs");
        require(sanitized.length() <= ConversationStatusRepository.MAX_EVENT_TEXT_LENGTH,
                "sanitized text must be truncated to the public bound");
    }

    private static void storesNetworkSafeEventLinesForLongCjkReplies() {
        ConversationStatusRepository repository = new ConversationStatusRepository();
        String reply = "Yes, aim at the lowest log, hold the break action until it drops, then keep going. A wooden axe helps a lot. ".repeat(3);
        repository.recordNpcReply(OWNER_ID, "alex_01", reply);

        String eventLine = repository.eventLines(OWNER_ID, "alex_01").get(0);
        require(eventLine.length() <= ConversationStatusRepository.MAX_NETWORK_EVENT_LINE_LENGTH,
                "conversation event line must fit the network writeUtf bound");
        require(eventLine.startsWith("action: "), "conversation event line must keep the event prefix");
    }

    private static void splitsLongCjkRepliesIntoSafeChatChunks() {
        String reply = ConversationReplyText.sanitizeProviderReply(
                "Yes, aim at the lowest log, hold the break action until it drops, then keep going. A wooden axe helps a lot. ".repeat(3)
        );
        List<String> chunks = ConversationReplyText.displayChunks(reply);

        require(chunks.size() > 1, "long CJK replies must be split into multiple chat chunks");
        for (String chunk : chunks) {
            require(chunk.length() <= ConversationReplyText.CHAT_REPLY_CHUNK_LENGTH,
                    "each chat chunk must fit the configured chat reply chunk bound");
        }
        require(String.join("", chunks).equals(reply), "chat chunks must reconstruct the sanitized reply");
    }

    private static void recordsDeterministicActionSummaryWithoutProviderText() {
        ConversationStatusRepository repository = new ConversationStatusRepository();
        String providerInstruction = "follow using token sk-live-secret from /home/alex/.ssh/id_rsa "
                + "raw provider content ".repeat(40);
        repository.recordAction(OWNER_ID, "alex_01", new CommandIntent(
                IntentKind.FOLLOW_OWNER,
                IntentPriority.HIGH,
                providerInstruction
        ));

        String event = repository.eventLines(OWNER_ID, "alex_01").get(0);
        require(event.equals("action: Action accepted: FOLLOW_OWNER"),
                "action summary must be derived only from the accepted intent kind");
        require(!event.contains("sk-live-secret"), "action summary must not expose provider-derived secrets");
        require(!event.contains("/home/alex/.ssh/id_rsa"), "action summary must not expose provider-derived paths");
        require(!event.contains("raw provider content"), "action summary must not expose raw provider content");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
