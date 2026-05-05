package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.aicore.AICoreEventBus;
import dev.soffits.openplayer.aicore.AICoreNpcToolExecutor;
import dev.soffits.openplayer.aicore.MinecraftPrimitiveTools;
import dev.soffits.openplayer.aicore.ToolCall;
import dev.soffits.openplayer.aicore.ToolResult;
import dev.soffits.openplayer.aicore.ToolResultStatus;
import dev.soffits.openplayer.aicore.ToolValidationContext;
import dev.soffits.openplayer.automation.AutomationInstructionParser.Coordinate;
import dev.soffits.openplayer.automation.CollectItemsInstructionParser.CollectItemsInstruction;
import dev.soffits.openplayer.automation.InventoryActionInstructionParser.ParsedItemInstruction;
import dev.soffits.openplayer.automation.InteractionInstruction.InteractionTargetKind;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskInstructionParser;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskInstructionParser.LoadedSearchInstruction;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskPolicy;
import dev.soffits.openplayer.automation.capability.RuntimeCapabilityRegistry;
import dev.soffits.openplayer.automation.navigation.LoadedAreaNavigator;
import dev.soffits.openplayer.automation.navigation.NavigationRuntime;
import dev.soffits.openplayer.automation.navigation.NavigationTarget;
import dev.soffits.openplayer.automation.navigation.NavigationTargetKind;
import dev.soffits.openplayer.automation.policy.MovementPolicyLoader;
import dev.soffits.openplayer.automation.policy.MovementProfile;
import dev.soffits.openplayer.automation.survival.SurvivalCooldownPolicy;
import dev.soffits.openplayer.automation.survival.SurvivalDangerKind;
import dev.soffits.openplayer.automation.survival.SurvivalDangerPolicy;
import dev.soffits.openplayer.automation.survival.SurvivalHealthPolicy;
import dev.soffits.openplayer.automation.survival.SurvivalTargetPolicy;
import dev.soffits.openplayer.automation.work.WorkRepeatPolicy;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.debug.OpenPlayerRawTrace;
import dev.soffits.openplayer.entity.NpcInventoryTransfer;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.ProviderPlanIntentCodec;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentPolicies;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidationResult;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

abstract class VanillaAutomationWorkController extends VanillaAutomationCombatController {
    protected VanillaAutomationWorkController(OpenPlayerNpcEntity entity) {
        super(entity);
    }

protected void collectItems(QueuedCommand command) {
    ServerLevel serverLevel = serverLevel();
    if (serverLevel == null) {
        failActiveCommand("server_level_unavailable");
        return;
    }
    if (!isWithinStartDistance(command, entity.position(), command.radius())) {
        failActiveCommand("outside_collect_radius");
        return;
    }
    ItemEntity itemEntity = nearestItem(serverLevel, command);
    if (itemEntity == null) {
        stopNavigation();
        if (command.trackedItemTarget()) {
            int inventoryDelta = inventoryCount(entity.inventorySnapshot()) - command.startInventoryCount();
            String reason = itemPickupCompletionReason(inventoryDelta, false, command.lastTargetClose(entity.position()), true);
            if (reason.startsWith("picked_up")) {
                completeActiveCommand(reason);
            } else {
                failActiveCommand(reason);
            }
            return;
        }
        completeActiveCommand("completed no_matching_items");
        return;
    }
    command.trackItemTarget(itemEntity, inventoryCount(entity.inventorySnapshot()));
    entity.getLookControl().setLookAt(itemEntity);
    if (entity.distanceToSqr(itemEntity) > COLLECT_REACH_DISTANCE * COLLECT_REACH_DISTANCE) {
        if (!moveToDroppedItem(itemEntity)) {
            return;
        }
        command.resetReachTicks();
        return;
    }
    stopNavigation();
    command.incrementReachTicks();
    if (command.reachTicks() >= COLLECT_REACH_TICKS) {
        int inventoryDelta = inventoryCount(entity.inventorySnapshot()) - command.startInventoryCount();
        boolean targetAlive = itemEntity.isAlive() && !itemEntity.getItem().isEmpty();
        boolean closeEnough = entity.distanceToSqr(itemEntity.position())
                <= (COLLECT_REACH_DISTANCE + 0.75D) * (COLLECT_REACH_DISTANCE + 0.75D);
        boolean inventoryCanAccept = canAcceptItem(entity.inventorySnapshot(), itemEntity.getItem());
        String reason = itemPickupCompletionReason(inventoryDelta, targetAlive, closeEnough, inventoryCanAccept);
        if (reason.startsWith("picked_up")) {
            completeActiveCommand(reason);
        } else {
            failActiveCommand(reason);
        }
    }
}

protected ItemEntity nearestItem(ServerLevel serverLevel, QueuedCommand command) {
    List<ItemEntity> itemEntities = serverLevel.getEntitiesOfClass(
            ItemEntity.class,
            entity.getBoundingBox().inflate(command.radius()),
            itemEntity -> itemEntity.isAlive()
                    && !itemEntity.hasPickUpDelay()
                    && !itemEntity.getItem().isEmpty()
                    && (command.collectItem() == null || itemEntity.getItem().is(command.collectItem()))
                    && entity.hasLineOfSight(itemEntity)
                    && isWithinStartDistance(command, itemEntity.position(), command.radius())
    );
    return itemEntities.stream()
            .min(Comparator.comparingDouble(itemEntity -> entity.distanceToSqr(itemEntity)))
            .orElse(null);
}

protected void breakBlock(QueuedCommand command) {
    ServerLevel serverLevel = serverLevel();
    if (serverLevel == null) {
        failActiveCommand("server_level_unavailable");
        return;
    }
    BlockPos blockPos = command.blockPos();
    if (!canUseBlockTarget(serverLevel, command, blockPos)) {
        failActiveCommand("block_target_unavailable");
        return;
    }
    BlockState blockState = serverLevel.getBlockState(blockPos);
    String brokenBlockId = blockId(blockState.getBlock());
    if (movementProfile.blocks().neverBreak().contains(brokenBlockId)) {
        failActiveCommand("block_policy_never_break=" + brokenBlockId);
        return;
    }
    if (blockState.isAir() || blockState.getDestroySpeed(serverLevel, blockPos) < 0.0F) {
        failActiveCommand("block_not_breakable");
        return;
    }
    entity.selectBestToolFor(blockState, serverLevel, blockPos);
    if (blockState.requiresCorrectToolForDrops() && !entity.getMainHandItem().isCorrectToolForDrops(blockState)) {
        failActiveCommand("missing_required_harvest_tool block=" + brokenBlockId);
        return;
    }
    lookAtBlock(blockPos);
    if (!isWithinInteractionDistance(blockPos)) {
        moveNearBlock(blockPos);
        return;
    }
    stopNavigation();
    List<ItemStack> beforeInventory = entity.inventorySnapshot();
    Map<String, Integer> beforeDrops = nearbyDropCounts(serverLevel, blockPos);
    entity.swingMainHandAction();
    serverLevel.destroyBlock(blockPos, true, entity);
    completeActiveCommand(blockBreakSummary(
            brokenBlockId,
            blockPos,
            inventoryDeltaSummary(beforeInventory, entity.inventorySnapshot()),
            dropDeltaSummary(beforeDrops, nearbyDropCounts(serverLevel, blockPos))
    ));
}

protected void placeBlock(QueuedCommand command) {
    ServerLevel serverLevel = serverLevel();
    if (serverLevel == null) {
        failActiveCommand("server_level_unavailable");
        return;
    }
    BlockPos blockPos = command.blockPos();
    if (!canUseBlockTarget(serverLevel, command, blockPos)) {
        failActiveCommand("block_target_unavailable");
        return;
    }
    ItemStack mainHandStack = entity.getMainHandItem();
    if (!(mainHandStack.getItem() instanceof BlockItem blockItem)) {
        failActiveCommand("missing_block_item");
        return;
    }
    if (!serverLevel.getBlockState(blockPos).isAir()) {
        failActiveCommand("block_target_occupied");
        return;
    }
    lookAtBlock(blockPos);
    if (!isWithinInteractionDistance(blockPos)) {
        moveNearBlock(blockPos);
        return;
    }
    BlockState placedState = blockItem.getBlock().defaultBlockState();
    if (!placedState.canSurvive(serverLevel, blockPos)
            || !serverLevel.isUnobstructed(placedState, blockPos, CollisionContext.empty())) {
        failActiveCommand("block_cannot_survive");
        return;
    }
    stopNavigation();
    if (serverLevel.setBlock(blockPos, placedState, Block.UPDATE_ALL)) {
        mainHandStack.shrink(1);
        entity.swingMainHandAction();
    }
    completeActiveCommand();
}
}
