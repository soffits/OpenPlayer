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

abstract class VanillaAutomationContainerController extends VanillaAutomationNavigationController {
    protected VanillaAutomationContainerController(OpenPlayerNpcEntity entity) {
        super(entity);
    }

protected boolean depositToContainer(Container container, ParsedItemInstruction parsed) {
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

protected boolean hasNormalInventoryToTransfer() {
    List<ItemStack> inventory = entity.inventorySnapshot();
    int endSlot = Math.min(NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT, inventory.size());
    for (int slot = NpcInventoryTransfer.FIRST_NORMAL_SLOT; slot < endSlot; slot++) {
        if (!inventory.get(slot).isEmpty()) {
            return true;
        }
    }
    return false;
}

protected boolean withdrawFromContainer(Container container, Item item, int count) {
    List<ItemStack> containerStacks = containerSnapshot(container);
    if (!entity.withdrawInventoryItemFrom(containerStacks, item, count)) {
        return false;
    }
    restoreContainer(container, containerStacks);
    return true;
}

protected SafeContainerTarget preferredStashContainer() {
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

protected SafeContainerTarget nearestSafeContainer() {
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

protected SafeContainerTarget safeContainerAt(ServerLevel serverLevel, BlockPos blockPos) {
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

protected static List<ItemStack> containerSnapshot(Container container) {
    List<ItemStack> stacks = new ArrayList<>(container.getContainerSize());
    for (int slot = 0; slot < container.getContainerSize(); slot++) {
        stacks.add(container.getItem(slot).copy());
    }
    return stacks;
}

protected static void restoreContainer(Container container, List<ItemStack> snapshot) {
    for (int slot = 0; slot < container.getContainerSize() && slot < snapshot.size(); slot++) {
        container.setItem(slot, snapshot.get(slot).copy());
    }
}

protected static String dimensionId(ServerLevel serverLevel) {
    return serverLevel.dimension().location().toString();
}

protected static int inventoryCount(List<ItemStack> stacks) {
    int count = 0;
    for (ItemStack stack : stacks) {
        if (!stack.isEmpty()) {
            count += stack.getCount();
        }
    }
    return count;
}

protected static boolean canAcceptItem(List<ItemStack> inventory, ItemStack itemStack) {
    if (itemStack == null || itemStack.isEmpty()) {
        return true;
    }
    for (ItemStack inventoryStack : inventory) {
        if (inventoryStack.isEmpty()) {
            return true;
        }
        if (ItemStack.isSameItemSameTags(inventoryStack, itemStack)
                && inventoryStack.getCount() < inventoryStack.getMaxStackSize()) {
            return true;
        }
    }
    return false;
}

protected static String inventoryDeltaSummary(List<ItemStack> before, List<ItemStack> after) {
    return stackDeltaSummary(stackCounts(before), stackCounts(after));
}

protected static Map<String, Integer> nearbyDropCounts(ServerLevel serverLevel, BlockPos blockPos) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    AABB area = new AABB(blockPos).inflate(2.0D);
    for (ItemEntity itemEntity : serverLevel.getEntitiesOfClass(ItemEntity.class, area, Entity::isAlive)) {
        ItemStack stack = itemEntity.getItem();
        if (!stack.isEmpty()) {
            counts.merge(itemId(stack), stack.getCount(), Integer::sum);
        }
    }
    return counts;
}

protected static String dropDeltaSummary(Map<String, Integer> before, Map<String, Integer> after) {
    return stackDeltaSummary(before, after);
}

protected static Map<String, Integer> stackCounts(List<ItemStack> stacks) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (ItemStack stack : stacks) {
        if (!stack.isEmpty()) {
            counts.merge(itemId(stack), stack.getCount(), Integer::sum);
        }
    }
    return counts;
}

protected static String stackDeltaSummary(Map<String, Integer> before, Map<String, Integer> after) {
    StringBuilder builder = new StringBuilder();
    java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>(before.keySet());
    ids.addAll(after.keySet());
    for (String id : ids) {
        int delta = after.getOrDefault(id, 0) - before.getOrDefault(id, 0);
        if (delta == 0) {
            continue;
        }
        if (builder.length() > 0) {
            builder.append(',');
        }
        builder.append(id).append(delta > 0 ? "+" : "").append(delta);
    }
    return builder.length() == 0 ? "none" : builder.toString();
}

protected static String itemId(ItemStack stack) {
    return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
}

protected static int untaggedNormalInventoryCount(List<ItemStack> inventory, Item item) {
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

protected boolean consumeOneUntaggedNormalInventoryItem(Item item) {
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

protected boolean hasLineOfSightToBlock(ServerLevel serverLevel, BlockPos blockPos) {
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
}
