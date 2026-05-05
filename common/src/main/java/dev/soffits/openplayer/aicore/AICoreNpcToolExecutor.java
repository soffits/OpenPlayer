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


public final class AICoreNpcToolExecutor extends AICoreNpcPathControlTools {
public AICoreNpcToolExecutor(OpenPlayerNpcEntity entity, AICoreEventBus eventBus) {
    this(entity, eventBus, intent -> entity.submitRuntimeCommand(new AiPlayerNpcCommand(UUID.randomUUID(), intent)));
}

public AICoreNpcToolExecutor(OpenPlayerNpcEntity entity, AICoreEventBus eventBus, Function<CommandIntent, CommandSubmissionResult> commandSubmitter) {
    super(entity, eventBus, commandSubmitter);
}

@Override
public ToolResult execute(ToolCall call, ToolValidationContext context) {
    ToolResult validation = MinecraftPrimitiveTools.validate(call, context);
    if (validation.status() != ToolResultStatus.SUCCESS) {
        publish(EventType.ERROR, call, validation.reason());
        return validation;
    }
    ToolResult result = executeValidated(call, context);
    publish(result.status() == ToolResultStatus.SUCCESS ? EventType.MESSAGE : EventType.ERROR, call,
            result.status() == ToolResultStatus.SUCCESS ? result.summary() : result.reason());
    return result;
}

protected ToolResult executeValidated(ToolCall call, ToolValidationContext context) {
    String tool = call.name().value();
    if (tool.equals("block_at")) {
        return blockAt(call);
    }
    if (tool.equals("block_at_cursor") || tool.equals("block_in_sight")) {
        return blockAtCursor(call);
    }
    if (tool.equals("entity_at_cursor")) {
        return entityAtCursor(call);
    }
    if (tool.equals("block_at_entity_cursor")) {
        return blockAtEntityCursor(call);
    }
    if (tool.equals("can_see_block")) {
        return canSeeBlock(call);
    }
    if (tool.equals("set_control_state")) {
        return setControlState(call);
    }
    if (tool.equals("get_control_state")) {
        return getControlState(call);
    }
    if (tool.equals("clear_control_states")) {
        entity.clearAICoreControlStates();
        return ToolResult.success("clear_control_states accepted");
    }
    if (tool.equals("wait_for_ticks")) {
        return ToolResult.success("wait_for_ticks accepted", Map.of("ticks", call.arguments().values().get("ticks")));
    }
    if (tool.equals("can_dig_block")) {
        return canDigBlock(call);
    }
    if (tool.equals("dig_time")) {
        return digTime(call);
    }
    if (tool.equals("stop_digging")) {
        entity.stopUsingItem();
        return ToolResult.success("stop_digging accepted", Map.of("activeDigging", "false"));
    }
    if (tool.equals("unequip")) {
        return unequip(call);
    }
    if (tool.equals("recipes_for") || tool.equals("recipes_all")) {
        return recipes(call);
    }
    if (tool.equals("craft")) {
        return craft(call);
    }
    if (tool.equals("pathfinder_get_path_to") || tool.equals("pathfinder_get_path_from_to")) {
        return pathfinderDiagnostics(call);
    }
    if (tool.equals("pathfinder_set_goal")) {
        return pathfinderSetGoal(call);
    }
    if (tool.equals("open_block") || tool.equals("open_container")) {
        return openContainer(call);
    }
    if (tool.equals("window_deposit")) {
        return windowTransfer(call, true);
    }
    if (tool.equals("window_withdraw")) {
        return windowTransfer(call, false);
    }
    if (tool.equals("window_close") || tool.equals("close_window")) {
        entity.aicoreSessionState().clear();
        return ToolResult.success("window_close accepted");
    }
    if (tool.equals("move_slot_item")) {
        return moveSlotItem(call);
    }
    if (tool.equals("transfer")) {
        return transfer(call);
    }
    if (tool.equals("look")) {
        return look(call);
    }
    if (tool.equals("set_quick_bar_slot")) {
        return setQuickBarSlot(call);
    }
    if (tool.equals("toss_stack")) {
        return entity.dropSelectedHotbarStack() ? ToolResult.success("toss_stack accepted") : ToolResult.rejected("toss_stack requires a selected hotbar stack");
    }
    if (tool.equals("swing_arm")) {
        entity.swingMainHandAction();
        return ToolResult.success("swing_arm accepted");
    }
    if (tool.equals("observe_area") || tool.equals("detect_nearby_structures") || tool.equals("find_workstations")) {
        return observeArea(call);
    }
    if (tool.equals("find_safe_stand_near")) {
        return findSafeStandNear(call);
    }
    Optional<CommandIntent> commandIntent = MinecraftPrimitiveTools.toCommandIntent(call, IntentPriority.NORMAL);
    if (commandIntent.isPresent()) {
        CommandSubmissionResult submission = commandSubmitter.apply(commandIntent.get());
        return submission.status() == CommandSubmissionStatus.ACCEPTED
                ? ToolResult.success(submission.message())
                : ToolResult.rejected(submission.message());
    }
    return ToolResult.rejected("Tool is not mapped to an NPC runtime adapter: " + tool);
}

protected ToolResult unequip(ToolCall call) {
    EquipmentSlot slot = equipmentSlot(call.arguments().values().get("destination"));
    if (slot == null) {
        return ToolResult.rejected("unsupported_equipment_destination");
    }
    return entity.unequipToNormalInventory(slot)
            ? ToolResult.success("unequip accepted")
            : ToolResult.rejected("unequip requires equipped item and normal inventory space");
}
}
