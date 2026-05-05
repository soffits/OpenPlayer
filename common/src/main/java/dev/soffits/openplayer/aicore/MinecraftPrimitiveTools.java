package dev.soffits.openplayer.aicore;

import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentPriority;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionClassifier;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidationResult;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidator;
import java.util.LinkedHashMap;
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
    public static final ToolName ATTACK_NEAREST = ToolName.of("attack_nearest");
    public static final ToolName ATTACK_TARGET = ToolName.of("attack_target");
    public static final ToolName REPORT_STATUS = ToolName.of("report_status");
    public static final ToolName STOP = ToolName.of("stop");
    public static final ToolName PAUSE = ToolName.of("pause");
    public static final ToolName UNPAUSE = ToolName.of("unpause");
    public static final ToolName CRAFT = ToolName.of("craft");

    private static final ToolRegistry REGISTRY = AICoreToolCatalog.registry();

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
        return Optional.of(new CommandIntent(kind, priority, AICoreToolInstructionFormatter.runtimeInstruction(call)));
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
        ToolSchema toolSchema = schema.get();
        if (toolSchema.mutatesWorld() && !context.allowWorldActions()) {
            return ToolResult.rejected("World actions are disabled for this OpenPlayer character");
        }
        Optional<AICoreToolDefinition> definition = AICoreToolCatalog.definition(call.name());
        if (definition.isPresent() && definition.get().capabilityStatus() == CapabilityStatus.POLICY_REJECTED) {
            return ToolResult.rejected(definition.get().resultReason());
        }
        Optional<ToolResult> schemaValidation = validateSchema(call, toolSchema);
        if (schemaValidation.isPresent()) {
            return schemaValidation.get();
        }
        Optional<ToolResult> toolSpecificValidation = validateToolSpecificArguments(call);
        if (toolSpecificValidation.isPresent()) {
            return toolSpecificValidation.get();
        }
        Optional<ToolResult> nestedValidation = validateNestedArguments(call);
        if (nestedValidation.isPresent()) {
            return nestedValidation.get();
        }
        if (definition.isPresent()) {
            CapabilityStatus status = definition.get().capabilityStatus();
            if (status == CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER || status == CapabilityStatus.NOT_APPLICABLE_SERVER_SIDE_NPC) {
                return ToolResult.failed(definition.get().resultReason());
            }
        }
        Optional<CommandIntent> commandIntent = toCommandIntent(call, IntentPriority.NORMAL);
        if (commandIntent.isEmpty()) {
            return ToolResult.success("Tool call accepted by AICore facade");
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

    public static String providerExecutableToolNames() {
        StringBuilder builder = new StringBuilder();
        for (ToolSchema schema : REGISTRY.schemas()) {
            if (!TOOL_TO_INTENT.containsKey(schema.name())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(schema.name().value());
        }
        return builder.toString();
    }

    public static String providerToolSchemaText() {
        return providerToolSchemaText(false);
    }

    public static String providerExecutableToolSchemaText() {
        return providerToolSchemaText(true);
    }

    private static String providerToolSchemaText(boolean executableOnly) {
        StringBuilder builder = new StringBuilder();
        for (ToolSchema schema : REGISTRY.schemas()) {
            if (executableOnly && !TOOL_TO_INTENT.containsKey(schema.name())) {
                continue;
            }
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

    private static Map<ToolName, IntentKind> toolToIntent() {
        LinkedHashMap<ToolName, IntentKind> map = new LinkedHashMap<>();
        map.put(OBSERVE_SELF, IntentKind.REPORT_STATUS);
        map.put(FIND_LOADED_BLOCKS, IntentKind.LOCATE_LOADED_BLOCK);
        map.put(FIND_LOADED_ENTITIES, IntentKind.LOCATE_LOADED_ENTITY);
        map.put(PICKUP_ITEMS_NEARBY, IntentKind.COLLECT_ITEMS);
        map.put(MOVE_TO, IntentKind.GOTO);
        map.put(ToolName.of("pathfinder_goto"), IntentKind.GOTO);
        map.put(LOOK_AT, IntentKind.LOOK);
        map.put(ToolName.of("pathfinder_stop"), IntentKind.STOP);
        map.put(BREAK_BLOCK_AT, IntentKind.BREAK_BLOCK);
        map.put(ToolName.of("dig"), IntentKind.BREAK_BLOCK);
        map.put(PLACE_BLOCK_AT, IntentKind.PLACE_BLOCK);
        map.put(ToolName.of("place_block"), IntentKind.PLACE_BLOCK);
        map.put(INVENTORY_QUERY, IntentKind.INVENTORY_QUERY);
        map.put(ToolName.of("inventory"), IntentKind.INVENTORY_QUERY);
        map.put(EQUIP_ITEM, IntentKind.EQUIP_ITEM);
        map.put(ToolName.of("equip"), IntentKind.EQUIP_ITEM);
        map.put(DROP_ITEM, IntentKind.DROP_ITEM);
        map.put(ToolName.of("toss"), IntentKind.DROP_ITEM);
        map.put(ToolName.of("activate_block"), IntentKind.INTERACT);
        map.put(ToolName.of("activate_entity"), IntentKind.INTERACT);
        map.put(ToolName.of("use_on_entity"), IntentKind.INTERACT);
        map.put(ATTACK_NEAREST, IntentKind.ATTACK_NEAREST);
        map.put(ATTACK_TARGET, IntentKind.ATTACK_TARGET);
        map.put(ToolName.of("attack"), IntentKind.ATTACK_TARGET);
        map.put(ToolName.of("pvp_stop"), IntentKind.STOP);
        map.put(ToolName.of("pvp_force_stop"), IntentKind.STOP);
        map.put(REPORT_STATUS, IntentKind.REPORT_STATUS);
        map.put(STOP, IntentKind.STOP);
        map.put(PAUSE, IntentKind.PAUSE);
        map.put(UNPAUSE, IntentKind.UNPAUSE);
        map.put(CRAFT, IntentKind.CRAFT);
        return Map.copyOf(map);
    }

    private static Map<IntentKind, ToolName> intentToTool() {
        LinkedHashMap<IntentKind, ToolName> map = new LinkedHashMap<>();
        TOOL_TO_INTENT.forEach((toolName, intentKind) -> {
            if (intentKind != IntentKind.INTERACT) {
                map.putIfAbsent(intentKind, toolName);
            }
        });
        map.put(IntentKind.GOTO, MOVE_TO);
        map.put(IntentKind.LOOK, LOOK_AT);
        map.put(IntentKind.BREAK_BLOCK, BREAK_BLOCK_AT);
        map.put(IntentKind.PLACE_BLOCK, PLACE_BLOCK_AT);
        map.put(IntentKind.INVENTORY_QUERY, INVENTORY_QUERY);
        map.put(IntentKind.EQUIP_ITEM, EQUIP_ITEM);
        map.put(IntentKind.DROP_ITEM, DROP_ITEM);
        map.put(IntentKind.ATTACK_TARGET, ATTACK_TARGET);
        map.put(IntentKind.MOVE, MOVE_TO);
        map.put(IntentKind.CRAFT, CRAFT);
        return Map.copyOf(map);
    }

    private static boolean hasCoordinates(Map<String, String> values) {
        return values.containsKey("x") && values.containsKey("y") && values.containsKey("z");
    }

    private static Optional<ToolResult> validateSchema(ToolCall call, ToolSchema schema) {
        Optional<ToolResult> unknownArgumentValidation = validateKnownArguments(call, schema);
        if (unknownArgumentValidation.isPresent()) {
            return unknownArgumentValidation;
        }
        for (ToolParameter parameter : schema.parameters()) {
            String value = call.arguments().values().get(parameter.name());
            if ((value == null || value.isBlank()) && parameter.required()) {
                if (!call.arguments().instruction().isBlank()) {
                    continue;
                }
                return Optional.of(ToolResult.rejected("Missing required argument: " + parameter.name()));
            }
            if (value != null && !value.isBlank()) {
                Optional<ToolResult> typeValidation = validateType(parameter, value);
                if (typeValidation.isPresent()) {
                    return typeValidation;
                }
            }
        }
        Optional<ToolResult> boundedValidation = validateBounds(call, schema);
        if (boundedValidation.isPresent()) {
            return boundedValidation;
        }
        return Optional.empty();
    }

    private static Optional<ToolResult> validateKnownArguments(ToolCall call, ToolSchema schema) {
        for (String argumentName : call.arguments().values().keySet()) {
            if (argumentName.equals("instruction")) {
                continue;
            }
            boolean known = schema.parameters().stream()
                    .anyMatch(parameter -> parameter.name().equals(argumentName));
            if (!known) {
                return Optional.of(ToolResult.rejected(
                        "Unknown argument for " + call.name().value() + ": " + argumentName
                ));
            }
        }
        return Optional.empty();
    }

    private static Optional<ToolResult> validateType(ToolParameter parameter, String value) {
        try {
            if (parameter.type().equals("integer")) {
                Integer.parseInt(value);
            } else if (parameter.type().equals("number")) {
                Double.parseDouble(value);
            } else if (parameter.type().equals("boolean") && !value.equals("true") && !value.equals("false")) {
                return Optional.of(ToolResult.rejected("Argument must be boolean: " + parameter.name()));
            }
        } catch (NumberFormatException exception) {
            return Optional.of(ToolResult.rejected("Argument has invalid " + parameter.type() + " value: " + parameter.name()));
        }
        return Optional.empty();
    }

    private static Optional<ToolResult> validateToolSpecificArguments(ToolCall call) {
        if (call.name().value().equals("observe_area")) {
            Map<String, String> values = call.arguments().values();
            if ((values.containsKey("x") || values.containsKey("y") || values.containsKey("z")) && !hasCoordinates(values)) {
                return Optional.of(ToolResult.rejected("observe_area coordinates require x y z together"));
            }
            return Optional.empty();
        }
        if (!call.name().equals(PICKUP_ITEMS_NEARBY)) {
            return Optional.empty();
        }
        Map<String, String> values = call.arguments().values();
        String matching = values.get("matching");
        String maxDistance = values.get("maxDistance");
        if ((matching == null || matching.isBlank()) && maxDistance != null && !maxDistance.isBlank()) {
            return Optional.of(ToolResult.rejected("pickup_items_nearby maxDistance requires matching"));
        }
        return Optional.empty();
    }

    private static Optional<ToolResult> validateBounds(ToolCall call, ToolSchema schema) {
        Optional<ToolParameter> maxDistanceParameter = schema.parameters().stream()
                .filter(parameter -> parameter.name().equals("maxDistance"))
                .findFirst();
        if (maxDistanceParameter.isPresent() && maxDistanceParameter.get().type().equals("number")) {
            Optional<ToolResult> maxDistanceValidation = validateNumberMaxDistance(call);
            if (maxDistanceValidation.isPresent()) {
                return maxDistanceValidation;
            }
        } else {
            Optional<Integer> maxDistance = integerArgument(call, "maxDistance");
            if (maxDistance.isPresent() && (maxDistance.get() < 1 || maxDistance.get() > 256)) {
                return Optional.of(ToolResult.rejected("maxDistance must be between 1 and 256"));
            }
        }
        Optional<Integer> count = integerArgument(call, "count");
        if (count.isPresent() && (count.get() < 1 || count.get() > 256)) {
            return Optional.of(ToolResult.rejected("count must be between 1 and 256"));
        }
        Optional<Integer> ticks = integerArgument(call, "ticks");
        if (ticks.isPresent() && (ticks.get() < 1 || ticks.get() > 1200)) {
            return Optional.of(ToolResult.rejected("ticks must be between 1 and 1200"));
        }
        Optional<Integer> timeoutTicks = integerArgument(call, "timeoutTicks");
        if (timeoutTicks.isPresent() && (timeoutTicks.get() < 0 || timeoutTicks.get() > 1200)) {
            return Optional.of(ToolResult.rejected("timeoutTicks must be between 0 and 1200"));
        }
        Optional<Integer> radius = integerArgument(call, "radius");
        if (radius.isPresent() && (radius.get() < 1 || radius.get() > WorldPerceptionClassifier.MAX_SCAN_RADIUS)) {
            return Optional.of(ToolResult.rejected("radius must be between 1 and " + WorldPerceptionClassifier.MAX_SCAN_RADIUS));
        }
        Optional<Integer> slot = integerArgument(call, "slot");
        if (slot.isPresent() && call.name().value().equals("set_quick_bar_slot") && (slot.get() < 0 || slot.get() > 8)) {
            return Optional.of(ToolResult.rejected("slot must be between 0 and 8"));
        }
        if (slot.isPresent() && (slot.get() < 0 || slot.get() > 255)) {
            return Optional.of(ToolResult.rejected("slot must be between 0 and 255"));
        }
        Optional<Integer> sourceSlot = integerArgument(call, "sourceSlot");
        if (sourceSlot.isPresent() && (sourceSlot.get() < 0 || sourceSlot.get() > 35)) {
            return Optional.of(ToolResult.rejected("sourceSlot must be between 0 and 35"));
        }
        Optional<Integer> destinationSlot = integerArgument(call, "destSlot");
        if (destinationSlot.isPresent() && (destinationSlot.get() < 0 || destinationSlot.get() > 35)) {
            return Optional.of(ToolResult.rejected("destSlot must be between 0 and 35"));
        }
        return Optional.empty();
    }

    private static Optional<ToolResult> validateNestedArguments(ToolCall call) {
        if (!call.name().value().equals("transfer")) {
            return Optional.empty();
        }
        String options = call.arguments().values().get("options");
        String direction = AICoreJsonFields.stringField(options, "direction");
        String itemType = AICoreJsonFields.stringField(options, "itemType");
        String countValue = AICoreJsonFields.integerField(options, "count");
        if (direction.isBlank() || itemType.isBlank() || countValue.isBlank()) {
            return Optional.of(ToolResult.rejected("transfer_options_require_direction_itemType_count"));
        }
        if (!direction.equals("deposit") && !direction.equals("to_window")
                && !direction.equals("withdraw") && !direction.equals("from_window")) {
            return Optional.of(ToolResult.rejected("unsupported_transfer_direction"));
        }
        try {
            int count = Integer.parseInt(countValue);
            if (count < 1 || count > 256) {
                return Optional.of(ToolResult.rejected("count must be between 1 and 256"));
            }
        } catch (NumberFormatException exception) {
            return Optional.of(ToolResult.rejected("Argument has invalid integer value: count"));
        }
        return Optional.empty();
    }

    private static Optional<ToolResult> validateNumberMaxDistance(ToolCall call) {
        String value = call.arguments().values().get("maxDistance");
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        double maxDistance = Double.parseDouble(value);
        if (!Double.isFinite(maxDistance) || maxDistance <= 0.0D || maxDistance > 256.0D) {
            return Optional.of(ToolResult.rejected("maxDistance must be finite and greater than 0 and at most 256"));
        }
        return Optional.empty();
    }

    private static Optional<Integer> integerArgument(ToolCall call, String name) {
        String value = call.arguments().values().get(name);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(value));
    }
}
