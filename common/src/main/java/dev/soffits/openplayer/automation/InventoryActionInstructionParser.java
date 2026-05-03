package dev.soffits.openplayer.automation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class InventoryActionInstructionParser {
    private InventoryActionInstructionParser() {
    }

    public static ParsedItemInstruction parseItemOnlyOrNull(String instruction) {
        ParsedItemInstruction parsed = parseItemCountOrNull(instruction, false);
        if (parsed == null || parsed.count() != 1 || parsed.ownerTarget()) {
            return null;
        }
        return parsed;
    }

    public static ParsedItemInstruction parseItemCountOrNull(String instruction, boolean allowOwnerTarget) {
        if (instruction == null) {
            return null;
        }
        String trimmed = instruction.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 1 || tokens.length > (allowOwnerTarget ? 3 : 2)) {
            return null;
        }
        boolean ownerTarget = false;
        if (tokens.length == 3) {
            if (!"owner".equals(tokens[2])) {
                return null;
            }
            ownerTarget = true;
        } else if (tokens.length == 2 && allowOwnerTarget && "owner".equals(tokens[1])) {
            ownerTarget = true;
        }

        Item item = itemByExactIdOrNull(tokens[0]);
        if (item == null) {
            return null;
        }
        int count = 1;
        if (tokens.length >= 2 && !(ownerTarget && "owner".equals(tokens[1]))) {
            count = parsePositiveCountOrNegative(tokens[1]);
            if (count < 1) {
                return null;
            }
        }
        if (count > item.getDefaultInstance().getMaxStackSize()) {
            return null;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        return new ParsedItemInstruction(itemId, item, count, ownerTarget);
    }

    private static Item itemByExactIdOrNull(String value) {
        ResourceLocation itemId = ResourceLocation.tryParse(value);
        if (itemId == null || !value.equals(itemId.toString())) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null || item == Items.AIR) {
            return null;
        }
        return item;
    }

    private static int parsePositiveCountOrNegative(String value) {
        if (value.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return -1;
            }
        }
        try {
            int count = Integer.parseInt(value);
            return count > 0 ? count : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    public record ParsedItemInstruction(ResourceLocation itemId, Item item, int count, boolean ownerTarget) {
        public ParsedItemInstruction {
            if (itemId == null) {
                throw new IllegalArgumentException("itemId cannot be null");
            }
            if (item == null || item == Items.AIR) {
                throw new IllegalArgumentException("item must be a registered non-air item");
            }
            if (count < 1 || count > item.getDefaultInstance().getMaxStackSize()) {
                throw new IllegalArgumentException("count must fit in one item stack");
            }
        }
    }
}
