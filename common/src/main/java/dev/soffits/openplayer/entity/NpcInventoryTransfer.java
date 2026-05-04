package dev.soffits.openplayer.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class NpcInventoryTransfer {
    public static final int INVENTORY_SIZE = 36;
    public static final int FIRST_NORMAL_SLOT = 0;
    public static final int FIRST_EQUIPMENT_SLOT = 31;
    public static final int HOTBAR_SIZE = 9;
    public static final int ARMOR_FEET_SLOT = 31;
    public static final int ARMOR_LEGS_SLOT = 32;
    public static final int ARMOR_CHEST_SLOT = 33;
    public static final int ARMOR_HEAD_SLOT = 34;
    public static final int OFFHAND_SLOT = 35;

    private NpcInventoryTransfer() {
    }

    public static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        if (stacks == null) {
            throw new IllegalArgumentException("stacks cannot be null");
        }
        List<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return copy;
    }

    public static int countItem(List<ItemStack> stacks, Item item, int startSlotInclusive, int endSlotExclusive) {
        requireItem(item);
        int count = 0;
        for (int slot = boundedStart(startSlotInclusive); slot < boundedEnd(stacks, endSlotExclusive); slot++) {
            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static ItemStack removeExactCount(List<ItemStack> stacks, Item item, int count,
                                             int startSlotInclusive, int endSlotExclusive) {
        requireItem(item);
        if (count < 1) {
            return ItemStack.EMPTY;
        }
        if (countItem(stacks, item, startSlotInclusive, endSlotExclusive) < count) {
            return ItemStack.EMPTY;
        }
        int remaining = count;
        for (int slot = boundedStart(startSlotInclusive); slot < boundedEnd(stacks, endSlotExclusive) && remaining > 0; slot++) {
            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                int moved = Math.min(remaining, stack.getCount());
                stack.shrink(moved);
                remaining -= moved;
                if (stack.isEmpty()) {
                    stacks.set(slot, ItemStack.EMPTY);
                }
            }
        }
        return new ItemStack(item, count);
    }

    public static boolean canInsertAll(List<ItemStack> stacks, ItemStack stack,
                                       int startSlotInclusive, int endSlotExclusive) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        List<ItemStack> copy = copyStacks(stacks);
        return insertAll(copy, stack.copy(), startSlotInclusive, endSlotExclusive);
    }

    public static boolean insertAll(List<ItemStack> stacks, ItemStack stack,
                                     int startSlotInclusive, int endSlotExclusive) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        if (!canFit(stacks, stack, startSlotInclusive, endSlotExclusive)) {
            return false;
        }
        ItemStack remaining = stack.copy();
        mergeIntoExisting(stacks, remaining, startSlotInclusive, endSlotExclusive);
        fillEmpty(stacks, remaining, startSlotInclusive, endSlotExclusive);
        return remaining.isEmpty();
    }

    public static boolean depositAllNormalInventory(List<ItemStack> npcStacks, List<ItemStack> containerStacks) {
        List<ItemStack> npcSnapshot = copyStacks(npcStacks);
        List<ItemStack> containerSnapshot = copyStacks(containerStacks);
        for (int slot = FIRST_NORMAL_SLOT; slot < boundedEnd(npcStacks, FIRST_EQUIPMENT_SLOT); slot++) {
            ItemStack stack = npcStacks.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!insertAll(containerStacks, stack.copy(), 0, containerStacks.size())) {
                restoreStacks(npcStacks, npcSnapshot);
                restoreStacks(containerStacks, containerSnapshot);
                return false;
            }
            npcStacks.set(slot, ItemStack.EMPTY);
        }
        return true;
    }

    public static boolean depositExactItem(List<ItemStack> npcStacks, List<ItemStack> containerStacks, Item item, int count) {
        requireItem(item);
        if (count < 1) {
            return false;
        }
        List<ItemStack> npcSnapshot = copyStacks(npcStacks);
        List<ItemStack> containerSnapshot = copyStacks(containerStacks);
        List<ItemStack> removedStacks = removeExactStacks(npcStacks, item, count, FIRST_NORMAL_SLOT, FIRST_EQUIPMENT_SLOT);
        if (removedStacks.isEmpty() || !insertAll(containerStacks, removedStacks, 0, containerStacks.size())) {
            restoreStacks(npcStacks, npcSnapshot);
            restoreStacks(containerStacks, containerSnapshot);
            return false;
        }
        return true;
    }

    public static boolean withdrawExactItem(List<ItemStack> npcStacks, List<ItemStack> containerStacks, Item item, int count) {
        requireItem(item);
        if (count < 1) {
            return false;
        }
        List<ItemStack> npcSnapshot = copyStacks(npcStacks);
        List<ItemStack> containerSnapshot = copyStacks(containerStacks);
        List<ItemStack> removedStacks = removeExactStacks(containerStacks, item, count, 0, containerStacks.size());
        if (removedStacks.isEmpty() || !insertAll(npcStacks, removedStacks, FIRST_NORMAL_SLOT, FIRST_EQUIPMENT_SLOT)) {
            restoreStacks(npcStacks, npcSnapshot);
            restoreStacks(containerStacks, containerSnapshot);
            return false;
        }
        return true;
    }

    public static int firstHotbarSlotMatchingItem(List<ItemStack> stacks, Item item) {
        requireItem(item);
        int end = Math.min(HOTBAR_SIZE, stacks.size());
        for (int slot = 0; slot < end; slot++) {
            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                return slot;
            }
        }
        return -1;
    }

    public static ItemStack selectedHotbarDropStackOrEmpty(List<ItemStack> stacks, int selectedSlot) {
        if (!isValidHotbarSlot(stacks, selectedSlot)) {
            return ItemStack.EMPTY;
        }
        ItemStack selectedStack = stacks.get(selectedSlot);
        return selectedStack.isEmpty() ? ItemStack.EMPTY : selectedStack.copy();
    }

    public static boolean commitSelectedHotbarDrop(List<ItemStack> stacks, int selectedSlot, boolean spawned) {
        if (!spawned || !isValidHotbarSlot(stacks, selectedSlot) || stacks.get(selectedSlot).isEmpty()) {
            return false;
        }
        stacks.set(selectedSlot, ItemStack.EMPTY);
        return true;
    }

    private static void restoreStacks(List<ItemStack> stacks, List<ItemStack> snapshot) {
        for (int slot = 0; slot < stacks.size() && slot < snapshot.size(); slot++) {
            stacks.set(slot, snapshot.get(slot).copy());
        }
    }

    private static List<ItemStack> removeExactStacks(List<ItemStack> stacks, Item item, int count,
                                                     int startSlotInclusive, int endSlotExclusive) {
        requireItem(item);
        if (count < 1 || countItem(stacks, item, startSlotInclusive, endSlotExclusive) < count) {
            return List.of();
        }
        List<ItemStack> removedStacks = new ArrayList<>();
        int remaining = count;
        for (int slot = boundedStart(startSlotInclusive); slot < boundedEnd(stacks, endSlotExclusive) && remaining > 0; slot++) {
            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                int moved = Math.min(remaining, stack.getCount());
                ItemStack removed = stack.copy();
                removed.setCount(moved);
                removedStacks.add(removed);
                stack.shrink(moved);
                remaining -= moved;
                if (stack.isEmpty()) {
                    stacks.set(slot, ItemStack.EMPTY);
                }
            }
        }
        return remaining == 0 ? removedStacks : List.of();
    }

    private static boolean insertAll(List<ItemStack> stacks, List<ItemStack> insertedStacks,
                                     int startSlotInclusive, int endSlotExclusive) {
        List<ItemStack> snapshot = copyStacks(stacks);
        for (ItemStack insertedStack : insertedStacks) {
            if (!insertAll(snapshot, insertedStack, startSlotInclusive, endSlotExclusive)) {
                return false;
            }
        }
        restoreStacks(stacks, snapshot);
        return true;
    }

    private static boolean canFit(List<ItemStack> stacks, ItemStack stack,
                                  int startSlotInclusive, int endSlotExclusive) {
        ItemStack remaining = stack.copy();
        mergeIntoExisting(copyStacks(stacks), remaining, startSlotInclusive, endSlotExclusive);
        if (remaining.isEmpty()) {
            return true;
        }
        int emptyCapacity = 0;
        for (int slot = boundedStart(startSlotInclusive); slot < boundedEnd(stacks, endSlotExclusive); slot++) {
            if (stacks.get(slot).isEmpty()) {
                emptyCapacity += Math.min(remaining.getMaxStackSize(), remaining.getCount() - emptyCapacity);
                if (emptyCapacity >= remaining.getCount()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void mergeIntoExisting(List<ItemStack> stacks, ItemStack remaining,
                                          int startSlotInclusive, int endSlotExclusive) {
        for (int slot = boundedStart(startSlotInclusive); slot < boundedEnd(stacks, endSlotExclusive) && !remaining.isEmpty(); slot++) {
            ItemStack existing = stacks.get(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, remaining)) {
                int moved = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    remaining.shrink(moved);
                }
            }
        }
    }

    private static void fillEmpty(List<ItemStack> stacks, ItemStack remaining,
                                  int startSlotInclusive, int endSlotExclusive) {
        for (int slot = boundedStart(startSlotInclusive); slot < boundedEnd(stacks, endSlotExclusive) && !remaining.isEmpty(); slot++) {
            if (stacks.get(slot).isEmpty()) {
                ItemStack inserted = remaining.copy();
                inserted.setCount(Math.min(remaining.getCount(), inserted.getMaxStackSize()));
                stacks.set(slot, inserted);
                remaining.shrink(inserted.getCount());
            }
        }
    }

    private static boolean isValidHotbarSlot(List<ItemStack> stacks, int slot) {
        return stacks != null && slot >= 0 && slot < HOTBAR_SIZE && slot < stacks.size();
    }

    private static int boundedStart(int startSlotInclusive) {
        return Math.max(0, startSlotInclusive);
    }

    private static int boundedEnd(List<ItemStack> stacks, int endSlotExclusive) {
        if (stacks == null) {
            throw new IllegalArgumentException("stacks cannot be null");
        }
        return Math.min(stacks.size(), Math.max(0, endSlotExclusive));
    }

    private static void requireItem(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
    }
}
