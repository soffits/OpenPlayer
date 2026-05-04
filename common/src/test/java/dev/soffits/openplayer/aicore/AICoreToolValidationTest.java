package dev.soffits.openplayer.aicore;

import java.util.Map;

public final class AICoreToolValidationTest {
    private AICoreToolValidationTest() {
    }

    public static void main(String[] args) {
        ToolResult accepted = MinecraftPrimitiveTools.validate(ToolCall.of("find_blocks", new ToolArguments(Map.of("matching", "minecraft:stone", "maxDistance", "16", "count", "4"))), new ToolValidationContext(true));
        AICoreTestSupport.require(accepted.status() == ToolResultStatus.SUCCESS, "bounded find_blocks must validate");
        AICoreTestSupport.requireRejected(MinecraftPrimitiveTools.validate(ToolCall.of("find_blocks", new ToolArguments(Map.of("matching", "minecraft:stone", "maxDistance", "999", "count", "4"))), new ToolValidationContext(true)), "maxDistance must be between 1 and 256");
        AICoreTestSupport.requireRejected(MinecraftPrimitiveTools.validate(ToolCall.of("find_blocks", new ToolArguments(Map.of("matching", "minecraft:stone", "maxDistance", "3.5", "count", "4"))), new ToolValidationContext(true)), "Argument has invalid integer value: maxDistance");
        requireDecimalMaxDistance("block_at_cursor", new ToolArguments(Map.of("maxDistance", "3.5")));
        requireDecimalMaxDistance("entity_at_cursor", new ToolArguments(Map.of("maxDistance", "3.5")));
        requireDecimalMaxDistance("block_at_entity_cursor", new ToolArguments(Map.of("entityId", "entity-1", "maxDistance", "3.5")));
        requireDecimalMaxDistance("block_at_cursor", new ToolArguments(Map.of("maxDistance", "0.5")));
        requireRejectedMaxDistance("block_at_cursor", new ToolArguments(Map.of("maxDistance", "NaN")));
        requireRejectedMaxDistance("entity_at_cursor", new ToolArguments(Map.of("maxDistance", "Infinity")));
        requireRejectedMaxDistance("block_at_entity_cursor", new ToolArguments(Map.of("entityId", "entity-1", "maxDistance", "0")));
        AICoreTestSupport.requireRejected(MinecraftPrimitiveTools.validate(ToolCall.of("dig", new ToolArguments(Map.of("x", "1", "y", "64", "z", "2"))), new ToolValidationContext(false)), "World actions are disabled for this OpenPlayer character");
        AICoreTestSupport.requireFailed(MinecraftPrimitiveTools.validate(ToolCall.of("fish", ToolArguments.empty()), new ToolValidationContext(true)), "unsupported_missing_npc_fishing_hook_adapter");
    }

    private static void requireDecimalMaxDistance(String toolName, ToolArguments arguments) {
        ToolResult result = MinecraftPrimitiveTools.validate(ToolCall.of(toolName, arguments), new ToolValidationContext(true));
        AICoreTestSupport.require(result.status() == ToolResultStatus.SUCCESS, toolName + " must accept decimal maxDistance");
    }

    private static void requireRejectedMaxDistance(String toolName, ToolArguments arguments) {
        AICoreTestSupport.requireRejected(MinecraftPrimitiveTools.validate(ToolCall.of(toolName, arguments), new ToolValidationContext(true)), "maxDistance must be finite and greater than 0 and at most 256");
    }
}
