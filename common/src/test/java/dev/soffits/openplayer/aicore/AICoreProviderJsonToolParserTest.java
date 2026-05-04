package dev.soffits.openplayer.aicore;

public final class AICoreProviderJsonToolParserTest {
    private AICoreProviderJsonToolParserTest() {
    }

    public static void main(String[] args) {
        AICoreProviderJsonToolParser parser = new AICoreProviderJsonToolParser(AICoreToolCatalog.registry(), 8);
        JsonToolParseResult single = parser.parse("{\"tool\":\"dig\",\"args\":{\"x\":10,\"y\":64,\"z\":-3,\"forceLook\":true}}");
        AICoreTestSupport.require(single.isAccepted(), "valid single tool call must parse");
        AICoreTestSupport.require("10".equals(single.calls().get(0).arguments().values().get("x")), "integer args must flatten");
        JsonToolParseResult plan = parser.parse("{\"plan\":[{\"tool\":\"find_blocks\",\"args\":{\"matching\":\"minecraft:oak_log\",\"maxDistance\":24,\"count\":2}},{\"tool\":\"pathfinder_goto\",\"args\":{\"goal\":{\"type\":\"goal_block\",\"x\":1,\"y\":64,\"z\":1}}}]}");
        AICoreTestSupport.require(plan.isAccepted() && plan.calls().size() == 2, "valid bounded plan must parse");
        AICoreTestSupport.require(!parser.parse("{\"tool\":\"get_item\",\"args\":{}}").isAccepted(), "unknown macro tool must be rejected");
    }
}
