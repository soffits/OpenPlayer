package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.automation.AutomationInstructionParser.Coordinate;
import dev.soffits.openplayer.automation.AutomationInstructionParser.GotoInstruction;
import dev.soffits.openplayer.automation.InventoryActionInstructionParser.ParsedItemInstruction;
import dev.soffits.openplayer.automation.InteractionInstruction.InteractionTargetKind;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskInstructionParser;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskInstructionParser.ExploreChunksInstruction;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskInstructionParser.LoadedSearchInstruction;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskInstructionParser.LocateStructureInstruction;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskInstructionParser.PortalTravelInstruction;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskPolicy;
import dev.soffits.openplayer.automation.advanced.LoadedStructureDiagnosticScanner;
import dev.soffits.openplayer.automation.advanced.PortalFramePlan;
import dev.soffits.openplayer.automation.advanced.PortalFramePlanner;
import dev.soffits.openplayer.automation.building.BuildPlan;
import dev.soffits.openplayer.automation.building.BuildPlanParser;
import dev.soffits.openplayer.automation.navigation.LoadedAreaNavigator;
import dev.soffits.openplayer.automation.navigation.LoadedChunkExplorationMemory;
import dev.soffits.openplayer.automation.navigation.NavigationRuntime;
import dev.soffits.openplayer.automation.navigation.NavigationTarget;
import dev.soffits.openplayer.automation.resource.GetItemRequest;
import dev.soffits.openplayer.automation.resource.ResourceAffordanceScanner;
import dev.soffits.openplayer.automation.resource.ResourceAffordanceSummary;
import dev.soffits.openplayer.automation.resource.ResourceDependencyPlanner;
import dev.soffits.openplayer.automation.resource.ResourcePlanningCapabilities;
import dev.soffits.openplayer.automation.resource.ResourcePlanResult;
import dev.soffits.openplayer.automation.resource.RuntimeCraftingRecipeIndex;
import dev.soffits.openplayer.automation.resource.RuntimeSmeltingRecipeIndex;
import dev.soffits.openplayer.automation.resource.SmeltingPlan;
import dev.soffits.openplayer.automation.survival.SurvivalCooldownPolicy;
import dev.soffits.openplayer.automation.survival.SurvivalDangerKind;
import dev.soffits.openplayer.automation.survival.SurvivalDangerPolicy;
import dev.soffits.openplayer.automation.survival.SurvivalFoodPolicy;
import dev.soffits.openplayer.automation.survival.SurvivalHealthPolicy;
import dev.soffits.openplayer.automation.survival.SurvivalTargetPolicy;
import dev.soffits.openplayer.automation.work.FarmingWorkPolicy;
import dev.soffits.openplayer.automation.work.FarmingWorkPolicy.FarmingReplantPlan;
import dev.soffits.openplayer.automation.work.FishingWorkPolicy;
import dev.soffits.openplayer.automation.work.WorkInstruction;
import dev.soffits.openplayer.automation.work.WorkRepeatPolicy;
import dev.soffits.openplayer.automation.workstation.WorkstationCapability;
import dev.soffits.openplayer.automation.workstation.WorkstationDiagnostics;
import dev.soffits.openplayer.automation.workstation.WorkstationKind;
import dev.soffits.openplayer.automation.workstation.WorkstationLocator;
import dev.soffits.openplayer.automation.workstation.WorkstationTarget;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.debug.OpenPlayerRawTrace;
import dev.soffits.openplayer.entity.NpcInventoryTransfer;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentPolicies;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.SoundSource;

public final class VanillaAutomationBackend implements AutomationBackend {
    public static final String NAME = "vanilla";
    public static final double PLAYER_LIKE_NAVIGATION_SPEED = 1.25D;

    static boolean isLocalWorldOrInventoryAction(IntentKind kind) {
        return RuntimeIntentPolicies.isLocalWorldOrInventoryAction(kind);
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
        private static final double COLLECT_FOOD_DEFAULT_RADIUS = 16.0D;
        private static final double COLLECT_FOOD_MAX_RADIUS = 24.0D;
        private static final double COLLECT_REACH_DISTANCE = 1.5D;
        private static final int COLLECT_REACH_TICKS = 20;
        private static final double BLOCK_TASK_MAX_DISTANCE = 24.0D;
        private static final double BLOCK_INTERACTION_DISTANCE = 4.0D;
        private static final double OWNER_ITEM_TRANSFER_DISTANCE = 4.0D;
        private static final int CONTAINER_SCAN_RADIUS = 4;
        private static final int FURNACE_SCAN_RADIUS = 4;
        private static final int CRAFTING_TABLE_SCAN_RADIUS = 4;
        private static final double ATTACK_DEFAULT_RADIUS = 12.0D;
        private static final double ATTACK_MAX_RADIUS = 24.0D;
        private static final double ATTACK_REACH_DISTANCE = 2.5D;
        private static final double GUARD_DEFAULT_RADIUS = 12.0D;
        private static final double GUARD_MAX_RADIUS = 16.0D;
        private static final double DEFEND_DEFAULT_RADIUS = 12.0D;
        private static final double DEFEND_MAX_RADIUS = 16.0D;
        private static final double FARM_DEFAULT_RADIUS = FarmingWorkPolicy.DEFAULT_RADIUS;
        private static final double FARM_MAX_RADIUS = FarmingWorkPolicy.MAX_RADIUS;
        private static final double PATROL_MAX_DISTANCE = 32.0D;
        private static final double GOTO_DEFAULT_RADIUS = LoadedAreaNavigator.DEFAULT_RADIUS;
        private static final double GOTO_MAX_RADIUS = LoadedAreaNavigator.MAX_RADIUS;
        private static final double GOTO_REACH_DISTANCE = 2.0D;
        private static final double EXPLORE_CHUNK_REACH_DISTANCE = 2.0D;
        private static final double PORTAL_REACH_DISTANCE = 1.8D;
        private static final String OVERWORLD_DIMENSION_ID = "minecraft:overworld";
        private static final String NETHER_DIMENSION_ID = "minecraft:the_nether";
        private static final String PORTAL_IGNITION_ADAPTER_UNAVAILABLE = "portal_ignition_adapter_unavailable";
        private static final int EXPLORE_MAX_CANDIDATE_CHUNKS = 81;
        private static final int MOVE_MAX_TICKS = 20 * 60;
        private static final int SHORT_TASK_MAX_TICKS = 20 * 30;
        private static final int COLLECT_MAX_TICKS = 20 * 45;
        private static final int LONG_TASK_MAX_TICKS = 20 * 120;
        private static final int FURNACE_START_MARGIN_TICKS = 20 * 30;
        private static final int FURNACE_OUTPUT_MARGIN_TICKS = 20 * 60;
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
        private final WorkstationLocator workstationLocator = new WorkstationLocator();
        private final LoadedAreaNavigator loadedAreaNavigator = new LoadedAreaNavigator();
        private final LoadedStructureDiagnosticScanner loadedStructureDiagnosticScanner = new LoadedStructureDiagnosticScanner();
        private final ResourceAffordanceScanner resourceAffordanceScanner = new ResourceAffordanceScanner();
        private final LoadedChunkExplorationMemory loadedChunkExplorationMemory = new LoadedChunkExplorationMemory();
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
            IntentKind kind = intent.kind();
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
                queuedCommands.add(QueuedCommand.collectItems());
                return accepted("COLLECT_ITEMS accepted");
            }
            if (kind == IntentKind.COLLECT_FOOD) {
                double radius = AutomationInstructionParser.parseOptionalRadiusOrNegative(
                        intent.instruction(), COLLECT_FOOD_DEFAULT_RADIUS, COLLECT_FOOD_MAX_RADIUS
                );
                if (radius < 0.0D) {
                    return rejected("COLLECT_FOOD instruction must be blank or a positive radius number");
                }
                queuedCommands.add(QueuedCommand.collectFood(radius));
                return accepted("COLLECT_FOOD accepted");
            }
            if (kind == IntentKind.FARM_NEARBY) {
                WorkInstruction instruction = WorkRepeatPolicy.parseRadiusInstructionOrNull(
                        intent.instruction(), FARM_DEFAULT_RADIUS, FARM_MAX_RADIUS
                );
                if (instruction == null) {
                    return rejected("FARM_NEARBY instruction must be blank, a positive radius number, or radius=<blocks> repeat=1.."
                            + WorkRepeatPolicy.MAX_REPEAT_COUNT);
                }
                queuedCommands.add(QueuedCommand.farmNearby(instruction.value(), instruction.repeatCount()));
                return accepted("FARM_NEARBY accepted: radius=" + formatRadius(instruction.value())
                        + " repeat=" + instruction.repeatCount());
            }
            if (kind == IntentKind.FISH) {
                if (FishingWorkPolicy.isStopInstruction(intent.instruction())) {
                    stopAll();
                    return accepted("FISH stop accepted");
                }
                WorkInstruction instruction = WorkRepeatPolicy.parseDurationSecondsInstructionOrNull(
                        intent.instruction(),
                        FishingWorkPolicy.DEFAULT_DURATION_TICKS / 20.0D,
                        FishingWorkPolicy.MAX_DURATION_TICKS / 20.0D
                );
                if (instruction == null) {
                    return rejected("FISH instruction must be blank, stop, cancel, a positive duration in seconds, or duration=<seconds> repeat=1.."
                            + WorkRepeatPolicy.MAX_REPEAT_COUNT);
                }
                return rejected("FISH requires a safe NPC fishing hook adapter; vanilla fishing is player-bound and is not simulated; durationTicks="
                        + (int) Math.ceil(instruction.value() * 20.0D) + " repeat=" + instruction.repeatCount());
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
            if (kind == IntentKind.GET_ITEM) {
                ParsedItemInstruction parsed = InventoryActionInstructionParser.parseItemCountOrNull(intent.instruction(), false);
                if (parsed == null) {
                    return rejected("GET_ITEM requires instruction: <item_id> [count]");
                }
                int currentCount = entity.normalInventoryCount(parsed.item());
                if (currentCount >= parsed.count()) {
                    return accepted("GET_ITEM accepted: " + parsed.itemId() + " x" + parsed.count()
                            + " already available in NPC inventory");
                }
                ResourceAffordanceSummary affordances = resourceAffordanceScanner.summarize(
                        entity, parsed.itemId().toString(), parsed.item(), parsed.count()
                );
                if (affordances.canSatisfyMissingFromVisibleDrops()) {
                    queuedCommands.add(QueuedCommand.getItem(
                            parsed.itemId().toString(), parsed.item(), parsed.count(), currentCount,
                            ResourceAffordanceScanner.DEFAULT_DROP_RADIUS
                    ));
                    return accepted("GET_ITEM accepted: collecting visible dropped " + parsed.itemId()
                            + " x" + affordances.missingCount() + "; "
                            + affordances.boundedDiagnostics(nearestSafeContainer() != null));
                }
                MinecraftServer server = entity.getServer();
                if (server == null) {
                    return rejected("GET_ITEM requires server recipe data for inventory crafting; "
                            + affordances.boundedDiagnostics(nearestSafeContainer() != null));
                }
                ResourceDependencyPlanner resourceDependencyPlanner = new ResourceDependencyPlanner(
                        RuntimeCraftingRecipeIndex.fromServer(server)
                );
                WorkstationTarget craftingTable = nearestCraftingTable();
                boolean hasCraftingTable = craftingTable != null;
                ResourcePlanResult plan = resourceDependencyPlanner.plan(
                        new GetItemRequest(parsed.itemId(), parsed.item(), parsed.count()),
                        entity.inventorySnapshot(),
                        hasCraftingTable
                                ? ResourcePlanningCapabilities.NEARBY_CRAFTING_TABLE
                                : ResourcePlanningCapabilities.INVENTORY_ONLY
                );
                if (plan.status() == ResourcePlanResult.Status.CRAFTING_STEPS) {
                    if (!canAcquireInteractionCooldown()) {
                        return rejected(interactionCooldownMessage());
                    }
                    if (!acquireInteractionCooldown() || !entity.applyInventoryCraftingSteps(plan.steps(), hasCraftingTable)) {
                        rollbackInteractionCooldown();
                        return rejected("GET_ITEM atomic apply failure: inventory crafting could not be applied");
                    }
                    entity.swingMainHandAction();
                    return accepted("GET_ITEM accepted: crafted " + parsed.itemId() + " x" + parsed.count()
                            + " using " + plan.steps().size() + " bounded crafting step(s)");
                }
                if (plan.status() == ResourcePlanResult.Status.ALREADY_AVAILABLE) {
                    return accepted("GET_ITEM accepted: " + parsed.itemId() + " x" + parsed.count()
                            + " already available in NPC inventory");
                }
                if (plan.status() == ResourcePlanResult.Status.MISSING_MATERIALS) {
                    return rejected("GET_ITEM missing materials for " + parsed.itemId() + " x" + parsed.count()
                            + ": " + missingItemsSummary(plan) + "; "
                            + affordances.boundedDiagnostics(nearestSafeContainer() != null));
                }
                return rejected("GET_ITEM unsupported for bounded inventory crafting: " + parsed.itemId()
                        + reasonSuffix(plan) + "; " + affordances.boundedDiagnostics(nearestSafeContainer() != null));
            }
            if (kind == IntentKind.SMELT_ITEM) {
                ParsedItemInstruction parsed = InventoryActionInstructionParser.parseItemCountOrNull(intent.instruction(), false);
                if (parsed == null) {
                    return rejected("SMELT_ITEM requires instruction: <output_item_id> [count]");
                }
                MinecraftServer server = entity.getServer();
                if (server == null) {
                    return rejected("SMELT_ITEM requires server recipe data for smelting");
                }
                RuntimeSmeltingRecipeIndex smeltingRecipeIndex = RuntimeSmeltingRecipeIndex.fromServer(server);
                SmeltingPlan plan = smeltingRecipeIndex.planFor(
                        parsed.itemId(), parsed.item(), parsed.count(), entity.inventorySnapshot()
                );
                if (plan == null) {
                    return rejected("SMELT_ITEM requires a safe furnace smelting recipe and NPC-carried input");
                }
                if (!(entity.level() instanceof ServerLevel serverLevel)
                        || !smeltingRecipeIndex.matchesResolvedRecipe(plan, serverLevel)) {
                    return rejected("SMELT_ITEM server recipe resolution changed before start");
                }
                FuelPlan fuelPlan = fuelPlanFor(plan);
                if (fuelPlan == null) {
                    return rejected("SMELT_ITEM requires enough NPC-carried non-container fuel");
                }
                WorkstationTarget target = nearestVanillaFurnace();
                if (target == null) {
                    return rejected("SMELT_ITEM requires workstation adapter: "
                            + WorkstationDiagnostics.noLoadedTarget(WorkstationCapability.VANILLA_FURNACE)
                            + "; smoker/blast_furnace adapters require matching loaded nearby furnace block entities");
                }
                Container furnace = WorkstationLocator.requireContainerAdapter(target);
                List<ItemStack> furnaceStacks = containerSnapshot(furnace);
                if (!NpcInventoryTransfer.canInsertAll(
                        entity.inventorySnapshot(), plan.requestedOutputStack(),
                        NpcInventoryTransfer.FIRST_NORMAL_SLOT, NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT
                )) {
                    return rejected("SMELT_ITEM requires NPC normal inventory output capacity");
                }
                if (!NpcInventoryTransfer.startFurnaceSmelt(
                        entity.inventorySnapshot(), furnaceStacks,
                        plan.inputItem(), plan.inputCount(), fuelPlan.item(), fuelPlan.count()
                )) {
                    return rejected("SMELT_ITEM requires compatible empty furnace input, fuel, and result slots");
                }
                queuedCommands.add(QueuedCommand.smelt(target.blockPos(), plan, fuelPlan));
                return accepted("SMELT_ITEM accepted: queued furnace " + target.blockPos().toShortString());
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
            if (kind == IntentKind.BUILD_STRUCTURE) {
                return submitBuildStructure(intent.instruction());
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
            if (kind == IntentKind.DEFEND_OWNER) {
                if (ownerId == null) {
                    return rejected("DEFEND_OWNER requires an NPC owner");
                }
                if (owner() == null) {
                    return rejected("NPC owner is unavailable in this dimension");
                }
                double radius = AutomationInstructionParser.parseOptionalRadiusOrNegative(
                        intent.instruction(), DEFEND_DEFAULT_RADIUS, DEFEND_MAX_RADIUS
                );
                if (radius < 0.0D) {
                    return rejected("DEFEND_OWNER instruction must be blank or a positive radius number");
                }
                queuedCommands.add(QueuedCommand.defendOwner(radius));
                return accepted("DEFEND_OWNER accepted");
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
            if (kind == IntentKind.EXPLORE_CHUNKS) {
                return submitExploreChunks(intent.instruction());
            }
            if (kind == IntentKind.LOCATE_STRUCTURE) {
                return reportLoadedStructure(intent.instruction());
            }
            if (kind == IntentKind.USE_PORTAL) {
                return submitPortalTravel(intent.instruction(), false);
            }
            if (kind == IntentKind.TRAVEL_NETHER) {
                return submitPortalTravel(intent.instruction(), true);
            }
            if (AdvancedTaskPolicy.isUnsupportedAdvancedKind(kind)) {
                return rejected(AdvancedTaskPolicy.unsupportedReason(kind));
            }
            return rejected("Unsupported intent: " + kind.name());
        }

        private AutomationCommandResult submitGoto(String instruction) {
            GotoInstruction gotoInstruction = AutomationInstructionParser.parseGotoInstructionOrNull(
                    instruction, GOTO_DEFAULT_RADIUS, GOTO_MAX_RADIUS
            );
            if (gotoInstruction == null) {
                return rejected("GOTO requires instruction: x y z, owner, block <block_or_item_id> [radius], or entity <entity_type_id> [radius]");
            }
            return switch (gotoInstruction.kind()) {
                case COORDINATE -> submitGotoCoordinate(gotoInstruction.coordinate());
                case OWNER -> submitGotoOwner();
                case BLOCK -> submitGotoBlock(gotoInstruction.resourceId(), gotoInstruction.radius());
                case ENTITY -> submitGotoEntity(gotoInstruction.resourceId(), gotoInstruction.radius());
            };
        }

        private AutomationCommandResult submitBuildStructure(String instruction) {
            BuildPlan plan = BuildPlanParser.parseOrNull(instruction);
            if (plan == null) {
                return rejected(BuildPlanParser.USAGE);
            }
            Item material = itemByIdOrNull(plan.materialId());
            if (!(material instanceof BlockItem blockItem) || material == Items.AIR) {
                return rejected("BUILD_STRUCTURE unsupported material: " + plan.materialId());
            }
            BlockState placedState = blockItem.getBlock().defaultBlockState();
            if (placedState.isAir() || !placedState.getFluidState().isEmpty()) {
                return rejected("BUILD_STRUCTURE unsupported material: " + plan.materialId());
            }
            int carried = untaggedNormalInventoryCount(entity.inventorySnapshot(), material);
            if (carried < plan.blockCount()) {
                return rejected("BUILD_STRUCTURE missing materials: material=" + plan.materialId()
                        + " required=" + plan.blockCount() + " carried=" + carried);
            }
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                return rejected("BUILD_STRUCTURE requires a server level");
            }
            for (BlockPos blockPos : plan.positions()) {
                String rejection = buildTargetRejection(serverLevel, blockPos, placedState);
                if (rejection != null) {
                    return rejected("BUILD_STRUCTURE rejected: " + rejection + " at " + blockPos.toShortString());
                }
            }
            queuedCommands.add(QueuedCommand.buildStructure(plan, material));
            return accepted("BUILD_STRUCTURE accepted: primitive=" + plan.primitive().name().toLowerCase()
                    + " blocks=" + plan.blockCount() + " material=" + plan.materialId());
        }

        private AutomationCommandResult submitPortalTravel(String instruction, boolean travelNether) {
            PortalTravelInstruction portalInstruction = travelNether
                    ? AdvancedTaskInstructionParser.parseTravelNetherOrNull(instruction)
                    : AdvancedTaskInstructionParser.parseUsePortalOrNull(instruction);
            if (portalInstruction == null) {
                return rejected(travelNether
                        ? AdvancedTaskInstructionParser.TRAVEL_NETHER_USAGE
                        : AdvancedTaskInstructionParser.USE_PORTAL_USAGE);
            }
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                return rejected("portal_travel requires a server level");
            }
            String currentDimension = dimensionId(serverLevel);
            String targetDimension = targetPortalDimension(portalInstruction, currentDimension);
            PortalSearchResult searchResult = nearestLoadedPortal(serverLevel, portalInstruction.radius());
            if (searchResult.portalPos() != null) {
                queuedCommands.add(QueuedCommand.portalTravel(
                        travelNether ? IntentKind.TRAVEL_NETHER : IntentKind.USE_PORTAL,
                        portalInstruction.radius(), targetDimension, searchResult.portalPos(),
                        null, "existing_portal", searchResult.diagnostics()
                ));
                return accepted("portal_travel accepted: mode=existing_portal target_dimension="
                        + targetDimensionSummary(targetDimension) + " current_dimension=" + currentDimension
                        + " source=loaded_scan radius=" + formatRadius(portalInstruction.radius())
                        + " portal=" + searchResult.portalPos().toShortString() + " " + searchResult.diagnostics());
            }
            boolean hasMaterials = hasPortalBuildMaterials();
            boolean build = portalInstruction.build() == null ? hasMaterials : portalInstruction.build();
            if (!build) {
                return rejected("portal_travel failed: mode=existing_portal target_dimension="
                        + targetDimensionSummary(targetDimension) + " current_dimension=" + currentDimension
                        + " source=loaded_scan radius=" + formatRadius(portalInstruction.radius())
                        + " failure=no_loaded_portal " + searchResult.diagnostics());
            }
            if (!currentDimension.equals(OVERWORLD_DIMENSION_ID) || !targetDimensionSummary(targetDimension).equals(NETHER_DIMENSION_ID)) {
                return rejected("portal_travel failed: mode=build_portal target_dimension="
                        + targetDimensionSummary(targetDimension) + " current_dimension=" + currentDimension
                        + " failure=portal_build_supported_only_overworld_to_nether " + searchResult.diagnostics());
            }
            int carriedObsidian = untaggedNormalInventoryCount(entity.inventorySnapshot(), Items.OBSIDIAN);
            boolean hasFlintAndSteel = entity.normalInventoryCount(Items.FLINT_AND_STEEL) > 0;
            if (carriedObsidian < PortalFramePlan.REQUIRED_OBSIDIAN || !hasFlintAndSteel) {
                return rejected("portal_travel failed: mode=build_portal target_dimension=" + NETHER_DIMENSION_ID
                        + " current_dimension=" + currentDimension + " required_obsidian="
                        + PortalFramePlan.REQUIRED_OBSIDIAN + " carried_obsidian=" + carriedObsidian
                        + " has_flint_and_steel=" + hasFlintAndSteel + " failure=missing_carried_portal_materials "
                        + searchResult.diagnostics());
            }
            PortalBuildPlanResult buildPlanResult = nearestSafePortalFramePlan(serverLevel, portalInstruction.radius());
            if (buildPlanResult.plan() == null) {
                return rejected("portal_travel failed: mode=build_portal target_dimension=" + NETHER_DIMENSION_ID
                        + " current_dimension=" + currentDimension + " source=player_like_build radius="
                        + formatRadius(portalInstruction.radius()) + " required_obsidian="
                        + PortalFramePlan.REQUIRED_OBSIDIAN + " has_flint_and_steel=" + hasFlintAndSteel
                        + " failure=no_safe_frame_footprint " + buildPlanResult.diagnostics());
            }
            queuedCommands.add(QueuedCommand.portalTravel(
                    travelNether ? IntentKind.TRAVEL_NETHER : IntentKind.USE_PORTAL,
                    portalInstruction.radius(), targetDimension, null,
                    buildPlanResult.plan(), "build_portal", buildPlanResult.diagnostics()
            ));
            return accepted("portal_travel accepted: mode=build_portal target_dimension=" + NETHER_DIMENSION_ID
                    + " current_dimension=" + currentDimension + " source=player_like_build radius="
                    + formatRadius(portalInstruction.radius()) + " frame="
                    + buildPlanResult.plan().origin().toShortString() + " required_obsidian="
                    + PortalFramePlan.REQUIRED_OBSIDIAN + " has_flint_and_steel=" + hasFlintAndSteel
                    + " " + buildPlanResult.diagnostics());
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

        private AutomationCommandResult submitGotoOwner() {
            if (ownerId == null) {
                return rejected("GOTO owner requires an NPC owner");
            }
            ServerPlayer owner = owner();
            if (owner == null || !owner.isAlive()) {
                return rejected("GOTO owner requires the NPC owner online and alive in this dimension");
            }
            if (!isBlockLoaded(owner.blockPosition())) {
                return rejected("GOTO owner target chunk is not loaded");
            }
            queuedCommands.add(QueuedCommand.gotoOwnerCommand());
            return accepted("GOTO accepted: owner");
        }

        private AutomationCommandResult submitGotoBlock(String blockOrItemId, double radius) {
            ServerLevel serverLevel = serverLevel();
            LoadedAreaNavigator.BlockSearchResult result = loadedAreaNavigator.nearestLoadedBlock(
                    serverLevel, entity.position(), blockOrItemId, radius
            );
            if (!result.found()) {
                return rejected("GOTO block target not found in loaded area: " + result.diagnostics().summary());
            }
            queuedCommands.add(QueuedCommand.gotoBlock(result.blockPos()));
            return accepted("GOTO accepted: block " + result.blockPos().toShortString()
                    + " diagnostics=" + result.diagnostics().summary());
        }

        private AutomationCommandResult submitGotoEntity(String entityTypeId, double radius) {
            ServerLevel serverLevel = serverLevel();
            LoadedAreaNavigator.EntitySearchResult result = loadedAreaNavigator.nearestLoadedEntity(
                    serverLevel,
                    entity.position(),
                    entityTypeId,
                    radius,
                    candidate -> candidate != entity && candidate.isAlive()
            );
            if (!result.found()) {
                return rejected("GOTO entity target not found in loaded area: " + result.diagnostics().summary());
            }
            queuedCommands.add(QueuedCommand.gotoEntity(result.entity()));
            return accepted("GOTO accepted: entity " + entityTypeId
                    + " diagnostics=" + result.diagnostics().summary());
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

        private AutomationCommandResult submitExploreChunks(String instruction) {
            ExploreChunksInstruction exploreInstruction = AdvancedTaskInstructionParser.parseExploreChunksOrNull(instruction);
            if (exploreInstruction == null) {
                return rejected(AdvancedTaskInstructionParser.EXPLORE_CHUNKS_USAGE);
            }
            if (exploreInstruction.resetOnly()) {
                loadedChunkExplorationMemory.clear();
                return accepted("EXPLORE_CHUNKS reset accepted: visited=0");
            }
            queuedCommands.add(QueuedCommand.exploreChunks(exploreInstruction.radius(), exploreInstruction.steps()));
            return accepted("EXPLORE_CHUNKS accepted: loaded_only radius=" + formatRadius(exploreInstruction.radius())
                    + " steps=" + exploreInstruction.steps());
        }

        private AutomationCommandResult reportLoadedStructure(String instruction) {
            LocateStructureInstruction locateStructureInstruction = AdvancedTaskInstructionParser.parseLocateStructureOrNull(instruction);
            if (locateStructureInstruction == null) {
                return rejected(AdvancedTaskInstructionParser.LOCATE_STRUCTURE_USAGE);
            }
            LoadedStructureDiagnosticScanner.StructureDiagnosticResult result = loadedStructureDiagnosticScanner.scan(
                    serverLevel(), entity.position(), locateStructureInstruction.structureId(), locateStructureInstruction.radius()
            );
            return accepted("LOCATE_STRUCTURE " + result.status() + ": " + result.summary());
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
            if (command.kind() == IntentKind.EXPLORE_CHUNKS) {
                startExploreChunks(command);
                return;
            }
            if (command.kind() == IntentKind.LOOK) {
                Coordinate coordinate = command.coordinate();
                entity.getLookControl().setLookAt(coordinate.x(), coordinate.y(), coordinate.z());
                completeActiveCommand();
                return;
            }
            if (command.kind() == IntentKind.COLLECT_ITEMS
                    || command.kind() == IntentKind.COLLECT_FOOD
                    || command.kind() == IntentKind.GET_ITEM
                    || command.kind() == IntentKind.FARM_NEARBY
                    || command.kind() == IntentKind.BREAK_BLOCK
                    || command.kind() == IntentKind.PLACE_BLOCK
                    || command.kind() == IntentKind.BUILD_STRUCTURE
                    || command.kind() == IntentKind.USE_PORTAL
                    || command.kind() == IntentKind.TRAVEL_NETHER
                    || command.kind() == IntentKind.INTERACT
                    || command.kind() == IntentKind.SMELT_ITEM
                    || command.kind() == IntentKind.ATTACK_NEAREST
                    || command.kind() == IntentKind.ATTACK_TARGET
                    || command.kind() == IntentKind.GUARD_OWNER
                    || command.kind() == IntentKind.DEFEND_OWNER
                    || command.kind() == IntentKind.PATROL) {
                command.setStartPosition(entity.blockPosition());
                if (command.kind() == IntentKind.USE_PORTAL || command.kind() == IntentKind.TRAVEL_NETHER) {
                    ServerLevel serverLevel = serverLevel();
                    command.setPortalStartDimensionId(serverLevel == null ? "unknown" : dimensionId(serverLevel));
                }
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
            if (activeCommand.kind() == IntentKind.EXPLORE_CHUNKS) {
                continueExploreChunks(activeCommand);
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
            if (activeCommand.kind() == IntentKind.COLLECT_FOOD) {
                collectFood(activeCommand);
                return;
            }
            if (activeCommand.kind() == IntentKind.GET_ITEM) {
                collectRequestedItem(activeCommand);
                return;
            }
            if (activeCommand.kind() == IntentKind.FARM_NEARBY) {
                if (!entity.allowWorldActions()) {
                    failActiveCommand("world_actions_disabled_before_repeat");
                    return;
                }
                farmNearby(activeCommand);
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
            if (activeCommand.kind() == IntentKind.BUILD_STRUCTURE) {
                buildStructure(activeCommand);
                return;
            }
            if (activeCommand.kind() == IntentKind.USE_PORTAL || activeCommand.kind() == IntentKind.TRAVEL_NETHER) {
                useOrBuildPortal(activeCommand);
                return;
            }
            if (activeCommand.kind() == IntentKind.SMELT_ITEM) {
                smeltItem(activeCommand);
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
            if (activeCommand.kind() == IntentKind.DEFEND_OWNER) {
                defendOwner(activeCommand);
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
            if (SurvivalHealthPolicy.isLowHealth(entity.getHealth(), entity.getMaxHealth())) {
                int foodSlot = entity.bestSafeFoodSlotForLocalUse();
                if (foodSlot >= 0 && canAcquireInteractionCooldown() && acquireInteractionCooldown()) {
                    if (entity.useSafeFoodSlotForLocalUse(foodSlot)) {
                        noteIdleSurvivalAction("survival:eat_safe_food slot=" + foodSlot);
                        return;
                    }
                    rollbackInteractionCooldown();
                }
                if (SurvivalHealthPolicy.isDangerouslyLowHealth(entity.getHealth(), entity.getMaxHealth())) {
                    noteIdleSurvivalDiagnostic("survival:dangerously_low_health_no_safe_food");
                    return;
                }
            }
            if (nearestSelfDefenseTarget(serverLevel, DEFEND_DEFAULT_RADIUS) != null) {
                queuedCommands.add(QueuedCommand.selfDefense(DEFEND_DEFAULT_RADIUS));
                noteIdleSurvivalAction("survival:queued_self_defense");
                return;
            }
            ServerPlayer owner = owner();
            if (owner != null && nearestGuardTarget(serverLevel, owner, DEFEND_DEFAULT_RADIUS) != null) {
                queuedCommands.add(QueuedCommand.defendOwner(DEFEND_DEFAULT_RADIUS));
                noteIdleSurvivalAction("survival:queued_defend_owner");
                return;
            }
            if (canAcquireInteractionCooldown() && acquireInteractionCooldown()) {
                if (entity.equipBestAvailableArmor()) {
                    entity.swingMainHandAction();
                    noteIdleSurvivalAction("survival:equip_armor");
                } else {
                    rollbackInteractionCooldown();
                }
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
            if (command.coordinate() != null) {
                moveToPosition(command.coordinate());
                return;
            }
            if (command.blockTarget() != null) {
                moveToBlock(command.blockTarget());
                return;
            }
            if (command.gotoOwner()) {
                ServerPlayer owner = owner();
                if (owner == null || !owner.isAlive()) {
                    failActiveCommand("goto_owner_unavailable");
                    return;
                }
                moveToEntity(owner, NavigationTarget.owner());
                return;
            }
            Entity target = command.entityTarget();
            if (target == null || !target.isAlive()) {
                failActiveCommand("goto_entity_unavailable");
                return;
            }
            moveToEntity(target, NavigationTarget.entity(entityTypeId(target)));
        }

        private void continueGoto(QueuedCommand command) {
            if (command.gotoOwner()) {
                ServerPlayer owner = owner();
                if (owner == null || !owner.isAlive()) {
                    failActiveCommand("goto_owner_unavailable");
                    return;
                }
                navigationRuntime.updateDistance(entity.distanceToSqr(owner));
                if (entity.distanceToSqr(owner) <= GOTO_REACH_DISTANCE * GOTO_REACH_DISTANCE) {
                    stopNavigation();
                    completeActiveCommand();
                    return;
                }
                if (entity.getNavigation().isDone()) {
                    moveToEntity(owner, NavigationTarget.owner());
                }
                return;
            }
            Entity target = command.entityTarget();
            if (target != null) {
                if (!target.isAlive() || !isBlockLoaded(target.blockPosition())) {
                    failActiveCommand("goto_entity_unavailable");
                    return;
                }
                navigationRuntime.updateDistance(entity.distanceToSqr(target));
                if (entity.distanceToSqr(target) <= GOTO_REACH_DISTANCE * GOTO_REACH_DISTANCE) {
                    stopNavigation();
                    completeActiveCommand();
                    return;
                }
                if (entity.getNavigation().isDone()) {
                    moveToEntity(target, NavigationTarget.entity(entityTypeId(target)));
                }
                return;
            }
            if (command.blockTarget() != null) {
                navigationRuntime.updateDistance(distanceTo(command.blockTarget()));
            } else {
                navigationRuntime.updateDistance(distanceTo(command.coordinate()));
            }
            if (entity.getNavigation().isDone()) {
                completeActiveCommand();
            }
        }

        private void startExploreChunks(QueuedCommand command) {
            if (!entity.allowWorldActions()) {
                failActiveCommand("world_actions_disabled_before_explore");
                return;
            }
            LoadedChunkTarget target = nextLoadedChunkTarget(command.radius());
            if (target == null) {
                failActiveCommand("no_safe_loaded_chunk_target");
                return;
            }
            command.setExplorationTarget(target.targetPos());
            command.setExplorationChunk(target.chunkX(), target.chunkZ());
            if (activeMonitor != null) {
                activeMonitor.note("explore:chunk=" + target.chunkX() + ',' + target.chunkZ()
                        + " visited=" + loadedChunkExplorationMemory.visitedCount()
                        + " remaining=" + command.repeatRemaining()
                        + " mode=" + (target.visited() ? "revisit" : "unvisited"));
            }
            moveToExploreTarget(target.targetPos());
        }

        private void continueExploreChunks(QueuedCommand command) {
            if (!entity.allowWorldActions()) {
                failActiveCommand("world_actions_disabled_during_explore");
                return;
            }
            BlockPos target = command.explorationTarget();
            if (target == null) {
                failActiveCommand("explore_target_unavailable");
                return;
            }
            if (!isBlockLoaded(target)) {
                failActiveCommand("explore_target_unloaded");
                return;
            }
            navigationRuntime.updateDistance(distanceTo(target));
            if (entity.distanceToSqr(Vec3.atCenterOf(target)) <= EXPLORE_CHUNK_REACH_DISTANCE * EXPLORE_CHUNK_REACH_DISTANCE) {
                loadedChunkExplorationMemory.markVisited(
                        dimensionId(serverLevel()), command.explorationChunkX(), command.explorationChunkZ()
                );
                stopNavigation();
                completeActiveCommand("explore_reached chunk=" + command.explorationChunkX() + ','
                        + command.explorationChunkZ() + " visited=" + loadedChunkExplorationMemory.visitedCount(), true);
                return;
            }
            if (entity.getNavigation().isDone()) {
                failActiveCommand("explore_target_not_reached");
            }
        }

        private boolean moveToExploreTarget(BlockPos target) {
            boolean loaded = isBlockLoaded(target);
            navigationRuntime.plan(NavigationTarget.block(target.getX(), target.getY(), target.getZ()), distanceTo(target), loaded);
            if (!loaded) {
                failActiveCommand("explore_target_unloaded");
                return false;
            }
            boolean accepted = entity.getNavigation().moveTo(
                    target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, PLAYER_LIKE_NAVIGATION_SPEED
            );
            if (!accepted) {
                failActiveCommand("explore_navigation_rejected");
                return false;
            }
            navigationRuntime.markReachable(true);
            return true;
        }

        private LoadedChunkTarget nextLoadedChunkTarget(double radius) {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                return null;
            }
            String dimensionId = dimensionId(serverLevel);
            Vec3 origin = entity.position();
            int boundedRadius = (int) Math.max(1.0D, Math.min(AdvancedTaskInstructionParser.EXPLORE_MAX_RADIUS, Math.floor(radius)));
            int radiusSquared = boundedRadius * boundedRadius;
            BlockPos originPos = entity.blockPosition();
            int originChunkX = Math.floorDiv(originPos.getX(), 16);
            int originChunkZ = Math.floorDiv(originPos.getZ(), 16);
            int chunkRadius = (int) Math.ceil(boundedRadius / 16.0D);
            LoadedChunkTarget bestUnvisited = null;
            LoadedChunkTarget bestVisited = null;
            int scanned = 0;
            for (int deltaX = -chunkRadius; deltaX <= chunkRadius && scanned < EXPLORE_MAX_CANDIDATE_CHUNKS; deltaX++) {
                for (int deltaZ = -chunkRadius; deltaZ <= chunkRadius && scanned < EXPLORE_MAX_CANDIDATE_CHUNKS; deltaZ++) {
                    int chunkX = originChunkX + deltaX;
                    int chunkZ = originChunkZ + deltaZ;
                    int centerX = chunkX * 16 + 8;
                    int centerZ = chunkZ * 16 + 8;
                    double distanceSquared = horizontalDistanceSquared(origin.x, origin.z, centerX + 0.5D, centerZ + 0.5D);
                    if (distanceSquared > radiusSquared) {
                        continue;
                    }
                    BlockPos target = safeExploreTargetNear(serverLevel, centerX, originPos.getY(), centerZ);
                    scanned++;
                    if (target == null) {
                        continue;
                    }
                    boolean visited = loadedChunkExplorationMemory.isVisited(dimensionId, chunkX, chunkZ);
                    int recency = loadedChunkExplorationMemory.recency(dimensionId, chunkX, chunkZ);
                    LoadedChunkTarget candidate = new LoadedChunkTarget(chunkX, chunkZ, target, distanceSquared, visited, recency);
                    if (visited) {
                        if (isBetterVisitedChunk(candidate, bestVisited)) {
                            bestVisited = candidate;
                        }
                    } else if (isBetterUnvisitedChunk(candidate, bestUnvisited)) {
                        bestUnvisited = candidate;
                    }
                }
            }
            return bestUnvisited == null ? bestVisited : bestUnvisited;
        }

        private BlockPos safeExploreTargetNear(ServerLevel serverLevel, int centerX, int originY, int centerZ) {
            int[] horizontalOffsets = {0, 1, -1, 2, -2};
            for (int yOffset = 0; yOffset <= 2; yOffset++) {
                for (int verticalSign = -1; verticalSign <= 1; verticalSign += 2) {
                    int y = originY + yOffset * verticalSign;
                    for (int xOffset : horizontalOffsets) {
                        for (int zOffset : horizontalOffsets) {
                            BlockPos candidate = new BlockPos(centerX + xOffset, y, centerZ + zOffset);
                            if (!serverLevel.hasChunkAt(candidate)) {
                                continue;
                            }
                            if (isSafeAdjacentTarget(serverLevel, candidate)) {
                                return candidate.immutable();
                            }
                        }
                    }
                }
            }
            return null;
        }

        private static boolean isBetterUnvisitedChunk(LoadedChunkTarget candidate, LoadedChunkTarget current) {
            if (current == null) {
                return true;
            }
            int distance = Double.compare(candidate.distanceSquared(), current.distanceSquared());
            if (distance != 0) {
                return distance < 0;
            }
            int chunkX = Integer.compare(candidate.chunkX(), current.chunkX());
            if (chunkX != 0) {
                return chunkX < 0;
            }
            return candidate.chunkZ() < current.chunkZ();
        }

        private static boolean isBetterVisitedChunk(LoadedChunkTarget candidate, LoadedChunkTarget current) {
            if (current == null) {
                return true;
            }
            int recency = Integer.compare(candidate.recency(), current.recency());
            if (recency != 0) {
                return recency < 0;
            }
            return isBetterUnvisitedChunk(candidate, current);
        }

        private static double horizontalDistanceSquared(double firstX, double firstZ, double secondX, double secondZ) {
            double deltaX = firstX - secondX;
            double deltaZ = firstZ - secondZ;
            return deltaX * deltaX + deltaZ * deltaZ;
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
                stopNavigation();
                completeActiveCommand();
                return;
            }
            entity.getLookControl().setLookAt(itemEntity);
            if (entity.distanceToSqr(itemEntity) > COLLECT_REACH_DISTANCE * COLLECT_REACH_DISTANCE) {
                if (!moveToEntity(itemEntity, NavigationTarget.entity(entityTypeId(itemEntity)))) {
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

        private void collectFood(QueuedCommand command) {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                failActiveCommand("server_level_unavailable");
                return;
            }
            if (!isWithinStartDistance(command, entity.position(), command.radius())) {
                failActiveCommand("outside_collect_food_radius");
                return;
            }
            ItemEntity itemEntity = nearestFoodItem(serverLevel, command);
            if (itemEntity == null) {
                stopNavigation();
                completeActiveCommand();
                return;
            }
            if (!NpcInventoryTransfer.canInsertAll(
                    entity.inventorySnapshot(), itemEntity.getItem(),
                    NpcInventoryTransfer.FIRST_NORMAL_SLOT, NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT
            )) {
                failActiveCommand("inventory_full_for_food");
                return;
            }
            entity.getLookControl().setLookAt(itemEntity);
            if (entity.distanceToSqr(itemEntity) > COLLECT_REACH_DISTANCE * COLLECT_REACH_DISTANCE) {
                if (!moveToEntity(itemEntity, NavigationTarget.entity(entityTypeId(itemEntity)))) {
                    return;
                }
                command.resetReachTicks();
                return;
            }
            stopNavigation();
            command.incrementReachTicks();
            if (command.reachTicks() >= COLLECT_REACH_TICKS) {
                failActiveCommand("food_pickup_not_observed");
            }
        }

        private ItemEntity nearestFoodItem(ServerLevel serverLevel, QueuedCommand command) {
            List<ItemEntity> itemEntities = serverLevel.getEntitiesOfClass(
                    ItemEntity.class,
                    entity.getBoundingBox().inflate(command.radius()),
                    itemEntity -> itemEntity.isAlive()
                            && !itemEntity.hasPickUpDelay()
                            && SurvivalFoodPolicy.isSafeEdibleDrop(itemEntity.getItem())
                            && entity.hasLineOfSight(itemEntity)
                            && isWithinStartDistance(command, itemEntity.position(), command.radius())
            );
            return itemEntities.stream()
                    .min(Comparator.comparingDouble((ItemEntity itemEntity) -> entity.distanceToSqr(itemEntity))
                            .thenComparing(itemEntity -> itemEntity.getUUID().toString()))
                    .orElse(null);
        }

        private void collectRequestedItem(QueuedCommand command) {
            if (!entity.allowWorldActions()) {
                failActiveCommand("world_actions_disabled_during_get_item");
                return;
            }
            int currentCount = entity.normalInventoryCount(command.targetItem());
            if (currentCount >= command.targetItemCount()) {
                stopNavigation();
                completeActiveCommand("get_item:inventory_verified " + command.targetItemId()
                        + " x" + command.targetItemCount());
                return;
            }
            if (currentCount > command.targetStartCount()) {
                command.setTargetStartCount(currentCount);
                command.resetReachTicks();
            }
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                failActiveCommand("server_level_unavailable");
                return;
            }
            if (!isWithinStartDistance(command, entity.position(), command.radius())) {
                failActiveCommand("outside_get_item_radius");
                return;
            }
            int missing = command.targetItemCount() - currentCount;
            if (ResourceAffordanceSummary.normalInventoryCapacityFor(entity.inventorySnapshot(), command.targetItem()) < missing) {
                failActiveCommand("inventory_full_for_item");
                return;
            }
            ItemEntity itemEntity = nearestRequestedItem(serverLevel, command, missing);
            if (itemEntity == null) {
                failActiveCommand("visible_dropped_item_unavailable");
                return;
            }
            entity.getLookControl().setLookAt(itemEntity);
            if (entity.distanceToSqr(itemEntity) > COLLECT_REACH_DISTANCE * COLLECT_REACH_DISTANCE) {
                if (!moveToEntity(itemEntity, NavigationTarget.entity(entityTypeId(itemEntity)))) {
                    return;
                }
                command.resetReachTicks();
                return;
            }
            stopNavigation();
            command.incrementReachTicks();
            if (command.reachTicks() >= COLLECT_REACH_TICKS) {
                int current = entity.normalInventoryCount(command.targetItem());
                if (current <= command.targetStartCount()) {
                    failActiveCommand("item_pickup_not_observed");
                } else {
                    command.setTargetStartCount(current);
                    command.resetReachTicks();
                }
            }
        }

        private ItemEntity nearestRequestedItem(ServerLevel serverLevel, QueuedCommand command, int missingCount) {
            List<ItemEntity> itemEntities = serverLevel.getEntitiesOfClass(
                    ItemEntity.class,
                    entity.getBoundingBox().inflate(command.radius()),
                    itemEntity -> itemEntity.isAlive()
                            && !itemEntity.hasPickUpDelay()
                            && !itemEntity.getItem().isEmpty()
                            && itemEntity.getItem().is(command.targetItem())
                            && itemEntity.getItem().getCount() <= missingCount
                            && serverLevel.hasChunkAt(itemEntity.blockPosition())
                            && entity.hasLineOfSight(itemEntity)
                            && isWithinStartDistance(command, itemEntity.position(), command.radius())
            );
            return itemEntities.stream()
                    .min(Comparator.comparingDouble((ItemEntity itemEntity) -> entity.distanceToSqr(itemEntity))
                            .thenComparingInt(itemEntity -> itemEntity.blockPosition().getX())
                            .thenComparingInt(itemEntity -> itemEntity.blockPosition().getY())
                            .thenComparingInt(itemEntity -> itemEntity.blockPosition().getZ())
                            .thenComparing(itemEntity -> itemEntity.getUUID().toString()))
                    .orElse(null);
        }

        private void farmNearby(QueuedCommand command) {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                failActiveCommand("server_level_unavailable");
                return;
            }
            if (!isWithinStartDistance(command, entity.position(), command.radius())) {
                failActiveCommand("outside_farm_radius");
                return;
            }
            BlockPos cropPos = nearestMatureCrop(serverLevel, command);
            if (cropPos == null) {
                stopNavigation();
                completeActiveCommand("farm:no_mature_crop radius=" + formatRadius(command.radius()));
                return;
            }
            BlockState cropState = serverLevel.getBlockState(cropPos);
            FarmingReplantPlan replantPlan = FarmingWorkPolicy.replantPlan(serverLevel, cropPos, cropState);
            lookAtBlock(cropPos);
            if (!isWithinInteractionDistance(cropPos)) {
                moveNearBlock(cropPos);
                return;
            }
            stopNavigation();
            entity.swingMainHandAction();
            boolean destroyed = serverLevel.destroyBlock(cropPos, true, entity);
            if (!destroyed) {
                failActiveCommand("crop_harvest_failed");
                return;
            }
            if (replantPlan == null) {
                completeActiveCommand("farm:harvested_no_replant unsupported_replant_capability harvested=1", true);
                return;
            }
            Item replantItem = replantPlan.item();
            BlockState replantState = replantPlan.state();
            if (entity.normalInventoryCount(replantItem) <= 0) {
                completeActiveCommand("farm:harvested_no_replant missing=" + itemId(replantItem) + " harvested=1", true);
                return;
            }
            if (!serverLevel.getBlockState(cropPos).isAir() || !replantState.canSurvive(serverLevel, cropPos)) {
                completeActiveCommand("farm:harvested_no_replant invalid_soil harvested=1", true);
                return;
            }
            if (!serverLevel.setBlock(cropPos, replantState, Block.UPDATE_ALL)) {
                completeActiveCommand("farm:harvested_no_replant placement_failed harvested=1", true);
                return;
            }
            if (!entity.consumeOneNormalInventoryItem(replantItem)) {
                serverLevel.destroyBlock(cropPos, false, entity);
                completeActiveCommand("farm:harvested_no_replant seed_race harvested=1", true);
                return;
            }
            completeActiveCommand("farm:harvested=1 replanted=1 item=" + itemId(replantItem), true);
        }

        private BlockPos nearestMatureCrop(ServerLevel serverLevel, QueuedCommand command) {
            int radius = (int) Math.ceil(command.radius());
            BlockPos origin = command.startPosition() == null ? entity.blockPosition() : command.startPosition();
            BlockPos bestPos = null;
            double bestDistance = Double.MAX_VALUE;
            for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
                for (int y = origin.getY() - radius; y <= origin.getY() + radius; y++) {
                    for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                        BlockPos candidate = new BlockPos(x, y, z);
                        if (!serverLevel.hasChunkAt(candidate)
                                || !isWithinStartDistance(command, Vec3.atCenterOf(candidate), command.radius())) {
                            continue;
                        }
                        BlockState blockState = serverLevel.getBlockState(candidate);
                        if (!FarmingWorkPolicy.isSupportedCrop(serverLevel, candidate, blockState)
                                || !FarmingWorkPolicy.isMature(blockState)) {
                            continue;
                        }
                        double distance = entity.distanceToSqr(Vec3.atCenterOf(candidate));
                        if (distance < bestDistance || (distance == bestDistance && compareBlockPos(candidate, bestPos) < 0)) {
                            bestDistance = distance;
                            bestPos = candidate.immutable();
                        }
                    }
                }
            }
            return bestPos;
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

        private void buildStructure(QueuedCommand command) {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                failActiveCommand("server_level_unavailable");
                return;
            }
            BuildPlan plan = command.buildPlan();
            Item material = command.buildMaterial();
            if (plan == null || material == null || !(material instanceof BlockItem blockItem)) {
                failActiveCommand("build_plan_unavailable");
                return;
            }
            if (command.buildIndex() >= plan.blockCount()) {
                completeActiveCommand("build:placed=" + plan.blockCount() + "/" + plan.blockCount()
                        + " material=" + plan.materialId());
                return;
            }
            BlockPos blockPos = plan.positions().get(command.buildIndex());
            if (!canUseBlockTarget(serverLevel, command, blockPos)) {
                failActiveCommand("build_target_unavailable");
                return;
            }
            BlockState placedState = blockItem.getBlock().defaultBlockState();
            String rejection = buildTargetRejection(serverLevel, blockPos, placedState);
            if (rejection != null) {
                failActiveCommand("build_" + rejection);
                return;
            }
            if (untaggedNormalInventoryCount(entity.inventorySnapshot(), material) < 1) {
                failActiveCommand("build_missing_material");
                return;
            }
            lookAtBlock(blockPos);
            if (!isWithinInteractionDistance(blockPos)) {
                moveNearBlock(blockPos);
                noteBuildProgress(command);
                return;
            }
            stopNavigation();
            if (!serverLevel.setBlock(blockPos, placedState, Block.UPDATE_ALL)) {
                failActiveCommand("build_place_failed");
                return;
            }
            if (!consumeOneUntaggedNormalInventoryItem(material)) {
                serverLevel.setBlock(blockPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                failActiveCommand("build_consume_failed_rolled_back");
                return;
            }
            entity.swingMainHandAction();
            command.incrementBuildIndex();
            noteBuildProgress(command);
            if (command.buildIndex() >= plan.blockCount()) {
                completeActiveCommand("build:placed=" + plan.blockCount() + "/" + plan.blockCount()
                        + " material=" + plan.materialId());
            }
        }

        private void useOrBuildPortal(QueuedCommand command) {
            if (!entity.allowWorldActions()) {
                failActiveCommand("world_actions_disabled_during_portal_travel " + portalCommandSummary(command, false,
                        "world_actions_disabled"));
                return;
            }
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                failActiveCommand("server_level_unavailable " + portalCommandSummary(command, false, "server_level_unavailable"));
                return;
            }
            String currentDimension = dimensionId(serverLevel);
            if (hasObservedPortalTransition(command, currentDimension)) {
                stopNavigation();
                completeActiveCommand(portalCommandSummary(command, true, "completed"));
                return;
            }
            if (command.portalFramePlan() != null) {
                buildPortalFrame(command, serverLevel, currentDimension);
                return;
            }
            BlockPos portalPos = command.portalPos();
            if (portalPos == null || !serverLevel.hasChunkAt(portalPos)
                    || !serverLevel.getBlockState(portalPos).is(Blocks.NETHER_PORTAL)) {
                failActiveCommand(portalCommandSummary(command, false, "portal_disappeared"));
                return;
            }
            lookAtBlock(portalPos);
            navigationRuntime.updateDistance(distanceTo(portalPos));
            if (entity.distanceToSqr(Vec3.atCenterOf(portalPos)) > PORTAL_REACH_DISTANCE * PORTAL_REACH_DISTANCE) {
                moveToBlock(portalPos);
                return;
            }
            if (!entity.getBoundingBox().intersects(new AABB(portalPos))) {
                moveToBlock(portalPos);
                return;
            }
            if (activeMonitor != null) {
                activeMonitor.note(portalCommandSummary(command, false, "waiting_for_dimension_transition"));
            }
        }

        private void buildPortalFrame(QueuedCommand command, ServerLevel serverLevel, String currentDimension) {
            PortalFramePlan plan = command.portalFramePlan();
            if (command.portalPlacedBlocks() >= plan.framePositions().size()) {
                igniteBuiltPortalFrame(command, serverLevel, currentDimension);
                return;
            }
            BlockPos blockPos = plan.framePositions().get(command.portalPlacedBlocks());
            if (!isWithinStartDistance(command, Vec3.atCenterOf(blockPos), command.radius())) {
                failActiveCommand(portalCommandSummary(command, false, "frame_position_outside_radius"));
                return;
            }
            String rejection = portalFrameTargetRejection(serverLevel, blockPos, Blocks.OBSIDIAN.defaultBlockState());
            if (rejection != null) {
                failActiveCommand(portalCommandSummary(command, false, "partial_progress_" + rejection));
                return;
            }
            if (untaggedNormalInventoryCount(entity.inventorySnapshot(), Items.OBSIDIAN) < 1) {
                failActiveCommand(portalCommandSummary(command, false, "partial_progress_missing_obsidian"));
                return;
            }
            lookAtBlock(blockPos);
            if (!isWithinInteractionDistance(blockPos)) {
                moveNearBlock(blockPos);
                notePortalProgress(command, currentDimension, "placing_frame");
                return;
            }
            stopNavigation();
            if (!serverLevel.setBlock(blockPos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL)) {
                failActiveCommand(portalCommandSummary(command, false, "partial_progress_place_failed"));
                return;
            }
            if (!consumeOneUntaggedNormalInventoryItem(Items.OBSIDIAN)) {
                failActiveCommand(portalCommandSummary(command, false, "partial_progress_consume_failed"));
                return;
            }
            entity.swingMainHandAction();
            command.incrementPortalPlacedBlocks();
            notePortalProgress(command, currentDimension, "placing_frame");
            if (command.portalPlacedBlocks() >= plan.framePositions().size()) {
                igniteBuiltPortalFrame(command, serverLevel, currentDimension);
            }
        }

        private void igniteBuiltPortalFrame(QueuedCommand command, ServerLevel serverLevel, String currentDimension) {
            PortalFramePlan plan = command.portalFramePlan();
            String rejection = portalIgnitionRejection(serverLevel, plan);
            if (rejection != null) {
                failActiveCommand(portalCommandSummary(command, false, rejection));
                return;
            }
            BlockPos ignitionPos = plan.interiorPositions().get(0);
            lookAtBlock(ignitionPos);
            if (!isWithinInteractionDistance(ignitionPos)) {
                moveNearBlock(ignitionPos);
                notePortalProgress(command, currentDimension, "moving_to_ignite_frame");
                return;
            }
            stopNavigation();
            if (!hasLineOfSightToBlock(serverLevel, ignitionPos)) {
                failActiveCommand(portalCommandSummary(command, false, "portal_ignition_no_line_of_sight"));
                return;
            }
            if (!canAcquireInteractionCooldown()) {
                return;
            }
            if (!acquireInteractionCooldown()) {
                return;
            }
            if (!entity.selectOrMoveNormalItemToHotbar(Items.FLINT_AND_STEEL)) {
                rollbackInteractionCooldown();
                failActiveCommand(portalCommandSummary(command, false, "portal_ignition_missing_flint_and_steel"));
                return;
            }
            if (!createPortalFromFrame(serverLevel, plan, ignitionPos)) {
                rollbackInteractionCooldown();
                failActiveCommand(portalCommandSummary(command, false, "portal_ignition_failed_no_portal_created"));
                return;
            }
            damageSelectedFlintAndSteelOnce();
            entity.swingMainHandAction();
            command.setPortalPos(ignitionPos);
            notePortalProgress(command, currentDimension, "portal_ignited_waiting_for_transition");
        }

        private void smeltItem(QueuedCommand command) {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                failActiveCommand("server_level_unavailable");
                return;
            }
            WorkstationTarget target = furnaceAt(serverLevel, command.furnacePos());
            if (target == null) {
                failActiveCommand("furnace_target_unavailable");
                return;
            }
            if (!isWithinStartDistance(command, Vec3.atCenterOf(command.furnacePos()), FURNACE_SCAN_RADIUS)) {
                failActiveCommand("outside_furnace_radius");
                return;
            }
            lookAtBlock(command.furnacePos());
            if (!isWithinInteractionDistance(command.furnacePos())) {
                moveNearBlock(command.furnacePos());
                return;
            }
            stopNavigation();
            Container furnace = WorkstationLocator.requireContainerAdapter(target);
            List<ItemStack> furnaceStacks = containerSnapshot(furnace);
            SmeltingPlan plan = command.smeltingPlan();
            if (!command.smeltStarted()) {
                MinecraftServer server = entity.getServer();
                if (server == null || !RuntimeSmeltingRecipeIndex.fromServer(server).matchesResolvedRecipe(plan, serverLevel)) {
                    failActiveCommand("furnace_recipe_resolution_changed");
                    return;
                }
                if (!canAcquireInteractionCooldown()) {
                    return;
                }
                boolean started = acquireInteractionCooldown() && entity.startFurnaceSmelt(
                        furnaceStacks,
                        plan.inputItem(),
                        plan.inputCount(),
                        command.fuelPlan().item(),
                        command.fuelPlan().count()
                );
                if (!started) {
                    rollbackInteractionCooldown();
                    failActiveCommand("furnace_start_state_changed");
                    return;
                }
                restoreContainer(furnace, furnaceStacks);
                furnace.setChanged();
                command.markSmeltStarted();
                entity.swingMainHandAction();
                return;
            }
            ItemStack resultStack = furnaceStacks.get(NpcInventoryTransfer.FURNACE_RESULT_SLOT);
            if (!resultStack.isEmpty() && !resultStack.is(plan.outputItem())) {
                failActiveCommand("furnace_result_incompatible");
                return;
            }
            if (resultStack.isEmpty() || resultStack.getCount() < plan.outputCount()) {
                return;
            }
            ItemStack requestedResult = resultStack.copy();
            requestedResult.setCount(plan.outputCount());
            if (!NpcInventoryTransfer.canInsertAll(
                    entity.inventorySnapshot(), requestedResult,
                    NpcInventoryTransfer.FIRST_NORMAL_SLOT, NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT
            )) {
                return;
            }
            if (!canAcquireInteractionCooldown()) {
                return;
            }
            boolean withdrawn = acquireInteractionCooldown()
                    && entity.withdrawFurnaceOutput(furnaceStacks, plan.outputItem(), plan.outputCount());
            if (!withdrawn) {
                rollbackInteractionCooldown();
                failActiveCommand("furnace_output_withdraw_failed");
                return;
            }
            restoreContainer(furnace, furnaceStacks);
            furnace.setChanged();
            entity.swingMainHandAction();
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
            entity.selectBestAttackItem();
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

        private void defendOwner(QueuedCommand command) {
            ServerLevel serverLevel = serverLevel();
            ServerPlayer owner = owner();
            if (serverLevel == null || owner == null) {
                stopAll();
                return;
            }
            if (!isWithinStartDistance(command, owner.position(), command.radius() + FOLLOW_START_DISTANCE)) {
                stopNavigation();
                failActiveCommand("outside_defend_radius");
                return;
            }
            if (canAcquireInteractionCooldown() && acquireInteractionCooldown()) {
                boolean equipped = entity.equipBestAvailableArmor() | entity.selectBestAttackItem();
                if (equipped) {
                    entity.swingMainHandAction();
                } else {
                    rollbackInteractionCooldown();
                }
            }
            LivingEntity target = nearestGuardTarget(serverLevel, owner, command.radius());
            if (target == null) {
                stopNavigation();
                completeActiveCommand();
                return;
            }
            if (attackTarget(target)) {
                completeActiveCommand();
            }
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

        private FuelPlan fuelPlanFor(SmeltingPlan plan) {
            int requiredBurnTicks = plan.inputCount() * plan.cookingTimeTicks();
            List<ItemStack> inventory = entity.inventorySnapshot();
            FuelPlan best = null;
            for (ItemStack stack : inventory) {
                if (stack.isEmpty() || stack.hasTag() || stack.getItem().hasCraftingRemainingItem()) {
                    continue;
                }
                int burnTicks = AbstractFurnaceBlockEntity.getFuel().getOrDefault(stack.getItem(), 0);
                if (burnTicks <= 0 || !AbstractFurnaceBlockEntity.isFuel(stack)) {
                    continue;
                }
                int available = untaggedNormalInventoryCount(inventory, stack.getItem());
                int needed = (requiredBurnTicks + burnTicks - 1) / burnTicks;
                if (needed < 1 || available < needed || needed > stack.getMaxStackSize()) {
                    continue;
                }
                FuelPlan candidate = new FuelPlan(stack.getItem(), needed, burnTicks);
                if (best == null
                        || candidate.count() < best.count()
                        || (candidate.count() == best.count() && candidate.burnTicksPerItem() < best.burnTicksPerItem())
                        || (candidate.count() == best.count()
                        && candidate.burnTicksPerItem() == best.burnTicksPerItem()
                        && itemId(candidate.item()).compareTo(itemId(best.item())) < 0)) {
                    best = candidate;
                }
            }
            return best;
        }

        private WorkstationTarget nearestVanillaFurnace() {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                return null;
            }
            List<WorkstationTarget> targets = new ArrayList<>();
            targets.addAll(workstationLocator.loadedNearby(
                    serverLevel, entity.position(), FURNACE_SCAN_RADIUS, WorkstationCapability.VANILLA_FURNACE
            ));
            targets.addAll(workstationLocator.loadedNearby(
                    serverLevel, entity.position(), FURNACE_SCAN_RADIUS, WorkstationCapability.SMOKER
            ));
            targets.addAll(workstationLocator.loadedNearby(
                    serverLevel, entity.position(), FURNACE_SCAN_RADIUS, WorkstationCapability.BLAST_FURNACE
            ));
            return targets.stream()
                    .min(Comparator
                            .comparingDouble((WorkstationTarget target) -> target.blockPos().distSqr(entity.blockPosition()))
                            .thenComparingInt(target -> target.blockPos().getX())
                            .thenComparingInt(target -> target.blockPos().getY())
                            .thenComparingInt(target -> target.blockPos().getZ()))
                    .orElse(null);
        }

        private WorkstationTarget furnaceAt(ServerLevel serverLevel, BlockPos blockPos) {
            if (serverLevel == null || blockPos == null) {
                return null;
            }
            WorkstationTarget target = workstationLocator.targetAt(
                    serverLevel, entity.position(), FURNACE_SCAN_RADIUS, blockPos, WorkstationCapability.VANILLA_FURNACE
            );
            if (target != null) {
                return target;
            }
            target = workstationLocator.targetAt(
                    serverLevel, entity.position(), FURNACE_SCAN_RADIUS, blockPos, WorkstationCapability.SMOKER
            );
            if (target != null) {
                return target;
            }
            return workstationLocator.targetAt(
                    serverLevel, entity.position(), FURNACE_SCAN_RADIUS, blockPos, WorkstationCapability.BLAST_FURNACE
            );
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

        private WorkstationTarget nearestCraftingTable() {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                return null;
            }
            return workstationLocator.nearestLoaded(
                    serverLevel, entity.position(), CRAFTING_TABLE_SCAN_RADIUS, WorkstationCapability.CRAFTING_TABLE
            );
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

        private String targetPortalDimension(PortalTravelInstruction instruction, String currentDimension) {
            if (instruction.travelNether() && currentDimension.equals(NETHER_DIMENSION_ID)) {
                return OVERWORLD_DIMENSION_ID;
            }
            return instruction.targetDimensionId();
        }

        private static String targetDimensionSummary(String targetDimension) {
            return targetDimension == null ? "any" : targetDimension;
        }

        private boolean hasObservedPortalTransition(QueuedCommand command, String currentDimension) {
            String targetDimension = command.portalTargetDimensionId();
            if (targetDimension == null) {
                return !currentDimension.equals(command.portalStartDimensionId());
            }
            return currentDimension.equals(targetDimension);
        }

        private boolean hasPortalBuildMaterials() {
            return untaggedNormalInventoryCount(entity.inventorySnapshot(), Items.OBSIDIAN) >= PortalFramePlan.REQUIRED_OBSIDIAN
                    && entity.normalInventoryCount(Items.FLINT_AND_STEEL) > 0;
        }

        private PortalSearchResult nearestLoadedPortal(ServerLevel serverLevel, double radius) {
            int boundedRadius = (int) Math.ceil(radius);
            BlockPos origin = entity.blockPosition();
            BlockPos bestPos = null;
            double bestDistance = Double.MAX_VALUE;
            int checked = 0;
            int inspectedLoaded = 0;
            int skippedUnloaded = 0;
            Set<Long> loadedChunks = new HashSet<>();
            for (BlockPos candidate : BlockPos.betweenClosed(
                    origin.offset(-boundedRadius, -boundedRadius, -boundedRadius),
                    origin.offset(boundedRadius, boundedRadius, boundedRadius)
            )) {
                checked++;
                if (!serverLevel.hasChunkAt(candidate)) {
                    skippedUnloaded++;
                    continue;
                }
                inspectedLoaded++;
                loadedChunks.add((((long) candidate.getX() >> 4) << 32) ^ ((long) candidate.getZ() >> 4));
                if (!isWithinHorizontalAndVerticalRadius(origin, candidate, radius)) {
                    continue;
                }
                if (!serverLevel.getBlockState(candidate).is(Blocks.NETHER_PORTAL)) {
                    continue;
                }
                double distance = distanceTo(candidate);
                if (distance < bestDistance || (distance == bestDistance && compareBlockPos(candidate, bestPos) < 0)) {
                    bestDistance = distance;
                    bestPos = candidate.immutable();
                }
            }
            return new PortalSearchResult(bestPos, "checked_positions=" + checked
                    + " inspected_loaded_positions=" + inspectedLoaded + " inspected_loaded_chunks=" + loadedChunks.size()
                    + " skipped_unloaded=" + skippedUnloaded + " capped=false transition_observed=false");
        }

        private PortalBuildPlanResult nearestSafePortalFramePlan(ServerLevel serverLevel, double radius) {
            int boundedRadius = (int) Math.ceil(radius);
            BlockPos origin = entity.blockPosition();
            int checked = 0;
            int skippedUnloaded = 0;
            for (int distance = 2; distance <= boundedRadius; distance++) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos base = origin.relative(direction, distance);
                    for (Direction.Axis axis : List.of(Direction.Axis.X, Direction.Axis.Z)) {
                        PortalFramePlan plan = PortalFramePlanner.plan(base, axis);
                        checked++;
                        String rejection = portalFramePlanRejection(serverLevel, origin, plan, radius);
                        if (rejection == null) {
                            return new PortalBuildPlanResult(plan, "checked_positions=" + checked
                                    + " skipped_unloaded=" + skippedUnloaded + " capped=false transition_observed=false");
                        }
                        if (rejection.contains("unloaded")) {
                            skippedUnloaded++;
                        }
                    }
                }
            }
            return new PortalBuildPlanResult(null, "checked_positions=" + checked
                    + " skipped_unloaded=" + skippedUnloaded + " capped=false transition_observed=false");
        }

        private String portalFramePlanRejection(ServerLevel serverLevel, BlockPos origin, PortalFramePlan plan, double radius) {
            for (BlockPos blockPos : plan.framePositions()) {
                if (!isWithinHorizontalAndVerticalRadius(origin, blockPos, radius)) {
                    return "outside_radius";
                }
                String rejection = portalFrameTargetRejection(serverLevel, blockPos, Blocks.OBSIDIAN.defaultBlockState());
                if (rejection != null) {
                    return rejection;
                }
            }
            for (BlockPos blockPos : plan.interiorPositions()) {
                if (!isWithinHorizontalAndVerticalRadius(origin, blockPos, radius)) {
                    return "outside_radius";
                }
                if (!serverLevel.hasChunkAt(blockPos)) {
                    return "target_chunk_unloaded";
                }
                BlockState state = serverLevel.getBlockState(blockPos);
                if (!state.isAir() || !state.getFluidState().isEmpty() || !serverLevel.getEntities(entity, new AABB(blockPos)).isEmpty()) {
                    return "interior_not_air";
                }
            }
            return null;
        }

        private String portalFrameTargetRejection(ServerLevel serverLevel, BlockPos blockPos, BlockState placedState) {
            if (!serverLevel.hasChunkAt(blockPos)) {
                return "target_chunk_unloaded";
            }
            BlockState current = serverLevel.getBlockState(blockPos);
            if (!current.isAir() || !current.getFluidState().isEmpty()) {
                return "target_occupied";
            }
            if (!serverLevel.getEntities(entity, new AABB(blockPos)).isEmpty()) {
                return "target_entity_collision";
            }
            if (!placedState.canSurvive(serverLevel, blockPos)
                    || !serverLevel.isUnobstructed(placedState, blockPos, CollisionContext.empty())) {
                return "target_collision_or_support";
            }
            return null;
        }

        private String portalIgnitionRejection(ServerLevel serverLevel, PortalFramePlan plan) {
            if (!entity.allowWorldActions()) {
                return "world_actions_disabled_before_portal_ignition";
            }
            if (entity.normalInventoryCount(Items.FLINT_AND_STEEL) <= 0) {
                return "portal_ignition_missing_flint_and_steel";
            }
            for (BlockPos blockPos : plan.framePositions()) {
                if (!serverLevel.hasChunkAt(blockPos)) {
                    return "portal_ignition_frame_unloaded";
                }
                if (!serverLevel.getBlockState(blockPos).is(Blocks.OBSIDIAN)) {
                    return "portal_ignition_frame_not_obsidian";
                }
            }
            for (BlockPos blockPos : plan.interiorPositions()) {
                if (!serverLevel.hasChunkAt(blockPos)) {
                    return "portal_ignition_interior_unloaded";
                }
                BlockState state = serverLevel.getBlockState(blockPos);
                if (!state.isAir() && !state.is(Blocks.FIRE) && !state.is(Blocks.NETHER_PORTAL)) {
                    return "portal_ignition_interior_blocked";
                }
            }
            return null;
        }

        private boolean createPortalFromFrame(ServerLevel serverLevel, PortalFramePlan plan, BlockPos ignitionPos) {
            if (PortalShape.findEmptyPortalShape(serverLevel, ignitionPos, plan.horizontalAxis()).isPresent()) {
                PortalShape.findEmptyPortalShape(serverLevel, ignitionPos, plan.horizontalAxis()).get().createPortalBlocks();
            } else if (serverLevel.getBlockState(ignitionPos).isAir()
                    && Blocks.FIRE.defaultBlockState().canSurvive(serverLevel, ignitionPos)) {
                serverLevel.setBlock(ignitionPos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            }
            for (BlockPos blockPos : plan.interiorPositions()) {
                if (serverLevel.getBlockState(blockPos).is(Blocks.NETHER_PORTAL)) {
                    return true;
                }
            }
            PortalShape.findEmptyPortalShape(serverLevel, ignitionPos, plan.horizontalAxis())
                    .ifPresent(PortalShape::createPortalBlocks);
            for (BlockPos blockPos : plan.interiorPositions()) {
                if (serverLevel.getBlockState(blockPos).is(Blocks.NETHER_PORTAL)) {
                    return true;
                }
            }
            return false;
        }

        private void damageSelectedFlintAndSteelOnce() {
            ItemStack selectedStack = entity.getMainHandItem();
            if (!selectedStack.is(Items.FLINT_AND_STEEL)) {
                return;
            }
            selectedStack.hurtAndBreak(1, entity, ignored -> { });
        }

        private static boolean isWithinHorizontalAndVerticalRadius(BlockPos origin, BlockPos candidate, double radius) {
            int deltaX = origin.getX() - candidate.getX();
            int deltaY = origin.getY() - candidate.getY();
            int deltaZ = origin.getZ() - candidate.getZ();
            return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= radius * radius;
        }

        private String portalCommandSummary(QueuedCommand command, boolean transitionObserved, String reason) {
            String currentDimension = serverLevel() == null ? "unknown" : dimensionId(serverLevel());
            BlockPos frame = command.portalFramePlan() == null ? null : command.portalFramePlan().origin();
            String position = command.portalPos() == null
                    ? frame == null ? "none" : frame.toShortString()
                    : command.portalPos().toShortString();
            return "mode=" + command.portalMode() + " target_dimension="
                    + targetDimensionSummary(command.portalTargetDimensionId()) + " current_dimension=" + currentDimension
                    + " source=" + (command.portalFramePlan() == null ? "loaded_scan" : "player_like_build")
                    + " radius=" + formatRadius(command.radius()) + " portal_frame_position=" + position
                    + " placed_blocks=" + command.portalPlacedBlocks() + " required_obsidian="
                    + PortalFramePlan.REQUIRED_OBSIDIAN + " has_flint_and_steel="
                    + (entity.normalInventoryCount(Items.FLINT_AND_STEEL) > 0)
                    + " " + command.portalDiagnostics() + " transition_observed=" + transitionObserved
                    + " failure=" + reason;
        }

        private void notePortalProgress(QueuedCommand command, String currentDimension, String reason) {
            if (activeMonitor == null) {
                return;
            }
            activeMonitor.note("mode=" + command.portalMode() + " target_dimension="
                    + targetDimensionSummary(command.portalTargetDimensionId()) + " current_dimension=" + currentDimension
                    + " source=player_like_build radius=" + formatRadius(command.radius())
                    + " frame=" + command.portalFramePlan().origin().toShortString()
                    + " placed_blocks=" + command.portalPlacedBlocks() + " required_obsidian="
                    + PortalFramePlan.REQUIRED_OBSIDIAN + " has_flint_and_steel="
                    + (entity.normalInventoryCount(Items.FLINT_AND_STEEL) > 0) + " status=" + reason);
        }

        private String statusSummary() {
            return snapshot().summary();
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
                cancelNavigation(activeMonitor.boundedReason());
                OpenPlayerDebugEvents.record("automation", status.name(), null, null, null,
                        "kind=" + activeCommand.kind().name() + " reason=" + activeMonitor.boundedReason());
                OpenPlayerRawTrace.automationOperation(status.name(), activeCommand.kind().name(),
                        "reason=" + activeMonitor.boundedReason());
                recoverActiveSmeltResources();
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

        private void noteBuildProgress(QueuedCommand command) {
            if (activeMonitor == null || command.buildPlan() == null) {
                return;
            }
            activeMonitor.note("build:placed=" + command.buildIndex() + "/" + command.buildPlan().blockCount()
                    + " material=" + command.buildPlan().materialId());
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
            recoverActiveSmeltResources();
            cancelNavigation(reason);
            activeCommand = null;
        }

        private void recoverActiveSmeltResources() {
            if (activeCommand == null || activeCommand.kind() != IntentKind.SMELT_ITEM || !activeCommand.smeltStarted()) {
                return;
            }
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                return;
            }
            WorkstationTarget target = furnaceAt(serverLevel, activeCommand.furnacePos());
            if (target == null) {
                return;
            }
            Container furnace = WorkstationLocator.requireContainerAdapter(target);
            List<ItemStack> furnaceStacks = containerSnapshot(furnace);
            SmeltingPlan plan = activeCommand.smeltingPlan();
            if (entity.recoverFurnaceSmeltResources(
                    furnaceStacks,
                    plan.inputItem(),
                    activeCommand.fuelPlan().item(),
                    plan.outputItem()
            )) {
                restoreContainer(furnace, furnaceStacks);
                furnace.setChanged();
            }
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
            if (kind == IntentKind.EXPLORE_CHUNKS) {
                if (!entity.allowWorldActions()) {
                    failActiveCommand("world_actions_disabled_before_explore");
                    return;
                }
                if (activeCommand.explorationTarget() != null) {
                    moveToExploreTarget(activeCommand.explorationTarget());
                } else {
                    startExploreChunks(activeCommand);
                }
                return;
            }
            if (kind == IntentKind.GET_ITEM) {
                if (!entity.allowWorldActions()) {
                    failActiveCommand("world_actions_disabled_during_get_item");
                    return;
                }
                ServerLevel serverLevel = serverLevel();
                if (serverLevel == null) {
                    failActiveCommand("server_level_unavailable");
                    return;
                }
                int missing = activeCommand.targetItemCount() - entity.normalInventoryCount(activeCommand.targetItem());
                ItemEntity target = nearestRequestedItem(serverLevel, activeCommand, missing);
                if (target != null) {
                    moveToEntity(target, NavigationTarget.entity(entityTypeId(target)));
                }
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
            if (kind == IntentKind.BUILD_STRUCTURE && activeCommand.buildPlan() != null
                    && activeCommand.buildIndex() < activeCommand.buildPlan().blockCount()) {
                moveNearBlock(activeCommand.buildPlan().positions().get(activeCommand.buildIndex()));
                return;
            }
            if ((kind == IntentKind.USE_PORTAL || kind == IntentKind.TRAVEL_NETHER)) {
                if (!entity.allowWorldActions()) {
                    failActiveCommand("world_actions_disabled_during_portal_travel");
                    return;
                }
                if (activeCommand.portalFramePlan() != null
                        && activeCommand.portalPlacedBlocks() < activeCommand.portalFramePlan().framePositions().size()) {
                    moveNearBlock(activeCommand.portalFramePlan().framePositions().get(activeCommand.portalPlacedBlocks()));
                } else if (activeCommand.portalPos() != null) {
                    moveToBlock(activeCommand.portalPos());
                }
                return;
            }
            if (kind == IntentKind.FARM_NEARBY) {
                ServerLevel serverLevel = serverLevel();
                if (serverLevel == null) {
                    failActiveCommand("server_level_unavailable");
                    return;
                }
                BlockPos cropPos = nearestMatureCrop(serverLevel, activeCommand);
                if (cropPos != null) {
                    moveNearBlock(cropPos);
                }
                return;
            }
            if (kind == IntentKind.SMELT_ITEM && activeCommand.furnacePos() != null) {
                moveNearBlock(activeCommand.furnacePos());
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
            if (kind == IntentKind.MOVE || kind == IntentKind.GOTO || kind == IntentKind.EXPLORE_CHUNKS) {
                return MOVE_MAX_TICKS;
            }
            if (kind == IntentKind.COLLECT_ITEMS || kind == IntentKind.COLLECT_FOOD || kind == IntentKind.GET_ITEM) {
                return COLLECT_MAX_TICKS;
            }
            if (kind == IntentKind.BUILD_STRUCTURE || kind == IntentKind.USE_PORTAL || kind == IntentKind.TRAVEL_NETHER) {
                return LONG_TASK_MAX_TICKS;
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

        private static String missingItemsSummary(ResourcePlanResult plan) {
            if (plan.missingItems().isEmpty()) {
                return "none";
            }
            List<String> entries = new ArrayList<>(plan.missingItems().size());
            for (ItemStack stack : plan.missingItems()) {
                entries.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount());
            }
            return String.join(", ", entries);
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
            if (block instanceof BedBlock) {
                return BlockInteractionCapability.BED_STATUS;
            }
            if (block instanceof CraftingTableBlock) {
                return BlockInteractionCapability.CRAFTING_TABLE_SURFACE;
            }
            if (block instanceof FurnaceBlock || state.is(Blocks.SMOKER) || state.is(Blocks.BLAST_FURNACE)) {
                return BlockInteractionCapability.FURNACE_SURFACE;
            }
            if (block instanceof BarrelBlock || state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
                return BlockInteractionCapability.CONTAINER_SURFACE;
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
                    || capability == BlockInteractionCapability.BED_STATUS
                    || capability == BlockInteractionCapability.CRAFTING_TABLE_SURFACE
                    || capability == BlockInteractionCapability.FURNACE_SURFACE
                    || capability == BlockInteractionCapability.CONTAINER_SURFACE) {
                return true;
            }
            return false;
        }

        private EntityInteractionCapability entityInteractionCapability(Entity target) {
            if (target instanceof Sheep sheep && sheep.readyForShearing()
                    && entity.normalInventoryCount(Items.SHEARS) > 0) {
                return EntityInteractionCapability.SHEAR_SHEEP;
            }
            if ((target instanceof Cow || target instanceof MushroomCow)
                    && entity.normalInventoryCount(Items.BUCKET) > 0
                    && canInsertOneNormalInventoryItem(Items.MILK_BUCKET)) {
                return EntityInteractionCapability.MILK_COW;
            }
            if (target instanceof net.minecraft.world.entity.npc.Villager) {
                return EntityInteractionCapability.UNAVAILABLE;
            }
            return EntityInteractionCapability.UNAVAILABLE;
        }

        private boolean applyEntityInteraction(ServerLevel serverLevel, Entity target, EntityInteractionCapability capability) {
            if (capability == EntityInteractionCapability.SHEAR_SHEEP && target instanceof Sheep sheep) {
                if (!entity.selectOrMoveNormalItemToHotbar(Items.SHEARS) || !sheep.readyForShearing()) {
                    return false;
                }
                sheep.shear(SoundSource.PLAYERS);
                damageSelectedToolOnce(Items.SHEARS);
                return !sheep.readyForShearing();
            }
            if (capability == EntityInteractionCapability.MILK_COW && (target instanceof Cow || target instanceof MushroomCow)) {
                if (entity.normalInventoryCount(Items.BUCKET) <= 0 || !canInsertOneNormalInventoryItem(Items.MILK_BUCKET)) {
                    return false;
                }
                List<ItemStack> snapshot = entity.inventorySnapshot();
                if (!entity.consumeOneNormalInventoryItem(Items.BUCKET) || !insertOneNormalInventoryItem(Items.MILK_BUCKET)) {
                    restoreEntityInventory(snapshot);
                    return false;
                }
                return true;
            }
            return false;
        }

        private void damageSelectedToolOnce(Item item) {
            ItemStack selectedStack = entity.getMainHandItem();
            if (selectedStack.is(item)) {
                selectedStack.hurtAndBreak(1, entity, ignored -> { });
            }
        }

        private boolean canInsertOneNormalInventoryItem(Item item) {
            return NpcInventoryTransfer.canInsertAll(
                    entity.inventorySnapshot(), new ItemStack(item),
                    NpcInventoryTransfer.FIRST_NORMAL_SLOT, NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT
            );
        }

        private boolean insertOneNormalInventoryItem(Item item) {
            List<ItemStack> inventory = entity.inventorySnapshot();
            ItemStack inserted = new ItemStack(item);
            for (int slot = NpcInventoryTransfer.FIRST_NORMAL_SLOT;
                 slot < Math.min(NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT, inventory.size()); slot++) {
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, inserted) && stack.getCount() < stack.getMaxStackSize()) {
                    stack.grow(1);
                    return entity.setInventoryItem(slot, stack);
                }
            }
            for (int slot = NpcInventoryTransfer.FIRST_NORMAL_SLOT;
                 slot < Math.min(NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT, inventory.size()); slot++) {
                if (inventory.get(slot).isEmpty()) {
                    return entity.setInventoryItem(slot, inserted);
                }
            }
            return false;
        }

        private void restoreEntityInventory(List<ItemStack> snapshot) {
            for (int slot = 0; slot < snapshot.size(); slot++) {
                entity.setInventoryItem(slot, snapshot.get(slot));
            }
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

        private static String reasonSuffix(ResourcePlanResult plan) {
            if (plan.reason().isEmpty()) {
                return "";
            }
            return ": " + plan.reason();
        }

        enum BlockInteractionCapability {
            LEVER("lever"),
            BUTTON("button"),
            DOOR("door"),
            TRAPDOOR("trapdoor"),
            FENCE_GATE("fence_gate"),
            BELL("bell"),
            NOTE_BLOCK("note_block"),
            BED_STATUS("bed_status"),
            CRAFTING_TABLE_SURFACE("crafting_table_surface"),
            FURNACE_SURFACE("furnace_surface"),
            CONTAINER_SURFACE("container_surface"),
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
            SHEAR_SHEEP("shear_sheep"),
            MILK_COW("milk_cow"),
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

        private record LoadedChunkTarget(
                int chunkX,
                int chunkZ,
                BlockPos targetPos,
                double distanceSquared,
                boolean visited,
                int recency
        ) {
        }

        private record PortalSearchResult(BlockPos portalPos, String diagnostics) {
        }

        private record PortalBuildPlanResult(PortalFramePlan plan, String diagnostics) {
        }

        private static final class QueuedCommand {
            private final IntentKind kind;
            private final Coordinate coordinate;
            private final double radius;
            private final BlockPos blockTarget;
            private final Entity entityTarget;
            private final boolean gotoOwner;
            private final BlockPos furnacePos;
            private final SmeltingPlan smeltingPlan;
            private final FuelPlan fuelPlan;
            private final int maxTicks;
            private final boolean survivalOnly;
            private final int repeatRemaining;
            private BuildPlan buildPlan;
            private Item buildMaterial;
            private int buildIndex;
            private BlockPos startPosition;
            private BlockPos explorationTarget;
            private int explorationChunkX;
            private int explorationChunkZ;
            private String targetItemId;
            private Item targetItem;
            private int targetItemCount;
            private int targetStartCount;
            private int reachTicks;
            private boolean patrolReturn;
            private boolean smeltStarted;
            private String portalTargetDimensionId;
            private String portalStartDimensionId;
            private BlockPos portalPos;
            private PortalFramePlan portalFramePlan;
            private String portalMode;
            private String portalDiagnostics;
            private int portalPlacedBlocks;

            private QueuedCommand(IntentKind kind, Coordinate coordinate, double radius) {
                this(kind, coordinate, radius, null, null, false, null, null, null, 0, false, 1);
            }

            private QueuedCommand(IntentKind kind, Coordinate coordinate, double radius, BlockPos blockTarget,
                                    Entity entityTarget, boolean gotoOwner, BlockPos furnacePos,
                                    SmeltingPlan smeltingPlan, FuelPlan fuelPlan, int maxTicks, boolean survivalOnly,
                                    int repeatRemaining) {
                this.kind = kind;
                this.coordinate = coordinate;
                this.radius = radius;
                this.blockTarget = blockTarget;
                this.entityTarget = entityTarget;
                this.gotoOwner = gotoOwner;
                this.furnacePos = furnacePos;
                this.smeltingPlan = smeltingPlan;
                this.fuelPlan = fuelPlan;
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

            private static QueuedCommand gotoOwnerCommand() {
                return new QueuedCommand(IntentKind.GOTO, null, 0.0D, null, null, true, null, null, null, 0, false, 1);
            }

            private static QueuedCommand gotoBlock(BlockPos blockPos) {
                return new QueuedCommand(
                        IntentKind.GOTO, null, 0.0D, blockPos.immutable(), null, false, null, null, null, 0,
                        false, 1
                );
            }

            private static QueuedCommand gotoEntity(Entity entity) {
                return new QueuedCommand(IntentKind.GOTO, null, 0.0D, null, entity, false, null, null, null, 0, false, 1);
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

            private static QueuedCommand collectFood(double radius) {
                return new QueuedCommand(IntentKind.COLLECT_FOOD, null, radius);
            }

            private static QueuedCommand getItem(String itemId, Item item, int targetCount, int startCount, double radius) {
                QueuedCommand command = new QueuedCommand(IntentKind.GET_ITEM, null, radius);
                command.targetItemId = itemId;
                command.targetItem = item;
                command.targetItemCount = targetCount;
                command.targetStartCount = startCount;
                return command;
            }

            private static QueuedCommand farmNearby(double radius, int repeatCount) {
                return new QueuedCommand(IntentKind.FARM_NEARBY, null, radius, null, null, false, null, null, null, 0,
                        false, repeatCount);
            }

            private static QueuedCommand breakBlock(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.BREAK_BLOCK, coordinate, 0.0D);
            }

            private static QueuedCommand placeBlock(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.PLACE_BLOCK, coordinate, 0.0D);
            }

            private static QueuedCommand buildStructure(BuildPlan buildPlan, Item buildMaterial) {
                QueuedCommand command = new QueuedCommand(IntentKind.BUILD_STRUCTURE, null, 0.0D);
                command.buildPlan = buildPlan;
                command.buildMaterial = buildMaterial;
                return command;
            }

            private static QueuedCommand portalTravel(IntentKind kind, double radius, String targetDimensionId,
                                                      BlockPos portalPos,
                                                      PortalFramePlan portalFramePlan, String mode,
                                                      String diagnostics) {
                QueuedCommand command = new QueuedCommand(kind, null, radius);
                command.portalTargetDimensionId = targetDimensionId;
                command.portalPos = portalPos == null ? null : portalPos.immutable();
                command.portalFramePlan = portalFramePlan;
                command.portalMode = mode;
                command.portalDiagnostics = diagnostics;
                return command;
            }

            private static QueuedCommand interactBlock(BlockPos blockPos) {
                return new QueuedCommand(
                        IntentKind.INTERACT, null, 0.0D, blockPos.immutable(), null, false, null, null, null, 0,
                        false, 1
                );
            }

            private static QueuedCommand interactEntity(Entity target, double radius) {
                return new QueuedCommand(
                        IntentKind.INTERACT, null, radius, null, target, false, null, null, null, 0, false, 1
                );
            }

            private static QueuedCommand attackNearest(double radius) {
                return new QueuedCommand(IntentKind.ATTACK_NEAREST, null, radius);
            }

            private static QueuedCommand attackTarget(Entity target, double radius) {
                return new QueuedCommand(IntentKind.ATTACK_TARGET, null, radius, null, target, false, null, null, null, 0, false, 1);
            }

            private static QueuedCommand selfDefense(double radius) {
                return new QueuedCommand(
                        IntentKind.ATTACK_NEAREST, null, radius, null, null, false, null, null, null, 0, true, 1
                );
            }

            private static QueuedCommand guardOwner(double radius) {
                return new QueuedCommand(IntentKind.GUARD_OWNER, null, radius);
            }

            private static QueuedCommand defendOwner(double radius) {
                return new QueuedCommand(IntentKind.DEFEND_OWNER, null, radius);
            }

            private static QueuedCommand patrol(Coordinate coordinate) {
                return new QueuedCommand(IntentKind.PATROL, coordinate, 0.0D);
            }

            private static QueuedCommand exploreChunks(double radius, int steps) {
                return new QueuedCommand(IntentKind.EXPLORE_CHUNKS, null, radius, null, null, false, null, null, null, 0,
                        false, steps);
            }

            private static QueuedCommand smelt(BlockPos furnacePos, SmeltingPlan smeltingPlan, FuelPlan fuelPlan) {
                int maxTicks = FURNACE_START_MARGIN_TICKS
                        + smeltingPlan.inputCount() * smeltingPlan.cookingTimeTicks()
                        + FURNACE_OUTPUT_MARGIN_TICKS;
                return new QueuedCommand(
                        IntentKind.SMELT_ITEM,
                        null,
                        0.0D,
                        null,
                        null,
                        false,
                        furnacePos.immutable(),
                        smeltingPlan,
                        fuelPlan,
                        maxTicks,
                        false,
                        1
                );
            }

            private QueuedCommand nextRepeat() {
                return new QueuedCommand(kind, coordinate, radius, blockTarget, entityTarget, gotoOwner, furnacePos,
                        smeltingPlan, fuelPlan, maxTicks, survivalOnly, repeatRemaining - 1);
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

            private boolean gotoOwner() {
                return gotoOwner;
            }

            private BlockPos furnacePos() {
                return furnacePos;
            }

            private String targetItemId() {
                return targetItemId;
            }

            private Item targetItem() {
                return targetItem;
            }

            private int targetItemCount() {
                return targetItemCount;
            }

            private int targetStartCount() {
                return targetStartCount;
            }

            private void setTargetStartCount(int targetStartCount) {
                this.targetStartCount = targetStartCount;
            }

            private SmeltingPlan smeltingPlan() {
                return smeltingPlan;
            }

            private FuelPlan fuelPlan() {
                return fuelPlan;
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

            private BuildPlan buildPlan() {
                return buildPlan;
            }

            private Item buildMaterial() {
                return buildMaterial;
            }

            private int buildIndex() {
                return buildIndex;
            }

            private void incrementBuildIndex() {
                buildIndex++;
            }

            private boolean smeltStarted() {
                return smeltStarted;
            }

            private void markSmeltStarted() {
                smeltStarted = true;
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

            private void setPortalStartDimensionId(String portalStartDimensionId) {
                this.portalStartDimensionId = portalStartDimensionId;
            }

            private BlockPos explorationTarget() {
                return explorationTarget;
            }

            private void setExplorationTarget(BlockPos explorationTarget) {
                this.explorationTarget = explorationTarget;
            }

            private int explorationChunkX() {
                return explorationChunkX;
            }

            private int explorationChunkZ() {
                return explorationChunkZ;
            }

            private void setExplorationChunk(int explorationChunkX, int explorationChunkZ) {
                this.explorationChunkX = explorationChunkX;
                this.explorationChunkZ = explorationChunkZ;
            }

            private boolean returningToStart() {
                return patrolReturn;
            }

            private String portalTargetDimensionId() {
                return portalTargetDimensionId;
            }

            private String portalStartDimensionId() {
                return portalStartDimensionId == null ? "" : portalStartDimensionId;
            }

            private BlockPos portalPos() {
                return portalPos;
            }

            private void setPortalPos(BlockPos portalPos) {
                this.portalPos = portalPos == null ? null : portalPos.immutable();
            }

            private PortalFramePlan portalFramePlan() {
                return portalFramePlan;
            }

            private String portalMode() {
                return portalMode == null ? "existing_portal" : portalMode;
            }

            private String portalDiagnostics() {
                return portalDiagnostics == null ? "" : portalDiagnostics;
            }

            private int portalPlacedBlocks() {
                return portalPlacedBlocks;
            }

            private void incrementPortalPlacedBlocks() {
                portalPlacedBlocks++;
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
