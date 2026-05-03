package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.OpenPlayerAutomationConfig;
import dev.soffits.openplayer.api.NpcOwnerId;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class VanillaAutomationBackend implements AutomationBackend {
    public static final String NAME = "vanilla";

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

        private final OpenPlayerNpcEntity entity;
        private final Queue<QueuedCommand> queuedCommands = new ArrayDeque<>();
        private QueuedCommand activeCommand;
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
            if (kind == IntentKind.MOVE) {
                CommandCoordinate coordinate = parseCoordinateOrNull(intent.instruction());
                if (coordinate == null) {
                    return rejected("MOVE requires instruction: x y z");
                }
                queuedCommands.add(QueuedCommand.move(coordinate));
                return accepted("MOVE accepted");
            }
            if (kind == IntentKind.LOOK) {
                CommandCoordinate coordinate = parseCoordinateOrNull(intent.instruction());
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
            if (kind == IntentKind.COLLECT_ITEMS) {
                queuedCommands.add(QueuedCommand.collectItems());
                return accepted("COLLECT_ITEMS accepted");
            }
            if (isWorldAction(kind) && !OpenPlayerAutomationConfig.allowWorldActions()) {
                return rejected("World actions are disabled by local OpenPlayer automation config");
            }
            if (kind == IntentKind.BREAK_BLOCK) {
                CommandCoordinate coordinate = parseCoordinateOrNull(intent.instruction());
                if (coordinate == null) {
                    return rejected("BREAK_BLOCK requires instruction: x y z");
                }
                queuedCommands.add(QueuedCommand.breakBlock(coordinate));
                return accepted("BREAK_BLOCK accepted");
            }
            if (kind == IntentKind.PLACE_BLOCK) {
                CommandCoordinate coordinate = parseCoordinateOrNull(intent.instruction());
                if (coordinate == null) {
                    return rejected("PLACE_BLOCK requires instruction: x y z");
                }
                if (!(entity.getMainHandItem().getItem() instanceof BlockItem)
                        && !entity.selectFirstHotbarBlockItem()) {
                    return rejected("PLACE_BLOCK requires a block item in the NPC selected hotbar slot");
                }
                queuedCommands.add(QueuedCommand.placeBlock(coordinate));
                return accepted("PLACE_BLOCK accepted");
            }
            if (kind == IntentKind.ATTACK_NEAREST) {
                double radius = parseOptionalRadiusOrNegative(intent.instruction(), ATTACK_DEFAULT_RADIUS, ATTACK_MAX_RADIUS);
                if (radius < 0.0D) {
                    return rejected("ATTACK_NEAREST instruction must be blank or a positive radius number");
                }
                queuedCommands.add(QueuedCommand.attackNearest(radius));
                return accepted("ATTACK_NEAREST accepted");
            }
            return rejected("Unsupported intent: " + kind.name());
        }

        @Override
        public void tick() {
            if (entity.level().isClientSide) {
                return;
            }
            if (activeCommand == null) {
                activeCommand = queuedCommands.poll();
                if (activeCommand == null) {
                    return;
                }
                start(activeCommand);
            }
            continueActiveCommand();
        }

        @Override
        public void stopAll() {
            queuedCommands.clear();
            activeCommand = null;
            entity.getNavigation().stop();
            entity.setDeltaMovement(Vec3.ZERO);
            entity.getLookControl().setLookAt(entity.getX(), entity.getEyeY(), entity.getZ());
        }

        private void start(QueuedCommand command) {
            if (command.kind() == IntentKind.MOVE) {
                CommandCoordinate coordinate = command.coordinate();
                entity.getNavigation().moveTo(coordinate.x(), coordinate.y(), coordinate.z(), MOVE_SPEED);
                return;
            }
            if (command.kind() == IntentKind.LOOK) {
                CommandCoordinate coordinate = command.coordinate();
                entity.getLookControl().setLookAt(coordinate.x(), coordinate.y(), coordinate.z());
                activeCommand = null;
                return;
            }
            if (command.kind() == IntentKind.COLLECT_ITEMS
                    || command.kind() == IntentKind.BREAK_BLOCK
                    || command.kind() == IntentKind.PLACE_BLOCK
                    || command.kind() == IntentKind.ATTACK_NEAREST) {
                command.setStartPosition(entity.blockPosition());
            }
        }

        private void continueActiveCommand() {
            if (activeCommand == null) {
                return;
            }
            if (activeCommand.kind() == IntentKind.MOVE) {
                if (entity.getNavigation().isDone()) {
                    activeCommand = null;
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
            }
        }

        private void collectItems(QueuedCommand command) {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                activeCommand = null;
                return;
            }
            if (!isWithinStartDistance(command, entity.position(), COLLECT_RADIUS)) {
                activeCommand = null;
                return;
            }
            ItemEntity itemEntity = nearestItem(serverLevel, command);
            if (itemEntity == null) {
                entity.getNavigation().stop();
                activeCommand = null;
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
                activeCommand = null;
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
                activeCommand = null;
                return;
            }
            BlockPos blockPos = command.blockPos();
            if (!canUseBlockTarget(serverLevel, command, blockPos)) {
                activeCommand = null;
                return;
            }
            BlockState blockState = serverLevel.getBlockState(blockPos);
            if (blockState.isAir() || blockState.getDestroySpeed(serverLevel, blockPos) < 0.0F) {
                activeCommand = null;
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
            activeCommand = null;
        }

        private void placeBlock(QueuedCommand command) {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                activeCommand = null;
                return;
            }
            BlockPos blockPos = command.blockPos();
            if (!canUseBlockTarget(serverLevel, command, blockPos)) {
                activeCommand = null;
                return;
            }
            ItemStack mainHandStack = entity.getMainHandItem();
            if (!(mainHandStack.getItem() instanceof BlockItem blockItem)) {
                activeCommand = null;
                return;
            }
            if (!serverLevel.getBlockState(blockPos).isAir()) {
                activeCommand = null;
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
                activeCommand = null;
                return;
            }
            entity.getNavigation().stop();
            if (serverLevel.setBlock(blockPos, placedState, Block.UPDATE_ALL)) {
                mainHandStack.shrink(1);
                entity.swingMainHandAction();
            }
            activeCommand = null;
        }

        private void attackNearest(QueuedCommand command) {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                activeCommand = null;
                return;
            }
            if (!isWithinStartDistance(command, entity.position(), command.radius())) {
                entity.getNavigation().stop();
                activeCommand = null;
                return;
            }
            LivingEntity target = nearestAttackTarget(serverLevel, command);
            if (target == null) {
                entity.getNavigation().stop();
                activeCommand = null;
                return;
            }
            entity.getLookControl().setLookAt(target);
            if (entity.distanceToSqr(target) > ATTACK_REACH_DISTANCE * ATTACK_REACH_DISTANCE) {
                entity.getNavigation().moveTo(target, ATTACK_SPEED);
                return;
            }
            entity.getNavigation().stop();
            entity.swingMainHandAction();
            entity.doHurtTarget(target);
            activeCommand = null;
        }

        private LivingEntity nearestAttackTarget(ServerLevel serverLevel, QueuedCommand command) {
            ServerPlayer owner = owner();
            List<LivingEntity> targets = serverLevel.getEntitiesOfClass(
                    LivingEntity.class,
                    entity.getBoundingBox().inflate(command.radius()),
                    target -> target.isAlive()
                            && target != entity
                            && !(target instanceof OpenPlayerNpcEntity)
                            && (owner == null || !target.getUUID().equals(owner.getUUID()))
                            && isWithinStartDistance(command, target.position(), command.radius())
                            && entity.hasLineOfSight(target)
            );
            return targets.stream()
                    .min(Comparator.comparingDouble(target -> entity.distanceToSqr(target)))
                    .orElse(null);
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

        private static CommandCoordinate parseCoordinate(String instruction) {
            String trimmedInstruction = instruction.trim();
            String[] parts = trimmedInstruction.split("\\s+");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Instruction must contain three coordinates: x y z");
            }
            try {
                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                double z = Double.parseDouble(parts[2]);
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                    throw new IllegalArgumentException("Coordinates must be finite numbers");
                }
                return new CommandCoordinate(x, y, z);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Coordinates must be numbers", exception);
            }
        }

        private static CommandCoordinate parseCoordinateOrNull(String instruction) {
            try {
                return parseCoordinate(instruction);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        private static double parseOptionalRadiusOrNegative(String instruction, double defaultRadius, double maxRadius) {
            String trimmedInstruction = instruction.trim();
            if (trimmedInstruction.isEmpty()) {
                return defaultRadius;
            }
            try {
                double radius = Double.parseDouble(trimmedInstruction);
                if (!Double.isFinite(radius) || radius <= 0.0D) {
                    return -1.0D;
                }
                return Math.min(radius, maxRadius);
            } catch (NumberFormatException exception) {
                return -1.0D;
            }
        }

        private static AutomationCommandResult accepted(String message) {
            return new AutomationCommandResult(AutomationCommandStatus.ACCEPTED, message);
        }

        private static AutomationCommandResult rejected(String message) {
            return new AutomationCommandResult(AutomationCommandStatus.REJECTED, message);
        }

        private static boolean isWorldAction(IntentKind kind) {
            return kind == IntentKind.BREAK_BLOCK
                    || kind == IntentKind.PLACE_BLOCK
                    || kind == IntentKind.ATTACK_NEAREST;
        }

        private record CommandCoordinate(double x, double y, double z) {
        }

        private static final class QueuedCommand {
            private final IntentKind kind;
            private final CommandCoordinate coordinate;
            private final double radius;
            private BlockPos startPosition;
            private int reachTicks;

            private QueuedCommand(IntentKind kind, CommandCoordinate coordinate, double radius) {
                this.kind = kind;
                this.coordinate = coordinate;
                this.radius = radius;
            }

            private static QueuedCommand move(CommandCoordinate coordinate) {
                return new QueuedCommand(IntentKind.MOVE, coordinate, 0.0D);
            }

            private static QueuedCommand look(CommandCoordinate coordinate) {
                return new QueuedCommand(IntentKind.LOOK, coordinate, 0.0D);
            }

            private static QueuedCommand followOwner() {
                return new QueuedCommand(IntentKind.FOLLOW_OWNER, null, 0.0D);
            }

            private static QueuedCommand collectItems() {
                return new QueuedCommand(IntentKind.COLLECT_ITEMS, null, 0.0D);
            }

            private static QueuedCommand breakBlock(CommandCoordinate coordinate) {
                return new QueuedCommand(IntentKind.BREAK_BLOCK, coordinate, 0.0D);
            }

            private static QueuedCommand placeBlock(CommandCoordinate coordinate) {
                return new QueuedCommand(IntentKind.PLACE_BLOCK, coordinate, 0.0D);
            }

            private static QueuedCommand attackNearest(double radius) {
                return new QueuedCommand(IntentKind.ATTACK_NEAREST, null, radius);
            }

            private IntentKind kind() {
                return kind;
            }

            private CommandCoordinate coordinate() {
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

            private BlockPos blockPos() {
                return BlockPos.containing(coordinate.x(), coordinate.y(), coordinate.z());
            }
        }
    }
}
