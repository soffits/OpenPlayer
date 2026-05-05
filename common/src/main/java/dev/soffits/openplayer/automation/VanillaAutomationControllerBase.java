package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.aicore.AICoreEventBus;
import dev.soffits.openplayer.automation.AutomationInstructionParser.Coordinate;
import dev.soffits.openplayer.automation.capability.RuntimeCapabilityRegistry;
import dev.soffits.openplayer.automation.navigation.LoadedAreaNavigator;
import dev.soffits.openplayer.automation.navigation.NavigationRuntime;
import dev.soffits.openplayer.automation.navigation.NavigationTarget;
import dev.soffits.openplayer.automation.policy.MovementPolicyLoader;
import dev.soffits.openplayer.automation.policy.MovementProfile;
import dev.soffits.openplayer.automation.survival.SurvivalCooldownPolicy;
import dev.soffits.openplayer.automation.work.WorkRepeatPolicy;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.debug.OpenPlayerRawTrace;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentPolicies;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

abstract class VanillaAutomationControllerBase implements AutomationController {
        protected static final String NAME = "vanilla";
        protected static final double PLAYER_LIKE_NAVIGATION_SPEED = 1.25D;
        protected static final double PLAYER_LIKE_MOVEMENT_ATTRIBUTE_SPEED = 0.18D;
        protected static final String DROPPED_ITEM_NAVIGATION_REJECTED_REASON = "item_navigation_rejected";
        protected static final double FOLLOW_STOP_DISTANCE = 3.0D;
        protected static final double FOLLOW_START_DISTANCE = 4.0D;
        protected static final double COLLECT_RADIUS = 16.0D;
        protected static final double COLLECT_REACH_DISTANCE = 1.5D;
        protected static final int COLLECT_REACH_TICKS = 20;
        protected static final double BLOCK_TASK_MAX_DISTANCE = 24.0D;
        protected static final double BLOCK_INTERACTION_DISTANCE = 4.0D;
        protected static final double OWNER_ITEM_TRANSFER_DISTANCE = 4.0D;
        protected static final int CONTAINER_SCAN_RADIUS = 4;
        protected static final double ATTACK_DEFAULT_RADIUS = 12.0D;
        protected static final double ATTACK_MAX_RADIUS = 24.0D;
        protected static final double ATTACK_REACH_DISTANCE = 2.5D;
        protected static final double GUARD_DEFAULT_RADIUS = 12.0D;
        protected static final double GUARD_MAX_RADIUS = 16.0D;
        protected static final double PATROL_MAX_DISTANCE = 32.0D;
        protected static final double GOTO_DEFAULT_RADIUS = LoadedAreaNavigator.DEFAULT_RADIUS;
        protected static final double GOTO_MAX_RADIUS = LoadedAreaNavigator.MAX_RADIUS;
        protected static final double GOTO_REACH_DISTANCE = 2.0D;
        protected static final int MOVE_MAX_TICKS = 20 * 60;
        protected static final int SHORT_TASK_MAX_TICKS = 20 * 30;
        protected static final int COLLECT_MAX_TICKS = 20 * 45;
        protected static final int LONG_TASK_MAX_TICKS = 20 * 120;
        protected static final int STUCK_CHECK_INTERVAL_TICKS = 40;
        protected static final double STUCK_MIN_PROGRESS_DISTANCE = 0.15D;
        protected static final int STUCK_MAX_CHECKS = 4;
        protected static final int NAVIGATION_MAX_RECOVERIES = 2;
        protected static final int INTERACTION_COOLDOWN_TICKS = 10;
        protected static final int SURVIVAL_ACTION_COOLDOWN_TICKS = 20 * 4;
        protected static final int SURVIVAL_DIAGNOSTIC_COOLDOWN_TICKS = 20;
        protected static final double SURVIVAL_DANGER_RADIUS = 8.0D;
        protected static final double SURVIVAL_PROJECTILE_RADIUS = 6.0D;

        protected final OpenPlayerNpcEntity entity;
        protected final Queue<QueuedCommand> queuedCommands = new ArrayDeque<>();
        protected final InteractionCooldown interactionCooldown = new InteractionCooldown(INTERACTION_COOLDOWN_TICKS);
        protected final SurvivalCooldownPolicy survivalCooldown = new SurvivalCooldownPolicy(
                SURVIVAL_ACTION_COOLDOWN_TICKS,
                SURVIVAL_DIAGNOSTIC_COOLDOWN_TICKS
        );
        protected final NavigationRuntime navigationRuntime = new NavigationRuntime(NAVIGATION_MAX_RECOVERIES);
        protected final LoadedAreaNavigator loadedAreaNavigator = new LoadedAreaNavigator();
        protected final AICoreEventBus aicoreEventBus = new AICoreEventBus(128);
        protected final MovementProfile movementProfile;
        protected QueuedCommand activeCommand;
        protected AutomationControllerMonitor activeMonitor;
        protected NpcOwnerId ownerId;
        protected String idleSurvivalReason = "idle";
        protected boolean paused;

        protected VanillaAutomationControllerBase(OpenPlayerNpcEntity entity) {
            if (entity == null) {
                throw new IllegalArgumentException("entity cannot be null");
            }
            this.entity = entity;
            this.movementProfile = MovementPolicyLoader.effectivePolicy(entity.persistedMovementPolicy().orElse(null));
        }

    protected static boolean isLocalWorldOrInventoryAction(IntentKind kind) {
        return RuntimeIntentPolicies.isLocalWorldOrInventoryAction(kind);
    }

    protected static NavigationTarget droppedItemNavigationTarget(String itemTypeId, BlockPos standPosition) {
        return NavigationTarget.position(
                standPosition.getX() + 0.5D,
                standPosition.getY(),
                standPosition.getZ() + 0.5D
        );
    }

    protected static String itemPickupCompletionReason(int inventoryDelta, boolean targetAlive, boolean closeEnough,
                                             boolean inventoryCanAccept) {
        if (inventoryDelta > 0) {
            return "picked_up inventory_delta=" + inventoryDelta;
        }
        if (!targetAlive && closeEnough) {
            return "picked_up item_disappeared_close";
        }
        if (!inventoryCanAccept) {
            return "inventory_full";
        }
        if (!targetAlive) {
            return "item_target_lost";
        }
        return "item_not_picked_up";
    }

    protected static String blockBreakSummary(String blockId, BlockPos target, String inventoryDelta, String nearbyDropDelta) {
        return "block=" + blockId
                + " target=" + target.toShortString()
                + " inventory_delta=" + inventoryDelta
                + " nearby_drop_delta=" + nearbyDropDelta;
    }

        @Override
        public void setOwnerId(NpcOwnerId ownerId) {
            if (ownerId == null) {
                throw new IllegalArgumentException("ownerId cannot be null");
            }
            this.ownerId = ownerId;
        }

        @Override
        public AutomationControllerSnapshot snapshot() {
            List<IntentKind> queuedKinds = queuedKinds();
            boolean showSurvivalReason = activeCommand == null && !"idle".equals(idleSurvivalReason);
            AutomationControllerMonitorStatus monitorStatus = showSurvivalReason
                    ? AutomationControllerMonitorStatus.IDLE
                    : activeMonitor == null
                    ? AutomationControllerMonitorStatus.IDLE
                    : activeMonitor.status();
            String monitorReason = showSurvivalReason ? idleSurvivalReason
                    : activeMonitor == null ? "idle" : activeMonitor.boundedReason();
            int elapsedTicks = activeMonitor == null ? 0 : activeMonitor.elapsedTicks();
            int maxTicks = activeMonitor == null ? 0 : activeMonitor.maxTicks();
            return new AutomationControllerSnapshot(
                    Math.round(entity.getHealth()),
                    entity.selectedHotbarSlot(),
                    activeCommand != null,
                    activeCommand == null ? null : activeCommand.kind(),
                    monitorStatus,
                    monitorReason,
                    elapsedTicks,
                    maxTicks,
                    queuedKinds.size(),
                    queuedKinds,
                    interactionCooldown.remainingTicks(),
                    paused,
                    navigationRuntime.snapshot()
            );
        }

        @Override
        public void stopAll() {
            queuedCommands.clear();
            if (activeCommand != null) {
                OpenPlayerDebugEvents.record("automation", "cancelled", null, null, null,
                        "kind=" + activeCommand.kind().name() + " reason=stop_all");
                OpenPlayerRawTrace.automationOperation("cancelled", activeCommand.kind().name(), "reason=stop_all");
            }
            activeCommand = null;
            if (activeMonitor != null) {
                activeMonitor.reset();
            }
            interactionCooldown.reset();
            survivalCooldown.reset();
            idleSurvivalReason = "idle";
            paused = false;
            navigationRuntime.reset();
            resetAndStopNavigation();
            entity.setDeltaMovement(Vec3.ZERO);
            entity.getLookControl().setLookAt(entity.getX(), entity.getEyeY(), entity.getZ());
        }

        protected String statusSummary() {
            List<String> capabilityLines = RuntimeCapabilityRegistry.reportLines();
            return snapshot().summary() + " capabilities="
                    + String.join(" | ", capabilityLines.subList(0, Math.min(3, capabilityLines.size())));
        }

        protected AutomationCommandResult applyBodyLanguage(BodyLanguageInstruction instruction) {
            if (instruction == BodyLanguageInstruction.IDLE) {
                entity.setShiftKeyDown(false);
                return accepted("BODY_LANGUAGE accepted: idle");
            }
            if (instruction == BodyLanguageInstruction.WAVE || instruction == BodyLanguageInstruction.SWING) {
                entity.swingMainHandAction();
                return accepted("BODY_LANGUAGE accepted: " + instruction.name().toLowerCase());
            }
            if (instruction == BodyLanguageInstruction.CROUCH) {
                entity.setShiftKeyDown(true);
                return accepted("BODY_LANGUAGE accepted: crouch");
            }
            if (instruction == BodyLanguageInstruction.UNCROUCH) {
                entity.setShiftKeyDown(false);
                return accepted("BODY_LANGUAGE accepted: uncrouch");
            }
            if (instruction == BodyLanguageInstruction.LOOK_OWNER) {
                ServerPlayer player = owner();
                if (player == null) {
                    return rejected("BODY_LANGUAGE look_owner requires an available owner in this dimension");
                }
                entity.getLookControl().setLookAt(player.getX(), player.getEyeY(), player.getZ());
                return accepted("BODY_LANGUAGE accepted: look_owner");
            }
            return rejected(BodyLanguageInstructionParser.USAGE);
        }

        protected List<IntentKind> queuedKinds() {
            List<IntentKind> queuedKinds = new ArrayList<>(queuedCommands.size());
            for (QueuedCommand queuedCommand : queuedCommands) {
                queuedKinds.add(queuedCommand.kind());
            }
            return queuedKinds;
        }

        protected void watchdogActiveCommand() {
            if (activeCommand == null || activeMonitor == null) {
                return;
            }
            AutomationControllerMonitorStatus status = activeMonitor.tick(
                    entity.getX(), entity.getY(), entity.getZ(), requiresNavigationProgress(activeCommand)
            );
            if (status == AutomationControllerMonitorStatus.STUCK && tryRecoverActiveNavigation()) {
                OpenPlayerDebugEvents.record("automation", "recovering", null, null, null,
                        "kind=" + activeCommand.kind().name() + " reason=" + navigationRuntime.snapshot().lastReason());
                OpenPlayerRawTrace.automationOperation("recovering", activeCommand.kind().name(),
                        "reason=" + navigationRuntime.snapshot().lastReason());
                activeMonitor.start(entity.getX(), entity.getY(), entity.getZ());
                return;
            }
            if (status == AutomationControllerMonitorStatus.TIMED_OUT || status == AutomationControllerMonitorStatus.STUCK) {
                String reason = activeMonitor.boundedReason();
                cancelNavigation(reason);
                OpenPlayerDebugEvents.record("automation", status.name(), null, null, null,
                        "kind=" + activeCommand.kind().name() + " reason=" + reason);
                OpenPlayerRawTrace.automationOperation(status.name(), activeCommand.kind().name(),
                        "reason=" + reason);
                activeMonitor.cancel(reason);
                activeCommand = null;
            }
        }

        protected void completeActiveCommand() {
            completeActiveCommand("completed");
        }

        protected void completeActiveCommand(String reason) {
            completeActiveCommand(reason, false);
        }

        protected void completeActiveCommand(String reason, boolean allowRepeat) {
            QueuedCommand repeatCommand = null;
            if (activeCommand != null) {
                if (WorkRepeatPolicy.shouldQueueNextRepeat(
                        allowRepeat, activeCommand.repeatRemaining(), entity.allowWorldActions()
                )) {
                    repeatCommand = activeCommand.nextRepeat();
                    reason = reason + " repeat_remaining=" + repeatCommand.repeatRemaining();
                } else if (allowRepeat && activeCommand.repeatRemaining() > 1 && !entity.allowWorldActions()) {
                    reason = reason + " repeat_stopped=world_actions_disabled";
                }
                OpenPlayerDebugEvents.record("automation", "completed", null, null, null,
                        "kind=" + activeCommand.kind().name() + " reason=" + AutomationControllerMonitor.bounded(reason));
                OpenPlayerRawTrace.automationOperation("completed", activeCommand.kind().name(),
                        "entity=" + entity.getUUID() + " position=" + entity.position()
                                + " reason=" + AutomationControllerMonitor.bounded(reason));
            }
            if (activeMonitor != null) {
                activeMonitor.complete(reason);
            }
            navigationRuntime.complete();
            activeCommand = null;
            if (repeatCommand != null) {
                queuedCommands.add(repeatCommand);
            }
        }

        protected void failActiveCommand(String reason) {
            if (activeCommand != null) {
                OpenPlayerDebugEvents.record("automation", "cancelled", null, null, null,
                        "kind=" + activeCommand.kind().name() + " reason=" + reason);
                OpenPlayerRawTrace.automationOperation("cancelled", activeCommand.kind().name(), "reason=" + reason);
            }
            if (activeMonitor != null) {
                activeMonitor.cancel(reason);
            }
            cancelNavigation(reason);
            activeCommand = null;
        }

        protected boolean requiresNavigationProgress(QueuedCommand command) {
            return command.kind() != IntentKind.LOOK && !entity.getNavigation().isDone();
        }

        protected boolean tryRecoverActiveNavigation() {
            if (activeCommand == null || !navigationRuntime.tryRecover("stuck")) {
                return false;
            }
            entity.getNavigation().stop();
            Vec3 look = entity.getLookAngle();
            entity.setDeltaMovement(entity.getDeltaMovement().add(look.x * 0.05D, 0.2D, look.z * 0.05D));
            entity.getJumpControl().jump();
            reissueActiveNavigation();
            if (activeCommand == null) {
                return false;
            }
            navigationRuntime.markActive("recovered");
            return true;
        }

        protected void reissueActiveNavigation() {
            if (activeCommand == null) {
                return;
            }
            IntentKind kind = activeCommand.kind();
            if (kind == IntentKind.MOVE) {
                moveToPosition(activeCommand.coordinate());
                return;
            }
            if (kind == IntentKind.GOTO) {
                startGoto(activeCommand);
                return;
            }
            if (kind == IntentKind.PATROL) {
                if (activeCommand.returningToStart() && activeCommand.startPosition() != null) {
                    moveToBlock(activeCommand.startPosition());
                } else {
                    moveToPosition(activeCommand.coordinate());
                }
                return;
            }
            if (kind == IntentKind.BREAK_BLOCK || kind == IntentKind.PLACE_BLOCK) {
                moveNearBlock(activeCommand.blockPos());
                return;
            }
        }

        protected AutomationControllerMonitor newMonitor(QueuedCommand command) {
            return new AutomationControllerMonitor(
                    maxTicks(command),
                    STUCK_CHECK_INTERVAL_TICKS,
                    STUCK_MIN_PROGRESS_DISTANCE,
                    STUCK_MAX_CHECKS
            );
        }

        protected int maxTicks(QueuedCommand command) {
            IntentKind kind = command.kind();
            if (command.maxTicks() > 0) {
                return command.maxTicks();
            }
            if (kind == IntentKind.MOVE || kind == IntentKind.GOTO) {
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

        protected boolean isCoordinateLoaded(Coordinate coordinate) {
            ServerLevel serverLevel = serverLevel();
            return serverLevel != null
                    && serverLevel.hasChunkAt(BlockPos.containing(coordinate.x(), coordinate.y(), coordinate.z()));
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

        protected static AutomationCommandResult accepted(String message) {
            return new AutomationCommandResult(AutomationCommandStatus.ACCEPTED, message);
        }

        protected static AutomationCommandResult rejected(String message) {
            return new AutomationCommandResult(AutomationCommandStatus.REJECTED, message);
        }

        protected boolean acquireInteractionCooldown() {
            return interactionCooldown.tryAcquire();
        }

        protected boolean canAcquireInteractionCooldown() {
            return interactionCooldown.canAcquire();
        }

        protected void rollbackInteractionCooldown() {
            interactionCooldown.rollbackAcquire();
        }

        protected String interactionCooldownMessage() {
            return "Interaction cooldown active for " + interactionCooldown.remainingTicks() + " ticks";
        }

        protected String buildTargetRejection(ServerLevel serverLevel, BlockPos blockPos, BlockState placedState) {
            if (!serverLevel.hasChunkAt(blockPos)) {
                return "target_chunk_unloaded";
            }
            if (!serverLevel.getBlockState(blockPos).isAir()) {
                return "target_occupied";
            }
            if (!placedState.canSurvive(serverLevel, blockPos)
                    || !serverLevel.isUnobstructed(placedState, blockPos, CollisionContext.empty())) {
                return "target_collision_or_support";
            }
            return null;
        }

        protected static Item itemByIdOrNull(ResourceLocation id) {
            return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        }

        protected static String blockId(Block block) {
            return BuiltInRegistries.BLOCK.getKey(block).toString();
        }

        protected static String itemId(Item item) {
            return BuiltInRegistries.ITEM.getKey(item).toString();
        }

        protected static int compareBlockPos(BlockPos first, BlockPos second) {
            if (second == null) {
                return -1;
            }
            int y = Integer.compare(first.getY(), second.getY());
            if (y != 0) {
                return y;
            }
            int x = Integer.compare(first.getX(), second.getX());
            if (x != 0) {
                return x;
            }
            return Integer.compare(first.getZ(), second.getZ());
        }

        protected static String formatRadius(double radius) {
            if (radius == Math.rint(radius)) {
                return Integer.toString((int) radius);
            }
            return Double.toString(radius);
        }

        protected abstract void resetAndStopNavigation();

        protected abstract void cancelNavigation(String reason);

        protected abstract boolean moveToPosition(Coordinate coordinate);

        protected abstract void startGoto(QueuedCommand command);

        protected abstract boolean moveToBlock(BlockPos blockPos);

        protected abstract void moveNearBlock(BlockPos blockPos);
}
