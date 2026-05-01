package dev.soffits.openplayer.entity;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

final class RuntimeCommandExecutor {
    private static final double MOVE_SPEED = 1.0D;
    private static final double FOLLOW_SPEED = 1.0D;
    private static final double FOLLOW_STOP_DISTANCE = 3.0D;
    private static final double FOLLOW_START_DISTANCE = 4.0D;

    private final OpenPlayerNpcEntity entity;
    private final Queue<RuntimeQueuedCommand> queuedCommands = new ArrayDeque<>();
    private RuntimeQueuedCommand activeCommand;
    private NpcOwnerId ownerId;

    RuntimeCommandExecutor(OpenPlayerNpcEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        this.entity = entity;
    }

    void setOwnerId(NpcOwnerId ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId cannot be null");
        }
        this.ownerId = ownerId;
    }

    CommandSubmissionResult submit(AiPlayerNpcCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        CommandIntent intent = command.intent();
        IntentKind kind = intent.kind();
        if (kind == IntentKind.STOP) {
            stopAll();
            return accepted("STOP accepted");
        }
        if (kind == IntentKind.MOVE) {
            RuntimeCommandCoordinate coordinate = parseCoordinate(intent.instruction());
            queuedCommands.add(RuntimeQueuedCommand.move(coordinate));
            return accepted("MOVE accepted");
        }
        if (kind == IntentKind.LOOK) {
            RuntimeCommandCoordinate coordinate = parseCoordinate(intent.instruction());
            queuedCommands.add(RuntimeQueuedCommand.look(coordinate));
            return accepted("LOOK accepted");
        }
        if (kind == IntentKind.FOLLOW_OWNER) {
            if (ownerId == null) {
                return rejected("FOLLOW_OWNER requires an NPC owner");
            }
            if (owner() == null) {
                return rejected("NPC owner is unavailable in this dimension");
            }
            queuedCommands.add(RuntimeQueuedCommand.followOwner());
            return accepted("FOLLOW_OWNER accepted");
        }
        return rejected("Unsupported intent: " + kind.name());
    }

    void tick() {
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

    private void start(RuntimeQueuedCommand command) {
        if (command.kind() == IntentKind.MOVE) {
            RuntimeCommandCoordinate coordinate = command.coordinate();
            entity.getNavigation().moveTo(coordinate.x(), coordinate.y(), coordinate.z(), MOVE_SPEED);
            return;
        }
        if (command.kind() == IntentKind.LOOK) {
            RuntimeCommandCoordinate coordinate = command.coordinate();
            entity.getLookControl().setLookAt(coordinate.x(), coordinate.y(), coordinate.z());
            activeCommand = null;
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
        }
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

    void stopAll() {
        queuedCommands.clear();
        activeCommand = null;
        entity.getNavigation().stop();
        entity.setDeltaMovement(Vec3.ZERO);
        entity.getLookControl().setLookAt(entity.getX(), entity.getEyeY(), entity.getZ());
    }

    private static RuntimeCommandCoordinate parseCoordinate(String instruction) {
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
            return new RuntimeCommandCoordinate(x, y, z);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Coordinates must be numbers", exception);
        }
    }

    private static CommandSubmissionResult accepted(String message) {
        return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED, message);
    }

    private static CommandSubmissionResult rejected(String message) {
        return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, message);
    }
}
