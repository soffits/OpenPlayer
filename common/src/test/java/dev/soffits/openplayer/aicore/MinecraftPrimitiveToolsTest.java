package dev.soffits.openplayer.aicore;

import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentPriority;
import java.util.Optional;

public final class MinecraftPrimitiveToolsTest {
    private MinecraftPrimitiveToolsTest() {
    }

    public static void main(String[] args) {
        registryExposesPrimitiveToolsOnly();
        mapsProviderToolsToRuntimePrimitives();
        validatesMoveToCoordinateOnly();
        rejectsUnknownAndMalformedTools();
        worldPolicyGatesMutatingTools();
    }

    private static void registryExposesPrimitiveToolsOnly() {
        String names = MinecraftPrimitiveTools.providerToolNames();
        require(names.contains("observe_self"), "registry must expose observe_self");
        require(names.contains("find_loaded_blocks"), "registry must expose loaded block search");
        require(names.contains("move_to"), "registry must expose move_to");
        require(names.contains("break_block_at"), "registry must expose block breaking primitive");
        require(names.contains("inventory_query"), "registry must expose inventory_query");
        require(names.contains("attack_nearest"), "registry must expose bounded attack primitive");
        String[] removedKinds = {
                "GET_ITEM", "SMELT_ITEM", "COLLECT_FOOD", "FARM_NEARBY", "FISH", "DEFEND_OWNER",
                "BUILD_STRUCTURE", "LOCATE_STRUCTURE", "EXPLORE_CHUNKS", "USE_PORTAL", "TRAVEL_NETHER"
        };
        for (String removedKind : removedKinds) {
            require(!names.contains(removedKind), "registry must not expose removed macro: " + removedKind);
        }
    }

    private static void mapsProviderToolsToRuntimePrimitives() {
        Optional<CommandIntent> move = MinecraftPrimitiveTools.toCommandIntent(
                ToolCall.of("move_to", ToolArguments.instruction("1 64 -2")), IntentPriority.HIGH
        );
        require(move.isPresent(), "move_to must map to runtime command");
        require(move.get().kind() == IntentKind.GOTO, "move_to must bridge to coordinate-only GOTO runtime primitive");
        require(move.get().priority() == IntentPriority.HIGH, "tool priority must be preserved");

        Optional<ToolCall> toolCall = MinecraftPrimitiveTools.toToolCall(
                new CommandIntent(IntentKind.BREAK_BLOCK, IntentPriority.NORMAL, "1 64 -2")
        );
        require(toolCall.isPresent(), "runtime primitive must map back to tool call");
        require("break_block_at".equals(toolCall.get().name().value()), "BREAK_BLOCK must map to break_block_at");
    }

    private static void validatesMoveToCoordinateOnly() {
        require(MinecraftPrimitiveTools.validate(
                ToolCall.of("move_to", ToolArguments.instruction("1 64 -2")), new ToolValidationContext(true)
        ).status() == ToolResultStatus.SUCCESS, "move_to must accept explicit coordinates");
        requireRejected(MinecraftPrimitiveTools.validate(
                ToolCall.of("move_to", ToolArguments.instruction("owner")), new ToolValidationContext(true)
        ), "GOTO requires instruction: x y z");
        requireRejected(MinecraftPrimitiveTools.validate(
                ToolCall.of("move_to", ToolArguments.instruction("block minecraft:oak_log")), new ToolValidationContext(true)
        ), "GOTO requires instruction: x y z");
    }

    private static void rejectsUnknownAndMalformedTools() {
        requireRejected(MinecraftPrimitiveTools.validate(
                ToolCall.of("get_item", ToolArguments.instruction("minecraft:bread")), new ToolValidationContext(true)
        ), "Unknown tool: get_item");
        try {
            ToolName.of("GET_ITEM");
            throw new AssertionError("tool names must reject upper-case macro spelling");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("lower_snake_case"), "malformed tool rejection must be deterministic");
        }
    }

    private static void worldPolicyGatesMutatingTools() {
        requireRejected(MinecraftPrimitiveTools.validate(
                ToolCall.of("break_block_at", ToolArguments.instruction("1 64 -2")), new ToolValidationContext(false)
        ), "World actions are disabled for this OpenPlayer character");
        require(MinecraftPrimitiveTools.validate(
                ToolCall.of("inventory_query", ToolArguments.empty()), new ToolValidationContext(false)
        ).status() == ToolResultStatus.SUCCESS, "read-only inventory query must pass when world actions are disabled");
    }

    private static void requireRejected(ToolResult result, String reason) {
        require(result.status() == ToolResultStatus.REJECTED, "tool result must be rejected");
        require(reason.equals(result.reason()), "unexpected rejection: " + result.reason());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
