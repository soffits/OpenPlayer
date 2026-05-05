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


abstract class AICoreNpcContainerTools extends AICoreNpcCraftingTools {
    protected AICoreNpcContainerTools(OpenPlayerNpcEntity entity, AICoreEventBus eventBus, Function<CommandIntent, CommandSubmissionResult> commandSubmitter) {
        super(entity, eventBus, commandSubmitter);
    }

protected ToolResult openContainer(ToolCall call) {
    ServerLevel level = serverLevel();
    if (level == null) {
        return ToolResult.failed("server_level_unavailable");
    }
    BlockPos pos = blockPosFromValuesOrTarget(call.arguments().values());
    if (pos == null) {
        return ToolResult.rejected("target_coordinates_required");
    }
    Container container = containerAt(level, pos);
    if (container == null) {
        return ToolResult.failed("unsupported_missing_loaded_container_block_entity");
    }
    entity.aicoreSessionState().openContainer(pos, container instanceof WorldlyContainer);
    return ToolResult.success("container session opened", Map.of("x", Integer.toString(pos.getX()), "y", Integer.toString(pos.getY()), "z", Integer.toString(pos.getZ()), "slots", Integer.toString(container.getContainerSize())));
}

protected ToolResult windowTransfer(ToolCall call, boolean deposit) {
    ToolResult sessionRejection = genericWindowTransferSessionRejection(entity.aicoreSessionState());
    if (sessionRejection != null) {
        return sessionRejection;
    }
    ServerLevel level = serverLevel();
    Container container = currentContainer(level);
    if (container == null) {
        return ToolResult.failed("no_current_loaded_container_session");
    }
    Item item = item(call.arguments().values().get("itemType"));
    if (item == null) {
        return ToolResult.rejected("unknown_item_type");
    }
    Integer count = boundedCountOrNull(call.arguments().values().get("count"));
    if (count == null) {
        return ToolResult.rejected("count must be between 1 and 256");
    }
    List<ItemStack> snapshot = containerSnapshot(container);
    boolean transferred = deposit
            ? entity.depositInventoryItemTo(snapshot, item, count)
            : entity.withdrawInventoryItemFrom(snapshot, item, count);
    if (!transferred) {
        return ToolResult.rejected(deposit ? "deposit_would_not_fit_or_item_missing" : "withdraw_would_not_fit_or_item_missing");
    }
    restoreContainer(container, snapshot);
    return ToolResult.success(deposit ? "window_deposit accepted" : "window_withdraw accepted", Map.of("itemType", BuiltInRegistries.ITEM.getKey(item).toString(), "count", Integer.toString(count)));
}

protected ToolResult moveSlotItem(ToolCall call) {
    int sourceSlot = Integer.parseInt(call.arguments().values().get("sourceSlot"));
    int destinationSlot = Integer.parseInt(call.arguments().values().get("destSlot"));
    return entity.moveInventorySlotItemNoLoss(sourceSlot, destinationSlot)
            ? ToolResult.success("move_slot_item accepted")
            : ToolResult.rejected("move_slot_item_requires_source_and_full_destination_capacity");
}

protected ToolResult transfer(ToolCall call) {
    String options = call.arguments().values().get("options");
    String direction = AICoreJsonFields.stringField(options, "direction");
    String itemType = AICoreJsonFields.stringField(options, "itemType");
    String count = AICoreJsonFields.numberField(options, "count");
    if (direction.isBlank() || itemType.isBlank() || count.isBlank()) {
        return ToolResult.rejected("transfer_options_require_direction_itemType_count");
    }
    boolean deposit = direction.equals("deposit") || direction.equals("to_window");
    boolean withdraw = direction.equals("withdraw") || direction.equals("from_window");
    if (!deposit && !withdraw) {
        return ToolResult.rejected("unsupported_transfer_direction");
    }
    return windowTransfer(ToolCall.of(deposit ? "window_deposit" : "window_withdraw", new ToolArguments(Map.of("itemType", itemType, "count", count))), deposit);
}

static ToolResult genericWindowTransferSessionRejection(AICoreNpcSessionState sessionState) {
    if (sessionState != null && sessionState.hasSlotRestrictedContainerSession()) {
        return ToolResult.rejected("slot_restricted_container_transfer_unsupported");
    }
    return null;
}

protected Container containerAt(ServerLevel level, BlockPos pos) {
    if (level == null || pos == null || !level.hasChunkAt(pos) || !withinInteractionDistance(pos)) {
        return null;
    }
    BlockEntity blockEntity = level.getBlockEntity(pos);
    return blockEntity instanceof Container container ? container : null;
}

protected Container currentContainer(ServerLevel level) {
    BlockPos pos = entity.aicoreSessionState().containerPos();
    return pos == null ? null : containerAt(level, pos);
}

protected static List<ItemStack> containerSnapshot(Container container) {
    ArrayList<ItemStack> stacks = new ArrayList<>(container.getContainerSize());
    for (int slot = 0; slot < container.getContainerSize(); slot++) {
        stacks.add(container.getItem(slot).copy());
    }
    return stacks;
}

protected static void restoreContainer(Container container, List<ItemStack> snapshot) {
    for (int slot = 0; slot < container.getContainerSize() && slot < snapshot.size(); slot++) {
        container.setItem(slot, snapshot.get(slot).copy());
    }
    container.setChanged();
}

protected static Item item(String itemType) {
    ResourceLocation itemId = ResourceLocation.tryParse(itemType == null ? "" : itemType);
    return itemId != null && BuiltInRegistries.ITEM.containsKey(itemId) ? BuiltInRegistries.ITEM.get(itemId) : null;
}
}
