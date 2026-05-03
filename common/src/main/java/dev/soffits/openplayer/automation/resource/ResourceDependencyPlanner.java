package dev.soffits.openplayer.automation.resource;

import dev.soffits.openplayer.entity.NpcInventoryTransfer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ResourceDependencyPlanner {
    private static final int MAX_RECURSION_DEPTH = 16;
    private static final String UNSUPPORTED_NO_RECIPE_PATH = "unsupported/no recipe path";
    private final CraftingRecipeIndex recipeIndex;

    public ResourceDependencyPlanner(CraftingRecipeIndex recipeIndex) {
        if (recipeIndex == null) {
            throw new IllegalArgumentException("recipeIndex cannot be null");
        }
        this.recipeIndex = recipeIndex;
    }

    public ResourcePlanResult plan(GetItemRequest request, List<ItemStack> inventoryStacks) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        List<ItemStack> simulatedInventory = NpcInventoryTransfer.copyStacks(inventoryStacks);
        if (countItem(simulatedInventory, request.item()) >= request.count()) {
            return ResourcePlanResult.alreadyAvailable();
        }
        List<RecipePlanEntry> targetRecipes = recipeIndex.recipesFor(request.item());
        if (targetRecipes.isEmpty()) {
            return ResourcePlanResult.unsupportedTarget(UNSUPPORTED_NO_RECIPE_PATH);
        }
        String targetBlockedReason = firstBlockedReason(targetRecipes);
        if (!hasExecutableRecipe(targetRecipes)) {
            return ResourcePlanResult.unsupportedTarget(targetBlockedReason);
        }

        List<ResourcePlanStep> steps = new ArrayList<>();
        Map<Item, Integer> missingItems = new HashMap<>();
        PlanningFailure failure = new PlanningFailure();
        if (!ensureItem(simulatedInventory, request.item(), request.count(), steps, missingItems, failure, new HashSet<>(), 0)) {
            if (missingItems.isEmpty()) {
                return ResourcePlanResult.unsupportedTarget(failure.reasonOr(targetBlockedReason));
            }
            return ResourcePlanResult.missingMaterials(missingStacks(missingItems));
        }
        if (steps.isEmpty()) {
            return ResourcePlanResult.alreadyAvailable();
        }
        return ResourcePlanResult.craftingSteps(steps);
    }

    private boolean ensureItem(List<ItemStack> inventory, Item item, int count, List<ResourcePlanStep> steps,
                               Map<Item, Integer> missingItems, PlanningFailure failure, Set<Item> visiting, int depth) {
        if (countItem(inventory, item) >= count) {
            return true;
        }
        if (depth >= MAX_RECURSION_DEPTH || !visiting.add(item)) {
            addMissing(missingItems, item, count - countItem(inventory, item));
            return false;
        }

        List<RecipePlanEntry> recipes = recipeIndex.recipesFor(item);
        if (recipes.isEmpty()) {
            addMissing(missingItems, item, count - countItem(inventory, item));
            visiting.remove(item);
            return false;
        }
        for (RecipePlanEntry recipe : recipes) {
            if (!recipe.executable()) {
                failure.add(recipe.blockedReason());
            }
        }

        for (RecipePlanEntry recipe : recipes) {
            if (!recipe.executable()) {
                continue;
            }
            List<ItemStack> snapshot = NpcInventoryTransfer.copyStacks(inventory);
            int stepStart = steps.size();
            Map<Item, Integer> recipeMissing = new HashMap<>();
            int neededCrafts = craftsNeeded(inventory, item, count, recipe.result().getCount());
            Map<Item, Integer> ingredients = selectedIngredients(inventory, recipe, neededCrafts, visiting, depth);
            if (!ingredients.isEmpty() && ensureIngredients(inventory, ingredients, steps, recipeMissing, failure, visiting, depth + 1)) {
                ResourcePlanStep step = stepFor(recipe, ingredients, neededCrafts);
                if (applyStep(inventory, step)) {
                    steps.add(step);
                    visiting.remove(item);
                    return true;
                }
            }
            restore(inventory, snapshot);
            removeStepsAfter(steps, stepStart);
            mergeMissing(missingItems, recipeMissing);
        }

        visiting.remove(item);
        return false;
    }

    private boolean ensureIngredients(List<ItemStack> inventory, Map<Item, Integer> ingredients,
                                      List<ResourcePlanStep> steps, Map<Item, Integer> missingItems, PlanningFailure failure,
                                      Set<Item> visiting, int depth) {
        for (Map.Entry<Item, Integer> ingredient : ingredients.entrySet()) {
            if (!ensureItem(inventory, ingredient.getKey(), ingredient.getValue(), steps, missingItems, failure, visiting, depth)) {
                return false;
            }
        }
        return true;
    }

    private Map<Item, Integer> selectedIngredients(List<ItemStack> inventory, RecipePlanEntry recipe, int crafts,
                                                   Set<Item> visiting, int depth) {
        Map<Item, Integer> selectedCounts = new HashMap<>();
        List<List<Item>> expandedIngredients = new ArrayList<>(recipe.ingredients().size() * crafts);
        for (int craft = 0; craft < crafts; craft++) {
            expandedIngredients.addAll(recipe.ingredients());
        }
        expandedIngredients.sort(Comparator
                .comparingInt((List<Item> choices) -> choices.size())
                .thenComparing(choices -> itemId(choices.get(0))));
        if (!assignIngredients(inventory, expandedIngredients, 0, selectedCounts, visiting, depth)) {
            return Map.of();
        }
        return sortedByItemId(selectedCounts);
    }

    private boolean assignIngredients(List<ItemStack> inventory, List<List<Item>> expandedIngredients, int index,
                                      Map<Item, Integer> selectedCounts, Set<Item> visiting, int depth) {
        if (index >= expandedIngredients.size()) {
            return true;
        }
        for (Item choice : orderedChoices(inventory, expandedIngredients.get(index), selectedCounts, visiting, depth)) {
            selectedCounts.merge(choice, 1, Integer::sum);
            if (assignIngredients(inventory, expandedIngredients, index + 1, selectedCounts, visiting, depth)) {
                return true;
            }
            decrement(selectedCounts, choice);
        }
        return false;
    }

    private List<Item> orderedChoices(List<ItemStack> inventory, List<Item> choices, Map<Item, Integer> selectedCounts,
                                      Set<Item> visiting, int depth) {
        List<Item> ordered = new ArrayList<>();
        for (Item choice : choices) {
            if (countItem(inventory, choice) > selectedCounts.getOrDefault(choice, 0)) {
                ordered.add(choice);
            }
        }
        for (Item choice : choices) {
            if (!ordered.contains(choice) && canCraftChoice(inventory, choice, selectedCounts.getOrDefault(choice, 0) + 1, visiting, depth)) {
                ordered.add(choice);
            }
        }
        for (Item choice : choices) {
            if (!ordered.contains(choice)) {
                ordered.add(choice);
            }
        }
        return ordered;
    }

    private boolean canCraftChoice(List<ItemStack> inventory, Item item, int count, Set<Item> visiting, int depth) {
        if (recipeIndex.recipesFor(item).isEmpty() || visiting.contains(item)) {
            return false;
        }
        List<ItemStack> inventoryCopy = NpcInventoryTransfer.copyStacks(inventory);
        List<ResourcePlanStep> ignoredSteps = new ArrayList<>();
        Map<Item, Integer> ignoredMissing = new HashMap<>();
        return ensureItem(inventoryCopy, item, count, ignoredSteps, ignoredMissing, new PlanningFailure(), new HashSet<>(visiting), depth + 1);
    }

    private static void decrement(Map<Item, Integer> counts, Item item) {
        Integer count = counts.get(item);
        if (count == null || count <= 1) {
            counts.remove(item);
        } else {
            counts.put(item, count - 1);
        }
    }

    private static boolean hasExecutableRecipe(List<RecipePlanEntry> recipes) {
        for (RecipePlanEntry recipe : recipes) {
            if (recipe.executable()) {
                return true;
            }
        }
        return false;
    }

    private static String firstBlockedReason(List<RecipePlanEntry> recipes) {
        for (RecipePlanEntry recipe : recipes) {
            String reason = recipe.blockedReason();
            if (!reason.isEmpty()) {
                return reason;
            }
        }
        return UNSUPPORTED_NO_RECIPE_PATH;
    }

    private static int craftsNeeded(List<ItemStack> inventory, Item item, int count, int outputCount) {
        int missingCount = count - countItem(inventory, item);
        return (missingCount + outputCount - 1) / outputCount;
    }

    private static ResourcePlanStep stepFor(RecipePlanEntry recipe, Map<Item, Integer> ingredients, int crafts) {
        List<ItemStack> ingredientStacks = new ArrayList<>();
        for (Map.Entry<Item, Integer> ingredient : ingredients.entrySet()) {
            ingredientStacks.add(new ItemStack(ingredient.getKey(), ingredient.getValue()));
        }
        ItemStack result = recipe.result().copy();
        result.setCount(recipe.result().getCount() * crafts);
        return new ResourcePlanStep(ingredientStacks, result);
    }

    private static boolean applyStep(List<ItemStack> inventory, ResourcePlanStep step) {
        List<ItemStack> snapshot = NpcInventoryTransfer.copyStacks(inventory);
        for (ItemStack ingredient : step.ingredients()) {
            if (NpcInventoryTransfer.removeExactCount(
                    inventory,
                    ingredient.getItem(),
                    ingredient.getCount(),
                    NpcInventoryTransfer.FIRST_NORMAL_SLOT,
                    normalEnd(inventory)
            ).isEmpty()) {
                restore(inventory, snapshot);
                return false;
            }
        }
        if (!NpcInventoryTransfer.insertAll(inventory, step.result(), NpcInventoryTransfer.FIRST_NORMAL_SLOT, normalEnd(inventory))) {
            restore(inventory, snapshot);
            return false;
        }
        return true;
    }

    private static void restore(List<ItemStack> inventory, List<ItemStack> snapshot) {
        for (int slot = 0; slot < inventory.size() && slot < snapshot.size(); slot++) {
            inventory.set(slot, snapshot.get(slot).copy());
        }
    }

    private static void removeStepsAfter(List<ResourcePlanStep> steps, int stepStart) {
        while (steps.size() > stepStart) {
            steps.remove(steps.size() - 1);
        }
    }

    private static void addMissing(Map<Item, Integer> missingItems, Item item, int count) {
        if (count > 0) {
            missingItems.merge(item, count, Math::max);
        }
    }

    private static void mergeMissing(Map<Item, Integer> target, Map<Item, Integer> source) {
        for (Map.Entry<Item, Integer> entry : source.entrySet()) {
            addMissing(target, entry.getKey(), entry.getValue());
        }
    }

    private static List<ItemStack> missingStacks(Map<Item, Integer> missingItems) {
        List<Map.Entry<Item, Integer>> entries = new ArrayList<>(missingItems.entrySet());
        entries.sort(Comparator.comparing(entry -> itemId(entry.getKey())));
        List<ItemStack> stacks = new ArrayList<>(entries.size());
        for (Map.Entry<Item, Integer> entry : entries) {
            stacks.add(new ItemStack(entry.getKey(), entry.getValue()));
        }
        return stacks;
    }

    private static Map<Item, Integer> sortedByItemId(Map<Item, Integer> counts) {
        List<Map.Entry<Item, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.comparing(entry -> itemId(entry.getKey())));
        Map<Item, Integer> sorted = new java.util.LinkedHashMap<>();
        for (Map.Entry<Item, Integer> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }

    private static int countItem(List<ItemStack> stacks, Item item) {
        return NpcInventoryTransfer.countItem(
                stacks,
                item,
                NpcInventoryTransfer.FIRST_NORMAL_SLOT,
                normalEnd(stacks)
        );
    }

    private static int normalEnd(List<ItemStack> stacks) {
        return Math.min(stacks.size(), NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT);
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static final class PlanningFailure {
        private String reason = "";

        private void add(String candidate) {
            if (reason.isEmpty() && candidate != null && !candidate.isBlank()) {
                reason = candidate.trim();
            }
        }

        private String reasonOr(String fallback) {
            if (!reason.isEmpty()) {
                return reason;
            }
            return fallback == null || fallback.isBlank() ? UNSUPPORTED_NO_RECIPE_PATH : fallback;
        }
    }
}
