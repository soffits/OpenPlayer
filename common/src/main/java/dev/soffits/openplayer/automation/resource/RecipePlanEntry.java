package dev.soffits.openplayer.automation.resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record RecipePlanEntry(
        ItemStack result,
        List<List<Item>> ingredients,
        String recipeId,
        String recipeKind,
        int gridWidth,
        int gridHeight,
        int maxGridSlots,
        boolean requiresCraftingTable,
        String unsupportedReason
) {
    public static final String REQUIRES_CRAFTING_TABLE = "requires crafting table";
    public static final String UNSUPPORTED_SPECIAL = "unsupported special recipe";
    public static final String UNSUPPORTED_NBT = "unsupported NBT recipe";
    public static final String UNSUPPORTED_REMAINDER = "unsupported crafting remainder recipe";

    public RecipePlanEntry(ItemStack result, List<List<Item>> ingredients) {
        this(result, ingredients, "openplayer:fake", "test", 0, 0, ingredients == null ? 0 : ingredients.size(), false, "");
    }

    public RecipePlanEntry {
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("result cannot be empty");
        }
        result = result.copy();
        if (ingredients == null) {
            throw new IllegalArgumentException("ingredients cannot be null");
        }
        List<List<Item>> ingredientCopies = new ArrayList<>(ingredients.size());
        for (List<Item> choices : ingredients) {
            if (choices == null || choices.isEmpty()) {
                throw new IllegalArgumentException("ingredient choices cannot be empty");
            }
            Set<Item> uniqueChoices = new LinkedHashSet<>();
            for (Item choice : choices) {
                if (choice == null || choice == Items.AIR) {
                    throw new IllegalArgumentException("ingredient choices cannot contain air");
                }
                uniqueChoices.add(choice);
            }
            List<Item> sortedChoices = new ArrayList<>(uniqueChoices);
            sortedChoices.sort(Comparator.comparing(RecipePlanEntry::itemId));
            ingredientCopies.add(List.copyOf(sortedChoices));
        }
        ingredients = List.copyOf(ingredientCopies);
        recipeId = normalize(recipeId, "unknown");
        recipeKind = normalize(recipeKind, "unknown");
        if (gridWidth < 0 || gridHeight < 0 || maxGridSlots < 0) {
            throw new IllegalArgumentException("recipe dimensions cannot be negative");
        }
        unsupportedReason = unsupportedReason == null ? "" : unsupportedReason.trim();
    }

    public boolean executable() {
        return executable(ResourcePlanningCapabilities.INVENTORY_ONLY);
    }

    public boolean executable(ResourcePlanningCapabilities capabilities) {
        if (!unsupportedReason.isEmpty()) {
            return false;
        }
        return !requiresCraftingTable || capabilities.allowCraftingTableRecipes();
    }

    public String blockedReason() {
        if (!unsupportedReason.isEmpty()) {
            return unsupportedReason;
        }
        if (requiresCraftingTable) {
            return REQUIRES_CRAFTING_TABLE;
        }
        return "";
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
