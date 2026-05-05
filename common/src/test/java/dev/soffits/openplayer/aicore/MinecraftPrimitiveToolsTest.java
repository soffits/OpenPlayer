package dev.soffits.openplayer.aicore;

import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentPriority;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class MinecraftPrimitiveToolsTest {
    private MinecraftPrimitiveToolsTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registryExposesPrimitiveToolsOnly();
        mapsProviderToolsToRuntimePrimitives();
        reverseRuntimeMappingsOnlyUseExecutableCatalogTools();
        directInteractIntentDoesNotReverseMapToNonexistentTool();
        mapsPickupItemsNearbyArgumentsToCollectInstruction();
        mapsDropItemArgumentsWithoutDroppingCount();
        rejectsUnknownAndIgnoredArguments();
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

    private static void reverseRuntimeMappingsOnlyUseExecutableCatalogTools() {
        for (IntentKind kind : IntentKind.values()) {
            Optional<ToolCall> toolCall = MinecraftPrimitiveTools.toToolCall(
                    new CommandIntent(kind, IntentPriority.NORMAL, runtimeInstructionFor(kind))
            );
            if (toolCall.isEmpty()) {
                continue;
            }
            ToolName toolName = toolCall.get().name();
            require(AICoreToolCatalog.definition(toolName).isPresent(),
                    "reverse mapped tool must exist in catalog: " + toolName.value());
            require(MinecraftPrimitiveTools.toCommandIntent(toolCall.get(), IntentPriority.NORMAL).isPresent(),
                    "reverse mapped tool must be command-executable: " + toolName.value());
        }
    }

    private static void directInteractIntentDoesNotReverseMapToNonexistentTool() {
        Optional<ToolCall> blockToolCall = MinecraftPrimitiveTools.toToolCall(
                new CommandIntent(IntentKind.INTERACT, IntentPriority.NORMAL, "block 1 64 1")
        );
        require(blockToolCall.isEmpty(), "direct block INTERACT must bypass ambiguous AICore reverse mapping");

        Optional<ToolCall> entityToolCall = MinecraftPrimitiveTools.toToolCall(
                new CommandIntent(IntentKind.INTERACT, IntentPriority.NORMAL, "entity minecraft:sheep 4")
        );
        require(entityToolCall.isEmpty(), "direct entity INTERACT must bypass ambiguous AICore reverse mapping");
    }

    private static void mapsPickupItemsNearbyArgumentsToCollectInstruction() {
        Optional<CommandIntent> legacyCollect = MinecraftPrimitiveTools.toCommandIntent(
                ToolCall.of("pickup_items_nearby", ToolArguments.empty()), IntentPriority.NORMAL
        );
        require(legacyCollect.isPresent(), "pickup_items_nearby must map to runtime command");
        require(legacyCollect.get().kind() == IntentKind.COLLECT_ITEMS,
                "pickup_items_nearby must bridge to COLLECT_ITEMS");
        require("".equals(legacyCollect.get().instruction()),
                "empty pickup_items_nearby args must preserve collect-any behavior");

        Optional<CommandIntent> filteredCollect = MinecraftPrimitiveTools.toCommandIntent(
                ToolCall.of("pickup_items_nearby", new ToolArguments(Map.of("matching", "spruce_log", "maxDistance", "8"))),
                IntentPriority.NORMAL
        );
        require(filteredCollect.isPresent(), "filtered pickup_items_nearby must map to runtime command");
        require("minecraft:spruce_log 8".equals(filteredCollect.get().instruction()),
                "pickup_items_nearby matching/maxDistance must become collect item/radius instruction");

        ToolCall radiusOnlyCollect = ToolCall.of(
                "pickup_items_nearby", new ToolArguments(Map.of("maxDistance", "8"))
        );
        requireRejected(MinecraftPrimitiveTools.validate(radiusOnlyCollect, new ToolValidationContext(true)),
                "pickup_items_nearby maxDistance requires matching");
        Optional<CommandIntent> radiusOnlyIntent = MinecraftPrimitiveTools.toCommandIntent(
                radiusOnlyCollect, IntentPriority.NORMAL
        );
        require(radiusOnlyIntent.isPresent(), "pickup_items_nearby maxDistance-only bridge must remain deterministic");
        require("8".equals(radiusOnlyIntent.get().instruction()),
                "pickup_items_nearby maxDistance-only bridge must not widen to collect-any");
    }

    private static void mapsDropItemArgumentsWithoutDroppingCount() {
        Optional<CommandIntent> drop = MinecraftPrimitiveTools.toCommandIntent(
                ToolCall.of("drop_item", new ToolArguments(Map.of("itemType", "minecraft:bread", "count", "3"))),
                IntentPriority.NORMAL
        );
        require(drop.isPresent(), "drop_item must map to runtime command");
        require(drop.get().kind() == IntentKind.DROP_ITEM, "drop_item must bridge to DROP_ITEM");
        require("minecraft:bread 3".equals(drop.get().instruction()),
                "drop_item itemType/count must preserve the requested count");
    }

    private static void rejectsUnknownAndIgnoredArguments() {
        requireRejected(MinecraftPrimitiveTools.validate(
                ToolCall.of("report_status", new ToolArguments(Map.of("matching", "minecraft:stone"))),
                new ToolValidationContext(true)
        ), "Unknown argument for report_status: matching");
        requireRejected(MinecraftPrimitiveTools.validate(
                ToolCall.of("dig", new ToolArguments(Map.of("x", "1", "y", "64", "z", "2", "forceLook", "true"))),
                new ToolValidationContext(true)
        ), "Unknown argument for dig: forceLook");
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

    private static String runtimeInstructionFor(IntentKind kind) {
        return switch (kind) {
            case BREAK_BLOCK, GOTO, LOOK, MOVE, PLACE_BLOCK -> "1 64 1";
            case COLLECT_ITEMS -> "minecraft:stone 8";
            case CRAFT -> "minecraft:stick 1";
            case DROP_ITEM -> "minecraft:stone 1";
            case EQUIP_ITEM -> "minecraft:stone";
            case INTERACT -> "block 1 64 1";
            case LOCATE_LOADED_BLOCK -> "minecraft:stone 8";
            case LOCATE_LOADED_ENTITY -> "minecraft:zombie 8";
            case ATTACK_NEAREST -> "8";
            case ATTACK_TARGET -> "minecraft:zombie";
            default -> "";
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
