package dev.soffits.openplayer.entity;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.automation.AutomationControllerSnapshot;
import dev.soffits.openplayer.aicore.AICoreNpcSessionState;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

abstract class OpenPlayerNpcEntityBase extends OpenPlayerNpcEntityInventoryBase {
    protected OpenPlayerNpcEntityBase(EntityType<? extends OpenPlayerNpcEntity> entityType, Level level) {
        super(entityType, level);
    }

    public void rememberStash(String dimensionId, BlockPos blockPos) {
        if (dimensionId == null || dimensionId.isBlank() || blockPos == null) {
            stashMemory = null;
            return;
        }
        stashMemory = new StashMemory(dimensionId.trim(), blockPos.immutable());
    }

    public BlockPos rememberedStashPos(String dimensionId) {
        if (dimensionId == null || stashMemory == null || !stashMemory.dimensionId().equals(dimensionId)) {
            return null;
        }
        return stashMemory.blockPos();
    }

    public boolean equipMatchingItem(Item item) {
        if (item == null) {
            return false;
        }
        int slot = NpcInventoryTransfer.firstHotbarSlotMatchingItem(internalInventory, item);
        return slot >= 0 && selectHotbarSlot(slot);
    }

    public boolean dropInventoryItem(Item item, int count) {
        if (item == null || count < 1) {
            return false;
        }
        List<ItemStack> snapshot = NpcInventoryTransfer.copyStacks(internalInventory);
        ItemStack droppedStack = NpcInventoryTransfer.removeExactCount(
                internalInventory,
                item,
                count,
                NpcInventoryTransfer.FIRST_NORMAL_SLOT,
                NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT
        );
        if (droppedStack.isEmpty()) {
            restoreInternalInventory(snapshot);
            return false;
        }
        ItemEntity itemEntity = spawnAtLocation(droppedStack, 0.0F);
        if (itemEntity == null) {
            restoreInternalInventory(snapshot);
            return false;
        }
        itemEntity.setPickUpDelay(40);
        return true;
    }

    public boolean consumeOneNormalInventoryItem(Item item) {
        if (item == null) {
            return false;
        }
        return !NpcInventoryTransfer.removeExactCount(
                internalInventory,
                item,
                1,
                NpcInventoryTransfer.FIRST_NORMAL_SLOT,
                NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT
        ).isEmpty();
    }

    public boolean giveInventoryItemToPlayer(ServerPlayer player, Item item, int count) {
        if (player == null || item == null || count < 1) {
            return false;
        }
        ItemStack transferStack = new ItemStack(item, count);
        List<ItemStack> playerInventorySnapshot = NpcInventoryTransfer.copyStacks(player.getInventory().items);
        if (!NpcInventoryTransfer.canInsertAll(
                playerInventorySnapshot,
                transferStack,
                0,
                playerInventorySnapshot.size()
        )) {
            return false;
        }

        List<ItemStack> npcSnapshot = NpcInventoryTransfer.copyStacks(internalInventory);
        List<ItemStack> livePlayerSnapshot = NpcInventoryTransfer.copyStacks(player.getInventory().items);
        ItemStack extractedStack = NpcInventoryTransfer.removeExactCount(
                internalInventory,
                item,
                count,
                NpcInventoryTransfer.FIRST_NORMAL_SLOT,
                NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT
        );
        if (extractedStack.isEmpty()) {
            restoreInternalInventory(npcSnapshot);
            return false;
        }
        ItemStack remainingStack = extractedStack.copy();
        boolean accepted = player.getInventory().add(remainingStack) && remainingStack.isEmpty();
        if (!accepted) {
            restoreInternalInventory(npcSnapshot);
            restorePlayerMainInventory(player, livePlayerSnapshot);
            return false;
        }
        return true;
    }

    public boolean swapSelectedHotbarStackToOffhand() {
        ItemStack selectedStack = getMainHandItem();
        if (selectedStack.isEmpty()) {
            return false;
        }
        ItemStack offhandStack = getItemBySlot(EquipmentSlot.OFFHAND);
        internalInventory.set(selectedMainHandSlot, offhandStack.copy());
        internalInventory.set(OFFHAND_SLOT, selectedStack.copy());
        return true;
    }

    public boolean unequipToNormalInventory(EquipmentSlot equipmentSlot) {
        int equipmentInventorySlot = inventorySlotForEquipmentSlot(equipmentSlot);
        if (equipmentInventorySlot < FIRST_EQUIPMENT_INVENTORY_SLOT || equipmentInventorySlot >= internalInventory.size()) {
            return false;
        }
        ItemStack equippedStack = internalInventory.get(equipmentInventorySlot);
        if (equippedStack.isEmpty()) {
            return false;
        }
        List<ItemStack> snapshot = inventorySnapshot();
        internalInventory.set(equipmentInventorySlot, ItemStack.EMPTY);
        if (!NpcInventoryTransfer.insertAll(internalInventory, equippedStack.copy(), FIRST_NORMAL_INVENTORY_SLOT, FIRST_EQUIPMENT_INVENTORY_SLOT)) {
            restoreInternalInventory(snapshot);
            return false;
        }
        return true;
    }

    protected abstract void collectNearbyItems();

    protected abstract void syncPersistedIdentity();

    protected abstract boolean isAICoreControl(String control);

    protected abstract List<ItemStack> hotbarItems();

    protected abstract void restoreInternalInventory(List<ItemStack> snapshot);

    protected abstract void restorePlayerMainInventory(ServerPlayer player, List<ItemStack> snapshot);

    protected abstract double toolScore(ItemStack itemStack, BlockState blockState);

    protected abstract int inventorySlotForEquipmentSlot(EquipmentSlot equipmentSlot);

    protected int firstNormalInventorySlotMatching(Item item, int startSlotInclusive, int endSlotExclusive) {
        for (int slot = startSlotInclusive; slot < endSlotExclusive; slot++) {
            ItemStack stack = internalInventory.get(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                return slot;
            }
        }
        return -1;
    }
}
