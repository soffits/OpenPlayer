package dev.soffits.openplayer.intent;

public final class ProviderBackedIntentParserTest {
    private ProviderBackedIntentParserTest() {
    }

    public static void main(String[] args) throws Exception {
        acceptsEveryIntentKindVocabularyValue();
        acceptsLowercaseIntentKindVocabularyValue();
        rejectsUnknownProviderKind();
    }

    private static void acceptsEveryIntentKindVocabularyValue() throws Exception {
        for (IntentKind kind : IntentKind.values()) {
            ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                    input -> new ProviderIntent(kind.name(), "NORMAL", "instruction")
            );
            CommandIntent intent = parser.parse("ignored");
            require(intent.kind() == kind, "parser must accept provider kind " + kind.name());
        }
    }

    private static void acceptsLowercaseIntentKindVocabularyValue() throws Exception {
        for (IntentKind kind : IntentKind.values()) {
            ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                    input -> new ProviderIntent(kind.name().toLowerCase(java.util.Locale.ROOT), "normal", "instruction")
            );
            CommandIntent intent = parser.parse("ignored");
            require(intent.kind() == kind, "parser must normalize lowercase provider kind " + kind.name());
            require(intent.priority() == IntentPriority.NORMAL, "parser must normalize lowercase provider priority");
        }
    }

    private static void rejectsUnknownProviderKind() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> new ProviderIntent("DANCE_BACKWARDS", "NORMAL", "instruction")
        );
        try {
            parser.parse("ignored");
            throw new AssertionError("parser must reject unknown provider kind");
        } catch (IntentParseException exception) {
            require("intent provider returned an unknown kind".equals(exception.getMessage()),
                    "unknown provider kind rejection must be deterministic");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
