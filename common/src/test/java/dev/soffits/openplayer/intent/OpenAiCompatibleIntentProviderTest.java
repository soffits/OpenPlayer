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
        require(prompt.contains("Online/commercial-service features are unavailable"),
                "system prompt must include deterministic unavailable feature instruction");
        require(prompt.contains("use UNAVAILABLE when the vanilla runtime cannot perform the requested action"),
                "system prompt must tell providers not to overclaim unsupported planned actions");
    }

    private static void systemPromptIncludesPhaseFiveSyntax() {
        String prompt = OpenAiCompatibleIntentProvider.systemPrompt();
        require(prompt.contains("INVENTORY_QUERY"), "system prompt must document inventory query syntax");
        require(prompt.contains("for GOTO use exactly one of: x y z, owner, block <block_or_item_id> [radius], or entity <entity_type_id> [radius]"),
                "system prompt must document deterministic GOTO syntax");
        require(prompt.contains("GOTO block/entity only searches already-loaded server-visible area with bounded radius"),
                "system prompt must document bounded loaded-area GOTO search");
        require(prompt.contains("do not include the literal GOTO in instruction"),
                "system prompt must prevent providers from putting GOTO in the instruction field");
        require(prompt.contains("For EQUIP_ITEM use exact item id <item_id>"),
                "system prompt must document exact equip item ids");
        require(prompt.contains("DROP_ITEM use blank to drop selected hotbar stack or exact one-stack item id syntax <item_id> [count]"),
                "system prompt must document drop item count syntax");
        require(prompt.contains("GIVE_ITEM use exact owner-only one-stack syntax <item_id> [count]"),
                "system prompt must document owner-only give syntax");
        require(prompt.contains("For DEPOSIT_ITEM and STASH_ITEM, instruction must be blank to move all normal inventory or exact item id syntax <item_id> [count]"),
                "system prompt must document deposit and stash syntax");
        require(prompt.contains("WITHDRAW_ITEM, instruction must contain only exact item id syntax <item_id> [count]"),
                "system prompt must document withdraw syntax");
        require(prompt.contains("remembered stash or loaded nearby safe container adapters / Container block entities"),
                "system prompt must document broadened bounded container capability limits");
        require(prompt.contains("vanilla chests and barrels are examples, not the only supported container surface"),
                "system prompt must document vanilla containers as examples only");
        require(prompt.contains("unsupported/locked/custom containers may reject with deterministic missing-adapter/state diagnostics"),
                "system prompt must document deterministic container rejection diagnostics");
        require(prompt.contains("must not use arbitrary inventory API calls or claim fake success"),
                "system prompt must prevent arbitrary provider inventory calls and fake success");
        require(prompt.contains("STASH_ITEM remembers a successful local stash container"),
                "system prompt must document stash memory");
        require(prompt.contains("For kind GET_ITEM, instruction must contain only exact item id syntax <item_id> [count]"),
                "system prompt must document get item instruction syntax without the kind token");
        require(prompt.contains("do not include the literal GET_ITEM in instruction"),
                "system prompt must prevent providers from putting GET_ITEM in the instruction field");
        require(!prompt.contains("GET_ITEM <item_id> [count]"),
                "system prompt must not include misleading GET_ITEM instruction syntax");
        require(prompt.contains("bounded one-stack local inventory/crafting plus exact visible dropped-item acquisition"),
                "system prompt must document bounded GET_ITEM scope");
        require(prompt.contains("already-loaded nearby LOS only"),
                "system prompt must document visible loaded dropped-item limits");
        require(prompt.contains("verifies NPC inventory count before completion"),
                "system prompt must document no-fake-completion policy");
        require(prompt.contains("server recipe data for supported simple recipes"),
                "system prompt must document dynamic GET_ITEM recipe data");
        require(prompt.contains("simple datapack/mod recipes visible to the server"),
                "system prompt must document simple datapack/mod recipe visibility");
        require(prompt.contains("finite tag-backed ingredient alternatives"),
                "system prompt must document finite tag-backed alternative support");
        require(prompt.contains("Crafting-table recipes require a loaded nearby crafting table capability gate"),
                "system prompt must document crafting table capability gate");
        require(prompt.contains("special/custom, NBT-bearing ingredient/result, crafting remainder recipes"),
                "system prompt must document GET_ITEM recipe support limits");
        require(!prompt.contains("NBT/tag"),
                "system prompt must not group finite tag-backed ingredients with unsupported NBT");
        require(prompt.contains("reports dropped item unavailable/disappeared before pickup, full inventory, stuck/timeout, missing materials, or unsupported recipes instead of searching indefinitely"),
                "system prompt must document GET_ITEM missing material behavior");
        require(prompt.contains("For SMELT_ITEM, instruction must contain only exact item id syntax <output_item_id> [count]"),
                "system prompt must document smelt output syntax");
        require(!prompt.contains("SMELT_ITEM <output_item_id> [count]"),
                "system prompt must not tell providers to include the literal SMELT_ITEM token in instruction");
        require(prompt.contains("loaded nearby vanilla furnace, smoker, or blast furnace block entity adapter"),
                "system prompt must document nearby loaded furnace/smoker/blast furnace adapters");
        require(prompt.contains("NPC-carried recipe input plus NPC-carried fuel"),
                "system prompt must document carried input and fuel requirement");
        require(prompt.contains("For PAUSE, UNPAUSE, and RESET_MEMORY, instruction must be blank"),
                "system prompt must document control command blank syntax");
        require(prompt.contains("RESET_MEMORY clears only bounded local conversation history"),
                "system prompt must document bounded reset memory scope");
        require(prompt.contains("does not clear automation-local exploration or navigation memory"),
                "system prompt must document reset memory does not clear automation-local exploration/navigation memory");
        require(prompt.contains("For BODY_LANGUAGE, instruction must be blank, idle, wave, swing, crouch, uncrouch, or look_owner"),
                "system prompt must document body language grammar");
        require(prompt.contains("unsupported gestures such as nod and shake should use UNAVAILABLE"),
                "system prompt must not overclaim unsupported gestures");
        require(prompt.contains("It is asynchronous"),
                "system prompt must document async smelting behavior");
        require(prompt.contains("REPORT_STATUS observes progress"),
                "system prompt must document status observation for smelting");
        require(prompt.contains("completion is only after requested output is transferred into NPC normal inventory"),
                "system prompt must prevent fake smelting success");
        require(prompt.contains("common vanilla blocks such as levers, buttons, doors, trapdoors, fence gates"),
                "system prompt must document expanded block interaction capability scope");
        require(prompt.contains("shearing sheep with carried shears and milking cows/mooshrooms with carried buckets"),
                "system prompt must document initial entity interaction adapters");
        require(prompt.contains("friendly mobs, neutral mobs, or arbitrary non-hostile entities"),
                "system prompt must document narrow explicit attack target scope");
        require(prompt.contains("For COLLECT_FOOD, instruction must be blank or only a positive radius number"),
                "system prompt must document COLLECT_FOOD radius syntax");
        require(prompt.contains("do not include the literal COLLECT_FOOD in instruction"),
                "system prompt must prevent COLLECT_FOOD kind token in instruction");
        require(prompt.contains("excluding potion, stew, and container-remainder items"),
                "system prompt must document safe food limits");
        require(prompt.contains("For DEFEND_OWNER, instruction must be blank or only a positive radius number"),
                "system prompt must document DEFEND_OWNER radius syntax");
        require(prompt.contains("do not include the literal DEFEND_OWNER in instruction"),
                "system prompt must prevent DEFEND_OWNER kind token in instruction");
        require(prompt.contains("not players, OpenPlayer NPCs, or passive animals by default"),
                "system prompt must document defense target limits");
        require(prompt.contains("EXPLORE_CHUNKS is loaded-only bounded navigation"),
                "system prompt must document loaded-only chunk exploration");
        require(prompt.contains("radius=<blocks> steps=<count>"),
                "system prompt must document EXPLORE_CHUNKS key/value syntax");
        require(prompt.contains("LOCATE_STRUCTURE is a loaded-only structure evidence diagnostic scan"),
                "system prompt must document loaded-only structure diagnostics");
        require(prompt.contains("<structure_id> [radius] [source=loaded]"),
                "system prompt must document LOCATE_STRUCTURE syntax");
        require(prompt.contains("source=loaded_scan evidence_found/not_found/unsupported_structure"),
                "system prompt must document truthful structure diagnostic statuses");
        require(prompt.contains("diagnostic-only nearby loaded chest/barrel hints"),
                "system prompt must document diagnostic-only container hints");
        require(prompt.contains("no ownership, loot, or structure membership guarantee"),
                "system prompt must avoid ownership, loot, and membership guarantees");
        require(prompt.contains("never uses server locate APIs, never loads chunks, never teleports, never moves items, and never auto-loots"),
                "system prompt must document LOCATE_STRUCTURE safety boundaries");
        require(prompt.contains("USE_PORTAL and TRAVEL_NETHER are player-like portal tasks"),
                "system prompt must document portal tasks as player-like actions");
        require(prompt.contains("radius=<blocks> target=<minecraft:the_nether|minecraft:overworld> build=<true|false>"),
                "system prompt must document USE_PORTAL strict syntax");
        require(prompt.contains("NPC-carried obsidian plus flint_and_steel"),
                "system prompt must document carried portal materials");
        require(prompt.contains("must not use OP/admin commands, teleport, /locate, /give, forced dimension changes"),
                "system prompt must document portal anti-cheat boundaries");
        require(prompt.contains("completion requires an observed dimension transition"),
                "system prompt must prevent fake portal success");
        require(prompt.contains("never generates chunks, never teleports"),
                "system prompt must not overclaim unsafe exploration behavior");
        require(!prompt.contains("COLLECT_FOOD <"),
                "system prompt must not tell providers to include COLLECT_FOOD token syntax");
        require(!prompt.contains("DEFEND_OWNER <"),
                "system prompt must not tell providers to include DEFEND_OWNER token syntax");
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
