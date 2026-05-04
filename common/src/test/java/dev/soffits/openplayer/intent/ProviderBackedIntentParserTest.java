package dev.soffits.openplayer.intent;

import java.util.List;

public final class ProviderBackedIntentParserTest {
    private ProviderBackedIntentParserTest() {
    }

    public static void main(String[] args) throws Exception {
        acceptsStructuredToolJsonWithArguments();
        defaultsStructuredToolJsonPriorityToNormal();
        acceptsPathfinderGotoStructuredToolJson();
        acceptsCraftStructuredToolJson();
        preservesCraftingTableStructuredToolJson();
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
                input -> ProviderIntent.structuredPlan("HIGH", "{\"plan\":[{\"tool\":\"move_to\",\"args\":{\"x\":1,\"y\":64,\"z\":2}},{\"tool\":\"look_at\",\"args\":{\"x\":3,\"y\":65,\"z\":4}}]}")
        );
        CommandIntent intent = parser.parse("ignored");
        require(intent.kind() == IntentKind.PROVIDER_PLAN, "provider plan must bridge to internal plan intent");
        require(intent.priority() == IntentPriority.HIGH, "provider plan priority must be preserved");
        List<CommandIntent> steps = ProviderPlanIntentCodec.decode(intent.instruction());
        require(steps.size() == 2, "provider plan must preserve two executable primitive steps");
        require(steps.get(0).kind() == IntentKind.GOTO, "move_to plan step must become GOTO");
        require("1 64 2".equals(steps.get(0).instruction()), "move_to plan args must become runtime instruction");
        require(steps.get(1).kind() == IntentKind.LOOK, "look_at plan step must become LOOK");
        require("3 65 4".equals(steps.get(1).instruction()), "look_at plan args must become runtime instruction");
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
