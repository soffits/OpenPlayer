package dev.soffits.openplayer.intent;

public final class ProviderBackedIntentParserTest {
    private ProviderBackedIntentParserTest() {
    }

    public static void main(String[] args) throws Exception {
        acceptsStructuredToolJsonWithArguments();
        defaultsStructuredToolJsonPriorityToNormal();
        rejectsUnsupportedAdapterStructuredToolJson();
        rejectsAdminStructuredToolJson();
        rejectsStructuredPlanJson();
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

    private static void rejectsUnsupportedAdapterStructuredToolJson() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"pathfinder_goto\",\"args\":{\"goal\":{\"type\":\"goal_block\",\"x\":1,\"y\":64,\"z\":2}}}")
        );
        requireRejected(parser, "unsupported_missing_pathfinder_adapter",
                "unsupported adapter tool JSON must not become executable");
    }

    private static void rejectsAdminStructuredToolJson() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"creative_set_inventory_slot\",\"args\":{\"slot\":1}}")
        );
        requireRejected(parser, "rejected_admin_capability", "admin tool JSON must be policy gated");
    }

    private static void rejectsStructuredPlanJson() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredPlan("NORMAL", "{\"plan\":[{\"tool\":\"report_status\",\"args\":{}}]}")
        );
        requireRejected(parser, "provider plans are parser-only and are not executable by this provider path",
                "provider plan JSON must not fake multi-step execution");
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
