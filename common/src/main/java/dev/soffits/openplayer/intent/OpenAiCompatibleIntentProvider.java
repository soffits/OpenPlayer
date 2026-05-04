package dev.soffits.openplayer.intent;

import dev.soffits.openplayer.debug.OpenPlayerRawTrace;
import dev.soffits.openplayer.aicore.MinecraftPrimitiveTools;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public final class OpenAiCompatibleIntentProvider implements IntentProvider {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30L);

    private final URI endpointUri;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;

    public OpenAiCompatibleIntentProvider(URI endpointUri, String apiKey, String model) {
        if (endpointUri == null) {
            throw new IllegalArgumentException("endpointUri cannot be null");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey cannot be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model cannot be blank");
        }
        this.endpointUri = normalizeChatCompletionsEndpoint(endpointUri);
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newHttpClient();
    }

    public static URI normalizeChatCompletionsEndpoint(URI endpointUri) {
        if (endpointUri == null) {
            throw new IllegalArgumentException("endpointUri cannot be null");
        }
        String path = endpointUri.getPath();
        if (path == null) {
            return endpointUri;
        }
        String normalizedPath = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        if (normalizedPath.endsWith("/chat/completions")) {
            return endpointUri;
        }
        if (!normalizedPath.endsWith("/v1")) {
            return endpointUri;
        }
        try {
            return new URI(
                    endpointUri.getScheme(),
                    endpointUri.getUserInfo(),
                    endpointUri.getHost(),
                    endpointUri.getPort(),
                    normalizedPath + "/chat/completions",
                    endpointUri.getQuery(),
                    endpointUri.getFragment()
            );
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("endpointUri cannot be normalized", exception);
        }
    }

    @Override
    public ProviderIntent parseIntent(String input) throws IntentProviderException {
        if (input == null) {
            throw new IntentProviderException("input cannot be null");
        }

        String requestBody = requestBody(input);
        OpenPlayerRawTrace.providerRequest(endpointUri.toString(), model, requestBody);
        HttpRequest request = HttpRequest.newBuilder(endpointUri)
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException exception) {
            throw new IntentProviderException("intent provider request timed out", exception);
        } catch (IOException exception) {
            throw new IntentProviderException("intent provider request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntentProviderException("intent provider request was interrupted", exception);
        }

        int statusCode = response.statusCode();
        OpenPlayerRawTrace.providerResponse(endpointUri.toString(), model, statusCode, response.body());
        if (statusCode < 200 || statusCode >= 300) {
            throw new IntentProviderException("intent provider request failed with status " + statusCode);
        }

        return parseProviderResponse(response.body());
    }

    private String requestBody(String input) {
        return "{"
            + "\"model\":\"" + escapeJson(model) + "\","
            + "\"temperature\":0,"
            + "\"messages\":["
            + "{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt()) + "\"},"
            + "{\"role\":\"user\",\"content\":\"" + escapeJson(input) + "\"}"
            + "]"
            + "}";
    }

    static String systemPrompt() {
        return "Parse the user command for an OpenPlayer NPC. Return only compact JSON. For conversation, use {\"chat\":\"message\",\"priority\":\"LOW\"|\"NORMAL\"|\"HIGH\"}. For refusal, use {\"unavailable\":\"reason\",\"priority\":\"LOW\"|\"NORMAL\"|\"HIGH\"}. "
            + "For one executable primitive tool, use {\"tool\":\"tool_name\",\"priority\":\"LOW\"|\"NORMAL\"|\"HIGH\",\"args\":{...}}; if priority is omitted, OpenPlayer treats it as NORMAL. "
            + "tool must be one provider-executable primitive tool name from: " + MinecraftPrimitiveTools.providerExecutableToolNames() + ". "
            + "Do not return plan JSON; provider plans are parser-only in this runtime and are not executed as multi-step tasks. Keep chat, unavailable, and args short and actionable. "
            + "The chat value must be the selected character's concise conversational reply, not a restatement of the player text. The unavailable value may be blank or a short safe reason. "
            + "Return JSON only, with no secrets, credentials, markdown, or explanatory text. "
            + "Provider-executable primitive tool schemas: " + MinecraftPrimitiveTools.providerExecutableToolSchemaText() + ". Facade-only tools that are implemented outside the CommandIntent provider path, including block_at, block_at_cursor, entity_at_cursor, look, set_quick_bar_slot, toss_stack, and held_item, must not be returned as provider tools yet. "
            + "Use args x, y, z only for move_to, look_at, break_block_at, dig, place_block_at, place_block, and activate_block. pathfinder_goto accepts a bounded goal object and is bridged only for coordinate goal shapes that existing loaded-area navigation can handle. move_to is coordinate-only; do not use owner, block id, entity id, search, resource, route, or goal syntax for move_to. Use empty args for pickup_items_nearby, report_status, inventory_query, stop, pause, unpause, activate_item, and consume; use args maxDistance for attack_nearest when needed. "
            + "For interact or activate_block use args target=block with x, y, z or target=entity with entityId and optional maxDistance. interact is player-like and capability-gated to loaded, reachable, line-of-sight targets with reviewed adapters; activate_entity, activate_entity_at, and use_on_entity use the same gates. Unsupported custom blocks, unsupported entity interactions, villager trading UI, breeding/taming without an adapter, and modded machines should use deterministic missing-adapter or UNAVAILABLE wording rather than fake success. For attack_target use args entityId and optional maxDistance; it only targets loaded explicitly allowlisted hostile danger entities and must not be used for players, owners, OpenPlayer NPCs, passive animals, friendly mobs, neutral mobs, or arbitrary non-hostile entities. "
            + "For equip_item use args item=<item_id>; for drop_item use empty args to drop selected hotbar stack or args itemType=<item_id> with optional count for one stack. Container transfer, owner item transfer, automatic best-equipment selection, following, guarding, patrol, and body-language controls are not provider tools in this AICore primitive contract. "
            + "find_loaded_blocks and find_loaded_entities are report-only loaded-world reconnaissance primitives with args matching and optional maxDistance; they do not navigate, mutate the world, load chunks, run long-range locate searches, or imply a plan. "
            + "pause stops automation ticks without clearing active or queued tasks; unpause resumes them. Memory reset is not an AICore tool and does not clear files, config, logs, raw traces, or secrets. "
            + "Do not emit high-level goals or macro task chains such as acquiring resources, crafting, smelting, farming, fishing, defending an owner, building structures, exploring chunks, locating structures, using portals, Nether travel, End travel, strongholds, or speedrun/endgame plans. Decompose only by selecting one currently supported primitive; if the requested next primitive is unavailable, return UNAVAILABLE or report_status. "
            + "Online/commercial-service features are unavailable; use UNAVAILABLE when a required reviewed primitive or capability adapter is absent, policy disallows the action, or visible world state lacks the requirement. "
            + "Only select world, inventory, or combat actions when the user prompt says the selected character allows world actions.";
    }

    static ProviderIntent parseProviderResponse(String responseBody) throws IntentProviderException {
        try {
            OpenPlayerRawTrace.parseInput("provider_response_body", null, responseBody);
            String content = readStringField(responseBody, "content", "provider response content");
            OpenPlayerRawTrace.parseInput("provider_model_content", null, content);
            String intentJson = extractJsonObject(content);
            int actionFieldCount = countProviderActionFields(intentJson);
            if (actionFieldCount != 1) {
                throw new IntentProviderException("provider intent JSON must contain exactly one of tool, plan, chat, or unavailable");
            }
            if (containsJsonField(intentJson, "plan")) {
                String priority = readOptionalStringField(intentJson, "priority").orElse("NORMAL");
                OpenPlayerRawTrace.parseOutput("provider_intent_json", null, intentJson);
                return ProviderIntent.structuredPlan(priority, intentJson);
            }
            if (containsJsonField(intentJson, "tool")) {
                String priority = readOptionalStringField(intentJson, "priority").orElse("NORMAL");
                OpenPlayerRawTrace.parseOutput("provider_intent_json", null, intentJson);
                return ProviderIntent.structuredTool(priority, intentJson);
            }
            if (containsJsonField(intentJson, "chat")) {
                String priority = readOptionalStringField(intentJson, "priority").orElse("NORMAL");
                String message = readStringField(intentJson, "chat", "provider intent chat");
                OpenPlayerRawTrace.parseOutput("provider_intent_json", null, intentJson);
                return ProviderIntent.chat(priority, message);
            }
            if (containsJsonField(intentJson, "unavailable")) {
                String priority = readOptionalStringField(intentJson, "priority").orElse("NORMAL");
                String reason = readStringField(intentJson, "unavailable", "provider intent unavailable");
                OpenPlayerRawTrace.parseOutput("provider_intent_json", null, intentJson);
                return ProviderIntent.unavailable(priority, reason);
            }
            throw new IntentProviderException("provider intent JSON must contain exactly one of tool, plan, chat, or unavailable");
        } catch (IntentProviderException exception) {
            OpenPlayerRawTrace.parseRejection("provider_response_body", null, responseBody, exception.getMessage());
            throw exception;
        }
    }

    private static int countProviderActionFields(String json) {
        int count = 0;
        if (containsJsonField(json, "tool")) {
            count++;
        }
        if (containsJsonField(json, "plan")) {
            count++;
        }
        if (containsJsonField(json, "chat")) {
            count++;
        }
        if (containsJsonField(json, "unavailable")) {
            count++;
        }
        return count;
    }

    private static String readStringField(String json, String fieldName, String description) throws IntentProviderException {
        if (json == null) {
            throw new IntentProviderException(description + " source cannot be null");
        }

        String quotedFieldName = "\"" + fieldName + "\"";
        int searchIndex = 0;
        while (searchIndex < json.length()) {
            int fieldIndex = json.indexOf(quotedFieldName, searchIndex);
            if (fieldIndex < 0) {
                break;
            }

            int index = skipWhitespace(json, fieldIndex + quotedFieldName.length());
            if (index >= json.length() || json.charAt(index) != ':') {
                searchIndex = fieldIndex + 1;
                continue;
            }

            index = skipWhitespace(json, index + 1);
            if (index < json.length() && json.charAt(index) == '"') {
                return readJsonString(json, index, description);
            }
            searchIndex = fieldIndex + 1;
        }

        throw new IntentProviderException(description + " is missing or not a JSON string");
    }

    private static Optional<String> readOptionalStringField(String json, String fieldName) throws IntentProviderException {
        try {
            return Optional.of(readStringField(json, fieldName, "provider intent " + fieldName));
        } catch (IntentProviderException exception) {
            if (exception.getMessage().contains("is missing or not a JSON string")) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private static boolean containsJsonField(String json, String fieldName) {
        if (json == null) {
            return false;
        }
        String quotedFieldName = "\"" + fieldName + "\"";
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = 0; index < json.length(); index++) {
            char character = json.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (inString && character == '\\') {
                escaped = true;
                continue;
            }
            if (character == '"') {
                if (!inString && depth == 1 && json.startsWith(quotedFieldName, index)) {
                    int fieldEndIndex = index + quotedFieldName.length();
                    int separatorIndex = skipWhitespace(json, fieldEndIndex);
                    if (separatorIndex < json.length() && json.charAt(separatorIndex) == ':') {
                        return true;
                    }
                }
                inString = !inString;
                continue;
            }
            if (!inString && character == '{') {
                depth++;
            } else if (!inString && character == '}') {
                depth--;
            }
        }
        return false;
    }

    private static String extractJsonObject(String value) throws IntentProviderException {
        int startIndex = value.indexOf('{');
        if (startIndex < 0) {
            throw new IntentProviderException("provider content did not include a JSON object");
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = startIndex; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                inString = !inString;
            } else if (!inString && character == '{') {
                depth++;
            } else if (!inString && character == '}') {
                depth--;
                if (depth == 0) {
                    return value.substring(startIndex, index + 1);
                }
            }
        }

        throw new IntentProviderException("provider content included incomplete JSON");
    }

    private static String readJsonString(String json, int quoteIndex, String description) throws IntentProviderException {
        StringBuilder builder = new StringBuilder();
        int index = quoteIndex + 1;
        while (index < json.length()) {
            char character = json.charAt(index++);
            if (character == '"') {
                return builder.toString();
            }
            if (character == '\\') {
                if (index >= json.length()) {
                    throw new IntentProviderException(description + " contains an incomplete JSON escape");
                }
                char escapedCharacter = json.charAt(index++);
                switch (escapedCharacter) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        if (index + 4 > json.length()) {
                            throw new IntentProviderException(description + " contains an incomplete unicode escape");
                        }
                        int codeUnit = 0;
                        for (int offset = 0; offset < 4; offset++) {
                            codeUnit = (codeUnit << 4) + hexDigit(json.charAt(index + offset), description);
                        }
                        index += 4;
                        builder.append((char) codeUnit);
                    }
                    default -> throw new IntentProviderException(description + " contains an invalid JSON escape");
                }
            } else {
                if (character < 0x20) {
                    throw new IntentProviderException(description + " contains an unescaped control character");
                }
                builder.append(character);
            }
        }
        throw new IntentProviderException(description + " ended before the JSON string closed");
    }

    private static int hexDigit(char character, String description) throws IntentProviderException {
        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        if (character >= 'a' && character <= 'f') {
            return character - 'a' + 10;
        }
        if (character >= 'A' && character <= 'F') {
            return character - 'A' + 10;
        }
        throw new IntentProviderException(description + " contains an invalid unicode escape");
    }

    private static int skipWhitespace(String value, int index) {
        int currentIndex = index;
        while (currentIndex < value.length() && Character.isWhitespace(value.charAt(currentIndex))) {
            currentIndex++;
        }
        return currentIndex;
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }
}
