package dev.soffits.openplayer.intent;

import dev.soffits.openplayer.debug.OpenPlayerRawTrace;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentPolicies;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

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
        return "Parse the user command for an OpenPlayer NPC. Return only compact JSON with string fields kind, priority, and instruction. "
            + "kind must be one of " + RuntimeIntentPolicies.allIntentKindNames() + ". "
            + "priority must be one of LOW, NORMAL, HIGH. Keep instruction short and actionable. "
            + "For CHAT, instruction must be the selected character's concise conversational reply, not a restatement of the player text. For UNAVAILABLE, instruction may be blank or a short safe reason. "
            + "Return JSON only, with no secrets, credentials, markdown, or explanatory text. "
            + "Use x y z for MOVE, LOOK, PATROL, BREAK_BLOCK, and PLACE_BLOCK; for GOTO use exactly one of: x y z, owner, block <block_or_item_id> [radius], or entity <entity_type_id> [radius]. GOTO block/entity only searches already-loaded server-visible area with bounded radius and may reject missing, unloaded, or unreachable targets; do not include the literal GOTO in instruction. Blank instruction for COLLECT_ITEMS, EQUIP_BEST_ITEM, EQUIP_ARMOR, USE_SELECTED_ITEM, SWAP_TO_OFFHAND, REPORT_STATUS, and INVENTORY_QUERY; blank or radius number for ATTACK_NEAREST, GUARD_OWNER, COLLECT_FOOD, and DEFEND_OWNER. "
            + "For EQUIP_ITEM use exact item id <item_id>; for DROP_ITEM use blank to drop selected hotbar stack or exact one-stack item id syntax <item_id> [count]; for GIVE_ITEM use exact owner-only one-stack syntax <item_id> [count] or <item_id> [count] owner. "
            + "For DEPOSIT_ITEM and STASH_ITEM, instruction must be blank to move all normal inventory or exact item id syntax <item_id> [count]; do not include the literal kind token in instruction. They only use loaded nearby vanilla chests or barrels, ignore armor/offhand, and must move all requested items atomically. STASH_ITEM remembers a successful local stash container. "
            + "For WITHDRAW_ITEM, instruction must contain only exact item id syntax <item_id> [count]; do not include the literal WITHDRAW_ITEM in instruction. It prefers remembered valid stash then loaded nearby vanilla chests or barrels, and exact-count withdrawal must fit NPC normal inventory. "
            + "For kind GET_ITEM, instruction must contain only exact item id syntax <item_id> [count]; do not include the literal GET_ITEM in instruction. This is bounded one-stack local inventory/crafting that can use server recipe data for supported simple recipes, including simple datapack/mod recipes visible to the server and finite tag-backed ingredient alternatives, and reports missing materials instead of searching indefinitely. Crafting-table recipes require a loaded nearby crafting table capability gate; special/custom, NBT-bearing ingredient/result, and crafting remainder recipes may be rejected with a reason. "
            + "For SMELT_ITEM, instruction must contain only exact item id syntax <output_item_id> [count]. It uses a loaded nearby vanilla furnace block, NPC-carried recipe input plus NPC-carried fuel, and bounded server recipe data. It is asynchronous: accepted means queued or started, and REPORT_STATUS observes progress; completion is only after requested output is transferred into NPC normal inventory. Furnace-only smelting may reject smoker, blast furnace, missing materials, missing fuel, occupied incompatible furnace slots, no output capacity, timeout, or changed state. "
            + "For COLLECT_FOOD, instruction must be blank or only a positive radius number; do not include the literal COLLECT_FOOD in instruction. It only navigates to already-loaded nearby safe edible dropped item stacks accepted by the NPC local food policy, excluding potion, stew, and container-remainder items, and may complete when none remain or reject inventory-full/unreachable targets. For DEFEND_OWNER, instruction must be blank or only a positive radius number; do not include the literal DEFEND_OWNER in instruction. It requires the owner in the same dimension, may pre-equip carried armor/weapon, and only attacks hostile danger targets near the owner, not players, OpenPlayer NPCs, or passive animals by default. "
            + "For FARM_NEARBY, instruction must be blank or only a positive radius number. It scans only already-loaded nearby blocks with bounded radius, harvests one mature vanilla crop at a time, and replants only when matching seed/item is in NPC normal inventory or hotbar. For FISH, instruction must be blank, a positive duration in seconds, stop, or cancel, but current NPC backend rejects actual fishing until a safe player-bound hook adapter exists; do not choose FISH unless the user explicitly asks to test fishing support. "
            + "Online/commercial-service features are unavailable; use UNAVAILABLE when the vanilla runtime cannot perform the requested action. "
            + "Only select world, inventory, or combat actions when the user prompt says the selected character allows world actions.";
    }

    private static ProviderIntent parseProviderResponse(String responseBody) throws IntentProviderException {
        try {
            OpenPlayerRawTrace.parseInput("provider_response_body", null, responseBody);
            String content = readStringField(responseBody, "content", "provider response content");
            OpenPlayerRawTrace.parseInput("provider_model_content", null, content);
            String intentJson = extractJsonObject(content);
            String kind = readStringField(intentJson, "kind", "provider intent kind");
            String priority = readStringField(intentJson, "priority", "provider intent priority");
            String instruction = readStringField(intentJson, "instruction", "provider intent instruction");
            OpenPlayerRawTrace.parseOutput("provider_intent_json", null, intentJson);
            return new ProviderIntent(kind, priority, instruction);
        } catch (IntentProviderException exception) {
            OpenPlayerRawTrace.parseRejection("provider_response_body", null, responseBody, exception.getMessage());
            throw exception;
        }
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
