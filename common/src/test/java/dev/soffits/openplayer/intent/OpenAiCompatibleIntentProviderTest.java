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
        systemPromptContainsPrimitiveToolNamesOnly();
        systemPromptIncludesPrimitiveSyntax();
        systemPromptIncludesPlannedUnsupportedInstruction();
        systemPromptRejectsMacroSurface();
        parsesStructuredToolProviderResponse();
        parsesStructuredPlanProviderResponse();
        parsesStructuredChatProviderResponse();
        parsesStructuredUnavailableProviderResponse();
        rejectsOldStyleProviderResponse();
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
        require(prompt.contains("The chat value must be the selected character's concise conversational reply"),
                "system prompt must make chat an NPC reply");
        require(prompt.contains("The unavailable value may be blank or a short safe reason"),
                "system prompt must allow a short safe UNAVAILABLE reason");
        require(prompt.contains("{\"chat\":\"message\""), "system prompt must document structured chat JSON");
        require(prompt.contains("{\"unavailable\":\"reason\""),
                "system prompt must document structured unavailable JSON");
        require(prompt.contains("For one executable primitive tool"),
                "system prompt must document structured tool JSON");
        require(prompt.contains("if priority is omitted, OpenPlayer treats it as NORMAL"),
                "system prompt must document deterministic structured priority default");
        require(prompt.contains("For compatibility only, a bounded plan"),
                "system prompt must document compatibility provider plan execution");
        require(prompt.contains("accepted or queued never means completed"),
                "system prompt must not overclaim provider plan completion");
        require(!prompt.contains("Do not return plan JSON for execution"),
                "system prompt must not contradict bounded provider plan execution");
        require(prompt.contains("Return JSON only"), "system prompt must constrain output to JSON only");
        require(prompt.contains("no secrets"), "system prompt must prohibit secrets");
    }

    private static void systemPromptContainsPrimitiveToolNamesOnly() {
        String prompt = OpenAiCompatibleIntentProvider.systemPrompt();
        require(prompt.contains("move_to"), "system prompt must list move_to");
        require(prompt.contains("break_block_at"), "system prompt must list break_block_at");
        require(prompt.contains("pickup_items_nearby"), "system prompt must list pickup_items_nearby");
        require(prompt.contains("report_status"), "system prompt must list provider-executable report_status");
        require(prompt.contains("dig"), "system prompt must list provider-executable dig");
        require(prompt.contains("pathfinder_goto"), "system prompt must list provider-executable pathfinder_goto");
        require(prompt.contains("inventory"), "system prompt may list provider-executable inventory alias");
        require(!prompt.contains("swing_arm"), "system prompt must not expose facade-only swing_arm");
        require(!prompt.contains("and inventory, must not be returned as provider tools"),
                "system prompt must not contradict provider-executable inventory alias");
        require(!prompt.contains("GIVE_ITEM"), "system prompt must not expose old non-tool enum names");
        require(!prompt.contains("\"kind\""), "system prompt must not expose old kind field");
        require(!prompt.contains("\"instruction\""), "system prompt must not expose old instruction field");
        String[] removedTools = {
                "open_furnace", "furnace_status", "open_chest", "is_bed", "armor_manager_equip_best",
                "collectblock_collect", "auto_eat_status", "open_anvil", "villager_trade", "move_vehicle", "elytra_fly"
        };
        for (String removedTool : removedTools) {
            require(!prompt.contains(removedTool), "system prompt must not expose removed narrow tool: " + removedTool);
        }
    }

    private static void systemPromptIncludesPlannedUnsupportedInstruction() {
        String prompt = OpenAiCompatibleIntentProvider.systemPrompt();
        require(prompt.contains("Online/commercial-service features are unavailable"),
                "system prompt must include deterministic unavailable feature instruction");
        require(prompt.contains("use UNAVAILABLE when a required reviewed primitive or capability adapter is absent"),
                "system prompt must tell providers not to overclaim missing adapters");
    }

    private static void systemPromptIncludesPrimitiveSyntax() {
        String prompt = OpenAiCompatibleIntentProvider.systemPrompt();
        require(prompt.contains("inventory_query"), "system prompt must document inventory query syntax");
        require(prompt.contains("move_to is coordinate-only"),
                "system prompt must narrow move_to to explicit coordinates");
        require(prompt.contains("do not use owner, block id, entity id, search, resource, route, or goal syntax for move_to"),
                "system prompt must reject move_to lookup macros");
        require(prompt.contains("For equip_item use args item=<item_id>"),
                "system prompt must document exact equip item ids");
        require(prompt.contains("drop_item use empty args to drop selected hotbar stack or args itemType=<item_id> with optional count"),
                "system prompt must document drop item count syntax");
        require(prompt.contains("Container transfer, owner item transfer, automatic best-equipment selection"),
                "system prompt must declare non-tool inventory macros unavailable to providers");
        require(prompt.contains("pause stops automation ticks without clearing active or queued tasks"),
                "system prompt must document control command blank syntax");
        require(prompt.contains("Memory reset is not an AICore tool"),
                "system prompt must document bounded reset memory scope");
        require(prompt.contains("These interaction primitives are player-like and capability-gated"),
                "system prompt must document interaction capability gates");
        require(prompt.contains("friendly mobs, neutral mobs, or arbitrary non-hostile entities"),
                "system prompt must document narrow explicit attack target scope");
        require(prompt.contains("find_loaded_blocks and find_loaded_entities are report-only loaded-world reconnaissance primitives"),
                "system prompt must document report-only loaded reconnaissance");
        require(prompt.contains("they do not navigate, mutate the world, load chunks, run long-range locate searches, or imply a plan"),
                "system prompt must constrain loaded reconnaissance to report-only behavior");
        require(prompt.contains("if the requested next primitive is unavailable, return UNAVAILABLE or report_status"),
                "system prompt must direct missing primitives to unavailable/status diagnostics");
        require(prompt.contains("Never tell the player to press keyboard controls for the NPC"),
                "system prompt must forbid keyboard-control claims for NPC actions");
        require(prompt.contains("if sprintControl is unsupported or no sprint primitive exists"),
                "system prompt must keep sprint claims tied to runtime capability");
        require(prompt.contains("If a value is unsupported, unavailable, or absent, say that instead of guessing"),
                "system prompt must require truthful hunger and status answers from context");
        require(prompt.contains("inventory_query, exact equip_item item ids, or report_status"),
                "system prompt must guide self-maintenance through generic primitives only");
    }

    private static void systemPromptRejectsMacroSurface() {
        String prompt = OpenAiCompatibleIntentProvider.systemPrompt();
        String[] removedKinds = {
                "GET_ITEM", "SMELT_ITEM", "COLLECT_FOOD", "FARM_NEARBY", "FISH", "DEFEND_OWNER",
                "BUILD_STRUCTURE", "LOCATE_STRUCTURE", "EXPLORE_CHUNKS", "USE_PORTAL", "TRAVEL_NETHER"
        };
        for (String removedKind : removedKinds) {
            require(!prompt.contains("kind must be one of " + removedKind),
                    "system prompt must not expose removed macro kind as provider kind: " + removedKind);
        }
        require(prompt.contains("Do not emit high-level goals or macro task chains"),
                "system prompt must forbid macro task chains");
        require(prompt.contains("acquiring resources, making workstations, smelting, farming, fishing, defending an owner, building structures, exploring chunks, locating structures, using portals, Nether travel"),
                "system prompt must name removed macro categories");
    }

    private static void parsesStructuredToolProviderResponse() throws Exception {
        ProviderIntent intent = OpenAiCompatibleIntentProvider.parseProviderResponse(responseWithContent(
                "{\"tool\":\"dig\",\"args\":{\"x\":10,\"y\":64,\"z\":-3}}"
        ));
        require(intent.hasStructuredToolJson(), "provider response parser must preserve structured tool JSON");
        require(!intent.plan(), "single tool provider response must not be marked as a plan");
        require("NORMAL".equals(intent.priority()), "missing structured tool priority must default to NORMAL");
    }

    private static void parsesStructuredPlanProviderResponse() throws Exception {
        ProviderIntent intent = OpenAiCompatibleIntentProvider.parseProviderResponse(responseWithContent(
                "{\"plan\":[{\"tool\":\"report_status\",\"args\":{}}]}"
        ));
        require(intent.hasStructuredToolJson(), "provider response parser must preserve structured plan JSON");
        require(intent.plan(), "provider response parser must mark plan JSON as bounded plan output");
    }

    private static void parsesStructuredChatProviderResponse() throws Exception {
        ProviderIntent intent = OpenAiCompatibleIntentProvider.parseProviderResponse(responseWithContent(
                "{\"chat\":\"I can help with that.\",\"priority\":\"LOW\"}"
        ));
        require(intent.type() == ProviderIntent.Type.CHAT, "provider response parser must preserve structured chat");
        require("LOW".equals(intent.priority()), "structured chat priority must be preserved");
        require("I can help with that.".equals(intent.message()), "structured chat message must be preserved");
    }

    private static void parsesStructuredUnavailableProviderResponse() throws Exception {
        ProviderIntent intent = OpenAiCompatibleIntentProvider.parseProviderResponse(responseWithContent(
                "{\"unavailable\":\"missing reviewed adapter\"}"
        ));
        require(intent.type() == ProviderIntent.Type.UNAVAILABLE,
                "provider response parser must preserve structured unavailable");
        require("NORMAL".equals(intent.priority()), "structured unavailable priority must default to NORMAL");
        require("missing reviewed adapter".equals(intent.message()), "structured unavailable reason must be preserved");
    }

    private static void rejectsOldStyleProviderResponse() throws Exception {
        try {
            OpenAiCompatibleIntentProvider.parseProviderResponse(responseWithContent(
                    "{\"kind\":\"move_to\",\"priority\":\"NORMAL\",\"instruction\":\"1 64 -2\"}"
            ));
            throw new AssertionError("provider response parser must reject old-style automation output");
        } catch (IntentProviderException exception) {
            require("provider intent JSON must contain exactly one of tool, plan, chat, or unavailable".equals(exception.getMessage()),
                    "old-style provider response rejection must be deterministic");
        }
    }

    private static String normalize(String value) throws Exception {
        return OpenAiCompatibleIntentProvider.normalizeChatCompletionsEndpoint(new URI(value)).toString();
    }

    private static String responseWithContent(String content) {
        return "{\"choices\":[{\"message\":{\"content\":\"" + escapeJson(content) + "\"}}]}";
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') {
                builder.append('\\');
            }
            builder.append(character);
        }
        return builder.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
