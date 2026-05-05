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


abstract class AICoreNpcPathControlTools extends AICoreNpcContainerTools {
    protected AICoreNpcPathControlTools(OpenPlayerNpcEntity entity, AICoreEventBus eventBus, Function<CommandIntent, CommandSubmissionResult> commandSubmitter) {
        super(entity, eventBus, commandSubmitter);
    }

protected ToolResult setControlState(ToolCall call) {
    Map<String, String> values = call.arguments().values();
    String control = values.get("control");
    boolean state = Boolean.parseBoolean(values.get("state"));
    if (!entity.setAICoreControlState(control, state)) {
        return ToolResult.rejected("unsupported_control_state");
    }
    return ToolResult.success("set_control_state accepted", Map.of("control", control, "state", Boolean.toString(state), "appliesMotion", "false"));
}

protected ToolResult getControlState(ToolCall call) {
    String control = call.arguments().values().get("control");
    if (!entity.setAICoreControlState(control, entity.aicoreControlState(control))) {
        return ToolResult.rejected("unsupported_control_state");
    }
    return ToolResult.success("get_control_state accepted", Map.of("control", control, "state", Boolean.toString(entity.aicoreControlState(control))));
}

protected ToolResult pathfinderDiagnostics(ToolCall call) {
    String instruction = call.name().value().equals("pathfinder_get_path_from_to")
            ? goalInstruction(call.arguments().values().get("start")) + " -> " + goalInstruction(call.arguments().values().get("goal"))
            : goalInstruction(call.arguments().values().get("goal"));
    if (instruction.isBlank() || instruction.contains("unsupported")) {
        return ToolResult.rejected("unsupported_goal_shape");
    }
    ServerLevel level = serverLevel();
    if (level == null) {
        return ToolResult.failed("server_level_unavailable");
    }
    BlockPos start = call.name().value().equals("pathfinder_get_path_from_to")
            ? blockPosFromGoal(call.arguments().values().get("start"))
            : entity.blockPosition();
    BlockPos goal = blockPosFromGoal(call.arguments().values().get("goal"));
    if (start == null || goal == null) {
        return ToolResult.rejected("unsupported_goal_shape");
    }
    boolean loaded = level.hasChunkAt(start) && level.hasChunkAt(goal);
    double distance = Math.sqrt(start.distSqr(goal));
    boolean withinLoadedAreaBounds = loaded && distance <= 256.0D;
    return ToolResult.success("pathfinder loaded-area diagnostic", Map.of(
            "diagnostic", "loaded_area_goal_bounds",
            "startChunkLoaded", Boolean.toString(level.hasChunkAt(start)),
            "goalChunkLoaded", Boolean.toString(level.hasChunkAt(goal)),
            "withinLoadedAreaBounds", Boolean.toString(withinLoadedAreaBounds),
            "distance", Double.toString(distance),
            "nodeListExposed", "false"
    ));
}

protected ToolResult pathfinderSetGoal(ToolCall call) {
    if (Boolean.parseBoolean(call.arguments().values().getOrDefault("dynamic", "false"))) {
        return ToolResult.failed("unsupported_missing_dynamic_goal_tick_adapter");
    }
    Optional<CommandIntent> commandIntent = MinecraftPrimitiveTools.toCommandIntent(
            ToolCall.of("pathfinder_goto", new ToolArguments(Map.of("goal", call.arguments().values().get("goal")))),
            IntentPriority.NORMAL
    );
    if (commandIntent.isEmpty()) {
        return ToolResult.rejected("unsupported_goal_shape");
    }
    CommandSubmissionResult submission = commandSubmitter.apply(commandIntent.get());
    return submission.status() == CommandSubmissionStatus.ACCEPTED
            ? ToolResult.success(submission.message())
            : ToolResult.rejected(submission.message());
}

protected ToolResult look(ToolCall call) {
    Map<String, String> values = call.arguments().values();
    float yaw = (float) Double.parseDouble(values.get("yaw"));
    float pitch = (float) Double.parseDouble(values.get("pitch"));
    entity.setYRot(yaw);
    entity.setXRot(Math.max(-90.0F, Math.min(90.0F, pitch)));
    entity.setYHeadRot(yaw);
    return ToolResult.success("look accepted");
}

protected ToolResult setQuickBarSlot(ToolCall call) {
    int slot = Integer.parseInt(call.arguments().values().get("slot"));
    return entity.selectHotbarSlot(slot) ? ToolResult.success("set_quick_bar_slot accepted") : ToolResult.rejected("slot must be between 0 and 8");
}
}
