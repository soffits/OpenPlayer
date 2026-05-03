package dev.soffits.openplayer.automation.resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public final class RuntimeCraftingRecipeIndex implements CraftingRecipeIndex {
    private final Map<Item, List<RecipePlanEntry>> recipesByOutput;

    private RuntimeCraftingRecipeIndex(Map<Item, List<RecipePlanEntry>> recipesByOutput) {
        this.recipesByOutput = recipesByOutput;
    }

    public static RuntimeCraftingRecipeIndex fromServer(MinecraftServer server) {
        if (server == null) {
            throw new IllegalArgumentException("server cannot be null");
        }
        RecipeManager recipeManager = server.getRecipeManager();
        RegistryAccess registryAccess = server.registryAccess();
        Map<Item, List<RecipePlanEntry>> recipesByOutput = new HashMap<>();
        for (CraftingRecipe recipe : recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = recipe.getResultItem(registryAccess);
            if (result.isEmpty()) {
                continue;
            }
            RecipePlanEntry entry = entryFor(recipe, result);
            if (entry == null) {
                continue;
            }
            recipesByOutput.computeIfAbsent(result.getItem(), ignored -> new ArrayList<>())
                    .add(entry);
        }
        for (List<RecipePlanEntry> recipes : recipesByOutput.values()) {
            recipes.sort(Comparator
                    .comparing((RecipePlanEntry recipe) -> recipe.executable()).reversed()
                    .thenComparingInt((RecipePlanEntry recipe) -> recipe.ingredients().size())
                    .thenComparing(RecipePlanEntry::recipeId)
                    .thenComparing(recipe -> itemId(recipe.result().getItem()))
                    .thenComparingInt(recipe -> recipe.result().getCount()));
        }
        return new RuntimeCraftingRecipeIndex(Map.copyOf(recipesByOutput));
    }

    @Override
    public List<RecipePlanEntry> recipesFor(Item outputItem) {
        List<RecipePlanEntry> recipes = recipesByOutput.get(outputItem);
        return recipes == null ? List.of() : recipes;
    }

    static RecipePlanEntry entryFor(CraftingRecipe recipe, ItemStack result) {
        String recipeId = recipe.getId().toString();
        String kind = recipe.getClass().getSimpleName();
        RecipeDimensions dimensions = dimensionsFor(recipe);
        String unsupportedReason = result.hasTag() ? RecipePlanEntry.UNSUPPORTED_NBT : unsupportedReasonFor(recipe);
        if (unsupportedReason.isEmpty()) {
            unsupportedReason = unsupportedIngredientReason(recipe);
        }
        List<List<Item>> ingredients = ingredientsFor(recipe);
        if (ingredients.isEmpty() && unsupportedReason.isEmpty()) {
            return null;
        }
        boolean requiresCraftingTable = dimensions.width() > 2 || dimensions.height() > 2 || dimensions.maxGridSlots() > 4;
        return new RecipePlanEntry(
                result,
                ingredients,
                recipeId,
                kind,
                dimensions.width(),
                dimensions.height(),
                dimensions.maxGridSlots(),
                requiresCraftingTable,
                unsupportedReason
        );
    }

    private static RecipeDimensions dimensionsFor(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            return new RecipeDimensions(shapedRecipe.getWidth(), shapedRecipe.getHeight(), shapedRecipe.getWidth() * shapedRecipe.getHeight());
        }
        if (recipe instanceof ShapelessRecipe) {
            int ingredientCount = nonEmptyIngredientCount(recipe);
            return new RecipeDimensions(0, 0, ingredientCount);
        }
        int ingredientCount = nonEmptyIngredientCount(recipe);
        return new RecipeDimensions(0, 0, ingredientCount);
    }

    private static String unsupportedReasonFor(CraftingRecipe recipe) {
        if (recipe.isSpecial()) {
            return RecipePlanEntry.UNSUPPORTED_SPECIAL;
        }
        Class<? extends CraftingRecipe> recipeClass = recipe.getClass();
        if (recipeClass != ShapedRecipe.class && recipeClass != ShapelessRecipe.class) {
            return "unsupported custom recipe";
        }
        return "";
    }

    private static String unsupportedIngredientReason(CraftingRecipe recipe) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack[] choices = ingredient.getItems();
            if (choices.length == 0) {
                return RecipePlanEntry.UNSUPPORTED_NBT;
            }
            for (ItemStack stack : choices) {
                if (stack.isEmpty()) {
                    return RecipePlanEntry.UNSUPPORTED_NBT;
                }
                if (stack.hasTag()) {
                    return RecipePlanEntry.UNSUPPORTED_NBT;
                }
                if (stack.getItem().hasCraftingRemainingItem()) {
                    return RecipePlanEntry.UNSUPPORTED_REMAINDER;
                }
            }
        }
        return "";
    }

    private static int nonEmptyIngredientCount(CraftingRecipe recipe) {
        int count = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static List<List<Item>> ingredientsFor(CraftingRecipe recipe) {
        List<List<Item>> ingredients = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            List<Item> choices = new ArrayList<>();
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty()) {
                    choices.add(stack.getItem());
                }
            }
            if (!choices.isEmpty()) {
                ingredients.add(choices);
            }
        }
        return ingredients;
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private record RecipeDimensions(int width, int height, int maxGridSlots) {
    }
}
