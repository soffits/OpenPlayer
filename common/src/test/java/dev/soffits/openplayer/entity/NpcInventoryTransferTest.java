package dev.soffits.openplayer.entity;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class NpcInventoryTransferTest {
    private NpcInventoryTransferTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        matchesExactItemsOnly();
        summaryMergesDuplicateCountsInSlotOrder();
        removesExactCountAcrossStacksWithoutPartialMutation();
        insertionRejectsFullTargetWithoutMutation();
        hotbarSelectionOnlyScansHotbar();
        selectedHotbarDropCommitsOnlyAfterSpawnSuccess();
        armorEquipRejectsWrongSlotAndSwapsSafely();
    }

    private static void matchesExactItemsOnly() {
        NonNullList<ItemStack> stacks = emptyInventory();
        stacks.set(0, new ItemStack(Items.BREAD, 2));
        stacks.set(1, new ItemStack(Items.APPLE, 3));

        require(NpcInventoryTransfer.countItem(stacks, Items.BREAD, 0, 31) == 2,
                "bread count must include only exact bread stacks");
        require(NpcInventoryTransfer.countItem(stacks, Items.COOKED_BEEF, 0, 31) == 0,
                "different food item must not match bread");
    }

    private static void summaryMergesDuplicateCountsInSlotOrder() {
        NonNullList<ItemStack> stacks = emptyInventory();
        stacks.set(0, new ItemStack(Items.BREAD, 2));
        stacks.set(10, new ItemStack(Items.COBBLESTONE, 4));
        stacks.set(11, new ItemStack(Items.BREAD, 3));
        stacks.set(33, new ItemStack(Items.IRON_CHESTPLATE, 1));

        String summary = NpcInventorySummary.format(stacks, 0);
        require(summary.contains("selected_hotbar=0"), "summary must include selected hotbar slot");
        require(summary.contains("0=minecraft:breadx2"), "summary must include hotbar slot stack");
        require(summary.contains("chest=minecraft:iron_chestplatex1"), "summary must include equipment stack");
        require(summary.contains("inventory=[minecraft:breadx5, minecraft:cobblestonex4]"),
                "summary must merge duplicate counts in first-seen deterministic order: " + summary);
    }

    private static void removesExactCountAcrossStacksWithoutPartialMutation() {
        NonNullList<ItemStack> stacks = emptyInventory();
        stacks.set(0, new ItemStack(Items.BREAD, 2));
        stacks.set(10, new ItemStack(Items.BREAD, 3));

        ItemStack removed = NpcInventoryTransfer.removeExactCount(stacks, Items.BREAD, 4, 0, 31);
        require(removed.getCount() == 4 && removed.is(Items.BREAD), "exact removal must return requested bread stack");
        require(stacks.get(0).isEmpty(), "first stack must be consumed first");
        require(stacks.get(10).getCount() == 1, "second stack must keep remainder");

        List<ItemStack> snapshot = NpcInventoryTransfer.copyStacks(stacks);
        ItemStack failed = NpcInventoryTransfer.removeExactCount(stacks, Items.BREAD, 4, 0, 31);
        require(failed.isEmpty(), "insufficient count must reject");
        require(stacksEqual(stacks, snapshot), "insufficient count must not partially mutate inventory");
    }

    private static void insertionRejectsFullTargetWithoutMutation() {
        NonNullList<ItemStack> stacks = NonNullList.withSize(2, ItemStack.EMPTY);
        stacks.set(0, new ItemStack(Items.STONE, 64));
        stacks.set(1, new ItemStack(Items.DIRT, 64));
        List<ItemStack> snapshot = NpcInventoryTransfer.copyStacks(stacks);

        boolean inserted = NpcInventoryTransfer.insertAll(stacks, new ItemStack(Items.BREAD, 1), 0, stacks.size());
        require(!inserted, "full target must reject insertion");
        require(stacksEqual(stacks, snapshot), "failed insertion must not mutate target inventory");
    }

    private static void hotbarSelectionOnlyScansHotbar() {
        NonNullList<ItemStack> stacks = emptyInventory();
        stacks.set(10, new ItemStack(Items.IRON_SWORD));
        require(NpcInventoryTransfer.firstHotbarSlotMatchingItem(stacks, Items.IRON_SWORD) == -1,
                "non-hotbar matching item must not be selected");
        stacks.set(8, new ItemStack(Items.IRON_SWORD));
        require(NpcInventoryTransfer.firstHotbarSlotMatchingItem(stacks, Items.IRON_SWORD) == 8,
                "hotbar scan must include slot eight");
    }

    private static void selectedHotbarDropCommitsOnlyAfterSpawnSuccess() {
        NonNullList<ItemStack> stacks = emptyInventory();
        stacks.set(2, new ItemStack(Items.COBBLESTONE, 5));

        ItemStack candidate = NpcInventoryTransfer.selectedHotbarDropStackOrEmpty(stacks, 2);
        require(candidate.is(Items.COBBLESTONE) && candidate.getCount() == 5,
                "drop candidate must copy the selected hotbar stack");
        candidate.setCount(1);
        require(stacks.get(2).getCount() == 5, "drop candidate mutation must not mutate source stack");

        require(!NpcInventoryTransfer.commitSelectedHotbarDrop(stacks, 2, false),
                "failed spawn must not commit selected hotbar drop");
        require(stacks.get(2).is(Items.COBBLESTONE) && stacks.get(2).getCount() == 5,
                "failed spawn must leave selected hotbar stack unchanged");

        require(NpcInventoryTransfer.commitSelectedHotbarDrop(stacks, 2, true),
                "successful spawn must commit selected hotbar drop");
        require(stacks.get(2).isEmpty(), "successful spawn must clear selected hotbar slot");
    }

    private static void armorEquipRejectsWrongSlotAndSwapsSafely() {
        NonNullList<ItemStack> stacks = emptyInventory();
        stacks.set(10, new ItemStack(Items.IRON_CHESTPLATE));
        stacks.set(33, new ItemStack(Items.LEATHER_CHESTPLATE));
        require(NpcInventoryTransfer.equipArmorByItem(stacks, Items.IRON_CHESTPLATE),
                "matching armor must equip");
        require(stacks.get(33).is(Items.IRON_CHESTPLATE), "target armor slot must receive selected armor");
        require(stacks.get(10).is(Items.LEATHER_CHESTPLATE), "source slot must receive previous armor");

        require(!NpcInventoryTransfer.equipArmorByItem(stacks, Items.IRON_SWORD), "non-armor must reject");
        require(!NpcInventoryTransfer.equipArmorByItem(stacks, Items.IRON_BOOTS), "missing armor item must reject");
    }

    private static NonNullList<ItemStack> emptyInventory() {
        return NonNullList.withSize(NpcInventoryTransfer.INVENTORY_SIZE, ItemStack.EMPTY);
    }

    private static boolean stacksEqual(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            ItemStack leftStack = left.get(index);
            ItemStack rightStack = right.get(index);
            if (!ItemStack.isSameItemSameTags(leftStack, rightStack) || leftStack.getCount() != rightStack.getCount()) {
                return false;
            }
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
