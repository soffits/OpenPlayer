package dev.soffits.openplayer.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
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
        if (count < 1 || count > item.getDefaultInstance().getMaxStackSize()) {
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

    public static boolean equipArmorByItem(List<ItemStack> stacks, Item item) {
        requireItem(item);
        if (!(item instanceof ArmorItem armorItem)) {
            return false;
        }
        int armorSlot = inventorySlotForArmorSlot(armorItem.getEquipmentSlot());
        if (armorSlot < 0 || armorSlot >= stacks.size()) {
            return false;
        }
        for (int slot = FIRST_NORMAL_SLOT; slot < Math.min(FIRST_EQUIPMENT_SLOT, stacks.size()); slot++) {
            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                ItemStack selectedArmor = stack.copy();
                selectedArmor.setCount(1);
                ItemStack remainingSource = stack.copy();
                remainingSource.shrink(1);
                ItemStack currentArmor = stacks.get(armorSlot).copy();
                if (!remainingSource.isEmpty() && !currentArmor.isEmpty()) {
                    return false;
                }
                stacks.set(armorSlot, selectedArmor);
                if (remainingSource.isEmpty()) {
                    stacks.set(slot, currentArmor);
                } else if (currentArmor.isEmpty()) {
                    stacks.set(slot, remainingSource);
                }
                return true;
            }
        }
        return false;
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

    private static int inventorySlotForArmorSlot(EquipmentSlot equipmentSlot) {
        return switch (equipmentSlot) {
            case FEET -> ARMOR_FEET_SLOT;
            case LEGS -> ARMOR_LEGS_SLOT;
            case CHEST -> ARMOR_CHEST_SLOT;
            case HEAD -> ARMOR_HEAD_SLOT;
            case MAINHAND, OFFHAND -> -1;
        };
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
