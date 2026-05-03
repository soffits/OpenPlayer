package dev.soffits.openplayer.automation.resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

public final class RuntimeSmeltingRecipeIndex {
    private final List<SmeltingRecipe> recipes;
    private final RegistryAccess registryAccess;
    private final RecipeManager recipeManager;

    private RuntimeSmeltingRecipeIndex(List<SmeltingRecipe> recipes, RegistryAccess registryAccess, RecipeManager recipeManager) {
        this.recipes = recipes;
        this.registryAccess = registryAccess;
        this.recipeManager = recipeManager;
    }

    public static RuntimeSmeltingRecipeIndex fromServer(MinecraftServer server) {
        if (server == null) {
            throw new IllegalArgumentException("server cannot be null");
        }
        RecipeManager recipeManager = server.getRecipeManager();
        return new RuntimeSmeltingRecipeIndex(
                List.copyOf(recipeManager.getAllRecipesFor(RecipeType.SMELTING)), server.registryAccess(), recipeManager
        );
    }

    public SmeltingPlan planFor(ResourceLocation outputItemId, Item outputItem, int outputCount, List<ItemStack> inventory) {
        if (outputItemId == null || outputItem == null || inventory == null || outputCount < 1) {
            return null;
        }
        List<SmeltingPlan> plans = new ArrayList<>();
        for (SmeltingRecipe recipe : recipes) {
            SmeltingPlan plan = planFor(recipe, registryAccess, outputItemId, outputItem, outputCount, inventory);
            if (plan != null) {
                plans.add(plan);
            }
        }
        return plans.stream()
                .min(Comparator
                        .comparingInt(SmeltingPlan::inputCount)
                        .thenComparing(SmeltingPlan::recipeId)
                        .thenComparing(plan -> itemId(plan.inputItem())))
                .orElse(null);
    }

    public boolean matchesResolvedRecipe(SmeltingPlan plan, Level level) {
        if (plan == null || level == null || recipeManager == null) {
            return false;
        }
        SimpleContainer input = new SimpleContainer(new ItemStack(plan.inputItem()));
        return recipeManager.getRecipeFor(RecipeType.SMELTING, input, level)
                .filter(recipe -> resolvedRecipeMatchesPlan(recipe, registryAccess, plan))
                .isPresent();
    }

    static SmeltingPlan planFor(SmeltingRecipe recipe, ResourceLocation outputItemId, Item outputItem,
                                 int outputCount, List<ItemStack> inventory) {
        return planFor(recipe, RegistryAccess.EMPTY, outputItemId, outputItem, outputCount, inventory);
    }

    static SmeltingPlan planFor(SmeltingRecipe recipe, RegistryAccess registryAccess, ResourceLocation outputItemId,
                                Item outputItem, int outputCount, List<ItemStack> inventory) {
        if (recipe == null || outputItemId == null || outputItem == null || inventory == null || outputCount < 1) {
            return null;
        }
        if (recipe.getClass() != SmeltingRecipe.class) {
            return null;
        }
        ItemStack result = safeResult(recipe, registryAccess);
        if (result.isEmpty()) {
            return null;
        }
        if (!result.is(outputItem) || result.hasTag() || result.getCount() < 1) {
            return null;
        }
        if (outputCount % result.getCount() != 0) {
            return null;
        }
        Item inputItem = firstAvailableSafeInput(recipe.getIngredients(), inventory);
        if (inputItem == null) {
            return null;
        }
        return new SmeltingPlan(
                outputItemId,
                outputItem,
                inputItem,
                outputCount / result.getCount(),
                outputCount,
                recipe.getCookingTime(),
                recipe.getId().toString()
        );
    }

    private static boolean resolvedRecipeMatchesPlan(SmeltingRecipe recipe, RegistryAccess registryAccess, SmeltingPlan plan) {
        if (recipe.getClass() != SmeltingRecipe.class || !recipe.getId().toString().equals(plan.recipeId())) {
            return false;
        }
        ItemStack result = safeResult(recipe, registryAccess);
        return !result.isEmpty()
                && result.is(plan.outputItem())
                && !result.hasTag()
                && result.getCount() >= 1
                && plan.outputCount() == plan.inputCount() * result.getCount();
    }

    private static ItemStack safeResult(SmeltingRecipe recipe, RegistryAccess registryAccess) {
        RegistryAccess access = registryAccess == null ? RegistryAccess.EMPTY : registryAccess;
        return recipe.getResultItem(access);
    }

    private static Item inputItemOrNull(Ingredient ingredient, List<ItemStack> inventory) {
        ItemStack[] choices = ingredient.getItems();
        if (choices.length == 0) {
            return null;
        }
        List<Item> candidates = new ArrayList<>();
        for (ItemStack choice : choices) {
            if (choice.isEmpty() || choice.hasTag()) {
                return null;
            }
            candidates.add(choice.getItem());
        }
        candidates.sort(Comparator.comparing(RuntimeSmeltingRecipeIndex::itemId));
        for (Item candidate : candidates) {
            if (untaggedCount(inventory, candidate) > 0) {
                return candidate;
            }
        }
        return null;
    }

    private static Item firstAvailableSafeInput(List<Ingredient> ingredients, List<ItemStack> inventory) {
        Ingredient selected = null;
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            if (selected != null) {
                return null;
            }
            selected = ingredient;
        }
        return selected == null ? null : inputItemOrNull(selected, inventory);
    }

    private static int untaggedCount(List<ItemStack> inventory, Item item) {
        int count = 0;
        int end = Math.min(31, inventory.size());
        for (int slot = 0; slot < end; slot++) {
            ItemStack stack = inventory.get(slot);
            if (!stack.isEmpty() && stack.is(item) && !stack.hasTag()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
