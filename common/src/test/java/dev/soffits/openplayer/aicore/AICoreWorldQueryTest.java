package dev.soffits.openplayer.aicore;

import java.util.Map;

public final class AICoreWorldQueryTest {
    private AICoreWorldQueryTest() {
    }

    public static void main(String[] args) {
        AICoreTestSupport.requireTool("block_at");
        AICoreTestSupport.requireTool("can_see_block");
        ToolResult missingAdapter = MinecraftPrimitiveTools.validate(ToolCall.of("block_at_cursor", new ToolArguments(Map.of("maxDistance", "32"))), new ToolValidationContext(true));
        AICoreTestSupport.requireFailed(missingAdapter, "unsupported_missing_raycast_adapter");
        ToolResult unbounded = MinecraftPrimitiveTools.validate(ToolCall.of("find_block", new ToolArguments(Map.of("matching", "minecraft:dirt"))), new ToolValidationContext(true));
        AICoreTestSupport.requireRejected(unbounded, "Missing required argument: maxDistance");
    }
}
