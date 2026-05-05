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

abstract class VanillaAutomationCombatController extends VanillaAutomationInteractionController {
    protected VanillaAutomationCombatController(OpenPlayerNpcEntity entity) {
        super(entity);
    }

protected AutomationCommandResult submitAttackTarget(String instruction) {
    TargetAttackInstruction attackInstruction = TargetAttackInstructionParser.parseOrNull(instruction);
    if (attackInstruction == null) {
        return rejected(TargetAttackInstructionParser.USAGE);
    }
    ServerLevel serverLevel = serverLevel();
    if (serverLevel == null) {
        return rejected("ATTACK_TARGET requires a server level");
    }
    LivingEntity target = resolveAttackTarget(serverLevel, attackInstruction, entity.position());
    if (target == null) {
        return rejected("ATTACK_TARGET found no safe hostile target in loaded range: " + attackInstruction.targetId());
    }
    queuedCommands.add(QueuedCommand.attackTarget(target, attackInstruction.radius()));
    return accepted("ATTACK_TARGET accepted: " + entityTypeId(target));
}

protected void attackNearest(QueuedCommand command) {
    ServerLevel serverLevel = serverLevel();
    if (serverLevel == null) {
        failActiveCommand("server_level_unavailable");
        return;
    }
    if (!isWithinStartDistance(command, entity.position(), command.radius())) {
        stopNavigation();
        failActiveCommand("outside_attack_radius");
        return;
    }
    LivingEntity target = command.survivalOnly()
            ? nearestSelfDefenseTarget(serverLevel, command.radius())
            : nearestAttackTarget(serverLevel, command.radius(), Vec3.atCenterOf(command.startPosition()));
    if (target == null) {
        stopNavigation();
        completeActiveCommand();
        return;
    }
    if (attackTarget(target)) {
        completeActiveCommand();
    }
}

protected void attackSpecificTarget(QueuedCommand command) {
    ServerLevel serverLevel = serverLevel();
    if (serverLevel == null) {
        failActiveCommand("server_level_unavailable");
        return;
    }
    Entity targetEntity = command.entityTarget();
    if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
        stopNavigation();
        failActiveCommand("attack_target_unavailable");
        return;
    }
    if (!target.level().dimension().equals(serverLevel.dimension()) || !isBlockLoaded(target.blockPosition())) {
        stopNavigation();
        failActiveCommand("attack_target_unavailable");
        return;
    }
    if (!isSafeAttackTarget(target) || !isWithinStartDistance(command, target.position(), command.radius())) {
        stopNavigation();
        failActiveCommand("attack_target_unsafe_or_outside_radius");
        return;
    }
    if (attackTarget(target)) {
        completeActiveCommand();
    }
}

protected LivingEntity nearestAttackTarget(ServerLevel serverLevel, double radius, Vec3 center) {
    if (serverLevel == null || center == null) {
        return null;
    }
    List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
            LivingEntity.class,
            entity.getBoundingBox().inflate(radius),
            target -> isSafeAttackTarget(target)
                    && center.distanceToSqr(target.position()) <= radius * radius
                    && entity.hasLineOfSight(target)
    );
    return targets.stream()
            .min(Comparator.comparingDouble(target -> entity.distanceToSqr(target)))
            .orElse(null);
}

protected LivingEntity nearestSelfDefenseTarget(ServerLevel serverLevel, double radius) {
    ServerPlayer owner = owner();
    List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
            LivingEntity.class,
            entity.getBoundingBox().inflate(radius),
            target -> SurvivalTargetPolicy.isHostileOrDangerTarget(target, owner, entity)
                    && entity.distanceToSqr(target) <= radius * radius
                    && entity.hasLineOfSight(target)
    );
    return targets.stream()
            .min(Comparator.comparingDouble((LivingEntity target) -> entity.distanceToSqr(target))
                    .thenComparing(target -> target.getUUID().toString()))
            .orElse(null);
}

protected LivingEntity resolveAttackTarget(ServerLevel serverLevel,
                                          TargetAttackInstruction instruction,
                                          Vec3 center) {
    if (instruction.targetsUuid()) {
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                entity.getBoundingBox().inflate(instruction.radius()),
                target -> target.getUUID().equals(instruction.targetUuid())
                        && isSafeAttackTarget(target)
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
    List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
            LivingEntity.class,
            entity.getBoundingBox().inflate(instruction.radius()),
            target -> target.getType() == entityType
                    && isSafeAttackTarget(target)
                    && center.distanceToSqr(target.position()) <= instruction.radius() * instruction.radius()
                    && entity.hasLineOfSight(target)
    );
    return targets.stream()
            .min(Comparator.comparingDouble((LivingEntity target) -> entity.distanceToSqr(target))
                    .thenComparing(target -> target.getUUID().toString()))
            .orElse(null);
}

protected LivingEntity nearestGuardTarget(ServerLevel serverLevel, ServerPlayer owner, double radius) {
    List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
            LivingEntity.class,
            owner.getBoundingBox().inflate(radius),
            target -> SurvivalTargetPolicy.isHostileOrDangerTarget(target, owner, entity)
                    && owner.distanceToSqr(target) <= radius * radius
                    && entity.hasLineOfSight(target)
    );
    return targets.stream()
            .min(Comparator.comparingDouble((LivingEntity target) -> owner.distanceToSqr(target))
                    .thenComparing(target -> target.getUUID().toString()))
            .orElse(null);
}

protected boolean attackTarget(LivingEntity target) {
    entity.getLookControl().setLookAt(target);
    if (entity.distanceToSqr(target) > ATTACK_REACH_DISTANCE * ATTACK_REACH_DISTANCE) {
        moveToEntity(target, NavigationTarget.entity(entityTypeId(target)));
        return false;
    }
    stopNavigation();
    entity.swingMainHandAction();
    entity.doHurtTarget(target);
    return true;
}

protected void followOwner() {
    ServerPlayer owner = owner();
    if (owner == null) {
        stopAll();
        return;
    }
    double distanceSquared = entity.distanceToSqr(owner);
    if (distanceSquared > FOLLOW_START_DISTANCE * FOLLOW_START_DISTANCE) {
        if (!moveToEntity(owner, NavigationTarget.owner())) {
            return;
        }
    } else if (distanceSquared <= FOLLOW_STOP_DISTANCE * FOLLOW_STOP_DISTANCE) {
        stopNavigation();
    }
    entity.getLookControl().setLookAt(owner);
}

protected void guardOwner(QueuedCommand command) {
    ServerLevel serverLevel = serverLevel();
    ServerPlayer owner = owner();
    if (serverLevel == null || owner == null) {
        stopAll();
        return;
    }
    if (!isWithinStartDistance(command, owner.position(), command.radius() + FOLLOW_START_DISTANCE)) {
        stopNavigation();
        failActiveCommand("outside_guard_radius");
        return;
    }
    LivingEntity target = nearestGuardTarget(serverLevel, owner, command.radius());
    if (target != null) {
        attackTarget(target);
        return;
    }
    followOwner();
}

protected void patrol(QueuedCommand command) {
    if (!isWithinStartDistance(command, entity.position(), PATROL_MAX_DISTANCE + FOLLOW_START_DISTANCE)) {
        stopNavigation();
        failActiveCommand("outside_patrol_radius");
        return;
    }
    if (!entity.getNavigation().isDone()) {
        return;
    }
    command.togglePatrolReturn();
    if (command.returningToStart()) {
        BlockPos startPosition = command.startPosition();
        moveToBlock(startPosition);
    } else {
        Coordinate coordinate = command.coordinate();
        moveToPosition(coordinate);
    }
}
}
