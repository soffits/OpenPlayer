package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.automation.AutomationInstructionParser.Coordinate;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class VanillaAutomationBackend implements AutomationBackend {
    public static final String NAME = "vanilla";

    static boolean isLocalWorldOrInventoryAction(IntentKind kind) {
        return kind == IntentKind.COLLECT_ITEMS
                || kind == IntentKind.BREAK_BLOCK
                || kind == IntentKind.PLACE_BLOCK
                || kind == IntentKind.ATTACK_NEAREST
                || kind == IntentKind.GUARD_OWNER
                || kind == IntentKind.EQUIP_BEST_ITEM
                || kind == IntentKind.EQUIP_ARMOR
                || kind == IntentKind.USE_SELECTED_ITEM
                || kind == IntentKind.SWAP_TO_OFFHAND
                || kind == IntentKind.DROP_ITEM;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AutomationBackendStatus status() {
        return new AutomationBackendStatus(NAME, AutomationBackendState.AVAILABLE, "Vanilla Minecraft NPC tasks");
    }

    @Override
    public AutomationController createController(OpenPlayerNpcEntity entity) {
        return new VanillaAutomationController(entity);
    }

    private static final class VanillaAutomationController implements AutomationController {
        private static final double MOVE_SPEED = 1.0D;
        private static final double FOLLOW_SPEED = 1.0D;
        private static final double FOLLOW_STOP_DISTANCE = 3.0D;
        private static final double FOLLOW_START_DISTANCE = 4.0D;
        private static final double COLLECT_RADIUS = 16.0D;
        private static final double COLLECT_REACH_DISTANCE = 1.5D;
        private static final int COLLECT_REACH_TICKS = 20;
        private static final double BLOCK_TASK_MAX_DISTANCE = 24.0D;
        private static final double BLOCK_INTERACTION_DISTANCE = 4.0D;
        private static final double ATTACK_DEFAULT_RADIUS = 12.0D;
        private static final double ATTACK_MAX_RADIUS = 24.0D;
        private static final double ATTACK_REACH_DISTANCE = 2.5D;
        private static final double ATTACK_SPEED = 1.1D;
        private static final double GUARD_DEFAULT_RADIUS = 12.0D;
        private static final double GUARD_MAX_RADIUS = 16.0D;
        private static final double PATROL_MAX_DISTANCE = 32.0D;
        private static final int MOVE_MAX_TICKS = 20 * 60;
        private static final int SHORT_TASK_MAX_TICKS = 20 * 30;
        private static final int COLLECT_MAX_TICKS = 20 * 45;
        private static final int LONG_TASK_MAX_TICKS = 20 * 120;
        private static final int STUCK_CHECK_INTERVAL_TICKS = 40;
        private static final double STUCK_MIN_PROGRESS_DISTANCE = 0.15D;
        private static final int STUCK_MAX_CHECKS = 4;
        private static final int INTERACTION_COOLDOWN_TICKS = 10;

        private final OpenPlayerNpcEntity entity;
        private final Queue<QueuedCommand> queuedCommands = new ArrayDeque<>();
        private final InteractionCooldown interactionCooldown = new InteractionCooldown(INTERACTION_COOLDOWN_TICKS);
        private QueuedCommand activeCommand;
        private AutomationControllerMonitor activeMonitor;
        private NpcOwnerId ownerId;

        private VanillaAutomationController(OpenPlayerNpcEntity entity) {
            if (entity == null) {
                throw new IllegalArgumentException("entity cannot be null");
            }
            this.entity = entity;
        }

        @Override
        public void setOwnerId(NpcOwnerId ownerId) {
            if (ownerId == null) {
                throw new IllegalArgumentException("ownerId cannot be null");
            }
            this.ownerId = ownerId;
        }

        @Override
        public AutomationCommandResult submit(CommandIntent intent) {
            if (intent == null) {
                throw new IllegalArgumentException("intent cannot be null");
            }
            IntentKind kind = intent.kind();
            if (kind == IntentKind.STOP) {
                stopAll();
                return accepted("STOP accepted");
            }
            if (kind == IntentKind.REPORT_STATUS) {
                if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
                    return rejected("REPORT_STATUS requires a blank instruction");
                }
                return accepted("REPORT_STATUS accepted: " + statusSummary());
            }
            if (kind == IntentKind.MOVE) {
                Coordinate coordinate = AutomationInstructionParser.parseCoordinateOrNull(intent.instruction());
                if (coordinate == null) {
                    return rejected("MOVE requires instruction: x y z");
                }
                if (!isCoordinateLoaded(coordinate)) {
                    return rejected("MOVE target chunk is not loaded");
                }
                queuedCommands.add(QueuedCommand.move(coordinate));
                return accepted("MOVE accepted");
            }
            if (kind == IntentKind.LOOK) {
                Coordinate coordinate = AutomationInstructionParser.parseCoordinateOrNull(intent.instruction());
                if (coordinate == null) {
                    return rejected("LOOK requires instruction: x y z");
                }
                queuedCommands.add(QueuedCommand.look(coordinate));
                return accepted("LOOK accepted");
            }
            if (kind == IntentKind.FOLLOW_OWNER) {
                if (ownerId == null) {
                    return rejected("FOLLOW_OWNER requires an NPC owner");
                }
                if (owner() == null) {
                    return rejected("NPC owner is unavailable in this dimension");
                }
                queuedCommands.add(QueuedCommand.followOwner());
                return accepted("FOLLOW_OWNER accepted");
            }
            if (kind == IntentKind.PATROL) {
                Coordinate coordinate = AutomationInstructionParser.parseBoundedCoordinateOrNull(
                        intent.instruction(),
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        PATROL_MAX_DISTANCE
                );
                if (coordinate == null) {
                    return rejected("PATROL requires instruction x y z within " + (int) PATROL_MAX_DISTANCE + " blocks");
                }
                if (!isCoordinateLoaded(coordinate)) {
                    return rejected("PATROL target chunk is not loaded");
                }
                queuedCommands.add(QueuedCommand.patrol(coordinate));
                return accepted("PATROL accepted");
            }
            if (isLocalWorldOrInventoryAction(kind) && !entity.allowWorldActions()) {
                return rejected("World actions are disabled for this OpenPlayer character");
            }
            if (kind == IntentKind.COLLECT_ITEMS) {
                queuedCommands.add(QueuedCommand.collectItems());
                return accepted("COLLECT_ITEMS accepted");
            }
            if (kind == IntentKind.EQUIP_BEST_ITEM) {
                if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
                    return rejected("EQUIP_BEST_ITEM requires a blank instruction");
                }
                if (!canAcquireInteractionCooldown()) {
                    return rejected(interactionCooldownMessage());
                }
                LivingEntity target = nearestAttackTarget(serverLevel(), ATTACK_DEFAULT_RADIUS, entity.position());
                if (target != null && acquireInteractionCooldown() && entity.selectBestAttackItem()) {
                    return accepted("EQUIP_BEST_ITEM accepted");
                }
                rollbackInteractionCooldown();
                return rejected("EQUIP_BEST_ITEM found no useful hotbar item for the nearby context");
            }
            if (kind == IntentKind.EQUIP_ARMOR) {
                if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
                    return rejected("EQUIP_ARMOR requires a blank instruction");
                }
                if (!canAcquireInteractionCooldown()) {
                    return rejected(interactionCooldownMessage());
                }
                if (!acquireInteractionCooldown() || !entity.equipBestAvailableArmor()) {
                    rollbackInteractionCooldown();
                    return rejected("EQUIP_ARMOR found no armor upgrade in NPC inventory");
                }
                entity.swingMainHandAction();
                return accepted("EQUIP_ARMOR accepted");
            }
            if (kind == IntentKind.USE_SELECTED_ITEM) {
                if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
                    return rejected("USE_SELECTED_ITEM requires a blank instruction");
                }
                if (!canAcquireInteractionCooldown()) {
                    return rejected(interactionCooldownMessage());
                }
                if (!acquireInteractionCooldown() || !entity.useSelectedMainHandItemLocally()) {
                    rollbackInteractionCooldown();
                    return rejected("USE_SELECTED_ITEM requires a selected edible or drinkable main-hand item");
                }
                return accepted("USE_SELECTED_ITEM accepted");
            }
            if (kind == IntentKind.SWAP_TO_OFFHAND) {
                if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
                    return rejected("SWAP_TO_OFFHAND requires a blank instruction");
                }
                if (!canAcquireInteractionCooldown()) {
                    return rejected(interactionCooldownMessage());
                }
                if (!acquireInteractionCooldown() || !entity.swapSelectedHotbarStackToOffhand()) {
                    rollbackInteractionCooldown();
                    return rejected("SWAP_TO_OFFHAND requires a selected hotbar stack");
                }
                entity.swingMainHandAction();
                return accepted("SWAP_TO_OFFHAND accepted");
            }
            if (kind == IntentKind.DROP_ITEM) {
                if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
                    return rejected("DROP_ITEM requires a blank instruction");
                }
                if (!canAcquireInteractionCooldown()) {
                    return rejected(interactionCooldownMessage());
                }
                if (!acquireInteractionCooldown() || !entity.dropSelectedHotbarStack()) {
                    rollbackInteractionCooldown();
                    return rejected("DROP_ITEM requires a selected hotbar stack");
                }
                entity.swingMainHandAction();
                return accepted("DROP_ITEM accepted");
            }
            if (kind == IntentKind.BREAK_BLOCK) {
                Coordinate coordinate = AutomationInstructionParser.parseCoordinateOrNull(intent.instruction());
                if (coordinate == null) {
                    return rejected("BREAK_BLOCK requires instruction: x y z");
                }
                if (!isCoordinateLoaded(coordinate)) {
                    return rejected("BREAK_BLOCK target chunk is not loaded");
                }
                queuedCommands.add(QueuedCommand.breakBlock(coordinate));
                return accepted("BREAK_BLOCK accepted");
            }
            if (kind == IntentKind.PLACE_BLOCK) {
                Coordinate coordinate = AutomationInstructionParser.parseCoordinateOrNull(intent.instruction());
                if (coordinate == null) {
                    return rejected("PLACE_BLOCK requires instruction: x y z");
                }
                if (!isCoordinateLoaded(coordinate)) {
                    return rejected("PLACE_BLOCK target chunk is not loaded");
                }
                if (!(entity.getMainHandItem().getItem() instanceof BlockItem)
                        && !entity.selectFirstHotbarBlockItem()) {
                    return rejected("PLACE_BLOCK requires a block item in the NPC selected hotbar slot");
                }
                queuedCommands.add(QueuedCommand.placeBlock(coordinate));
                return accepted("PLACE_BLOCK accepted");
            }
            if (kind == IntentKind.ATTACK_NEAREST) {
                double radius = AutomationInstructionParser.parseOptionalRadiusOrNegative(
                        intent.instruction(), ATTACK_DEFAULT_RADIUS, ATTACK_MAX_RADIUS
                );
                if (radius < 0.0D) {
                    return rejected("ATTACK_NEAREST instruction must be blank or a positive radius number");
                }
                queuedCommands.add(QueuedCommand.attackNearest(radius));
                return accepted("ATTACK_NEAREST accepted");
            }
            if (kind == IntentKind.GUARD_OWNER) {
                if (ownerId == null) {
                    return rejected("GUARD_OWNER requires an NPC owner");
                }
                if (owner() == null) {
                    return rejected("NPC owner is unavailable in this dimension");
                }
                double radius = AutomationInstructionParser.parseOptionalRadiusOrNegative(
                        intent.instruction(), GUARD_DEFAULT_RADIUS, GUARD_MAX_RADIUS
                );
                if (radius < 0.0D) {
                    return rejected("GUARD_OWNER instruction must be blank or a positive radius number");
                }
                queuedCommands.add(QueuedCommand.guardOwner(radius));
                return accepted("GUARD_OWNER accepted");
            }
            return rejected("Unsupported intent: " + kind.name());
        }

        @Override
        public void tick() {
            if (entity.level().isClientSide) {
                return;
            }
            interactionCooldown.tick();
            if (activeCommand == null) {
                activeCommand = queuedCommands.poll();
                if (activeCommand == null) {
                    return;
                }
                activeMonitor = newMonitor(activeCommand);
                activeMonitor.start(entity.getX(), entity.getY(), entity.getZ());
                start(activeCommand);
            }
            continueActiveCommand();
            watchdogActiveCommand();
        }

        @Override
        public void stopAll() {
            queuedCommands.clear();
            activeCommand = null;
            if (activeMonitor != null) {
                activeMonitor.reset();
            }
            interactionCooldown.reset();
            entity.getNavigation().stop();
            entity.setDeltaMovement(Vec3.ZERO);
            entity.getLookControl().setLookAt(entity.getX(), entity.getEyeY(), entity.getZ());
        }

        private void start(QueuedCommand command) {
            if (command.kind() == IntentKind.MOVE) {
                Coordinate coordinate = command.coordinate();
                entity.getNavigation().moveTo(coordinate.x(), coordinate.y(), coordinate.z(), MOVE_SPEED);
                return;
            }
            if (command.kind() == IntentKind.LOOK) {
                Coordinate coordinate = command.coordinate();
                entity.getLookControl().setLookAt(coordinate.x(), coordinate.y(), coordinate.z());
                completeActiveCommand();
                return;
            }
            if (command.kind() == IntentKind.COLLECT_ITEMS
                    || command.kind() == IntentKind.BREAK_BLOCK
                    || command.kind() == IntentKind.PLACE_BLOCK
                    || command.kind() == IntentKind.ATTACK_NEAREST
                    || command.kind() == IntentKind.GUARD_OWNER
                    || command.kind() == IntentKind.PATROL) {
                command.setStartPosition(entity.blockPosition());
            }
            if (command.kind() == IntentKind.PATROL) {
                Coordinate coordinate = command.coordinate();
                entity.getNavigation().moveTo(coordinate.x(), coordinate.y(), coordinate.z(), MOVE_SPEED);
            }
        }

        private void continueActiveCommand() {
            if (activeCommand == null) {
                return;
            }
            if (activeCommand.kind() == IntentKind.MOVE) {
                if (entity.getNavigation().isDone()) {
                    completeActiveCommand();
                }
                return;
            }
            if (activeCommand.kind() == IntentKind.FOLLOW_OWNER) {
                followOwner();
                return;
            }
            if (activeCommand.kind() == IntentKind.COLLECT_ITEMS) {
                collectItems(activeCommand);
                return;
            }
            if (activeCommand.kind() == IntentKind.BREAK_BLOCK) {
                breakBlock(activeCommand);
                return;
            }
            if (activeCommand.kind() == IntentKind.PLACE_BLOCK) {
                placeBlock(activeCommand);
                return;
            }
            if (activeCommand.kind() == IntentKind.ATTACK_NEAREST) {
                attackNearest(activeCommand);
                return;
            }
            if (activeCommand.kind() == IntentKind.GUARD_OWNER) {
                guardOwner(activeCommand);
                return;
            }
            if (activeCommand.kind() == IntentKind.PATROL) {
                patrol(activeCommand);
            }
        }

        private void collectItems(QueuedCommand command) {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                failActiveCommand("server_level_unavailable");
                return;
            }
            if (!isWithinStartDistance(command, entity.position(), COLLECT_RADIUS)) {
                failActiveCommand("outside_collect_radius");
                return;
            }
            ItemEntity itemEntity = nearestItem(serverLevel, command);
            if (itemEntity == null) {
                entity.getNavigation().stop();
                completeActiveCommand();
                return;
            }
            entity.getLookControl().setLookAt(itemEntity);
            if (entity.distanceToSqr(itemEntity) > COLLECT_REACH_DISTANCE * COLLECT_REACH_DISTANCE) {
                entity.getNavigation().moveTo(itemEntity, MOVE_SPEED);
                command.resetReachTicks();
                return;
            }
            entity.getNavigation().stop();
            command.incrementReachTicks();
            if (command.reachTicks() >= COLLECT_REACH_TICKS) {
                completeActiveCommand();
            }
        }

        private ItemEntity nearestItem(ServerLevel serverLevel, QueuedCommand command) {
            List<ItemEntity> itemEntities = serverLevel.getEntitiesOfClass(
                    ItemEntity.class,
                    entity.getBoundingBox().inflate(COLLECT_RADIUS),
                    itemEntity -> itemEntity.isAlive()
                            && !itemEntity.hasPickUpDelay()
                            && !itemEntity.getItem().isEmpty()
                            && entity.hasLineOfSight(itemEntity)
                            && isWithinStartDistance(command, itemEntity.position(), COLLECT_RADIUS)
            );
            return itemEntities.stream()
                    .min(Comparator.comparingDouble(itemEntity -> entity.distanceToSqr(itemEntity)))
                    .orElse(null);
        }

        private void breakBlock(QueuedCommand command) {
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
            if (blockState.isAir() || blockState.getDestroySpeed(serverLevel, blockPos) < 0.0F) {
                failActiveCommand("block_not_breakable");
                return;
            }
            entity.selectBestToolFor(blockState, serverLevel, blockPos);
            lookAtBlock(blockPos);
            if (!isWithinInteractionDistance(blockPos)) {
                moveNearBlock(blockPos);
                return;
            }
            entity.getNavigation().stop();
            entity.swingMainHandAction();
            serverLevel.destroyBlock(blockPos, true, entity);
            completeActiveCommand();
        }

        private void placeBlock(QueuedCommand command) {
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
            entity.getNavigation().stop();
            if (serverLevel.setBlock(blockPos, placedState, Block.UPDATE_ALL)) {
                mainHandStack.shrink(1);
                entity.swingMainHandAction();
            }
            completeActiveCommand();
        }

        private void attackNearest(QueuedCommand command) {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                failActiveCommand("server_level_unavailable");
                return;
            }
            if (!isWithinStartDistance(command, entity.position(), command.radius())) {
                entity.getNavigation().stop();
                failActiveCommand("outside_attack_radius");
                return;
            }
            LivingEntity target = nearestAttackTarget(serverLevel, command.radius(), Vec3.atCenterOf(command.startPosition()));
            if (target == null) {
                entity.getNavigation().stop();
                completeActiveCommand();
                return;
            }
            if (attackTarget(target)) {
                completeActiveCommand();
            }
        }

        private LivingEntity nearestAttackTarget(ServerLevel serverLevel, double radius, Vec3 center) {
            if (serverLevel == null || center == null) {
                return null;
            }
            ServerPlayer owner = owner();
            List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
                    LivingEntity.class,
                    entity.getBoundingBox().inflate(radius),
                    target -> target.isAlive()
                            && target != entity
                            && !(target instanceof Player)
                            && !(target instanceof OpenPlayerNpcEntity)
                            && (owner == null || !target.getUUID().equals(owner.getUUID()))
                            && center.distanceToSqr(target.position()) <= radius * radius
                            && entity.hasLineOfSight(target)
            );
            return targets.stream()
                    .min(Comparator.comparingDouble(target -> entity.distanceToSqr(target)))
                    .orElse(null);
        }

        private LivingEntity nearestGuardTarget(ServerLevel serverLevel, ServerPlayer owner, double radius) {
            List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
                    LivingEntity.class,
                    owner.getBoundingBox().inflate(radius),
                    target -> target.isAlive()
                            && target instanceof Enemy
                            && target != entity
                            && !(target instanceof Player)
                            && !(target instanceof OpenPlayerNpcEntity)
                            && !target.getUUID().equals(owner.getUUID())
                            && owner.distanceToSqr(target) <= radius * radius
                            && entity.hasLineOfSight(target)
            );
            return targets.stream()
                    .min(Comparator.comparingDouble(target -> owner.distanceToSqr(target)))
                    .orElse(null);
        }

        private boolean attackTarget(LivingEntity target) {
            entity.getLookControl().setLookAt(target);
            entity.selectBestAttackItem();
            if (entity.distanceToSqr(target) > ATTACK_REACH_DISTANCE * ATTACK_REACH_DISTANCE) {
                entity.getNavigation().moveTo(target, ATTACK_SPEED);
                return false;
            }
            entity.getNavigation().stop();
            entity.swingMainHandAction();
            entity.doHurtTarget(target);
            return true;
        }

        private boolean canUseBlockTarget(ServerLevel serverLevel, QueuedCommand command, BlockPos blockPos) {
            return serverLevel.hasChunkAt(blockPos)
                    && isWithinStartDistance(command, Vec3.atCenterOf(blockPos), BLOCK_TASK_MAX_DISTANCE);
        }

        private boolean isWithinInteractionDistance(BlockPos blockPos) {
            return entity.distanceToSqr(Vec3.atCenterOf(blockPos))
                    <= BLOCK_INTERACTION_DISTANCE * BLOCK_INTERACTION_DISTANCE;
        }

        private boolean isWithinStartDistance(QueuedCommand command, Vec3 position, double distance) {
            BlockPos startPosition = command.startPosition();
            return startPosition == null || Vec3.atCenterOf(startPosition).distanceToSqr(position) <= distance * distance;
        }

        private void moveNearBlock(BlockPos blockPos) {
            entity.getNavigation().moveTo(blockPos.getX() + 0.5D, blockPos.getY(), blockPos.getZ() + 0.5D, MOVE_SPEED);
        }

        private void lookAtBlock(BlockPos blockPos) {
            entity.getLookControl().setLookAt(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D);
        }

        private void followOwner() {
            ServerPlayer owner = owner();
            if (owner == null) {
                stopAll();
                return;
            }
            double distanceSquared = entity.distanceToSqr(owner);
            if (distanceSquared > FOLLOW_START_DISTANCE * FOLLOW_START_DISTANCE) {
                entity.getNavigation().moveTo(owner, FOLLOW_SPEED);
            } else if (distanceSquared <= FOLLOW_STOP_DISTANCE * FOLLOW_STOP_DISTANCE) {
                entity.getNavigation().stop();
            }
            entity.getLookControl().setLookAt(owner);
        }

        private void guardOwner(QueuedCommand command) {
            ServerLevel serverLevel = serverLevel();
            ServerPlayer owner = owner();
            if (serverLevel == null || owner == null) {
                stopAll();
                return;
            }
            if (!isWithinStartDistance(command, owner.position(), command.radius() + FOLLOW_START_DISTANCE)) {
                entity.getNavigation().stop();
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

        private void patrol(QueuedCommand command) {
            if (!isWithinStartDistance(command, entity.position(), PATROL_MAX_DISTANCE + FOLLOW_START_DISTANCE)) {
                entity.getNavigation().stop();
                failActiveCommand("outside_patrol_radius");
                return;
            }
            if (!entity.getNavigation().isDone()) {
                return;
            }
            command.togglePatrolReturn();
            if (command.returningToStart()) {
                BlockPos startPosition = command.startPosition();
                entity.getNavigation().moveTo(
                        startPosition.getX() + 0.5D,
                        startPosition.getY(),
                        startPosition.getZ() + 0.5D,
                        MOVE_SPEED
                );
            } else {
                Coordinate coordinate = command.coordinate();
                entity.getNavigation().moveTo(coordinate.x(), coordinate.y(), coordinate.z(), MOVE_SPEED);
            }
        }

        private String statusSummary() {
            String activeKind = activeCommand == null ? "idle" : activeCommand.kind().name();
            String controllerStatus = activeMonitor == null ? "idle" : activeMonitor.status().name().toLowerCase();
            String controllerReason = activeMonitor == null ? "idle" : activeMonitor.boundedReason();
            return "hp=" + Math.round(entity.getHealth())
                    + ", slot=" + entity.selectedHotbarSlot()
                    + ", active=" + activeKind
                    + ", queued=" + queuedCommands.size()
                    + ", interactCd=" + interactionCooldown.remainingTicks()
                    + ", ctrl=" + controllerStatus
                    + ", reason=" + controllerReason;
        }

        private void watchdogActiveCommand() {
            if (activeCommand == null || activeMonitor == null) {
                return;
            }
            AutomationControllerMonitorStatus status = activeMonitor.tick(
                    entity.getX(), entity.getY(), entity.getZ(), requiresNavigationProgress(activeCommand)
            );
            if (status == AutomationControllerMonitorStatus.TIMED_OUT || status == AutomationControllerMonitorStatus.STUCK) {
                entity.getNavigation().stop();
                activeCommand = null;
            }
        }

        private void completeActiveCommand() {
            if (activeMonitor != null) {
                activeMonitor.complete();
            }
            activeCommand = null;
        }

        private void failActiveCommand(String reason) {
            if (activeMonitor != null) {
                activeMonitor.cancel(reason);
            }
            entity.getNavigation().stop();
            activeCommand = null;
        }

        private boolean requiresNavigationProgress(QueuedCommand command) {
            return command.kind() != IntentKind.LOOK && !entity.getNavigation().isDone();
        }

        private AutomationControllerMonitor newMonitor(QueuedCommand command) {
            return new AutomationControllerMonitor(
                    maxTicks(command.kind()),
                    STUCK_CHECK_INTERVAL_TICKS,
                    STUCK_MIN_PROGRESS_DISTANCE,
                    STUCK_MAX_CHECKS
            );
        }

        private int maxTicks(IntentKind kind) {
            if (kind == IntentKind.MOVE) {
                return MOVE_MAX_TICKS;
            }
            if (kind == IntentKind.COLLECT_ITEMS) {
                return COLLECT_MAX_TICKS;
            }
            if (kind == IntentKind.FOLLOW_OWNER || kind == IntentKind.GUARD_OWNER || kind == IntentKind.PATROL) {
                return LONG_TASK_MAX_TICKS;
            }
            return SHORT_TASK_MAX_TICKS;
        }

        private boolean isCoordinateLoaded(Coordinate coordinate) {
            ServerLevel serverLevel = serverLevel();
            return serverLevel != null
                    && serverLevel.hasChunkAt(BlockPos.containing(coordinate.x(), coordinate.y(), coordinate.z()));
        }

        private ServerPlayer owner() {
            if (ownerId == null || !(entity.level() instanceof ServerLevel serverLevel)) {
                return null;
            }
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(ownerId.value());
            if (player == null || !player.level().dimension().equals(serverLevel.dimension())) {
                return null;
            }
            return player;
        }

        private ServerLevel serverLevel() {
            if (entity.level() instanceof ServerLevel serverLevel) {
                return serverLevel;
            }
            return null;
        }

        private static AutomationCommandResult accepted(String message) {
            return new AutomationCommandResult(AutomationCommandStatus.ACCEPTED, message);
        }

        private static AutomationCommandResult rejected(String message) {
            return new AutomationCommandResult(AutomationCommandStatus.REJECTED, message);
        }

        private boolean acquireInteractionCooldown() {
            return interactionCooldown.tryAcquire();
        }

        private boolean canAcquireInteractionCooldown() {
            return interactionCooldown.canAcquire();
        }

        private void rollbackInteractionCooldown() {
            interactionCooldown.rollbackAcquire();
        }

        private String interactionCooldownMessage() {
            return "Interaction cooldown active for " + interactionCooldown.remainingTicks() + " ticks";
        }

        private static final class QueuedCommand {
            private final IntentKind kind;
            private final Coordinate coordinate;
            private final double radius;
            private BlockPos startPosition;
            private int reachTicks;
            private boolean patrolReturn;

            private QueuedCommand(IntentKind kind, Coordinate coordinate, double radius) {
                this.kind = kind;
                this.coordinate = coordinate;
                this.radius = radius;
            }

            private static QueuedCommand move(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.MOVE, coordinate, 0.0D);
            }

            private static QueuedCommand look(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.LOOK, coordinate, 0.0D);
            }

            private static QueuedCommand followOwner() {
                return new QueuedCommand(IntentKind.FOLLOW_OWNER, null, 0.0D);
            }

            private static QueuedCommand collectItems() {
                return new QueuedCommand(IntentKind.COLLECT_ITEMS, null, 0.0D);
            }

            private static QueuedCommand breakBlock(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.BREAK_BLOCK, coordinate, 0.0D);
            }

            private static QueuedCommand placeBlock(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.PLACE_BLOCK, coordinate, 0.0D);
            }

            private static QueuedCommand attackNearest(double radius) {
                return new QueuedCommand(IntentKind.ATTACK_NEAREST, null, radius);
            }

            private static QueuedCommand guardOwner(double radius) {
                return new QueuedCommand(IntentKind.GUARD_OWNER, null, radius);
            }

            private static QueuedCommand patrol(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.PATROL, coordinate, 0.0D);
            }

            private IntentKind kind() {
                return kind;
            }

            private Coordinate coordinate() {
                return coordinate;
            }

            private double radius() {
                return radius;
            }

            private int reachTicks() {
                return reachTicks;
            }

            private void incrementReachTicks() {
                reachTicks++;
            }

            private void resetReachTicks() {
                reachTicks = 0;
            }

            private BlockPos startPosition() {
                return startPosition;
            }

            private void setStartPosition(BlockPos startPosition) {
                this.startPosition = startPosition;
            }

            private boolean returningToStart() {
                return patrolReturn;
            }

            private void togglePatrolReturn() {
                patrolReturn = !patrolReturn;
            }

            private BlockPos blockPos() {
                return BlockPos.containing(coordinate.x(), coordinate.y(), coordinate.z());
            }
        }
    }
}
