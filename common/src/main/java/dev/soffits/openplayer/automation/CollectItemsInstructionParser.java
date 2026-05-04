package dev.soffits.openplayer.automation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class CollectItemsInstructionParser {
    public static final String USAGE = "COLLECT_ITEMS requires blank or instruction: <item_id> [radius]";
    public static final double DEFAULT_RADIUS = 16.0D;
    public static final double MAX_RADIUS = 16.0D;

    private CollectItemsInstructionParser() {
    }

    public static CollectItemsInstruction parseOrNull(String instruction) {
        if (AutomationInstructionParser.isBlankInstruction(instruction)) {
            return new CollectItemsInstruction(null, null, DEFAULT_RADIUS);
        }
        String trimmedInstruction = instruction.trim();
        String[] parts = trimmedInstruction.split("\\s+");
        if (parts.length < 1 || parts.length > 2) {
            return null;
        }
        Item item = itemByExactIdOrNull(parts[0]);
        if (item == null) {
            return null;
        }
        double radius = DEFAULT_RADIUS;
        if (parts.length == 2) {
            radius = AutomationInstructionParser.parseOptionalRadiusOrNegative(parts[1], DEFAULT_RADIUS, MAX_RADIUS);
            if (radius < 0.0D) {
                return null;
            }
        }
        return new CollectItemsInstruction(BuiltInRegistries.ITEM.getKey(item), item, radius);
    }

    public static String canonicalInstruction(String matching, String maxDistance) {
        if ((matching == null || matching.isBlank()) && (maxDistance == null || maxDistance.isBlank())) {
            return "";
        }
        if (matching == null || matching.isBlank()) {
            return maxDistance.trim();
        }
        String itemId = namespacedMinecraftId(matching);
        String radius = maxDistance == null ? "" : maxDistance.trim();
        if (radius.isEmpty()) {
            return itemId;
        }
        double parsedRadius = AutomationInstructionParser.parseOptionalRadiusOrNegative(radius, DEFAULT_RADIUS, MAX_RADIUS);
        if (parsedRadius < 0.0D) {
            return itemId + " " + radius;
        }
        return itemId + " " + formatRadius(parsedRadius);
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

    private static String namespacedMinecraftId(String value) {
        String trimmedValue = value.trim();
        return trimmedValue.indexOf(':') >= 0 ? trimmedValue : "minecraft:" + trimmedValue;
    }

    private static String formatRadius(double radius) {
        if (radius == Math.rint(radius)) {
            return Integer.toString((int) radius);
        }
        return Double.toString(radius);
    }

    public record CollectItemsInstruction(ResourceLocation itemId, Item item, double radius) {
        public CollectItemsInstruction {
            if ((itemId == null) != (item == null)) {
                throw new IllegalArgumentException("itemId and item must both be present or absent");
            }
            if (item == Items.AIR) {
                throw new IllegalArgumentException("item must be a registered non-air item");
            }
            if (!Double.isFinite(radius) || radius <= 0.0D || radius > MAX_RADIUS) {
                throw new IllegalArgumentException("radius must be finite and within collect bounds");
            }
        }
    }
}
