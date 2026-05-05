package dev.soffits.openplayer.intent;

import dev.soffits.openplayer.automation.advanced.AdvancedTaskInstructionParser;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class ProviderBackedIntentParserTest {
    private ProviderBackedIntentParserTest() {
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        acceptsStructuredToolJsonWithArguments();
        defaultsStructuredToolJsonPriorityToNormal();
        acceptsPathfinderGotoStructuredToolJson();
        acceptsCraftStructuredToolJson();
        preservesCraftingTableStructuredToolJson();
        defaultsLoadedBlockSearchMaxDistance();
        defaultsLoadedEntitySearchMaxDistance();
        preservesExplicitLoadedSearchMaxDistance();
        acceptsPickupItemsNearbyMatchingOnlyFromLogs();
        acceptsPickupItemsNearbyMatchingAndExplicitMaxDistance();
        acceptsPickupItemsNearbyExactAdvertisedArgumentShape();
        rejectsPickupItemsNearbyMaxDistanceWithoutMatching();
        rejectsUnknownStructuredToolArguments();
        rejectsIgnoredStructuredToolArguments();
        preservesDropItemCountStructuredToolJson();
        preservesAttackTargetMaxDistanceStructuredToolJson();
        rejectsWorldObservationUntilAdapterExists();
        rejectsPositionSpecificEntityActivationUntilAdapterExists();
        rejectsFacadeOnlyStructuredToolJson();
        providerPromptExcludesFacadeOnlyTools();
        rejectsAdminStructuredToolJson();
        acceptsStructuredPlanJson();
        rejectsRemovedToolInsideStructuredPlanJson();
        rejectsOverMaxStructuredPlanJson();
        acceptsStructuredChat();
        acceptsStructuredUnavailable();
    }

    private static void acceptsStructuredToolJsonWithArguments() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("HIGH", "{\"tool\":\"dig\",\"args\":{\"x\":10,\"y\":64,\"z\":-3}}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.BREAK_BLOCK, "dig tool JSON must bridge to block breaking intent");
        require(intent.priority() == IntentPriority.HIGH, "tool JSON priority must be preserved");
        require("10 64 -3".equals(intent.instruction()), "coordinate args must become runtime instruction");
    }

    private static void defaultsStructuredToolJsonPriorityToNormal() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("", "{\"tool\":\"report_status\",\"args\":{}}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.priority() == IntentPriority.NORMAL, "missing structured tool priority must default to NORMAL");
    }

    private static void acceptsPathfinderGotoStructuredToolJson() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"pathfinder_goto\",\"args\":{\"goal\":{\"type\":\"goal_block\",\"x\":1,\"y\":64,\"z\":2}}}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.GOTO, "pathfinder_goto goal_block must bridge to bounded GOTO intent");
        require("1 64 2".equals(intent.instruction()), "pathfinder_goto goal coordinates must become runtime instruction");
    }

    private static void acceptsCraftStructuredToolJson() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"craft\",\"args\":{\"recipe\":\"minecraft:oak_planks\",\"count\":1}}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.CRAFT, "craft tool JSON must bridge to craft intent");
        require("minecraft:oak_planks 1".equals(intent.instruction()), "craft args must become recipe/count instruction");
    }

    private static void preservesCraftingTableStructuredToolJson() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"craft\",\"args\":{\"recipe\":\"minecraft:iron_pickaxe\",\"count\":1,\"craftingTable\":{\"x\":10,\"y\":64,\"z\":-2}}}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.CRAFT, "craft tool JSON must bridge to craft intent");
        require("minecraft:iron_pickaxe 1 table 10 64 -2".equals(intent.instruction()),
                "craftingTable args must be preserved in the runtime instruction");
    }

    private static void defaultsLoadedBlockSearchMaxDistance() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"find_loaded_blocks\",\"args\":{\"matching\":\"stone\"}}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.LOCATE_LOADED_BLOCK,
                "find_loaded_blocks tool JSON must bridge to loaded block reconnaissance");
        require(("minecraft:stone " + (int) AdvancedTaskInstructionParser.DEFAULT_RADIUS).equals(intent.instruction()),
                "missing maxDistance must default to a bounded loaded block search radius");
    }

    private static void defaultsLoadedEntitySearchMaxDistance() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"find_loaded_entities\",\"args\":{\"matching\":\"zombie\"}}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.LOCATE_LOADED_ENTITY,
                "find_loaded_entities tool JSON must bridge to loaded entity reconnaissance");
        require(("minecraft:zombie " + (int) AdvancedTaskInstructionParser.DEFAULT_RADIUS).equals(intent.instruction()),
                "missing maxDistance must default to a bounded loaded entity search radius");
    }

    private static void preservesExplicitLoadedSearchMaxDistance() throws Exception {
        ProviderBackedIntentParser blockParser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"find_loaded_blocks\",\"args\":{\"matching\":\"minecraft:oak_log\",\"maxDistance\":24}}")
        );
        CommandIntent blockIntent = blockParser.parse("ignored");
        require("minecraft:oak_log 24".equals(blockIntent.instruction()),
                "explicit loaded block maxDistance must be preserved");

        ProviderBackedIntentParser entityParser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"find_loaded_entities\",\"args\":{\"matching\":\"minecraft:skeleton\",\"maxDistance\":12}}")
        );
        CommandIntent entityIntent = entityParser.parse("ignored");
        require("minecraft:skeleton 12".equals(entityIntent.instruction()),
                "explicit loaded entity maxDistance must be preserved");
    }

    private static void acceptsPickupItemsNearbyMatchingOnlyFromLogs() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"pickup_items_nearby\",\"args\":{\"matching\":\"minecraft:spruce_log\"}}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.COLLECT_ITEMS,
                "pickup_items_nearby tool JSON must bridge to collect items intent");
        require("minecraft:spruce_log".equals(intent.instruction()),
                "matching-only pickup_items_nearby must preserve exact item filter");
    }

    private static void acceptsPickupItemsNearbyMatchingAndExplicitMaxDistance() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"pickup_items_nearby\",\"args\":{\"matching\":\"spruce_log\",\"maxDistance\":8}}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.COLLECT_ITEMS,
                "pickup_items_nearby with radius must bridge to collect items intent");
        require("minecraft:spruce_log 8".equals(intent.instruction()),
                "pickup_items_nearby must canonicalize bare provider item ids and preserve bounded radius");
    }

    private static void acceptsPickupItemsNearbyExactAdvertisedArgumentShape() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"pickup_items_nearby\",\"args\":{\"matching\":\"minecraft:spruce_log\",\"maxDistance\":16}}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.COLLECT_ITEMS,
                "advertised pickup_items_nearby JSON must bridge to COLLECT_ITEMS");
        require("minecraft:spruce_log 16".equals(intent.instruction()),
                "advertised pickup_items_nearby args must become bounded collect instruction");
    }

    private static void rejectsPickupItemsNearbyMaxDistanceWithoutMatching() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"pickup_items_nearby\",\"args\":{\"maxDistance\":8}}")
        );
        requireRejected(parser, "pickup_items_nearby maxDistance requires matching",
                "provider parser must reject pickup_items_nearby maxDistance without matching");
    }

    private static void rejectsUnknownStructuredToolArguments() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"report_status\",\"args\":{\"matching\":\"minecraft:stone\"}}")
        );
        requireRejected(parser, "Unknown argument for report_status: matching",
                "provider parser must reject undeclared arguments before they are ignored");
    }

    private static void rejectsIgnoredStructuredToolArguments() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"dig\",\"args\":{\"x\":10,\"y\":64,\"z\":-3,\"forceLook\":true}}")
        );
        requireRejected(parser, "Unknown argument for dig: forceLook",
                "provider parser must reject arguments the runtime bridge cannot preserve");
    }

    private static void preservesDropItemCountStructuredToolJson() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"drop_item\",\"args\":{\"itemType\":\"minecraft:bread\",\"count\":3}}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.DROP_ITEM, "drop_item tool JSON must bridge to drop item intent");
        require("minecraft:bread 3".equals(intent.instruction()),
                "drop_item tool JSON must preserve itemType and count");
    }

    private static void preservesAttackTargetMaxDistanceStructuredToolJson() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"attack_target\",\"args\":{\"entityId\":\"minecraft:zombie\",\"maxDistance\":9}}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.ATTACK_TARGET, "attack_target tool JSON must bridge to attack target intent");
        require("minecraft:zombie 9".equals(intent.instruction()),
                "attack_target tool JSON must preserve optional maxDistance");
    }

    private static void rejectsWorldObservationUntilAdapterExists() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"observe_world\",\"args\":{}}")
        );
        requireRejected(parser, "unsupported_missing_world_observation_adapter",
                "provider parser must not bridge world observation to generic status");
    }

    private static void rejectsPositionSpecificEntityActivationUntilAdapterExists() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"activate_entity_at\",\"args\":{\"entityId\":\"target\",\"position\":{\"x\":0,\"y\":1,\"z\":0}}}")
        );
        requireRejected(parser, "unsupported_missing_entity_position_interaction_adapter",
                "provider parser must reject position-specific entity activation until the adapter preserves position");
    }

    private static void rejectsFacadeOnlyStructuredToolJson() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"set_quick_bar_slot\",\"args\":{\"slot\":1}}")
        );
        requireRejected(parser, "intent provider returned a non-executable primitive tool",
                "provider parser must reject facade-only tools without a CommandIntent route");
    }

    private static void providerPromptExcludesFacadeOnlyTools() {
        String prompt = OpenAiCompatibleIntentProvider.systemPrompt();
        require(!prompt.contains("set_quick_bar_slot: Select hotbar slot"),
                "provider prompt schemas must not advertise facade-only hotbar selection as executable");
        require(!prompt.contains("observe_world: Report bounded loaded-world status"),
                "provider prompt schemas must not advertise missing world observation adapter as executable");
        require(!prompt.contains("activate_entity_at: Activate an entity at a relative position"),
                "provider prompt schemas must not advertise position-specific entity activation without an adapter");
        require(!prompt.contains("For interact"),
                "provider prompt must not advertise nonexistent interact tool syntax");
        require(!prompt.contains("interact is player-like"),
                "provider prompt must not describe interact as an executable provider tool");
        require(prompt.contains("report_status: Report active OpenPlayer automation status"),
                "provider prompt schemas must retain executable command-bridged tools");
    }

    private static void rejectsAdminStructuredToolJson() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"creative_set_inventory_slot\",\"args\":{\"slot\":1}}")
        );
        requireRejected(parser, "rejected_admin_capability", "admin tool JSON must be policy gated");
    }

    private static void acceptsStructuredPlanJson() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredPlan("HIGH", "{\"plan\":[{\"tool\":\"move_to\",\"args\":{\"x\":1,\"y\":64,\"z\":2}}]}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.PROVIDER_PLAN, "provider plan must bridge to internal plan intent");
        require(intent.priority() == IntentPriority.HIGH, "provider plan priority must be preserved");
        List<CommandIntent> steps = ProviderPlanIntentCodec.decode(intent.instruction());
        require(steps.size() == 1, "provider plan must preserve one executable primitive step");
        require(steps.get(0).kind() == IntentKind.GOTO, "move_to plan step must become GOTO");
        require("1 64 2".equals(steps.get(0).instruction()), "move_to plan args must become runtime instruction");
    }

    private static void rejectsRemovedToolInsideStructuredPlanJson() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredPlan("NORMAL", "{\"plan\":[{\"tool\":\"collectblock_collect\",\"args\":{}}]}")
        );
        requireRejected(parser, "unknown tool: collectblock_collect",
                "provider plans must reject removed narrow tools before any execution claim");
    }

    private static void rejectsOverMaxStructuredPlanJson() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredPlan("NORMAL", "{\"plan\":["
                        + "{\"tool\":\"report_status\",\"args\":{}},"
                        + "{\"tool\":\"report_status\",\"args\":{}},"
                        + "{\"tool\":\"report_status\",\"args\":{}},"
                        + "{\"tool\":\"report_status\",\"args\":{}},"
                        + "{\"tool\":\"report_status\",\"args\":{}},"
                        + "{\"tool\":\"report_status\",\"args\":{}}]}")
        );
        requireRejected(parser, "plan step count is out of bounds",
                "provider plans over max step count must reject before execution claim");
    }

    private static void acceptsStructuredChat() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.chat("normal", "hello")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.CHAT, "parser must preserve chat as conversation output");
        require(intent.priority() == IntentPriority.NORMAL, "parser must normalize lowercase provider priority");
        require("hello".equals(intent.instruction()), "parser must preserve chat message");
    }

    private static void acceptsStructuredUnavailable() throws Exception {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.unavailable("NORMAL", "not supported")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.UNAVAILABLE, "parser must preserve unavailable provider output");
        require("not supported".equals(intent.instruction()), "parser must preserve unavailable reason");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireRejected(ProviderBackedIntentParser parser, String message, String failureMessage) {
        try {
            parser.parse("ignored");
            throw new AssertionError(failureMessage);
        } catch (IntentParseException exception) {
            require(message.equals(exception.getMessage()), failureMessage);
        }
    }
}
