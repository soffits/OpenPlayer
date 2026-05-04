package dev.soffits.openplayer.aicore;

import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentPriority;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidationResult;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MinecraftPrimitiveTools {
    public static final ToolName OBSERVE_SELF = ToolName.of("observe_self");
    public static final ToolName OBSERVE_WORLD = ToolName.of("observe_world");
    public static final ToolName FIND_LOADED_BLOCKS = ToolName.of("find_loaded_blocks");
    public static final ToolName FIND_LOADED_ENTITIES = ToolName.of("find_loaded_entities");
    public static final ToolName PICKUP_ITEMS_NEARBY = ToolName.of("pickup_items_nearby");
    public static final ToolName MOVE_TO = ToolName.of("move_to");
    public static final ToolName LOOK_AT = ToolName.of("look_at");
    public static final ToolName BREAK_BLOCK_AT = ToolName.of("break_block_at");
    public static final ToolName PLACE_BLOCK_AT = ToolName.of("place_block_at");
    public static final ToolName INVENTORY_QUERY = ToolName.of("inventory_query");
    public static final ToolName EQUIP_ITEM = ToolName.of("equip_item");
    public static final ToolName DROP_ITEM = ToolName.of("drop_item");
    public static final ToolName INTERACT = ToolName.of("interact");
    public static final ToolName ATTACK_NEAREST = ToolName.of("attack_nearest");
    public static final ToolName ATTACK_TARGET = ToolName.of("attack_target");
    public static final ToolName REPORT_STATUS = ToolName.of("report_status");
    public static final ToolName STOP = ToolName.of("stop");
    public static final ToolName PAUSE = ToolName.of("pause");
    public static final ToolName UNPAUSE = ToolName.of("unpause");

    private static final ToolRegistry REGISTRY = new ToolRegistry(List.of(
            schema(OBSERVE_SELF, "Report the NPC's current status and inventory summary.", false, text("instruction", false, "blank")),
            schema(OBSERVE_WORLD, "Report bounded loaded-world status without loading chunks or mutating world state.", false, text("instruction", false, "blank")),
            schema(FIND_LOADED_BLOCKS, "Find loaded blocks by exact resource id within a bounded radius.", false, text("instruction", true, "<resource_id> [radius]")),
            schema(FIND_LOADED_ENTITIES, "Find loaded entities by exact resource id within a bounded radius.", false, text("instruction", true, "<resource_id> [radius]")),
            schema(PICKUP_ITEMS_NEARBY, "Collect already dropped item entities nearby; does not acquire resources or break blocks.", true, text("instruction", false, "blank")),
            schema(MOVE_TO, "Move to an explicit loaded coordinate.", false, text("instruction", true, "x y z")),
            schema(LOOK_AT, "Look at an explicit coordinate.", false, text("instruction", true, "x y z")),
            schema(BREAK_BLOCK_AT, "Break a loaded reachable block at an explicit coordinate.", true, text("instruction", true, "x y z")),
            schema(PLACE_BLOCK_AT, "Place the selected block item at an explicit loaded coordinate.", true, text("instruction", true, "x y z")),
            schema(INVENTORY_QUERY, "Report the NPC inventory summary.", false, text("instruction", false, "blank")),
            schema(EQUIP_ITEM, "Equip an exact inventory item id when available.", true, text("instruction", true, "<item_id>")),
            schema(DROP_ITEM, "Drop selected stack or one exact inventory item stack.", true, text("instruction", false, "blank or <item_id> [count]")),
            schema(INTERACT, "Interact with a loaded block or supported entity adapter.", true, text("instruction", true, "block <x> <y> <z> or entity <entity_type_or_uuid> [radius]")),
            schema(ATTACK_NEAREST, "Attack nearest allowlisted hostile danger entity in a bounded radius.", true, text("instruction", false, "blank or radius")),
            schema(ATTACK_TARGET, "Attack an explicitly named or UUID allowlisted hostile danger entity.", true, text("instruction", true, "[entity] <entity_type_or_uuid> [radius]")),
            schema(REPORT_STATUS, "Report current automation status.", false, text("instruction", false, "blank")),
            schema(STOP, "Stop active and queued automation.", false, text("instruction", false, "blank")),
            schema(PAUSE, "Pause automation ticks without clearing active or queued work.", false, text("instruction", false, "blank")),
            schema(UNPAUSE, "Resume paused automation ticks.", false, text("instruction", false, "blank"))
    ));

    private static final Map<ToolName, IntentKind> TOOL_TO_INTENT = toolToIntent();
    private static final Map<IntentKind, ToolName> INTENT_TO_TOOL = intentToTool();

    private MinecraftPrimitiveTools() {
    }

    public static ToolRegistry registry() {
        return REGISTRY;
    }

    public static Optional<CommandIntent> toCommandIntent(ToolCall call, IntentPriority priority) {
        if (call == null) {
            throw new IllegalArgumentException("tool call cannot be null");
        }
        IntentKind kind = TOOL_TO_INTENT.get(call.name());
        if (kind == null) {
            return Optional.empty();
        }
        return Optional.of(new CommandIntent(kind, priority, call.arguments().instruction()));
    }

    public static Optional<ToolCall> toToolCall(CommandIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("intent cannot be null");
        }
        ToolName toolName = INTENT_TO_TOOL.get(intent.kind());
        if (toolName == null) {
            return Optional.empty();
        }
        return Optional.of(new ToolCall(toolName, ToolArguments.instruction(intent.instruction())));
    }

    public static Optional<ToolName> toolNameForProviderKind(String value) {
        if (value == null) {
            throw new IllegalArgumentException("provider kind cannot be null");
        }
        try {
            ToolName toolName = ToolName.of(value);
            return REGISTRY.contains(toolName) ? Optional.of(toolName) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static ToolResult validate(ToolCall call, ToolValidationContext context) {
        if (call == null) {
            return ToolResult.rejected("Tool call cannot be null");
        }
        Optional<ToolSchema> schema = REGISTRY.schema(call.name());
        if (schema.isEmpty()) {
            return ToolResult.rejected("Unknown tool: " + call.name().value());
        }
        if (schema.get().mutatesWorld() && !context.allowWorldActions()) {
            return ToolResult.rejected("World actions are disabled for this OpenPlayer character");
        }
        Optional<CommandIntent> commandIntent = toCommandIntent(call, IntentPriority.NORMAL);
        if (commandIntent.isEmpty()) {
            return ToolResult.rejected("Tool is not mapped to a runtime primitive: " + call.name().value());
        }
        RuntimeIntentValidationResult validation = RuntimeIntentValidator.validate(commandIntent.get(), context.allowWorldActions());
        if (!validation.isAccepted()) {
            return ToolResult.rejected(validation.message());
        }
        return ToolResult.success("Tool call accepted");
    }

    public static String providerToolNames() {
        return REGISTRY.toolNamesCsv();
    }

    public static String providerToolSchemaText() {
        StringBuilder builder = new StringBuilder();
        for (ToolSchema schema : REGISTRY.schemas()) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(schema.name().value()).append(": ").append(schema.description()).append(" Args: ");
            for (int index = 0; index < schema.parameters().size(); index++) {
                ToolParameter parameter = schema.parameters().get(index);
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append(parameter.name()).append("=").append(parameter.description());
            }
        }
        return builder.toString();
    }

    private static ToolSchema schema(ToolName name, String description, boolean mutatesWorld, ToolParameter... parameters) {
        return new ToolSchema(name, description, List.of(parameters), mutatesWorld);
    }

    private static ToolParameter text(String name, boolean required, String description) {
        return new ToolParameter(name, "string", required, description);
    }

    private static Map<ToolName, IntentKind> toolToIntent() {
        LinkedHashMap<ToolName, IntentKind> map = new LinkedHashMap<>();
        map.put(OBSERVE_SELF, IntentKind.REPORT_STATUS);
        map.put(OBSERVE_WORLD, IntentKind.REPORT_STATUS);
        map.put(FIND_LOADED_BLOCKS, IntentKind.LOCATE_LOADED_BLOCK);
        map.put(FIND_LOADED_ENTITIES, IntentKind.LOCATE_LOADED_ENTITY);
        map.put(PICKUP_ITEMS_NEARBY, IntentKind.COLLECT_ITEMS);
        map.put(MOVE_TO, IntentKind.GOTO);
        map.put(LOOK_AT, IntentKind.LOOK);
        map.put(BREAK_BLOCK_AT, IntentKind.BREAK_BLOCK);
        map.put(PLACE_BLOCK_AT, IntentKind.PLACE_BLOCK);
        map.put(INVENTORY_QUERY, IntentKind.INVENTORY_QUERY);
        map.put(EQUIP_ITEM, IntentKind.EQUIP_ITEM);
        map.put(DROP_ITEM, IntentKind.DROP_ITEM);
        map.put(INTERACT, IntentKind.INTERACT);
        map.put(ATTACK_NEAREST, IntentKind.ATTACK_NEAREST);
        map.put(ATTACK_TARGET, IntentKind.ATTACK_TARGET);
        map.put(REPORT_STATUS, IntentKind.REPORT_STATUS);
        map.put(STOP, IntentKind.STOP);
        map.put(PAUSE, IntentKind.PAUSE);
        map.put(UNPAUSE, IntentKind.UNPAUSE);
        return Map.copyOf(map);
    }

    private static Map<IntentKind, ToolName> intentToTool() {
        LinkedHashMap<IntentKind, ToolName> map = new LinkedHashMap<>();
        TOOL_TO_INTENT.forEach((toolName, intentKind) -> map.putIfAbsent(intentKind, toolName));
        map.put(IntentKind.MOVE, MOVE_TO);
        return Map.copyOf(map);
    }
}
