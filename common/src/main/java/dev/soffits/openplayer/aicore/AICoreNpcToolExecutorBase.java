package dev.soffits.openplayer.aicore;

import dev.soffits.openplayer.automation.CraftInstructionParser;
import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentPriority;
import dev.soffits.openplayer.runtime.perception.ServerWorldPerceptionScanner;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionClassifier;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionFormatter;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.ObjectCluster;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.SafeStandSpot;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;


abstract class AICoreNpcToolExecutorBase implements ToolExecutor {
protected static final double MAX_ENTITY_CURSOR_DISTANCE = 64.0D;

protected final OpenPlayerNpcEntity entity;
protected final AICoreEventBus eventBus;
protected final Function<CommandIntent, CommandSubmissionResult> commandSubmitter;

protected AICoreNpcToolExecutorBase(OpenPlayerNpcEntity entity, AICoreEventBus eventBus) {
    this(entity, eventBus, intent -> entity.submitRuntimeCommand(new AiPlayerNpcCommand(UUID.randomUUID(), intent)));
}

protected AICoreNpcToolExecutorBase(OpenPlayerNpcEntity entity, AICoreEventBus eventBus, Function<CommandIntent, CommandSubmissionResult> commandSubmitter) {
    if (entity == null) {
        throw new IllegalArgumentException("entity cannot be null");
    }
    if (eventBus == null) {
        throw new IllegalArgumentException("eventBus cannot be null");
    }
    if (commandSubmitter == null) {
        throw new IllegalArgumentException("commandSubmitter cannot be null");
    }
    this.entity = entity;
    this.eventBus = eventBus;
    this.commandSubmitter = commandSubmitter;
}

protected boolean withinInteractionDistance(BlockPos pos) {
    return entity.distanceToSqr(Vec3.atCenterOf(pos)) <= 4.0D * 4.0D;
}

protected ServerLevel serverLevel() {
    return entity.level() instanceof ServerLevel serverLevel ? serverLevel : null;
}

protected static BlockPos blockPos(Map<String, String> values) {
    return new BlockPos(Integer.parseInt(values.get("x")), Integer.parseInt(values.get("y")), Integer.parseInt(values.get("z")));
}

protected static BlockPos blockPosFromValuesOrTarget(Map<String, String> values) {
    if (values.containsKey("x") && values.containsKey("y") && values.containsKey("z")) {
        return blockPos(values);
    }
    String target = values.get("target");
    if (target == null || target.isBlank()) {
        return null;
    }
    String x = AICoreJsonFields.numberField(target, "x");
    String y = AICoreJsonFields.numberField(target, "y");
    String z = AICoreJsonFields.numberField(target, "z");
    if (x.isBlank() || y.isBlank() || z.isBlank()) {
        return null;
    }
    return new BlockPos(Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(z));
}

protected static String goalInstruction(String goalJson) {
    if (goalJson == null || goalJson.isBlank()) {
        return "unsupported_empty_goal";
    }
    String type = AICoreJsonFields.stringField(goalJson, "type");
    String x = AICoreJsonFields.numberField(goalJson, "x");
    String y = AICoreJsonFields.numberField(goalJson, "y");
    String z = AICoreJsonFields.numberField(goalJson, "z");
    if ((type.equals("goal_block") || type.equals("goal_near") || type.equals("goal_get_to_block")
            || type.equals("goal_look_at_block") || type.equals("goal_place_block"))
            && !x.isBlank() && !y.isBlank() && !z.isBlank()) {
        return x + " " + y + " " + z;
    }
    if ((type.equals("goal_xz") || type.equals("goal_near_xz")) && !x.isBlank() && !z.isBlank()) {
        return x + " 0 " + z;
    }
    return "unsupported_goal_shape";
}

protected static BlockPos blockPosFromGoal(String goalJson) {
    String instruction = goalInstruction(goalJson);
    if (instruction.isBlank() || instruction.contains("unsupported")) {
        return null;
    }
    String[] parts = instruction.split(" ");
    if (parts.length != 3) {
        return null;
    }
    try {
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    } catch (NumberFormatException exception) {
        return null;
    }
}


protected static double maxDistance(ToolCall call, double defaultValue) {
    Map<String, String> values = call.arguments().values();
    if (values.containsKey("maxDistance")) {
        return Double.parseDouble(values.get("maxDistance"));
    }
    if (values.containsKey("maxSteps") && values.containsKey("vectorLength")) {
        return Double.parseDouble(values.get("maxSteps")) * Double.parseDouble(values.get("vectorLength"));
    }
    return defaultValue;
}

protected static String entityTypeId(Entity entity) {
    return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
}

protected static Entity entityById(ServerLevel level, String entityId) {
    if (entityId == null || entityId.isBlank()) {
        return null;
    }
    try {
        Entity entity = level.getEntity(UUID.fromString(entityId));
        return entity != null && entity.isAlive() ? entity : null;
    } catch (IllegalArgumentException exception) {
        for (Entity candidate : level.getAllEntities()) {
            if (candidate.getStringUUID().equals(entityId) && candidate.isAlive()) {
                return candidate;
            }
        }
        return null;
    }
}

protected static EquipmentSlot equipmentSlot(String destination) {
    if (destination == null) {
        return null;
    }
    return switch (destination) {
        case "head", "helmet" -> EquipmentSlot.HEAD;
        case "torso", "chest", "chestplate" -> EquipmentSlot.CHEST;
        case "legs", "leggings" -> EquipmentSlot.LEGS;
        case "feet", "boots" -> EquipmentSlot.FEET;
        case "off-hand", "offhand" -> EquipmentSlot.OFFHAND;
        default -> null;
    };
}

protected void publish(EventType type, ToolCall call, String message) {
    LinkedHashMap<String, String> payload = new LinkedHashMap<>();
    payload.put("tool", call.name().value());
    payload.put("message", message);
    eventBus.publish(type, payload);
}

protected record DigPreconditions(ServerLevel level, BlockPos pos, BlockState state, String failureReason) {
    static DigPreconditions failed(String reason) {
        return new DigPreconditions(null, null, null, reason);
    }
}

protected record CraftRequest(ResourceLocation recipeId, int count, BlockPos craftingTablePos) {
}
}
