package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.automation.AutomationInstructionParser.Coordinate;
import dev.soffits.openplayer.automation.InventoryActionInstructionParser.ParsedItemInstruction;
import dev.soffits.openplayer.automation.navigation.NavigationRuntime;
import dev.soffits.openplayer.automation.navigation.NavigationTarget;
import dev.soffits.openplayer.automation.resource.GetItemRequest;
import dev.soffits.openplayer.automation.resource.ResourceDependencyPlanner;
import dev.soffits.openplayer.automation.resource.ResourcePlanningCapabilities;
import dev.soffits.openplayer.automation.resource.ResourcePlanResult;
import dev.soffits.openplayer.automation.resource.RuntimeCraftingRecipeIndex;
import dev.soffits.openplayer.automation.resource.RuntimeSmeltingRecipeIndex;
import dev.soffits.openplayer.automation.resource.SmeltingPlan;
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
import java.util.List;
import java.util.Queue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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
        private static final double PATROL_MAX_DISTANCE = 32.0D;
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

        private final OpenPlayerNpcEntity entity;
        private final Queue<QueuedCommand> queuedCommands = new ArrayDeque<>();
        private final InteractionCooldown interactionCooldown = new InteractionCooldown(INTERACTION_COOLDOWN_TICKS);
        private final NavigationRuntime navigationRuntime = new NavigationRuntime(NAVIGATION_MAX_RECOVERIES);
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
                ParsedItemInstruction parsed = null;
                if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
                    parsed = InventoryActionInstructionParser.parseItemCountOrNull(intent.instruction(), false);
                    if (parsed == null) {
                        return rejected(kind.name() + " requires blank or instruction: <item_id> [count]");
                    }
                }
                SafeContainerTarget target = kind == IntentKind.STASH_ITEM ? preferredStashContainer() : nearestSafeContainer();
                if (target == null) {
                    return rejected(kind.name() + " requires a loaded nearby vanilla chest or barrel");
                }
                if (!canAcquireInteractionCooldown()) {
                    return rejected(interactionCooldownMessage());
                }
                boolean transferred = acquireInteractionCooldown() && depositToContainer(target.container(), parsed);
                if (!transferred) {
                    rollbackInteractionCooldown();
                    return rejected(kind.name() + " requires all requested normal inventory items to fit in the container");
                }
                if (kind == IntentKind.STASH_ITEM) {
                    entity.rememberStash(dimensionId(serverLevel()), target.blockPos());
                }
                target.container().setChanged();
                entity.swingMainHandAction();
                return accepted(kind.name() + " accepted: container " + target.blockPos().toShortString());
            }
            if (kind == IntentKind.WITHDRAW_ITEM) {
                ParsedItemInstruction parsed = InventoryActionInstructionParser.parseItemCountOrNull(intent.instruction(), false);
                if (parsed == null) {
                    return rejected("WITHDRAW_ITEM requires instruction: <item_id> [count]");
                }
                SafeContainerTarget target = preferredStashContainer();
                if (target == null) {
                    return rejected("WITHDRAW_ITEM requires a loaded nearby vanilla chest or barrel");
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
                MinecraftServer server = entity.getServer();
                if (server == null) {
                    return rejected("GET_ITEM requires server recipe data for inventory crafting");
                }
                ResourceDependencyPlanner resourceDependencyPlanner = new ResourceDependencyPlanner(
                        RuntimeCraftingRecipeIndex.fromServer(server)
                );
                boolean hasCraftingTable = hasNearbyCraftingTable();
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
                            + ": " + missingItemsSummary(plan));
                }
                return rejected("GET_ITEM unsupported for bounded inventory crafting: " + parsed.itemId()
                        + reasonSuffix(plan));
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
                SafeFurnaceTarget target = nearestSafeFurnace();
                if (target == null) {
                    return rejected("SMELT_ITEM requires a loaded nearby vanilla furnace");
                }
                List<ItemStack> furnaceStacks = containerSnapshot(target.furnace());
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
            AutomationControllerMonitorStatus monitorStatus = activeMonitor == null
                    ? AutomationControllerMonitorStatus.IDLE
                    : activeMonitor.status();
            String monitorReason = activeMonitor == null ? "idle" : activeMonitor.boundedReason();
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
            if (command.kind() == IntentKind.LOOK) {
                Coordinate coordinate = command.coordinate();
                entity.getLookControl().setLookAt(coordinate.x(), coordinate.y(), coordinate.z());
                completeActiveCommand();
                return;
            }
            if (command.kind() == IntentKind.COLLECT_ITEMS
                    || command.kind() == IntentKind.BREAK_BLOCK
                    || command.kind() == IntentKind.PLACE_BLOCK
                    || command.kind() == IntentKind.SMELT_ITEM
                    || command.kind() == IntentKind.ATTACK_NEAREST
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
            if (activeCommand.kind() == IntentKind.SMELT_ITEM) {
                smeltItem(activeCommand);
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
                stopNavigation();
                completeActiveCommand();
                return;
            }
            entity.getLookControl().setLookAt(itemEntity);
            if (entity.distanceToSqr(itemEntity) > COLLECT_REACH_DISTANCE * COLLECT_REACH_DISTANCE) {
                moveToEntity(itemEntity, NavigationTarget.entity(entityTypeId(itemEntity)));
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

        private void smeltItem(QueuedCommand command) {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                failActiveCommand("server_level_unavailable");
                return;
            }
            SafeFurnaceTarget target = safeFurnaceAt(serverLevel, command.furnacePos());
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
            List<ItemStack> furnaceStacks = containerSnapshot(target.furnace());
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
                restoreContainer(target.furnace(), furnaceStacks);
                target.furnace().setChanged();
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
            restoreContainer(target.furnace(), furnaceStacks);
            target.furnace().setChanged();
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
            LivingEntity target = nearestAttackTarget(serverLevel, command.radius(), Vec3.atCenterOf(command.startPosition()));
            if (target == null) {
                stopNavigation();
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
                moveToEntity(owner, NavigationTarget.owner());
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

        private SafeFurnaceTarget nearestSafeFurnace() {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                return null;
            }
            BlockPos center = entity.blockPosition();
            List<SafeFurnaceTarget> targets = new ArrayList<>();
            for (BlockPos candidate : BlockPos.betweenClosed(
                    center.offset(-FURNACE_SCAN_RADIUS, -FURNACE_SCAN_RADIUS, -FURNACE_SCAN_RADIUS),
                    center.offset(FURNACE_SCAN_RADIUS, FURNACE_SCAN_RADIUS, FURNACE_SCAN_RADIUS)
            )) {
                SafeFurnaceTarget target = safeFurnaceAt(serverLevel, candidate);
                if (target != null) {
                    targets.add(target);
                }
            }
            return targets.stream()
                    .min(Comparator
                            .comparingDouble((SafeFurnaceTarget target) -> target.blockPos().distSqr(center))
                            .thenComparingInt(target -> target.blockPos().getX())
                            .thenComparingInt(target -> target.blockPos().getY())
                            .thenComparingInt(target -> target.blockPos().getZ()))
                    .orElse(null);
        }

        private SafeFurnaceTarget safeFurnaceAt(ServerLevel serverLevel, BlockPos blockPos) {
            if (serverLevel == null || blockPos == null || !serverLevel.hasChunkAt(blockPos)) {
                return null;
            }
            if (entity.distanceToSqr(Vec3.atCenterOf(blockPos)) > FURNACE_SCAN_RADIUS * FURNACE_SCAN_RADIUS) {
                return null;
            }
            BlockState blockState = serverLevel.getBlockState(blockPos);
            if (!blockState.is(Blocks.FURNACE)) {
                return null;
            }
            BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
            if (!(blockEntity instanceof AbstractFurnaceBlockEntity furnace)) {
                return null;
            }
            return new SafeFurnaceTarget(blockPos.immutable(), furnace);
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
            BlockState blockState = serverLevel.getBlockState(blockPos);
            if (!blockState.is(Blocks.CHEST) && !blockState.is(Blocks.BARREL)) {
                return null;
            }
            BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
            if (!(blockEntity instanceof Container container)) {
                return null;
            }
            return new SafeContainerTarget(blockPos.immutable(), container);
        }

        private boolean hasNearbyCraftingTable() {
            ServerLevel serverLevel = serverLevel();
            if (serverLevel == null) {
                return false;
            }
            BlockPos center = entity.blockPosition();
            for (BlockPos candidate : BlockPos.betweenClosed(
                    center.offset(-CRAFTING_TABLE_SCAN_RADIUS, -CRAFTING_TABLE_SCAN_RADIUS, -CRAFTING_TABLE_SCAN_RADIUS),
                    center.offset(CRAFTING_TABLE_SCAN_RADIUS, CRAFTING_TABLE_SCAN_RADIUS, CRAFTING_TABLE_SCAN_RADIUS)
            )) {
                if (serverLevel.hasChunkAt(candidate)
                        && entity.distanceToSqr(Vec3.atCenterOf(candidate))
                        <= CRAFTING_TABLE_SCAN_RADIUS * CRAFTING_TABLE_SCAN_RADIUS
                        && serverLevel.getBlockState(candidate).is(Blocks.CRAFTING_TABLE)) {
                    return true;
                }
            }
            return false;
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
            return snapshot().summary();
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
            if (activeCommand != null) {
                OpenPlayerDebugEvents.record("automation", "completed", null, null, null,
                        "kind=" + activeCommand.kind().name());
                OpenPlayerRawTrace.automationOperation("completed", activeCommand.kind().name(),
                        "entity=" + entity.getUUID() + " position=" + entity.position());
            }
            if (activeMonitor != null) {
                activeMonitor.complete();
            }
            navigationRuntime.complete();
            activeCommand = null;
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
            SafeFurnaceTarget target = safeFurnaceAt(serverLevel, activeCommand.furnacePos());
            if (target == null) {
                return;
            }
            List<ItemStack> furnaceStacks = containerSnapshot(target.furnace());
            SmeltingPlan plan = activeCommand.smeltingPlan();
            if (entity.recoverFurnaceSmeltResources(
                    furnaceStacks,
                    plan.inputItem(),
                    activeCommand.fuelPlan().item(),
                    plan.outputItem()
            )) {
                restoreContainer(target.furnace(), furnaceStacks);
                target.furnace().setChanged();
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
            if (kind == IntentKind.SMELT_ITEM && activeCommand.furnacePos() != null) {
                moveNearBlock(activeCommand.furnacePos());
            }
        }

        private void moveToPosition(Coordinate coordinate) {
            boolean loaded = isCoordinateLoaded(coordinate);
            navigationRuntime.plan(NavigationTarget.position(coordinate.x(), coordinate.y(), coordinate.z()),
                    distanceTo(coordinate), loaded);
            if (!loaded) {
                failActiveCommand("navigation_target_unloaded");
                return;
            }
            boolean accepted = entity.getNavigation().moveTo(
                    coordinate.x(), coordinate.y(), coordinate.z(), PLAYER_LIKE_NAVIGATION_SPEED
            );
            navigationRuntime.markReachable(accepted);
        }

        private void moveToBlock(BlockPos blockPos) {
            boolean loaded = isBlockLoaded(blockPos);
            navigationRuntime.plan(NavigationTarget.block(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                    distanceTo(blockPos), loaded);
            if (!loaded) {
                failActiveCommand("navigation_target_unloaded");
                return;
            }
            boolean accepted = entity.getNavigation().moveTo(
                    blockPos.getX() + 0.5D, blockPos.getY(), blockPos.getZ() + 0.5D, PLAYER_LIKE_NAVIGATION_SPEED
            );
            navigationRuntime.markReachable(accepted);
        }

        private void moveToEntity(Entity target, NavigationTarget navigationTarget) {
            boolean loaded = isBlockLoaded(target.blockPosition());
            navigationRuntime.plan(navigationTarget, entity.distanceToSqr(target), loaded);
            if (!loaded) {
                failActiveCommand("navigation_target_unloaded");
                return;
            }
            boolean accepted = entity.getNavigation().moveTo(target, PLAYER_LIKE_NAVIGATION_SPEED);
            navigationRuntime.markReachable(accepted);
        }

        private void stopNavigation() {
            entity.getNavigation().stop();
            navigationRuntime.complete();
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

        private static String itemId(Item item) {
            return BuiltInRegistries.ITEM.getKey(item).toString();
        }

        private static String reasonSuffix(ResourcePlanResult plan) {
            if (plan.reason().isEmpty()) {
                return "";
            }
            return ": " + plan.reason();
        }

        private record SafeContainerTarget(BlockPos blockPos, Container container) {
        }

        private record SafeFurnaceTarget(BlockPos blockPos, AbstractFurnaceBlockEntity furnace) {
        }

        private record FuelPlan(Item item, int count, int burnTicksPerItem) {
        }

        private static final class QueuedCommand {
            private final IntentKind kind;
            private final Coordinate coordinate;
            private final double radius;
            private final BlockPos furnacePos;
            private final SmeltingPlan smeltingPlan;
            private final FuelPlan fuelPlan;
            private final int maxTicks;
            private BlockPos startPosition;
            private int reachTicks;
            private boolean patrolReturn;
            private boolean smeltStarted;

            private QueuedCommand(IntentKind kind, Coordinate coordinate, double radius) {
                this(kind, coordinate, radius, null, null, null, 0);
            }

            private QueuedCommand(IntentKind kind, Coordinate coordinate, double radius, BlockPos furnacePos,
                                  SmeltingPlan smeltingPlan, FuelPlan fuelPlan, int maxTicks) {
                this.kind = kind;
                this.coordinate = coordinate;
                this.radius = radius;
                this.furnacePos = furnacePos;
                this.smeltingPlan = smeltingPlan;
                this.fuelPlan = fuelPlan;
                this.maxTicks = maxTicks;
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

            private static QueuedCommand smelt(BlockPos furnacePos, SmeltingPlan smeltingPlan, FuelPlan fuelPlan) {
                int maxTicks = FURNACE_START_MARGIN_TICKS
                        + smeltingPlan.inputCount() * smeltingPlan.cookingTimeTicks()
                        + FURNACE_OUTPUT_MARGIN_TICKS;
                return new QueuedCommand(
                        IntentKind.SMELT_ITEM,
                        null,
                        0.0D,
                        furnacePos.immutable(),
                        smeltingPlan,
                        fuelPlan,
                        maxTicks
                );
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

            private BlockPos furnacePos() {
                return furnacePos;
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
