package dev.soffits.openplayer.intent;

import java.net.URI;

public final class OpenAiCompatibleIntentProviderTest {
    private OpenAiCompatibleIntentProviderTest() {
    }

    public static void main(String[] args) throws Exception {
        resolvesV1BaseEndpoint();
        preservesCompleteChatCompletionsEndpoint();
        preservesOtherExplicitEndpoints();
        preservesQueryWhenResolvingV1BaseEndpoint();
        systemPromptConstrainConversationReplies();
        systemPromptContainsEveryIntentKind();
        systemPromptIncludesPlannedUnsupportedInstruction();
    }

    private static void resolvesV1BaseEndpoint() throws Exception {
        require(
                "https://api.example.invalid/v1/chat/completions".equals(normalize("https://api.example.invalid/v1")),
                "base /v1 endpoint must resolve to chat completions"
        );
        require(
                "https://api.example.invalid/v1/chat/completions".equals(normalize("https://api.example.invalid/v1/")),
                "base /v1/ endpoint must resolve to chat completions"
        );
    }

    private static void preservesCompleteChatCompletionsEndpoint() throws Exception {
        require(
                "https://api.example.invalid/v1/chat/completions".equals(normalize("https://api.example.invalid/v1/chat/completions")),
                "complete chat completions endpoint must be preserved"
        );
    }

    private static void preservesOtherExplicitEndpoints() throws Exception {
        require(
                "https://gateway.example.invalid/openai".equals(normalize("https://gateway.example.invalid/openai")),
                "custom explicit endpoint must be preserved"
        );
    }

    private static void preservesQueryWhenResolvingV1BaseEndpoint() throws Exception {
        require(
                "https://api.example.invalid/v1/chat/completions?api-version=2024-01-01".equals(normalize("https://api.example.invalid/v1?api-version=2024-01-01")),
                "query must be preserved when resolving base endpoint"
        );
    }

    private static void systemPromptConstrainConversationReplies() {
        String prompt = OpenAiCompatibleIntentProvider.systemPrompt();
        require(prompt.contains("For CHAT, instruction must be the selected character's concise conversational reply"),
                "system prompt must make CHAT instruction an NPC reply");
        require(prompt.contains("For UNAVAILABLE, instruction may be blank or a short safe reason"),
                "system prompt must allow a short safe UNAVAILABLE reason");
        require(prompt.contains("Return JSON only"), "system prompt must constrain output to JSON only");
        require(prompt.contains("no secrets"), "system prompt must prohibit secrets");
    }

    private static void systemPromptContainsEveryIntentKind() {
        String prompt = OpenAiCompatibleIntentProvider.systemPrompt();
        for (IntentKind kind : IntentKind.values()) {
            require(prompt.contains(kind.name()), "system prompt must list " + kind.name());
        }
    }

    private static void systemPromptIncludesPlannedUnsupportedInstruction() {
        String prompt = OpenAiCompatibleIntentProvider.systemPrompt();
        require(prompt.contains("Planned PlayerEngine-style intents may be recognized but are unsupported until implemented"),
                "system prompt must include deterministic planned unsupported instruction");
        require(prompt.contains("use UNAVAILABLE when the vanilla runtime cannot perform the requested action"),
                "system prompt must tell providers not to overclaim unsupported planned actions");
    }

    private static String normalize(String value) throws Exception {
        return OpenAiCompatibleIntentProvider.normalizeChatCompletionsEndpoint(new URI(value)).toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
