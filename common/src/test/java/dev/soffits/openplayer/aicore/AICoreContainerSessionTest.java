package dev.soffits.openplayer.aicore;

import java.util.Map;

public final class AICoreContainerSessionTest {
    private AICoreContainerSessionTest() {
    }

    public static void main(String[] args) {
        AICoreTestSupport.requireTool("open_chest");
        AICoreTestSupport.requireTool("villager_trade");
        ToolResult result = MinecraftPrimitiveTools.validate(ToolCall.of("open_furnace", new ToolArguments(Map.of("x", "1", "y", "64", "z", "2"))), new ToolValidationContext(true));
        AICoreTestSupport.requireFailed(result, "unsupported_missing_workstation_adapter");
    }
}
