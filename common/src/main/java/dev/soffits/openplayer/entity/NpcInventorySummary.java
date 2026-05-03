package dev.soffits.openplayer.entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class NpcInventorySummary {
    private NpcInventorySummary() {
    }

    public static String format(List<ItemStack> stacks, int selectedHotbarSlot) {
        if (stacks == null) {
            throw new IllegalArgumentException("stacks cannot be null");
        }
        return "selected_hotbar=" + selectedHotbarSlot
                + "; hotbar=[" + hotbarSummary(stacks) + "]"
                + "; equipment=[" + equipmentSummary(stacks) + "]"
                + "; inventory=[" + inventoryCounts(stacks) + "]";
    }

    private static String hotbarSummary(List<ItemStack> stacks) {
        StringBuilder builder = new StringBuilder();
        for (int slot = 0; slot < NpcInventoryTransfer.HOTBAR_SIZE; slot++) {
            if (slot > 0) {
                builder.append(", ");
            }
            builder.append(slot).append('=');
            if (slot < stacks.size()) {
                builder.append(stackSummary(stacks.get(slot)));
            } else {
                builder.append("empty");
            }
        }
        return builder.toString();
    }

    private static String equipmentSummary(List<ItemStack> stacks) {
        return "feet=" + slotSummary(stacks, NpcInventoryTransfer.ARMOR_FEET_SLOT)
                + ", legs=" + slotSummary(stacks, NpcInventoryTransfer.ARMOR_LEGS_SLOT)
                + ", chest=" + slotSummary(stacks, NpcInventoryTransfer.ARMOR_CHEST_SLOT)
                + ", head=" + slotSummary(stacks, NpcInventoryTransfer.ARMOR_HEAD_SLOT)
                + ", offhand=" + slotSummary(stacks, NpcInventoryTransfer.OFFHAND_SLOT);
    }

    private static String inventoryCounts(List<ItemStack> stacks) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        int end = Math.min(NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT, stacks.size());
        for (int slot = NpcInventoryTransfer.FIRST_NORMAL_SLOT; slot < end; slot++) {
            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return "empty";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(itemId(entry.getKey())).append('x').append(entry.getValue());
            first = false;
        }
        return builder.toString();
    }

    private static String slotSummary(List<ItemStack> stacks, int slot) {
        if (slot < 0 || slot >= stacks.size()) {
            return "empty";
        }
        return stackSummary(stacks.get(slot));
    }

    private static String stackSummary(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        return itemId(stack.getItem()) + "x" + stack.getCount();
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
