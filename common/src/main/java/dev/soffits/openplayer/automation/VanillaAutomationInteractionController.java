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

abstract class VanillaAutomationInteractionController extends VanillaAutomationContainerController {
    protected VanillaAutomationInteractionController(OpenPlayerNpcEntity entity) {
        super(entity);
    }

protected AutomationCommandResult submitInteract(String instruction) {
    InteractionInstruction interaction = InteractionInstructionParser.parseOrNull(instruction);
    if (interaction == null) {
        return rejected(InteractionInstructionParser.USAGE);
    }
    if (interaction.kind() == InteractionTargetKind.ENTITY) {
        ServerLevel serverLevel = serverLevel();
        if (serverLevel == null) {
            return rejected("INTERACT entity requires a server level");
        }
        Entity target = resolveInteractionTarget(serverLevel, interaction, entity.position());
        if (target == null) {
            return rejected("INTERACT entity found no loaded safe reachable target or adapter: " + interaction.targetId());
        }
        EntityInteractionCapability capability = entityInteractionCapability(target);
        if (capability == EntityInteractionCapability.UNAVAILABLE) {
            return rejected("INTERACT entity capability_unavailable: " + entityTypeId(target));
        }
        queuedCommands.add(QueuedCommand.interactEntity(target, interaction.radius()));
        return accepted("INTERACT accepted: entity " + entityTypeId(target) + " capability=" + capability.id());
    }
    BlockPos blockPos = BlockPos.containing(
            interaction.coordinate().x(), interaction.coordinate().y(), interaction.coordinate().z()
    );
    ServerLevel serverLevel = serverLevel();
    if (serverLevel == null || !serverLevel.hasChunkAt(blockPos)) {
        return rejected("INTERACT target chunk is not loaded");
    }
    if (entity.distanceToSqr(Vec3.atCenterOf(blockPos)) > BLOCK_TASK_MAX_DISTANCE * BLOCK_TASK_MAX_DISTANCE) {
        return rejected("INTERACT target is outside the start distance");
    }
    BlockState state = serverLevel.getBlockState(blockPos);
    BlockInteractionCapability capability = blockInteractionCapability(state);
    if (capability == BlockInteractionCapability.UNAVAILABLE) {
        return rejected("INTERACT block capability_unavailable: " + blockId(state.getBlock()));
    }
    queuedCommands.add(QueuedCommand.interactBlock(blockPos));
    return accepted("INTERACT accepted: block " + blockPos.toShortString() + " capability=" + capability.id());
}

protected void interactBlock(QueuedCommand command) {
    ServerLevel serverLevel = serverLevel();
    if (serverLevel == null) {
        failActiveCommand("server_level_unavailable");
        return;
    }
    BlockPos blockPos = command.blockTarget();
    if (!canUseBlockTarget(serverLevel, command, blockPos)) {
        failActiveCommand("interact_target_unloaded_or_outside_radius");
        return;
    }
    BlockState state = serverLevel.getBlockState(blockPos);
    BlockInteractionCapability capability = blockInteractionCapability(state);
    if (capability == BlockInteractionCapability.UNAVAILABLE) {
        failActiveCommand("interact_capability_unavailable=" + blockId(state.getBlock()));
        return;
    }
    lookAtBlock(blockPos);
    if (!isWithinInteractionDistance(blockPos)) {
        moveNearBlock(blockPos);
        return;
    }
    stopNavigation();
    if (!hasLineOfSightToBlock(serverLevel, blockPos)) {
        failActiveCommand("interact_no_line_of_sight");
        return;
    }
    if (!canAcquireInteractionCooldown()) {
        return;
    }
    if (!acquireInteractionCooldown() || !applyBlockInteraction(serverLevel, blockPos, state, capability)) {
        rollbackInteractionCooldown();
        failActiveCommand("interact_state_change_failed");
        return;
    }
    entity.swingMainHandAction();
    completeActiveCommand("interact:block capability=" + capability.id());
}

protected void interactEntity(QueuedCommand command) {
    ServerLevel serverLevel = serverLevel();
    if (serverLevel == null) {
        failActiveCommand("server_level_unavailable");
        return;
    }
    Entity target = command.entityTarget();
    if (target == null || !target.isAlive() || !target.level().dimension().equals(serverLevel.dimension())
            || !isBlockLoaded(target.blockPosition())) {
        failActiveCommand("interact_entity_unavailable");
        return;
    }
    if (!isSafeEntityInteractionTarget(target) || !isWithinStartDistance(command, target.position(), command.radius())) {
        failActiveCommand("interact_entity_unsafe_or_outside_radius");
        return;
    }
    EntityInteractionCapability capability = entityInteractionCapability(target);
    if (capability == EntityInteractionCapability.UNAVAILABLE) {
        failActiveCommand("interact_entity_capability_unavailable=" + entityTypeId(target));
        return;
    }
    entity.getLookControl().setLookAt(target);
    if (entity.distanceToSqr(target) > BLOCK_INTERACTION_DISTANCE * BLOCK_INTERACTION_DISTANCE) {
        moveToEntity(target, NavigationTarget.entity(entityTypeId(target)));
        return;
    }
    stopNavigation();
    if (!entity.hasLineOfSight(target)) {
        failActiveCommand("interact_entity_no_line_of_sight");
        return;
    }
    if (!canAcquireInteractionCooldown()) {
        return;
    }
    if (!acquireInteractionCooldown() || !applyEntityInteraction(serverLevel, target, capability)) {
        rollbackInteractionCooldown();
        failActiveCommand("interact_entity_state_change_failed=" + capability.id());
        return;
    }
    entity.swingMainHandAction();
    completeActiveCommand("interact:entity capability=" + capability.id());
}

protected Entity resolveInteractionTarget(ServerLevel serverLevel,
                                        InteractionInstruction instruction,
                                        Vec3 center) {
    if (instruction.targetsUuid()) {
        List<Entity> targets = serverLevel.getEntities(
                entity,
                entity.getBoundingBox().inflate(instruction.radius()),
                target -> target.getUUID().equals(instruction.targetUuid())
                        && isSafeEntityInteractionTarget(target)
                        && center.distanceToSqr(target.position()) <= instruction.radius() * instruction.radius()
                        && entity.hasLineOfSight(target)
        );
        return targets.stream().findFirst().orElse(null);
    }
    ResourceLocation id = ResourceLocation.tryParse(instruction.targetId());
    if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
        return null;
    }
    EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(id);
    List<Entity> targets = serverLevel.getEntities(
            entity,
            entity.getBoundingBox().inflate(instruction.radius()),
            target -> target.getType() == entityType
                    && isSafeEntityInteractionTarget(target)
                    && center.distanceToSqr(target.position()) <= instruction.radius() * instruction.radius()
                    && entity.hasLineOfSight(target)
    );
    return targets.stream()
            .min(Comparator.comparingDouble((Entity target) -> entity.distanceToSqr(target))
                    .thenComparing(target -> target.getUUID().toString()))
            .orElse(null);
}

protected boolean isSafeEntityInteractionTarget(Entity target) {
    if (target == null || !target.isAlive() || target == entity || target instanceof Player
            || target instanceof OpenPlayerNpcEntity) {
        return false;
    }
    ServerPlayer owner = owner();
    return owner == null || !target.getUUID().equals(owner.getUUID());
}

protected boolean isSafeAttackTarget(LivingEntity target) {
    return InteractionSafetyPolicy.isSafeExplicitAttackTarget(target, owner(), entity);
}

protected boolean canUseBlockTarget(ServerLevel serverLevel, QueuedCommand command, BlockPos blockPos) {
    return serverLevel.hasChunkAt(blockPos)
            && isWithinStartDistance(command, Vec3.atCenterOf(blockPos), BLOCK_TASK_MAX_DISTANCE);
}

protected boolean isWithinInteractionDistance(BlockPos blockPos) {
    return entity.distanceToSqr(Vec3.atCenterOf(blockPos))
            <= BLOCK_INTERACTION_DISTANCE * BLOCK_INTERACTION_DISTANCE;
}

protected boolean isWithinStartDistance(QueuedCommand command, Vec3 position, double distance) {
    BlockPos startPosition = command.startPosition();
    return startPosition == null || Vec3.atCenterOf(startPosition).distanceToSqr(position) <= distance * distance;
}

protected void moveNearBlock(BlockPos blockPos) {
    moveToBlock(blockPos);
}

protected void lookAtBlock(BlockPos blockPos) {
    entity.getLookControl().setLookAt(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D);
}

protected static BlockInteractionCapability blockInteractionCapability(BlockState state) {
    if (state == null) {
        return BlockInteractionCapability.UNAVAILABLE;
    }
    Block block = state.getBlock();
    if (block instanceof LeverBlock) {
        return BlockInteractionCapability.LEVER;
    }
    if (block instanceof ButtonBlock) {
        return BlockInteractionCapability.BUTTON;
    }
    if (block instanceof DoorBlock) {
        return BlockInteractionCapability.DOOR;
    }
    if (block instanceof TrapDoorBlock) {
        return BlockInteractionCapability.TRAPDOOR;
    }
    if (block instanceof FenceGateBlock) {
        return BlockInteractionCapability.FENCE_GATE;
    }
    if (block instanceof BellBlock) {
        return BlockInteractionCapability.BELL;
    }
    if (block instanceof NoteBlock) {
        return BlockInteractionCapability.NOTE_BLOCK;
    }
    if (block instanceof BarrelBlock || state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
        return BlockInteractionCapability.LOADED_CONTAINER;
    }
    return BlockInteractionCapability.UNAVAILABLE;
}

protected static boolean applyBlockInteraction(ServerLevel serverLevel, BlockPos blockPos, BlockState state,
                                             BlockInteractionCapability capability) {
    Block block = state.getBlock();
    if (block instanceof LeverBlock) {
        return serverLevel.setBlock(blockPos, state.setValue(LeverBlock.POWERED, !state.getValue(LeverBlock.POWERED)), Block.UPDATE_ALL);
    }
    if (block instanceof TrapDoorBlock) {
        return serverLevel.setBlock(blockPos, state.setValue(TrapDoorBlock.OPEN, !state.getValue(TrapDoorBlock.OPEN)), Block.UPDATE_ALL);
    }
    if (block instanceof FenceGateBlock) {
        return serverLevel.setBlock(blockPos, state.setValue(FenceGateBlock.OPEN, !state.getValue(FenceGateBlock.OPEN)), Block.UPDATE_ALL);
    }
    if (block instanceof DoorBlock) {
        return serverLevel.setBlock(blockPos, state.cycle(DoorBlock.OPEN), Block.UPDATE_ALL);
    }
    if (block instanceof ButtonBlock) {
        return serverLevel.setBlock(blockPos, state.setValue(ButtonBlock.POWERED, true), Block.UPDATE_ALL);
    }
    if (capability == BlockInteractionCapability.BELL || capability == BlockInteractionCapability.NOTE_BLOCK
            || capability == BlockInteractionCapability.LOADED_CONTAINER) {
        return true;
    }
    return false;
}

protected EntityInteractionCapability entityInteractionCapability(Entity target) {
    if (target instanceof net.minecraft.world.entity.npc.Villager) {
        return EntityInteractionCapability.UNAVAILABLE;
    }
    return EntityInteractionCapability.UNAVAILABLE;
}

protected boolean applyEntityInteraction(ServerLevel serverLevel, Entity target, EntityInteractionCapability capability) {
    return false;
}
}
