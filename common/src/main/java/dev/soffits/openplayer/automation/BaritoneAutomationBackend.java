package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class BaritoneAutomationBackend implements AutomationBackend {
    public static final String NAME = "baritone";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AutomationBackendStatus status() {
        BaritoneCommandBridge bridge = BaritoneCommandBridge.resolve();
        if (!bridge.available()) {
            return new AutomationBackendStatus(NAME, AutomationBackendState.UNAVAILABLE, bridge.message());
        }
        return new AutomationBackendStatus(
                NAME,
                AutomationBackendState.AVAILABLE,
                "Baritone command bridge available for the primary local player"
        );
    }

    @Override
    public AutomationController createController(OpenPlayerNpcEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        return new BaritoneAutomationController(entity);
    }

    private static final class BaritoneAutomationController implements AutomationController {
        private final OpenPlayerNpcEntity entity;
        private NpcOwnerId ownerId;

        private BaritoneAutomationController(OpenPlayerNpcEntity entity) {
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
            BaritoneCommandBridge bridge = BaritoneCommandBridge.resolve();
            if (!bridge.available()) {
                return rejected(bridge.message());
            }
            try {
                IntentKind kind = intent.kind();
                if (kind == IntentKind.STOP) {
                    bridge.execute("stop");
                    return accepted("STOP sent to the primary Baritone-controlled local player");
                }
                if (kind == IntentKind.MOVE) {
                    CommandCoordinate coordinate = parseCoordinate(intent.instruction());
                    bridge.execute("goto " + coordinate.commandText());
                    return accepted("MOVE sent to the primary Baritone-controlled local player, not the NPC entity");
                }
                if (kind == IntentKind.FOLLOW_OWNER) {
                    if (ownerId == null) {
                        return rejected("FOLLOW_OWNER requires an NPC owner");
                    }
                    if (owner() == null) {
                        return rejected("NPC owner is unavailable in this dimension");
                    }
                    bridge.execute("follow players");
                    return accepted("FOLLOW_OWNER sent as Baritone follow players for the primary local player, not the NPC entity");
                }
                if (kind == IntentKind.LOOK) {
                    return rejected("LOOK is unsupported by the Baritone command bridge");
                }
                return rejected("Unsupported intent: " + kind.name());
            } catch (IllegalArgumentException exception) {
                return rejected(exception.getMessage());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                return rejected("Baritone command bridge failed: " + conciseMessage(exception));
            }
        }

        @Override
        public void tick() {
        }

        @Override
        public void stopAll() {
            BaritoneCommandBridge bridge = BaritoneCommandBridge.resolve();
            if (!bridge.available()) {
                return;
            }
            try {
                bridge.execute("stop");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            }
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

        private static CommandCoordinate parseCoordinate(String instruction) {
            String trimmedInstruction = instruction.trim();
            String[] parts = trimmedInstruction.split("\\s+");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Instruction must contain three coordinates: x y z");
            }
            try {
                double x = parseCoordinatePart(parts[0]);
                double y = parseCoordinatePart(parts[1]);
                double z = parseCoordinatePart(parts[2]);
                return new CommandCoordinate(x, y, z);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Coordinates must be finite numbers", exception);
            }
        }

        private static double parseCoordinatePart(String value) {
            double coordinate = Double.parseDouble(value);
            if (!Double.isFinite(coordinate)) {
                throw new NumberFormatException("coordinate is not finite");
            }
            return coordinate;
        }

        private static AutomationCommandResult accepted(String message) {
            return new AutomationCommandResult(AutomationCommandStatus.ACCEPTED, message);
        }

        private static AutomationCommandResult rejected(String message) {
            return new AutomationCommandResult(AutomationCommandStatus.REJECTED, message);
        }
    }

    private record CommandCoordinate(double x, double y, double z) {
        private String commandText() {
            return coordinateText(x) + " " + coordinateText(y) + " " + coordinateText(z);
        }

        private static String coordinateText(double value) {
            long wholeValue = (long) value;
            if ((double) wholeValue == value) {
                return Long.toString(wholeValue);
            }
            return Double.toString(value);
        }
    }

    private record BaritoneCommandBridge(Object commandManager, String message) {
        private static BaritoneCommandBridge resolve() {
            try {
                Class<?> baritoneApiClass = Class.forName("baritone.api.BaritoneAPI");
                Object provider = baritoneApiClass.getMethod("getProvider").invoke(null);
                if (provider == null) {
                    return unavailable("Baritone provider unavailable");
                }
                Object primaryBaritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
                if (primaryBaritone == null) {
                    return unavailable("Primary Baritone instance unavailable");
                }
                Object commandManager = primaryBaritone.getClass().getMethod("getCommandManager").invoke(primaryBaritone);
                if (commandManager == null) {
                    return unavailable("Baritone command manager unavailable");
                }
                return new BaritoneCommandBridge(commandManager, "available");
            } catch (ClassNotFoundException exception) {
                return unavailable("Baritone API classes not found");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                return unavailable("Baritone command bridge unavailable: " + conciseMessage(exception));
            }
        }

        private static BaritoneCommandBridge unavailable(String message) {
            return new BaritoneCommandBridge(null, message);
        }

        private boolean available() {
            return commandManager != null;
        }

        private void execute(String command) throws ReflectiveOperationException {
            try {
                commandManager.getClass().getMethod("execute", String.class).invoke(commandManager, command);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw exception;
            }
        }
    }

    private static String conciseMessage(Throwable throwable) {
        Throwable cause = throwable instanceof InvocationTargetException invocationTargetException
                && invocationTargetException.getCause() != null
                ? invocationTargetException.getCause()
                : throwable;
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }
}
