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
        systemPromptIncludesPhaseFiveSyntax();
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

    private static void systemPromptIncludesPhaseFiveSyntax() {
        String prompt = OpenAiCompatibleIntentProvider.systemPrompt();
        require(prompt.contains("INVENTORY_QUERY"), "system prompt must document inventory query syntax");
        require(prompt.contains("For EQUIP_ITEM use exact item id <item_id>"),
                "system prompt must document exact equip item ids");
        require(prompt.contains("DROP_ITEM use blank to drop selected hotbar stack or exact one-stack MVP item id syntax <item_id> [count]"),
                "system prompt must document drop item count syntax");
        require(prompt.contains("GIVE_ITEM use exact owner-only one-stack MVP syntax <item_id> [count]"),
                "system prompt must document owner-only give syntax");
        require(prompt.contains("For kind GET_ITEM, instruction must contain only exact item id syntax <item_id> [count]"),
                "system prompt must document get item instruction syntax without the kind token");
        require(prompt.contains("do not include the literal GET_ITEM in instruction"),
                "system prompt must prevent providers from putting GET_ITEM in the instruction field");
        require(!prompt.contains("GET_ITEM <item_id> [count]"),
                "system prompt must not include misleading GET_ITEM instruction syntax");
        require(prompt.contains("bounded one-stack local inventory/crafting MVP"),
                "system prompt must document bounded GET_ITEM scope");
        require(prompt.contains("server recipe data for supported simple inventory recipes"),
                "system prompt must document dynamic GET_ITEM recipe data");
        require(prompt.contains("simple datapack/mod recipes visible to the server"),
                "system prompt must document simple datapack/mod recipe visibility");
        require(prompt.contains("finite tag-backed ingredient alternatives"),
                "system prompt must document finite tag-backed alternative support");
        require(prompt.contains("Crafting-table-only, special/custom, NBT-bearing ingredient/result, and crafting remainder recipes may be rejected with a reason"),
                "system prompt must document GET_ITEM recipe support limits");
        require(!prompt.contains("NBT/tag"),
                "system prompt must not group finite tag-backed ingredients with unsupported NBT");
        require(prompt.contains("reports missing materials instead of searching indefinitely"),
                "system prompt must document GET_ITEM missing material behavior");
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
