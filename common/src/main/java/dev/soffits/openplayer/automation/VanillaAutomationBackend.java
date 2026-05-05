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
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

public final class VanillaAutomationBackend implements AutomationBackend {
    public static final String NAME = "vanilla";
    public static final double PLAYER_LIKE_NAVIGATION_SPEED = 1.25D;
    static final String DROPPED_ITEM_NAVIGATION_REJECTED_REASON = "navigation_item_position_rejected";

    static boolean isLocalWorldOrInventoryAction(IntentKind kind) {
        return RuntimeIntentPolicies.isLocalWorldOrInventoryAction(kind);
    }

    static NavigationTarget droppedItemNavigationTarget(Vec3 position) {
        return NavigationTarget.position(position.x, position.y, position.z);
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
        private static final double FOLLOW_STOP_DISTANCE = 3.0D;
        private static final double FOLLOW_START_DISTANCE = 4.0D;
        private static final double COLLECT_RADIUS = 16.0D;
        private static final double COLLECT_REACH_DISTANCE = 1.5D;
        private static final int COLLECT_REACH_TICKS = 20;
        private static final double BLOCK_TASK_MAX_DISTANCE = 24.0D;
        private static final double BLOCK_INTERACTION_DISTANCE = 4.0D;
        private static final double OWNER_ITEM_TRANSFER_DISTANCE = 4.0D;
        private static final int CONTAINER_SCAN_RADIUS = 4;
        private static final double ATTACK_DEFAULT_RADIUS = 12.0D;
        private static final double ATTACK_MAX_RADIUS = 24.0D;
        private static final double ATTACK_REACH_DISTANCE = 2.5D;
        private static final double GUARD_DEFAULT_RADIUS = 12.0D;
        private static final double GUARD_MAX_RADIUS = 16.0D;
        private static final double PATROL_MAX_DISTANCE = 32.0D;
        private static final double GOTO_DEFAULT_RADIUS = LoadedAreaNavigator.DEFAULT_RADIUS;
        private static final double GOTO_MAX_RADIUS = LoadedAreaNavigator.MAX_RADIUS;
        private static final double GOTO_REACH_DISTANCE = 2.0D;
        private static final int MOVE_MAX_TICKS = 20 * 60;
        private static final int SHORT_TASK_MAX_TICKS = 20 * 30;
        private static final int COLLECT_MAX_TICKS = 20 * 45;
        private static final int LONG_TASK_MAX_TICKS = 20 * 120;
        private static final int STUCK_CHECK_INTERVAL_TICKS = 40;
        private static final double STUCK_MIN_PROGRESS_DISTANCE = 0.15D;
        private static final int STUCK_MAX_CHECKS = 4;
        private static final int NAVIGATION_MAX_RECOVERIES = 2;
        private static final int INTERACTION_COOLDOWN_TICKS = 10;
        private static final int SURVIVAL_ACTION_COOLDOWN_TICKS = 20 * 4;
        private static final int SURVIVAL_DIAGNOSTIC_COOLDOWN_TICKS = 20;
        private static final double SURVIVAL_DANGER_RADIUS = 8.0D;
        private static final double SURVIVAL_PROJECTILE_RADIUS = 6.0D;

        private final OpenPlayerNpcEntity entity;
        private final Queue<QueuedCommand> queuedCommands = new ArrayDeque<>();
        private final InteractionCooldown interactionCooldown = new InteractionCooldown(INTERACTION_COOLDOWN_TICKS);
        private final SurvivalCooldownPolicy survivalCooldown = new SurvivalCooldownPolicy(
                SURVIVAL_ACTION_COOLDOWN_TICKS,
                SURVIVAL_DIAGNOSTIC_COOLDOWN_TICKS
        );
        private final NavigationRuntime navigationRuntime = new NavigationRuntime(NAVIGATION_MAX_RECOVERIES);
        private final LoadedAreaNavigator loadedAreaNavigator = new LoadedAreaNavigator();
        private final AICoreEventBus aicoreEventBus = new AICoreEventBus(128);
        private QueuedCommand activeCommand;
        private AutomationControllerMonitor activeMonitor;
        private NpcOwnerId ownerId;
        private String idleSurvivalReason = "idle";
        private boolean paused;

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
            ToolCall toolCall = MinecraftPrimitiveTools.toToolCall(intent).orElse(null);
            if (toolCall == null) {
                return submitPrimitiveIntent(intent);
            }
            AICoreNpcToolExecutor executor = new AICoreNpcToolExecutor(entity, aicoreEventBus,
                    primitiveIntent -> {
                        AutomationCommandResult primitiveResult = submitPrimitiveIntent(primitiveIntent);
                        CommandSubmissionStatus status = primitiveResult.status() == AutomationCommandStatus.ACCEPTED
                                ? CommandSubmissionStatus.ACCEPTED
                                : CommandSubmissionStatus.REJECTED;
                        return new CommandSubmissionResult(status, primitiveResult.message());
                    });
            ToolResult result = executor.execute(toolCall, new ToolValidationContext(entity.allowWorldActions()));
            if (result.status() == ToolResultStatus.SUCCESS || result.status() == ToolResultStatus.RUNNING) {
                return accepted(result.summary());
            }
            return rejected(result.reason());
        }

        private AutomationCommandResult submitPrimitiveIntent(CommandIntent intent) {
            IntentKind kind = intent.kind();
            if (kind == IntentKind.PROVIDER_PLAN) {
                return submitProviderPlan(intent);
            }
            if (kind == IntentKind.STOP) {
                stopAll();
                return accepted("STOP accepted");
            }
            if (kind == IntentKind.PAUSE) {
                if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
                    return rejected("PAUSE requires a blank instruction");
                }
                paused = true;
                suspendNavigation();
                entity.setDeltaMovement(Vec3.ZERO);
                return accepted("PAUSE accepted");
            }
            if (kind == IntentKind.UNPAUSE) {
                if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
                    return rejected("UNPAUSE requires a blank instruction");
                }
                paused = false;
                reissueActiveNavigation();
                return accepted("UNPAUSE accepted");
            }
            if (kind == IntentKind.RESET_MEMORY) {
                if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
                    return rejected("RESET_MEMORY requires a blank instruction");
                }
                return accepted("RESET_MEMORY accepted: no automation-local memory was cleared");
            }
            if (kind == IntentKind.REPORT_STATUS) {
                if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
                    return rejected("REPORT_STATUS requires a blank instruction");
                }
                return accepted("REPORT_STATUS accepted: " + statusSummary());
            }
            if (kind == IntentKind.BODY_LANGUAGE) {
                BodyLanguageInstruction bodyLanguage = BodyLanguageInstructionParser.parseOrNull(intent.instruction());
                if (bodyLanguage == null) {
                    return rejected(BodyLanguageInstructionParser.USAGE);
                }
                if (paused) {
                    return rejected("BODY_LANGUAGE is unavailable while automation is paused");
                }
                return applyBodyLanguage(bodyLanguage);
            }
            if (kind == IntentKind.INVENTORY_QUERY) {
                if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
                    return rejected("INVENTORY_QUERY requires a blank instruction");
                }
                return accepted("INVENTORY_QUERY accepted: " + entity.inventorySummary());
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
            if (kind == IntentKind.GOTO) {
                return submitGoto(intent.instruction());
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
                CollectItemsInstruction collectInstruction = CollectItemsInstructionParser.parseOrNull(intent.instruction());
                if (collectInstruction == null) {
                    return rejected(CollectItemsInstructionParser.USAGE);
                }
                queuedCommands.add(QueuedCommand.collectItems(collectInstruction.item(), collectInstruction.radius()));
                return accepted("COLLECT_ITEMS accepted");
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
                if (!canAcquireInteractionCooldown()) {
                    return rejected(interactionCooldownMessage());
                }
                boolean dropped;
                String failureMessage;
                if (AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
                    dropped = acquireInteractionCooldown() && entity.dropSelectedHotbarStack();
                    failureMessage = "DROP_ITEM requires a selected hotbar stack";
                } else {
                    ParsedItemInstruction parsed = InventoryActionInstructionParser.parseItemCountOrNull(
                            intent.instruction(), false
                    );
                    if (parsed == null) {
                        return rejected("DROP_ITEM requires blank or instruction: <item_id> [count]");
                    }
                    dropped = acquireInteractionCooldown() && entity.dropInventoryItem(parsed.item(), parsed.count());
                    failureMessage = "DROP_ITEM requires the requested item count in NPC inventory";
                }
                if (!dropped) {
                    rollbackInteractionCooldown();
                    return rejected(failureMessage);
                }
                entity.swingMainHandAction();
                return accepted("DROP_ITEM accepted");
            }
            if (kind == IntentKind.EQUIP_ITEM) {
                ParsedItemInstruction parsed = InventoryActionInstructionParser.parseItemOnlyOrNull(intent.instruction());
                if (parsed == null) {
                    return rejected("EQUIP_ITEM requires instruction: <item_id>");
                }
                if (!canAcquireInteractionCooldown()) {
                    return rejected(interactionCooldownMessage());
                }
                if (!acquireInteractionCooldown() || !entity.equipMatchingItem(parsed.item())) {
                    rollbackInteractionCooldown();
                    return rejected("EQUIP_ITEM found no matching equippable armor or hotbar item: " + parsed.itemId());
                }
                entity.swingMainHandAction();
                return accepted("EQUIP_ITEM accepted");
            }
            if (kind == IntentKind.GIVE_ITEM) {
                ParsedItemInstruction parsed = InventoryActionInstructionParser.parseItemCountOrNull(intent.instruction(), true);
                if (parsed == null) {
                    return rejected("GIVE_ITEM requires instruction: <item_id> [count] [owner]");
                }
                ServerPlayer owner = owner();
                if (owner == null || !owner.isAlive()) {
                    return rejected("GIVE_ITEM requires the NPC owner online and alive in this dimension");
                }
                if (entity.distanceToSqr(owner) > OWNER_ITEM_TRANSFER_DISTANCE * OWNER_ITEM_TRANSFER_DISTANCE) {
                    return rejected("GIVE_ITEM requires the NPC owner within " + (int) OWNER_ITEM_TRANSFER_DISTANCE + " blocks");
                }
                if (!canAcquireInteractionCooldown()) {
                    return rejected(interactionCooldownMessage());
                }
                if (!acquireInteractionCooldown() || !entity.giveInventoryItemToPlayer(owner, parsed.item(), parsed.count())) {
                    rollbackInteractionCooldown();
                    return rejected("GIVE_ITEM requires the requested item count and owner inventory capacity");
                }
                entity.swingMainHandAction();
                return accepted("GIVE_ITEM accepted");
            }
            if (kind == IntentKind.DEPOSIT_ITEM || kind == IntentKind.STASH_ITEM) {
                WorkRepeatPolicy.InventoryRepeatInstruction repeatInstruction = WorkRepeatPolicy
                        .parseInventoryRepeatInstructionOrNull(intent.instruction());
                if (repeatInstruction == null) {
                    return rejected(kind.name() + " requires blank or instruction: <item_id> [count] [repeat=1.."
                            + WorkRepeatPolicy.MAX_REPEAT_COUNT + "]");
                }
                ParsedItemInstruction parsed = null;
                if (!AutomationInstructionParser.isBlankInstruction(repeatInstruction.itemInstruction())) {
                    parsed = InventoryActionInstructionParser.parseItemCountOrNull(
                            repeatInstruction.itemInstruction(), false
                    );
                    if (parsed == null) {
                        return rejected(kind.name() + " requires blank or instruction: <item_id> [count] [repeat=1.."
                                + WorkRepeatPolicy.MAX_REPEAT_COUNT + "]");
                    }
                }
                SafeContainerTarget target = kind == IntentKind.STASH_ITEM ? preferredStashContainer() : nearestSafeContainer();
                if (target == null) {
                    return rejected(kind.name() + " requires a loaded nearby safe Container block entity adapter");
                }
                if (!canAcquireInteractionCooldown()) {
                    return rejected(interactionCooldownMessage());
                }
                if (!acquireInteractionCooldown()) {
                    return rejected(interactionCooldownMessage());
                }
                int completedRepeats = 0;
                for (int repeatIndex = 0; repeatIndex < repeatInstruction.repeatCount(); repeatIndex++) {
                    if (parsed == null && !hasNormalInventoryToTransfer()) {
                        if (completedRepeats == 0) {
                            rollbackInteractionCooldown();
                            return rejected(kind.name() + " repeat stopped after " + completedRepeats
                                    + " completed iteration(s): no_inventory_to_transfer");
                        }
                        if (kind == IntentKind.STASH_ITEM) {
                            entity.rememberStash(dimensionId(serverLevel()), target.blockPos());
                        }
                        target.container().setChanged();
                        entity.swingMainHandAction();
                        return accepted(kind.name() + " accepted: container " + target.blockPos().toShortString()
                                + " repeat=" + completedRepeats + "/" + repeatInstruction.repeatCount()
                                + " stopped=no_inventory_to_transfer");
                    }
                    boolean transferred = depositToContainer(target.container(), parsed);
                    if (!transferred) {
                        if (completedRepeats == 0) {
                            rollbackInteractionCooldown();
                        }
                        if (completedRepeats > 0) {
                            if (kind == IntentKind.STASH_ITEM) {
                                entity.rememberStash(dimensionId(serverLevel()), target.blockPos());
                            }
                            target.container().setChanged();
                            entity.swingMainHandAction();
                            return accepted(kind.name() + " accepted: container " + target.blockPos().toShortString()
                                    + " repeat=" + completedRepeats + "/" + repeatInstruction.repeatCount()
                                    + " stopped=no_more_safe_transfer");
                        }
                        return rejected(kind.name() + " repeat stopped after " + completedRepeats
                                + " completed iteration(s): all requested normal inventory items must fit in the container");
                    }
                    completedRepeats++;
                }
                if (kind == IntentKind.STASH_ITEM) {
                    entity.rememberStash(dimensionId(serverLevel()), target.blockPos());
                }
                target.container().setChanged();
                entity.swingMainHandAction();
                return accepted(kind.name() + " accepted: container " + target.blockPos().toShortString()
                        + " repeat=" + completedRepeats);
            }
            if (kind == IntentKind.WITHDRAW_ITEM) {
                ParsedItemInstruction parsed = InventoryActionInstructionParser.parseItemCountOrNull(intent.instruction(), false);
                if (parsed == null) {
                    return rejected("WITHDRAW_ITEM requires instruction: <item_id> [count]");
                }
                SafeContainerTarget target = preferredStashContainer();
                if (target == null) {
                    return rejected("WITHDRAW_ITEM requires a loaded nearby safe Container block entity adapter");
                }
                if (!canAcquireInteractionCooldown()) {
                    return rejected(interactionCooldownMessage());
                }
                boolean transferred = acquireInteractionCooldown()
                        && withdrawFromContainer(target.container(), parsed.item(), parsed.count());
                if (!transferred) {
                    rollbackInteractionCooldown();
                    return rejected("WITHDRAW_ITEM requires exact item count in the container and NPC inventory capacity");
                }
                target.container().setChanged();
                entity.swingMainHandAction();
                return accepted("WITHDRAW_ITEM accepted: " + parsed.itemId() + " x" + parsed.count()
                        + " from container " + target.blockPos().toShortString());
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
            if (kind == IntentKind.INTERACT) {
                return submitInteract(intent.instruction());
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
            if (kind == IntentKind.ATTACK_TARGET) {
                return submitAttackTarget(intent.instruction());
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
            if (kind == IntentKind.LOCATE_LOADED_BLOCK) {
                return reportLoadedBlock(intent.instruction());
            }
            if (kind == IntentKind.LOCATE_LOADED_ENTITY) {
                return reportLoadedEntity(intent.instruction());
            }
            if (kind == IntentKind.FIND_LOADED_BIOME) {
                return reportLoadedBiome(intent.instruction());
            }
            if (AdvancedTaskPolicy.isUnsupportedAdvancedKind(kind)) {
                return rejected(AdvancedTaskPolicy.unsupportedReason(kind));
            }
            return rejected("Unsupported intent: " + kind.name());
        }

        private AutomationCommandResult submitProviderPlan(CommandIntent intent) {
            List<CommandIntent> steps;
            try {
                steps = ProviderPlanIntentCodec.decode(intent.instruction());
            } catch (IllegalArgumentException exception) {
                return rejected(exception.getMessage());
            }
            for (CommandIntent step : steps) {
                RuntimeIntentValidationResult validation = RuntimeIntentValidator.validate(step, entity.allowWorldActions());
                if (!validation.isAccepted()) {
                    return rejected("PROVIDER_PLAN step rejected: " + validation.message());
                }
            }
            if (steps.size() > 1) {
                return rejected("PROVIDER_PLAN with multiple steps requires the interactive planner");
            }
            for (CommandIntent step : steps) {
                AutomationCommandResult result = submitPrimitiveIntent(step);
                if (result.status() != AutomationCommandStatus.ACCEPTED) {
                    return rejected("PROVIDER_PLAN step rejected: " + result.message());
                }
            }
            return accepted("PROVIDER_PLAN accepted: " + steps.size()
                    + " validated primitive step(s) accepted or queued without replanning");
        }

        private AutomationCommandResult submitGoto(String instruction) {
            Coordinate coordinate = AutomationInstructionParser.parseCoordinateOrNull(instruction);
            if (coordinate == null) {
                return rejected("GOTO requires instruction: x y z");
            }
            return submitGotoCoordinate(coordinate);
        }

        private AutomationCommandResult submitInteract(String instruction) {
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

        private AutomationCommandResult submitAttackTarget(String instruction) {
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

        private AutomationCommandResult submitGotoCoordinate(Coordinate coordinate) {
            if (!isCoordinateLoaded(coordinate)) {
                return rejected("GOTO target chunk is not loaded");
            }
            queuedCommands.add(QueuedCommand.gotoCoordinate(coordinate));
            return accepted("GOTO accepted: position");
        }

        private AutomationCommandResult reportLoadedBlock(String instruction) {
            LoadedSearchInstruction loadedSearchInstruction = AdvancedTaskInstructionParser.parseLoadedSearchOrNull(instruction);
            if (loadedSearchInstruction == null) {
                return rejected(AdvancedTaskInstructionParser.LOCATE_LOADED_BLOCK_USAGE);
            }
            LoadedAreaNavigator.BlockSearchResult result = loadedAreaNavigator.nearestLoadedBlock(
                    serverLevel(), entity.position(), loadedSearchInstruction.resourceId(), loadedSearchInstruction.radius()
            );
            if (!result.found()) {
                return accepted("LOCATE_LOADED_BLOCK not found: target=" + loadedSearchInstruction.resourceId()
                        + " diagnostics=" + result.diagnostics().summary());
            }
            return accepted("LOCATE_LOADED_BLOCK found: target=" + loadedSearchInstruction.resourceId()
                    + " pos=" + result.blockPos().toShortString()
                    + " diagnostics=" + result.diagnostics().summary());
        }

        private AutomationCommandResult reportLoadedEntity(String instruction) {
            LoadedSearchInstruction loadedSearchInstruction = AdvancedTaskInstructionParser.parseLoadedSearchOrNull(instruction);
            if (loadedSearchInstruction == null) {
                return rejected(AdvancedTaskInstructionParser.LOCATE_LOADED_ENTITY_USAGE);
            }
            LoadedAreaNavigator.EntitySearchResult result = loadedAreaNavigator.nearestLoadedEntity(
                    serverLevel(),
                    entity.position(),
                    loadedSearchInstruction.resourceId(),
                    loadedSearchInstruction.radius(),
                    candidate -> candidate != entity && candidate.isAlive()
            );
            if (!result.found()) {
                return accepted("LOCATE_LOADED_ENTITY not found: target=" + loadedSearchInstruction.resourceId()
                        + " diagnostics=" + result.diagnostics().summary());
            }
            return accepted("LOCATE_LOADED_ENTITY found: target=" + loadedSearchInstruction.resourceId()
                    + " pos=" + result.entity().blockPosition().toShortString()
                    + " diagnostics=" + result.diagnostics().summary());
        }

        private AutomationCommandResult reportLoadedBiome(String instruction) {
            LoadedSearchInstruction loadedSearchInstruction = AdvancedTaskInstructionParser.parseLoadedSearchOrNull(instruction);
            if (loadedSearchInstruction == null) {
                return rejected(AdvancedTaskInstructionParser.FIND_LOADED_BIOME_USAGE);
            }
            LoadedAreaNavigator.BiomeSearchResult result = loadedAreaNavigator.nearestLoadedBiome(
                    serverLevel(), entity.position(), loadedSearchInstruction.resourceId(), loadedSearchInstruction.radius()
            );
            if (!result.found()) {
                return accepted("FIND_LOADED_BIOME not found: target=" + loadedSearchInstruction.resourceId()
                        + " diagnostics=" + result.diagnostics().summary());
            }
            return accepted("FIND_LOADED_BIOME found: target=" + loadedSearchInstruction.resourceId()
                    + " pos=" + result.blockPos().toShortString()
                    + " diagnostics=" + result.diagnostics().summary());
        }

        @Override
        public void tick() {
            if (entity.level().isClientSide) {
                return;
            }
            if (paused) {
                entity.setDeltaMovement(Vec3.ZERO);
                return;
            }
            interactionCooldown.tick();
            survivalCooldown.tick();
            if (activeCommand == null) {
                activeCommand = queuedCommands.poll();
                if (activeCommand == null) {
                    monitorIdleSurvival();
                    return;
                }
                activeMonitor = newMonitor(activeCommand);
                activeMonitor.start(entity.getX(), entity.getY(), entity.getZ());
                if (activeCommand.repeatRemaining() > 1) {
                    activeMonitor.note("repeat:remaining=" + activeCommand.repeatRemaining());
                }
                OpenPlayerDebugEvents.record("automation", "started", null, null, null,
                        "kind=" + activeCommand.kind().name());
                OpenPlayerRawTrace.automationOperation("started", activeCommand.kind().name(),
                        "entity=" + entity.getUUID() + " position=" + entity.position()
                                + " coordinate=" + activeCommand.coordinate());
                start(activeCommand);
            }
            continueActiveCommand();
            watchdogActiveCommand();
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

        private void start(QueuedCommand command) {
            if (command.kind() == IntentKind.MOVE) {
                Coordinate coordinate = command.coordinate();
                moveToPosition(coordinate);
                return;
            }
            if (command.kind() == IntentKind.GOTO) {
                startGoto(command);
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
                    || command.kind() == IntentKind.INTERACT
                    || command.kind() == IntentKind.ATTACK_NEAREST
                    || command.kind() == IntentKind.ATTACK_TARGET
                    || command.kind() == IntentKind.GUARD_OWNER
                    || command.kind() == IntentKind.PATROL) {
                command.setStartPosition(entity.blockPosition());
            }
            if (command.kind() == IntentKind.PATROL) {
                Coordinate coordinate = command.coordinate();
                moveToPosition(coordinate);
            }
        }

        private void continueActiveCommand() {
            if (activeCommand == null) {
                return;
            }
            if (activeCommand.kind() == IntentKind.MOVE) {
                navigationRuntime.updateDistance(distanceTo(activeCommand.coordinate()));
                if (entity.getNavigation().isDone()) {
                    completeActiveCommand();
                }
                return;
            }
            if (activeCommand.kind() == IntentKind.GOTO) {
                continueGoto(activeCommand);
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
            if (activeCommand.kind() == IntentKind.INTERACT) {
                if (activeCommand.entityTarget() != null) {
                    interactEntity(activeCommand);
                } else {
                    interactBlock(activeCommand);
                }
                return;
            }
            if (activeCommand.kind() == IntentKind.ATTACK_NEAREST) {
                attackNearest(activeCommand);
                return;
            }
            if (activeCommand.kind() == IntentKind.ATTACK_TARGET) {
                attackSpecificTarget(activeCommand);
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

        private void monitorIdleSurvival() {
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

        private void noteIdleSurvivalAction(String reason) {
            idleSurvivalReason = reason;
            survivalCooldown.backoffAfterAction();
            OpenPlayerDebugEvents.record("automation", "survival", null, null, null, reason);
            OpenPlayerRawTrace.automationOperation("survival", "IDLE", "reason=" + reason);
        }

        private void noteIdleSurvivalDiagnostic(String reason) {
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

        private boolean tryMoveToSafeAdjacentBlock(ServerLevel serverLevel, SurvivalDangerKind dangerKind) {
            BlockPos origin = entity.blockPosition();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = origin.relative(direction);
                if (!isSafeAdjacentTarget(serverLevel, candidate)) {
                    continue;
                }
                navigationRuntime.plan(
                        NavigationTarget.block(candidate.getX(), candidate.getY(), candidate.getZ()),
                        distanceTo(candidate),
                        true
                );
                boolean accepted = entity.getNavigation().moveTo(
                        candidate.getX() + 0.5D,
                        candidate.getY(),
                        candidate.getZ() + 0.5D,
                        PLAYER_LIKE_NAVIGATION_SPEED
                );
                if (accepted) {
                    navigationRuntime.markReachable(true);
                    return true;
                }
            }
            navigationRuntime.fail("unable_to_avoid_" + dangerKind.name().toLowerCase());
            return false;
        }

        private boolean isSafeAdjacentTarget(ServerLevel serverLevel, BlockPos candidate) {
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

        private void startGoto(QueuedCommand command) {
            moveToPosition(command.coordinate());
        }

        private void continueGoto(QueuedCommand command) {
            navigationRuntime.updateDistance(distanceTo(command.coordinate()));
            if (entity.getNavigation().isDone()) {
                completeActiveCommand();
            }
        }

        private void collectItems(QueuedCommand command) {
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
                completeActiveCommand();
                return;
            }
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
                completeActiveCommand();
            }
        }

        private ItemEntity nearestItem(ServerLevel serverLevel, QueuedCommand command) {
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
            stopNavigation();
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
            stopNavigation();
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

        private void attackSpecificTarget(QueuedCommand command) {
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

        private void interactBlock(QueuedCommand command) {
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

        private void interactEntity(QueuedCommand command) {
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

        private LivingEntity nearestAttackTarget(ServerLevel serverLevel, double radius, Vec3 center) {
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

        private LivingEntity nearestSelfDefenseTarget(ServerLevel serverLevel, double radius) {
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

        private LivingEntity resolveAttackTarget(ServerLevel serverLevel,
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

        private Entity resolveInteractionTarget(ServerLevel serverLevel,
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

        private boolean isSafeEntityInteractionTarget(Entity target) {
            if (target == null || !target.isAlive() || target == entity || target instanceof Player
                    || target instanceof OpenPlayerNpcEntity) {
                return false;
            }
            ServerPlayer owner = owner();
            return owner == null || !target.getUUID().equals(owner.getUUID());
        }

        private boolean isSafeAttackTarget(LivingEntity target) {
            return PhaseFourteenSafetyPolicy.isSafeExplicitAttackTarget(target, owner(), entity);
        }

        private LivingEntity nearestGuardTarget(ServerLevel serverLevel, ServerPlayer owner, double radius) {
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

        private boolean attackTarget(LivingEntity target) {
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
            moveToBlock(blockPos);
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
                if (!moveToEntity(owner, NavigationTarget.owner())) {
                    return;
                }
            } else if (distanceSquared <= FOLLOW_STOP_DISTANCE * FOLLOW_STOP_DISTANCE) {
                stopNavigation();
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

        private void patrol(QueuedCommand command) {
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

        private boolean depositToContainer(Container container, ParsedItemInstruction parsed) {
            List<ItemStack> containerStacks = containerSnapshot(container);
            boolean transferred;
            if (parsed == null) {
                transferred = entity.depositAllNormalInventoryTo(containerStacks);
            } else {
                transferred = entity.depositInventoryItemTo(containerStacks, parsed.item(), parsed.count());
            }
            if (!transferred) {
                return false;
            }
            restoreContainer(container, containerStacks);
            return true;
        }

        private boolean hasNormalInventoryToTransfer() {
            List<ItemStack> inventory = entity.inventorySnapshot();
            int endSlot = Math.min(NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT, inventory.size());
            for (int slot = NpcInventoryTransfer.FIRST_NORMAL_SLOT; slot < endSlot; slot++) {
                if (!inventory.get(slot).isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        private boolean withdrawFromContainer(Container container, Item item, int count) {
            List<ItemStack> containerStacks = containerSnapshot(container);
            if (!entity.withdrawInventoryItemFrom(containerStacks, item, count)) {
                return false;
            }
            restoreContainer(container, containerStacks);
            return true;
        }

        private SafeContainerTarget preferredStashContainer() {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                return null;
            }
            BlockPos remembered = entity.rememberedStashPos(dimensionId(serverLevel));
            SafeContainerTarget rememberedTarget = safeContainerAt(serverLevel, remembered);
            if (rememberedTarget != null) {
                return rememberedTarget;
            }
            return nearestSafeContainer();
        }

        private SafeContainerTarget nearestSafeContainer() {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                return null;
            }
            BlockPos center = entity.blockPosition();
            List<SafeContainerTarget> targets = new ArrayList<>();
            for (BlockPos candidate : BlockPos.betweenClosed(
                    center.offset(-CONTAINER_SCAN_RADIUS, -CONTAINER_SCAN_RADIUS, -CONTAINER_SCAN_RADIUS),
                    center.offset(CONTAINER_SCAN_RADIUS, CONTAINER_SCAN_RADIUS, CONTAINER_SCAN_RADIUS)
            )) {
                SafeContainerTarget target = safeContainerAt(serverLevel, candidate);
                if (target != null) {
                    targets.add(target);
                }
            }
            return targets.stream()
                    .min(Comparator
                            .comparingDouble((SafeContainerTarget target) -> target.blockPos().distSqr(center))
                            .thenComparingInt(target -> target.blockPos().getX())
                            .thenComparingInt(target -> target.blockPos().getY())
                            .thenComparingInt(target -> target.blockPos().getZ()))
                    .orElse(null);
        }

        private SafeContainerTarget safeContainerAt(ServerLevel serverLevel, BlockPos blockPos) {
            if (serverLevel == null || blockPos == null || !serverLevel.hasChunkAt(blockPos)) {
                return null;
            }
            if (entity.distanceToSqr(Vec3.atCenterOf(blockPos)) > CONTAINER_SCAN_RADIUS * CONTAINER_SCAN_RADIUS) {
                return null;
            }
            BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
            if (!(blockEntity instanceof Container container)) {
                return null;
            }
            if (container.isEmpty() && container.getContainerSize() <= 0) {
                return null;
            }
            return new SafeContainerTarget(blockPos.immutable(), container);
        }

        private static List<ItemStack> containerSnapshot(Container container) {
            List<ItemStack> stacks = new ArrayList<>(container.getContainerSize());
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                stacks.add(container.getItem(slot).copy());
            }
            return stacks;
        }

        private static void restoreContainer(Container container, List<ItemStack> snapshot) {
            for (int slot = 0; slot < container.getContainerSize() && slot < snapshot.size(); slot++) {
                container.setItem(slot, snapshot.get(slot).copy());
            }
        }

        private static String dimensionId(ServerLevel serverLevel) {
            return serverLevel.dimension().location().toString();
        }

        private String statusSummary() {
            List<String> capabilityLines = RuntimeCapabilityRegistry.reportLines();
            return snapshot().summary() + " capabilities="
                    + String.join(" | ", capabilityLines.subList(0, Math.min(3, capabilityLines.size())));
        }

        private AutomationCommandResult applyBodyLanguage(BodyLanguageInstruction instruction) {
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

        private List<IntentKind> queuedKinds() {
            List<IntentKind> queuedKinds = new ArrayList<>(queuedCommands.size());
            for (QueuedCommand queuedCommand : queuedCommands) {
                queuedKinds.add(queuedCommand.kind());
            }
            return queuedKinds;
        }

        private void watchdogActiveCommand() {
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

        private void completeActiveCommand() {
            completeActiveCommand("completed");
        }

        private void completeActiveCommand(String reason) {
            completeActiveCommand(reason, false);
        }

        private void completeActiveCommand(String reason, boolean allowRepeat) {
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

        private void failActiveCommand(String reason) {
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

        private boolean requiresNavigationProgress(QueuedCommand command) {
            return command.kind() != IntentKind.LOOK && !entity.getNavigation().isDone();
        }

        private boolean tryRecoverActiveNavigation() {
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

        private void reissueActiveNavigation() {
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

        private boolean moveToPosition(Coordinate coordinate) {
            boolean loaded = isCoordinateLoaded(coordinate);
            navigationRuntime.plan(NavigationTarget.position(coordinate.x(), coordinate.y(), coordinate.z()),
                    distanceTo(coordinate), loaded);
            if (!loaded) {
                failActiveCommand("navigation_target_unloaded");
                return false;
            }
            boolean accepted = entity.getNavigation().moveTo(
                    coordinate.x(), coordinate.y(), coordinate.z(), PLAYER_LIKE_NAVIGATION_SPEED
            );
            if (!accepted) {
                failActiveCommand("navigation_position_rejected");
                return false;
            }
            navigationRuntime.markReachable(true);
            return true;
        }

        private boolean moveToBlock(BlockPos blockPos) {
            boolean loaded = isBlockLoaded(blockPos);
            navigationRuntime.plan(NavigationTarget.block(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                    distanceTo(blockPos), loaded);
            if (!loaded) {
                failActiveCommand("navigation_target_unloaded");
                return false;
            }
            boolean accepted = entity.getNavigation().moveTo(
                    blockPos.getX() + 0.5D, blockPos.getY(), blockPos.getZ() + 0.5D, PLAYER_LIKE_NAVIGATION_SPEED
            );
            if (!accepted) {
                failActiveCommand("navigation_block_rejected");
                return false;
            }
            navigationRuntime.markReachable(true);
            return true;
        }

        private boolean moveToDroppedItem(ItemEntity itemEntity) {
            Vec3 position = itemEntity.position();
            boolean loaded = isBlockLoaded(itemEntity.blockPosition());
            navigationRuntime.plan(droppedItemNavigationTarget(position), entity.distanceToSqr(position), loaded);
            if (!loaded) {
                failActiveCommand("navigation_target_unloaded");
                return false;
            }
            boolean accepted = entity.getNavigation().moveTo(
                    position.x, position.y, position.z, PLAYER_LIKE_NAVIGATION_SPEED
            );
            if (!accepted) {
                failActiveCommand(DROPPED_ITEM_NAVIGATION_REJECTED_REASON);
                return false;
            }
            navigationRuntime.markReachable(true);
            return true;
        }

        private boolean moveToEntity(Entity target, NavigationTarget navigationTarget) {
            boolean loaded = isBlockLoaded(target.blockPosition());
            navigationRuntime.plan(navigationTarget, entity.distanceToSqr(target), loaded);
            if (!loaded) {
                failActiveCommand("navigation_target_unloaded");
                return false;
            }
            boolean accepted = entity.getNavigation().moveTo(target, PLAYER_LIKE_NAVIGATION_SPEED);
            if (!accepted) {
                failActiveCommand("navigation_entity_rejected");
                return false;
            }
            navigationRuntime.markReachable(true);
            return true;
        }

        private void stopNavigation() {
            entity.getNavigation().stop();
            navigationRuntime.complete();
        }

        private void suspendNavigation() {
            entity.getNavigation().stop();
            navigationRuntime.suspend();
        }

        private void cancelNavigation(String reason) {
            entity.getNavigation().stop();
            navigationRuntime.fail(reason);
        }

        private void resetAndStopNavigation() {
            entity.getNavigation().stop();
            navigationRuntime.reset();
        }

        private double distanceTo(Coordinate coordinate) {
            return entity.distanceToSqr(coordinate.x(), coordinate.y(), coordinate.z());
        }

        private double distanceTo(BlockPos blockPos) {
            return entity.distanceToSqr(Vec3.atCenterOf(blockPos));
        }

        private boolean isBlockLoaded(BlockPos blockPos) {
            ServerLevel serverLevel = serverLevel();
            return serverLevel != null && serverLevel.hasChunkAt(blockPos);
        }

        private static String entityTypeId(Entity target) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
        }

        private AutomationControllerMonitor newMonitor(QueuedCommand command) {
            return new AutomationControllerMonitor(
                    maxTicks(command),
                    STUCK_CHECK_INTERVAL_TICKS,
                    STUCK_MIN_PROGRESS_DISTANCE,
                    STUCK_MAX_CHECKS
            );
        }

        private int maxTicks(QueuedCommand command) {
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

        private static int untaggedNormalInventoryCount(List<ItemStack> inventory, Item item) {
            int count = 0;
            int end = Math.min(NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT, inventory.size());
            for (int slot = NpcInventoryTransfer.FIRST_NORMAL_SLOT; slot < end; slot++) {
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty() && stack.is(item) && !stack.hasTag()) {
                    count += stack.getCount();
                }
            }
            return count;
        }

        private boolean consumeOneUntaggedNormalInventoryItem(Item item) {
            List<ItemStack> inventory = entity.inventorySnapshot();
            int end = Math.min(NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT, inventory.size());
            for (int slot = NpcInventoryTransfer.FIRST_NORMAL_SLOT; slot < end; slot++) {
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty() && stack.is(item) && !stack.hasTag()) {
                    stack.shrink(1);
                    return entity.setInventoryItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
                }
            }
            return false;
        }

        private String buildTargetRejection(ServerLevel serverLevel, BlockPos blockPos, BlockState placedState) {
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

        private boolean hasLineOfSightToBlock(ServerLevel serverLevel, BlockPos blockPos) {
            Vec3 eyePosition = entity.getEyePosition();
            Vec3 targetPosition = Vec3.atCenterOf(blockPos);
            BlockHitResult result = serverLevel.clip(new ClipContext(
                    eyePosition,
                    targetPosition,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    entity
            ));
            return result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(blockPos);
        }

        static BlockInteractionCapability blockInteractionCapability(BlockState state) {
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

        private static boolean applyBlockInteraction(ServerLevel serverLevel, BlockPos blockPos, BlockState state,
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

        private EntityInteractionCapability entityInteractionCapability(Entity target) {
            if (target instanceof net.minecraft.world.entity.npc.Villager) {
                return EntityInteractionCapability.UNAVAILABLE;
            }
            return EntityInteractionCapability.UNAVAILABLE;
        }

        private boolean applyEntityInteraction(ServerLevel serverLevel, Entity target, EntityInteractionCapability capability) {
            return false;
        }

        private static Item itemByIdOrNull(ResourceLocation id) {
            return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        }

        private static String blockId(Block block) {
            return BuiltInRegistries.BLOCK.getKey(block).toString();
        }

        private static String itemId(Item item) {
            return BuiltInRegistries.ITEM.getKey(item).toString();
        }

        private static int compareBlockPos(BlockPos first, BlockPos second) {
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

        private static String formatRadius(double radius) {
            if (radius == Math.rint(radius)) {
                return Integer.toString((int) radius);
            }
            return Double.toString(radius);
        }

        enum BlockInteractionCapability {
            LEVER("lever"),
            BUTTON("button"),
            DOOR("door"),
            TRAPDOOR("trapdoor"),
            FENCE_GATE("fence_gate"),
            BELL("bell"),
            NOTE_BLOCK("note_block"),
            LOADED_CONTAINER("loaded_container"),
            UNAVAILABLE("capability_unavailable");

            private final String id;

            BlockInteractionCapability(String id) {
                this.id = id;
            }

            String id() {
                return id;
            }
        }

        enum EntityInteractionCapability {
            UNAVAILABLE("capability_unavailable");

            private final String id;

            EntityInteractionCapability(String id) {
                this.id = id;
            }

            String id() {
                return id;
            }
        }

        private record SafeContainerTarget(BlockPos blockPos, Container container) {
        }

        private record FuelPlan(Item item, int count, int burnTicksPerItem) {
        }

        private static final class QueuedCommand {
            private final IntentKind kind;
            private final Coordinate coordinate;
            private final double radius;
            private final BlockPos blockTarget;
            private final Entity entityTarget;
            private final Item collectItem;
            private final int maxTicks;
            private final boolean survivalOnly;
            private final int repeatRemaining;
            private BlockPos startPosition;
            private int reachTicks;
            private boolean patrolReturn;

            private QueuedCommand(IntentKind kind, Coordinate coordinate, double radius) {
                this(kind, coordinate, radius, null, null, null, 0, false, 1);
            }

            private QueuedCommand(IntentKind kind, Coordinate coordinate, double radius, BlockPos blockTarget,
                                    Entity entityTarget, Item collectItem, int maxTicks, boolean survivalOnly,
                                    int repeatRemaining) {
                this.kind = kind;
                this.coordinate = coordinate;
                this.radius = radius;
                this.blockTarget = blockTarget;
                this.entityTarget = entityTarget;
                this.collectItem = collectItem;
                this.maxTicks = maxTicks;
                this.survivalOnly = survivalOnly;
                this.repeatRemaining = repeatRemaining;
            }

            private static QueuedCommand move(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.MOVE, coordinate, 0.0D);
            }

            private static QueuedCommand gotoCoordinate(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.GOTO, coordinate, 0.0D);
            }

            private static QueuedCommand look(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.LOOK, coordinate, 0.0D);
            }

            private static QueuedCommand followOwner() {
                return new QueuedCommand(IntentKind.FOLLOW_OWNER, null, 0.0D);
            }

            private static QueuedCommand collectItems(Item collectItem, double radius) {
                return new QueuedCommand(IntentKind.COLLECT_ITEMS, null, radius, null, null, collectItem, 0, false, 1);
            }

            private static QueuedCommand breakBlock(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.BREAK_BLOCK, coordinate, 0.0D);
            }

            private static QueuedCommand placeBlock(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.PLACE_BLOCK, coordinate, 0.0D);
            }

            private static QueuedCommand interactBlock(BlockPos blockPos) {
                return new QueuedCommand(
                        IntentKind.INTERACT, null, 0.0D, blockPos.immutable(), null, null, 0, false, 1
                );
            }

            private static QueuedCommand interactEntity(Entity target, double radius) {
                return new QueuedCommand(
                        IntentKind.INTERACT, null, radius, null, target, null, 0, false, 1
                );
            }

            private static QueuedCommand attackNearest(double radius) {
                return new QueuedCommand(IntentKind.ATTACK_NEAREST, null, radius);
            }

            private static QueuedCommand attackTarget(Entity target, double radius) {
                return new QueuedCommand(IntentKind.ATTACK_TARGET, null, radius, null, target, null, 0, false, 1);
            }

            private static QueuedCommand selfDefense(double radius) {
                return new QueuedCommand(
                        IntentKind.ATTACK_NEAREST, null, radius, null, null, null, 0, true, 1
                );
            }

            private static QueuedCommand guardOwner(double radius) {
                return new QueuedCommand(IntentKind.GUARD_OWNER, null, radius);
            }

            private static QueuedCommand patrol(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.PATROL, coordinate, 0.0D);
            }

            private QueuedCommand nextRepeat() {
                return new QueuedCommand(kind, coordinate, radius, blockTarget, entityTarget, collectItem, maxTicks, survivalOnly,
                        repeatRemaining - 1);
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

            private BlockPos blockTarget() {
                return blockTarget;
            }

            private Entity entityTarget() {
                return entityTarget;
            }

            private Item collectItem() {
                return collectItem;
            }

            private int maxTicks() {
                return maxTicks;
            }

            private boolean survivalOnly() {
                return survivalOnly;
            }

            private int repeatRemaining() {
                return repeatRemaining;
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
