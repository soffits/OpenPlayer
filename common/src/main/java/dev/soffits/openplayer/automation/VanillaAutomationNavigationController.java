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

abstract class VanillaAutomationNavigationController extends VanillaAutomationControllerBase {
    protected VanillaAutomationNavigationController(OpenPlayerNpcEntity entity) {
        super(entity);
    }

protected abstract LivingEntity nearestSelfDefenseTarget(ServerLevel serverLevel, double radius);

protected void monitorIdleSurvival() {
    if (!entity.allowWorldActions() || !survivalCooldown.ready() || activeCommand != null || !queuedCommands.isEmpty()) {
        return;
    }
    ServerLevel serverLevel = serverLevel();
    if (serverLevel == null || !entity.isAlive()) {
        return;
    }
    SurvivalDangerKind dangerKind = SurvivalDangerPolicy.immediateDanger(
            entity.isOnFire(), entity.isInLava(), immediateProjectileDanger(serverLevel) != null
    );
    if (dangerKind != SurvivalDangerKind.NONE) {
        if (tryMoveToSafeAdjacentBlock(serverLevel, dangerKind)) {
            noteIdleSurvivalAction("survival:avoid_" + dangerKind.name().toLowerCase());
            return;
        }
        noteIdleSurvivalDiagnostic("survival:unable_to_avoid_" + dangerKind.name().toLowerCase());
        return;
    }
    if (SurvivalHealthPolicy.isDangerouslyLowHealth(entity.getHealth(), entity.getMaxHealth())) {
        noteIdleSurvivalDiagnostic("survival:dangerously_low_health_manual_item_use_required");
        return;
    }
    if (nearestSelfDefenseTarget(serverLevel, SURVIVAL_DANGER_RADIUS) != null) {
        queuedCommands.add(QueuedCommand.selfDefense(SURVIVAL_DANGER_RADIUS));
        noteIdleSurvivalAction("survival:queued_self_defense");
        return;
    }
}

protected void noteIdleSurvivalAction(String reason) {
    idleSurvivalReason = reason;
    survivalCooldown.backoffAfterAction();
    OpenPlayerDebugEvents.record("automation", "survival", null, null, null, reason);
    OpenPlayerRawTrace.automationOperation("survival", "IDLE", "reason=" + reason);
}

protected void noteIdleSurvivalDiagnostic(String reason) {
    idleSurvivalReason = reason;
    survivalCooldown.backoffAfterDiagnostic();
    OpenPlayerDebugEvents.record("automation", "survival", null, null, null, reason);
    OpenPlayerRawTrace.automationOperation("survival", "IDLE", "reason=" + reason);
}

private Projectile immediateProjectileDanger(ServerLevel serverLevel) {
    List<Projectile> projectiles = serverLevel.getEntitiesOfClass(
            Projectile.class,
            entity.getBoundingBox().inflate(SURVIVAL_PROJECTILE_RADIUS),
            projectile -> projectile.isAlive()
                    && entity.distanceToSqr(projectile) <= SURVIVAL_PROJECTILE_RADIUS * SURVIVAL_PROJECTILE_RADIUS
                    && isBlockLoaded(projectile.blockPosition())
                    && entity.hasLineOfSight(projectile)
    );
    return projectiles.stream()
            .min(Comparator.comparingDouble(projectile -> entity.distanceToSqr(projectile)))
            .orElse(null);
}

protected boolean tryMoveToSafeAdjacentBlock(ServerLevel serverLevel, SurvivalDangerKind dangerKind) {
    BlockPos origin = entity.blockPosition();
    for (Direction direction : Direction.Plane.HORIZONTAL) {
        BlockPos candidate = origin.relative(direction);
        if (!isSafeAdjacentTarget(serverLevel, candidate)) {
            continue;
        }
        navigationRuntime.plan(
                entity.tickCount,
                NavigationTarget.block(candidate.getX(), candidate.getY(), candidate.getZ()),
                distanceTo(candidate),
                true
        );
        boolean accepted = entity.getNavigation().moveTo(
                candidate.getX() + 0.5D,
                candidate.getY(),
                candidate.getZ() + 0.5D,
                navigationSpeed(distanceTo(candidate), false, true, false)
        );
        if (accepted) {
            navigationRuntime.markReachable(true);
            return true;
        }
    }
    navigationRuntime.fail("unable_to_avoid_" + dangerKind.name().toLowerCase());
    return false;
}

protected boolean isSafeAdjacentTarget(ServerLevel serverLevel, BlockPos candidate) {
    if (!serverLevel.hasChunkAt(candidate)) {
        return false;
    }
    BlockPos feet = candidate;
    BlockPos head = candidate.above();
    BlockPos below = candidate.below();
    BlockState feetState = serverLevel.getBlockState(feet);
    BlockState headState = serverLevel.getBlockState(head);
    BlockState belowState = serverLevel.getBlockState(below);
    if (!feetState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty()) {
        return false;
    }
    if (feetState.is(Blocks.FIRE) || feetState.is(Blocks.LAVA) || headState.is(Blocks.FIRE) || headState.is(Blocks.LAVA)) {
        return false;
    }
    if (belowState.isAir() || belowState.getFluidState().isSource()) {
        return false;
    }
    return serverLevel.noCollision(entity, entity.getBoundingBox().move(Vec3.atCenterOf(candidate).subtract(entity.position())));
}

protected void startGoto(QueuedCommand command) {
    moveToPosition(command.coordinate());
}

protected void continueGoto(QueuedCommand command) {
    navigationRuntime.updateDistance(distanceTo(command.coordinate()));
    if (entity.getNavigation().isDone()) {
        completeActiveCommand();
    }
}

protected boolean moveToPosition(Coordinate coordinate) {
    boolean loaded = isCoordinateLoaded(coordinate);
    double distanceSquared = distanceTo(coordinate);
    if (shouldKeepCurrentNavigation(distanceSquared)) {
        navigationRuntime.updateDistance(distanceSquared);
        return true;
    }
    navigationRuntime.plan(entity.tickCount, NavigationTarget.position(coordinate.x(), coordinate.y(), coordinate.z()),
            distanceSquared, loaded);
    if (!loaded) {
        failActiveCommand("navigation_target_unloaded");
        return false;
    }
    boolean accepted = entity.getNavigation().moveTo(
            coordinate.x(),
            coordinate.y(),
            coordinate.z(),
            navigationSpeed(distanceTo(coordinate), false, false, false)
    );
    if (!accepted) {
        failActiveCommand("navigation_position_rejected");
        return false;
    }
    navigationRuntime.markReachable(true);
    return true;
}

protected boolean moveToBlock(BlockPos blockPos) {
    boolean loaded = isBlockLoaded(blockPos);
    double distanceSquared = distanceTo(blockPos);
    if (shouldKeepCurrentNavigation(distanceSquared)) {
        navigationRuntime.updateDistance(distanceSquared);
        return true;
    }
    navigationRuntime.plan(entity.tickCount, NavigationTarget.block(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
            distanceSquared, loaded);
    if (!loaded) {
        failActiveCommand("navigation_target_unloaded");
        return false;
    }
    boolean accepted = entity.getNavigation().moveTo(
            blockPos.getX() + 0.5D,
            blockPos.getY(),
            blockPos.getZ() + 0.5D,
            navigationSpeed(distanceTo(blockPos), false, false, true)
    );
    if (!accepted) {
        failActiveCommand("navigation_block_rejected");
        return false;
    }
    navigationRuntime.markReachable(true);
    return true;
}

protected boolean moveToDroppedItem(ItemEntity itemEntity) {
    boolean loaded = isBlockLoaded(itemEntity.blockPosition());
    if (!loaded) {
        navigationRuntime.plan(entity.tickCount, NavigationTarget.position(
                itemEntity.getX(), itemEntity.getY(), itemEntity.getZ()
        ), entity.distanceToSqr(itemEntity), false);
        failActiveCommand("navigation_target_unloaded");
        return false;
    }
    BlockPos standPosition = reachableDroppedItemStandPosition(itemEntity);
    if (standPosition == null) {
        navigationRuntime.plan(entity.tickCount, NavigationTarget.position(
                itemEntity.getX(), itemEntity.getY(), itemEntity.getZ()
        ), entity.distanceToSqr(itemEntity), true);
        failActiveCommand("item_no_reachable_pickup_position");
        return false;
    }
    double distanceSquared = distanceTo(standPosition);
    if (shouldKeepCurrentNavigation(distanceSquared)) {
        navigationRuntime.updateDistance(distanceSquared);
        return true;
    }
    navigationRuntime.plan(entity.tickCount, droppedItemNavigationTarget(itemId(itemEntity.getItem()), standPosition),
            distanceSquared, true);
    boolean accepted = entity.getNavigation().moveTo(
            standPosition.getX() + 0.5D,
            standPosition.getY(),
            standPosition.getZ() + 0.5D,
            navigationSpeed(distanceTo(standPosition), false, false, false)
    );
    if (!accepted) {
        failActiveCommand(DROPPED_ITEM_NAVIGATION_REJECTED_REASON);
        return false;
    }
    navigationRuntime.markReachable(true);
    return true;
}

protected BlockPos reachableDroppedItemStandPosition(ItemEntity itemEntity) {
    BlockPos origin = itemEntity.blockPosition();
    BlockPos current = entity.blockPosition();
    BlockPos best = null;
    double bestDistance = Double.MAX_VALUE;
    for (BlockPos candidate : droppedItemStandCandidates(origin)) {
        if (!isSafeAdjacentTarget(serverLevel(), candidate)) {
            continue;
        }
        double pickupDistance = Vec3.atCenterOf(candidate).distanceToSqr(itemEntity.position());
        if (pickupDistance > (COLLECT_REACH_DISTANCE + 0.75D) * (COLLECT_REACH_DISTANCE + 0.75D)) {
            continue;
        }
        double candidateDistance = candidate.distSqr(current);
        if (candidateDistance < bestDistance) {
            bestDistance = candidateDistance;
            best = candidate.immutable();
        }
    }
    return best;
}

protected static List<BlockPos> droppedItemStandCandidates(BlockPos origin) {
    List<BlockPos> candidates = new ArrayList<>();
    candidates.add(origin);
    for (Direction direction : Direction.Plane.HORIZONTAL) {
        candidates.add(origin.relative(direction));
    }
    candidates.add(origin.below());
    candidates.add(origin.above());
    return candidates;
}

protected boolean moveToEntity(Entity target, NavigationTarget navigationTarget) {
    boolean loaded = isBlockLoaded(target.blockPosition());
    double distanceSquared = entity.distanceToSqr(target);
    if (shouldKeepCurrentNavigation(distanceSquared)) {
        navigationRuntime.updateDistance(distanceSquared);
        return true;
    }
    navigationRuntime.plan(entity.tickCount, navigationTarget, distanceSquared, loaded);
    if (!loaded) {
        failActiveCommand("navigation_target_unloaded");
        return false;
    }
    boolean accepted = entity.getNavigation().moveTo(
            target,
            navigationSpeed(entity.distanceToSqr(target), true, false, false)
    );
    if (!accepted) {
        failActiveCommand("navigation_entity_rejected");
        return false;
    }
    navigationRuntime.markReachable(true);
    return true;
}

protected void stopNavigation() {
    entity.getNavigation().stop();
    navigationRuntime.complete();
}

protected void suspendNavigation() {
    entity.getNavigation().stop();
    navigationRuntime.suspend();
}

protected void cancelNavigation(String reason) {
    entity.getNavigation().stop();
    navigationRuntime.fail(reason);
}

protected void resetAndStopNavigation() {
    entity.getNavigation().stop();
    navigationRuntime.reset();
}

protected double distanceTo(Coordinate coordinate) {
    return entity.distanceToSqr(coordinate.x(), coordinate.y(), coordinate.z());
}

protected double distanceTo(BlockPos blockPos) {
    return entity.distanceToSqr(Vec3.atCenterOf(blockPos));
}

protected boolean isBlockLoaded(BlockPos blockPos) {
    ServerLevel serverLevel = serverLevel();
    return serverLevel != null && serverLevel.hasChunkAt(blockPos);
}

protected static String entityTypeId(Entity target) {
    return BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
}

protected static String itemId(ItemStack stack) {
    return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
}

protected boolean isCoordinateLoaded(Coordinate coordinate) {
    ServerLevel serverLevel = serverLevel();
    return serverLevel != null
            && serverLevel.hasChunkAt(BlockPos.containing(coordinate.x(), coordinate.y(), coordinate.z()));
}

private double navigationSpeed(double distanceSquared, boolean movingTarget,
                               boolean requiresSafety, boolean requiresPrecision) {
    double distanceBlocks = Math.sqrt(Math.max(0.0D, distanceSquared));
    navigationRuntime.updateSpeedContext(
            distanceBlocks,
            movingTarget,
            requiresSafety,
            requiresPrecision,
            movementProfile
    );
    return navigationRuntime.lastSpeedContext().speed();
}

private boolean shouldKeepCurrentNavigation(double distanceSquared) {
    return !entity.getNavigation().isDone()
            && !navigationRuntime.shouldReplan(entity.tickCount, Math.max(0.0D, distanceSquared));
}

protected ServerPlayer owner() {
    if (ownerId == null || !(entity.level() instanceof ServerLevel serverLevel)) {
        return null;
    }
    ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(ownerId.value());
    if (player == null || !player.level().dimension().equals(serverLevel.dimension())) {
        return null;
    }
    return player;
}

protected ServerLevel serverLevel() {
    if (entity.level() instanceof ServerLevel serverLevel) {
        return serverLevel;
    }
    return null;
}

}
