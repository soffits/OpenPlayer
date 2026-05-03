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

    private static String normalize(String value) throws Exception {
        return OpenAiCompatibleIntentProvider.normalizeChatCompletionsEndpoint(new URI(value)).toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
