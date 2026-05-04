package dev.soffits.openplayer.intent;

public final class ProviderBackedIntentParserTest {
    private ProviderBackedIntentParserTest() {
    }

    public static void main(String[] args) throws Exception {
        acceptsPrimitiveToolVocabularyValue();
        acceptsLowercaseConversationVocabularyValue();
        acceptsConversationKindsOnlyFromLegacyEnumSurface();
        rejectsUnknownProviderKind();
        rejectsRemovedMacroProviderKind();
    }

    private static void acceptsPrimitiveToolVocabularyValue() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> new ProviderIntent("move_to", "NORMAL", "1 64 -2")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.GOTO, "move_to must bridge to coordinate-only GOTO runtime primitive");
        require("1 64 -2".equals(intent.instruction()), "tool instruction must be preserved");
    }

    private static void acceptsLowercaseConversationVocabularyValue() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> new ProviderIntent("chat", "normal", "hello")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.CHAT, "parser must preserve chat as conversation output");
        require(intent.priority() == IntentPriority.NORMAL, "parser must normalize lowercase provider priority");
    }

    private static void acceptsConversationKindsOnlyFromLegacyEnumSurface() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> new ProviderIntent("UNAVAILABLE", "NORMAL", "not supported")
        );
        require(parser.parse("ignored").kind() == IntentKind.UNAVAILABLE,
                "parser must preserve unavailable provider output");
        ProviderBackedIntentParser rejectedParser = new ProviderBackedIntentParser(
                input -> new ProviderIntent("GIVE_ITEM", "NORMAL", "minecraft:bread")
        );
        try {
            rejectedParser.parse("ignored");
            throw new AssertionError("parser must reject legacy non-tool automation enum names");
        } catch (IntentParseException exception) {
            require("intent provider returned an unsupported primitive tool".equals(exception.getMessage()),
                    "legacy automation enum rejection must be deterministic");
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

    private static void rejectsRemovedMacroProviderKind() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> new ProviderIntent("GET_ITEM", "NORMAL", "minecraft:bread")
        );
        try {
            parser.parse("ignored");
            throw new AssertionError("parser must reject removed macro kind");
        } catch (IntentParseException exception) {
            require("intent provider returned an unknown kind".equals(exception.getMessage()),
                    "removed macro rejection must be deterministic");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
