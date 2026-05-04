package dev.soffits.openplayer.aicore;

import java.util.Map;

public final class AICoreToolValidationTest {
    private AICoreToolValidationTest() {
    }

    public static void main(String[] args) {
        ToolResult accepted = MinecraftPrimitiveTools.validate(ToolCall.of("find_blocks", new ToolArguments(Map.of("matching", "minecraft:stone", "maxDistance", "16", "count", "4"))), new ToolValidationContext(true));
        AICoreTestSupport.require(accepted.status() == ToolResultStatus.SUCCESS, "bounded find_blocks must validate");
        AICoreTestSupport.requireRejected(MinecraftPrimitiveTools.validate(ToolCall.of("find_blocks", new ToolArguments(Map.of("matching", "minecraft:stone", "maxDistance", "999", "count", "4"))), new ToolValidationContext(true)), "maxDistance must be between 1 and 256");
        AICoreTestSupport.requireRejected(MinecraftPrimitiveTools.validate(ToolCall.of("dig", new ToolArguments(Map.of("x", "1", "y", "64", "z", "2"))), new ToolValidationContext(false)), "World actions are disabled for this OpenPlayer character");
        AICoreTestSupport.requireFailed(MinecraftPrimitiveTools.validate(ToolCall.of("fish", ToolArguments.empty()), new ToolValidationContext(true)), "unsupported_missing_npc_fishing_hook_adapter");
    }
}
