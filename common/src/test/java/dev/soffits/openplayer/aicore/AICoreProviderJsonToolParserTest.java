package dev.soffits.openplayer.aicore;

public final class AICoreProviderJsonToolParserTest {
    private AICoreProviderJsonToolParserTest() {
    }

    public static void main(String[] args) {
        AICoreProviderJsonToolParser parser = new AICoreProviderJsonToolParser(AICoreToolCatalog.registry(), 8);
        JsonToolParseResult single = parser.parse("{\"tool\":\"dig\",\"args\":{\"x\":10,\"y\":64,\"z\":-3,\"forceLook\":true}}");
        AICoreTestSupport.require(single.isAccepted(), "valid single tool call must parse");
        AICoreTestSupport.require("10".equals(single.calls().get(0).arguments().values().get("x")), "integer args must flatten");
        JsonToolParseResult plan = parser.parse("{\"plan\":[{\"tool\":\"find_loaded_blocks\",\"args\":{\"matching\":\"minecraft:oak_log\",\"maxDistance\":24}}]}");
        AICoreTestSupport.require(plan.isAccepted() && plan.calls().size() == 1, "valid bounded plan must parse");
        AICoreProviderJsonToolParser singleStepParser = new AICoreProviderJsonToolParser(AICoreToolCatalog.registry(), 1);
        JsonToolParseResult overMaxPlan = singleStepParser.parse("{\"plan\":[{\"tool\":\"report_status\",\"args\":{}},{\"tool\":\"report_status\",\"args\":{}}]}");
        AICoreTestSupport.require(!overMaxPlan.isAccepted(), "over-max provider plan must be rejected");
        JsonToolParseResult instructionEscape = parser.parse("{\"tool\":\"dig\",\"args\":{\"instruction\":\"10 64 -3\",\"x\":10,\"y\":64,\"z\":-3}}");
        AICoreTestSupport.require(!instructionEscape.isAccepted(), "provider tool args must not accept instruction escape hatches");
        AICoreTestSupport.require(!parser.parse("{\"tool\":\"get_item\",\"args\":{}}").isAccepted(), "unknown macro tool must be rejected");
    }
}
