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
            + "Use x y z for MOVE, LOOK, PATROL, BREAK_BLOCK, and PLACE_BLOCK; for GOTO use exactly one of: x y z, owner, block <block_or_item_id> [radius], or entity <entity_type_id> [radius]. GOTO block/entity only searches already-loaded server-visible area with bounded radius and may reject missing, unloaded, or unreachable targets; do not include the literal GOTO in instruction. Blank instruction for COLLECT_ITEMS, EQUIP_BEST_ITEM, EQUIP_ARMOR, USE_SELECTED_ITEM, SWAP_TO_OFFHAND, REPORT_STATUS, and INVENTORY_QUERY; blank or radius number for ATTACK_NEAREST, GUARD_OWNER, COLLECT_FOOD, and DEFEND_OWNER. For INTERACT use only block <x> <y> <z> for loaded nearby safe empty-hand block toggles on explicit vanilla levers, wooden trapdoors, and wooden fence gates; entity interaction, trading, breeding, doors, buttons, iron trapdoors, buckets, item-use-on-block, villager UI, container UI, modded/custom interaction, and generic item interaction are unsupported. For ATTACK_TARGET use [entity] <entity_type_or_uuid> [radius]; it only targets loaded explicitly allowlisted hostile danger entities and must not be used for players, owners, OpenPlayer NPCs, passive animals, friendly mobs, neutral mobs, or arbitrary non-hostile entities. "
            + "For EQUIP_ITEM use exact item id <item_id>; for DROP_ITEM use blank to drop selected hotbar stack or exact one-stack item id syntax <item_id> [count]; for GIVE_ITEM use exact owner-only one-stack syntax <item_id> [count] or <item_id> [count] owner. "
            + "For DEPOSIT_ITEM and STASH_ITEM, instruction must be blank to move all normal inventory or exact item id syntax <item_id> [count] with optional repeat=1..5; do not include the literal kind token in instruction. They only use loaded nearby vanilla chests or barrels, ignore armor/offhand, and each bounded repeat iteration must move all requested items atomically. STASH_ITEM remembers a successful local stash container. "
            + "For WITHDRAW_ITEM, instruction must contain only exact item id syntax <item_id> [count]; do not include the literal WITHDRAW_ITEM in instruction. It prefers remembered valid stash then loaded nearby vanilla chests or barrels, and exact-count withdrawal must fit NPC normal inventory. "
            + "For kind GET_ITEM, instruction must contain only exact item id syntax <item_id> [count]; do not include the literal GET_ITEM in instruction. This is bounded one-stack local inventory/crafting plus exact visible dropped-item acquisition in already-loaded nearby LOS only; it verifies NPC inventory count before completion and reports dropped item unavailable/disappeared before pickup, full inventory, stuck/timeout, missing materials, or unsupported recipes instead of searching indefinitely. It can use server recipe data for supported simple recipes, including simple datapack/mod recipes visible to the server and finite tag-backed ingredient alternatives. Crafting-table recipes require a loaded nearby crafting table capability gate; special/custom, NBT-bearing ingredient/result, crafting remainder recipes, hidden mining, arbitrary gathering, buckets, shearing, trading, fishing, portals, and modded machines may be rejected with a reason. "
            + "For SMELT_ITEM, instruction must contain only exact item id syntax <output_item_id> [count]. It uses a loaded nearby vanilla furnace block, NPC-carried recipe input plus NPC-carried fuel, and bounded server recipe data. It is asynchronous: accepted means queued or started, and REPORT_STATUS observes progress; completion is only after requested output is transferred into NPC normal inventory. Furnace-only smelting may reject smoker, blast furnace, missing materials, missing fuel, occupied incompatible furnace slots, no output capacity, timeout, or changed state. "
            + "For COLLECT_FOOD, instruction must be blank or only a positive radius number; do not include the literal COLLECT_FOOD in instruction. It only navigates to already-loaded nearby safe edible dropped item stacks accepted by the NPC local food policy, excluding potion, stew, and container-remainder items, and may complete when none remain or reject inventory-full/unreachable targets. For DEFEND_OWNER, instruction must be blank or only a positive radius number; do not include the literal DEFEND_OWNER in instruction. It requires the owner in the same dimension, may pre-equip carried armor/weapon, and only attacks hostile danger targets near the owner, not players, OpenPlayer NPCs, or passive animals by default. "
            + "For PAUSE, UNPAUSE, and RESET_MEMORY, instruction must be blank. PAUSE stops automation ticks without clearing active or queued tasks; UNPAUSE resumes them; RESET_MEMORY clears only bounded local conversation history for the selected owner and assignment and does not clear automation-local exploration or navigation memory, delete character files, assignment files, provider config, logs, raw traces, or secrets. For BODY_LANGUAGE, instruction must be blank, idle, wave, swing, crouch, uncrouch, or look_owner. BODY_LANGUAGE is visual-only and must not be used for movement, pathing, inventory, world mutation, or combat; unsupported gestures such as nod and shake should use UNAVAILABLE. "
            + "For FARM_NEARBY, instruction must be blank, only a positive radius number, or radius=<blocks> repeat=1..5/count=1..5. It scans only already-loaded nearby blocks with bounded radius, harvests one mature crop per iteration, and replants only when server block/item metadata exposes a same-block replant capability and the needed item is in NPC normal inventory or hotbar. For FISH, instruction must be blank, a positive duration in seconds, duration=<seconds> repeat=1..5, stop, or cancel, but current NPC backend rejects actual fishing until a safe player-bound hook adapter exists; do not choose FISH unless the user explicitly asks to test fishing support. "
            + "For BUILD_STRUCTURE, instruction must exactly use space-separated key=value fields: primitive=<line|wall|floor|box|stairs> origin=<x,y,z> size=<x,y,z> material=<item_id>. Coordinates and sizes are integer comma triples with no spaces, material must be an exact namespaced carried block item id, max dimension is 16, max placed blocks is 64, and it only places into loaded air blocks without fluids or collision. Do not provide JSON, scripts, code, prose, or free-form blueprints. "
            + "Advanced world tasks are mostly unsupported and review-gated. LOCATE_LOADED_BLOCK, LOCATE_LOADED_ENTITY, and FIND_LOADED_BIOME are report-only loaded-world reconnaissance intents with instruction <resource_id> [radius]; they do not navigate, mutate the world, load chunks, or run long-range vanilla locate searches. EXPLORE_CHUNKS is loaded-only bounded navigation; instruction must be blank, reset, clear, or key/value syntax radius=<blocks> steps=<count>, uses only already-loaded chunks near the NPC, never generates chunks, never teleports, and may truthfully fail when no safe loaded target exists. LOCATE_STRUCTURE is a loaded-only structure evidence diagnostic scan with instruction <structure_id> [radius] [source=loaded]; it supports only reviewed loaded-world evidence such as minecraft:village, returns source=loaded_scan evidence_found/not_found/unsupported_structure, may include diagnostic-only nearby loaded chest/barrel hints with no ownership, loot, or structure membership guarantee, never uses server locate APIs, never loads chunks, never teleports, never moves items, and never auto-loots. USE_PORTAL, TRAVEL_NETHER, LOCATE_STRONGHOLD, and END_GAME_TASK are recognized only to return deterministic unsupported status until separate reviewed safe phases exist; do not choose Nether, End, dragon, stronghold, portal, or speedrun intents unless the user explicitly asks to test unsupported status. "
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
