package dev.soffits.openplayer.automation.resource;

import dev.soffits.openplayer.entity.NpcInventoryTransfer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ResourceDependencyPlannerTest {
    private ResourceDependencyPlannerTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        inventoryOnlySuccessWhenTargetAlreadyPresent();
        plansPlanksStackFromLogsWithoutStepCapFailure();
        plansSticksStackFromLogsThroughDependencyChain();
        ingredientAlternativesPreferAvailableItem();
        finiteExpandedAlternativesPreferAvailableChoice();
        ingredientAlternativesUseLexicographicallySmallestFallback();
        ingredientAlternativeConflictBacktracksToValidAssignment();
        unsupportedTargetRejectsDeterministically();
        knownTableRecipeRejectsWithRequiresTableReason();
        knownRemainderRecipeRejectsWithUnsupportedReason();
        missingMaterialsReportRequiredItemAndCount();
        plannerDoesNotMutateSourceInventory();
        applyingStepsRollsBackOnInsertionFailure();
        applyingStepsRollsBackOnMissingIngredients();
    }

    private static void inventoryOnlySuccessWhenTargetAlreadyPresent() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.STICK, 3));

        ResourcePlanResult result = planner().plan(GetItemRequest.of(Items.STICK, 2), inventory);
        require(result.status() == ResourcePlanResult.Status.ALREADY_AVAILABLE, "existing target must be accepted");
        require(result.steps().isEmpty(), "existing target must not create crafting steps");
    }

    private static void plansPlanksStackFromLogsWithoutStepCapFailure() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.OAK_LOG, 64));

        ResourcePlanResult result = planner().plan(GetItemRequest.of(Items.OAK_PLANKS, 64), inventory);
        require(result.status() == ResourcePlanResult.Status.CRAFTING_STEPS, "oak planks x64 must craft from 16 logs");
        require(result.steps().size() == 1, "batched oak planks must require one step");
        ResourcePlanStep step = result.steps().get(0);
        require(step.ingredients().size() == 1, "plank step must consume one ingredient type");
        require(step.ingredients().get(0).is(Items.OAK_LOG), "plank step must consume oak logs");
        require(step.ingredients().get(0).getCount() == 16, "plank step must consume 16 oak logs");
        require(step.result().is(Items.OAK_PLANKS), "plank step must produce oak planks");
        require(step.result().getCount() == 64, "plank step must produce one planks stack");
    }

    private static void plansSticksStackFromLogsThroughDependencyChain() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.OAK_LOG, 8));

        ResourcePlanResult result = planner().plan(GetItemRequest.of(Items.STICK, 64), inventory);
        require(result.status() == ResourcePlanResult.Status.CRAFTING_STEPS, "sticks x64 must craft through planks from logs");
        require(result.steps().size() == 2, "sticks from logs must batch into two dependency steps");
        require(result.steps().get(0).result().is(Items.OAK_PLANKS), "first step must make oak planks");
        require(result.steps().get(0).result().getCount() == 32, "first step must make enough planks");
        require(result.steps().get(1).result().is(Items.STICK), "second step must make sticks");
        require(result.steps().get(1).result().getCount() == 64, "second step must make one stick stack");
    }

    private static void ingredientAlternativesPreferAvailableItem() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.BIRCH_PLANKS, 2));

        ResourcePlanResult result = planner().plan(GetItemRequest.of(Items.STICK, 4), inventory);
        require(result.status() == ResourcePlanResult.Status.CRAFTING_STEPS, "sticks must craft from available planks");
        require(result.steps().size() == 1, "available planks should need one stick step");
        require(result.steps().get(0).ingredients().get(0).is(Items.BIRCH_PLANKS), "available birch planks must be preferred");
    }

    private static void finiteExpandedAlternativesPreferAvailableChoice() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.BIRCH_PLANKS, 1));
        Map<Item, List<RecipePlanEntry>> recipes = new HashMap<>();
        recipes.put(Items.CHEST, List.of(recipe(Items.CHEST, 1,
                List.of(List.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS, Items.ACACIA_PLANKS)))));

        ResourcePlanResult result = new ResourceDependencyPlanner(output -> recipes.getOrDefault(output, List.of()))
                .plan(GetItemRequest.of(Items.CHEST, 1), inventory);

        require(result.status() == ResourcePlanResult.Status.CRAFTING_STEPS,
                "finite expanded alternatives must plan from an available choice");
        require(result.steps().size() == 1, "finite expanded alternative recipe must need one step");
        require(result.steps().get(0).ingredients().get(0).is(Items.BIRCH_PLANKS),
                "finite expanded alternatives must use available birch planks");
    }

    private static void ingredientAlternativesUseLexicographicallySmallestFallback() {
        NonNullList<ItemStack> inventory = emptyInventory();

        ResourcePlanResult result = planner().plan(GetItemRequest.of(Items.STICK, 4), inventory);
        require(result.status() == ResourcePlanResult.Status.MISSING_MATERIALS, "missing fallback planks must report materials");
        require(result.missingItems().size() == 1, "missing fallback must be deterministic");
        require(result.missingItems().get(0).is(Items.ACACIA_PLANKS), "lexicographically smallest plank id must be selected");
        require(result.missingItems().get(0).getCount() == 2, "stick recipe must require two fallback planks");
    }

    private static void unsupportedTargetRejectsDeterministically() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.OAK_LOG, 64));

        ResourcePlanResult result = planner().plan(GetItemRequest.of(Items.DIAMOND_PICKAXE, 1), inventory);
        require(result.status() == ResourcePlanResult.Status.UNSUPPORTED_TARGET,
                "unknown target with no recipe path must reject deterministically");
        require("unsupported/no recipe path".equals(result.reason()), "unknown target must report no recipe path");
        require(result.steps().isEmpty(), "unsupported target must not create steps");
    }

    private static void ingredientAlternativeConflictBacktracksToValidAssignment() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.OAK_LOG, 1));
        inventory.set(1, new ItemStack(Items.BIRCH_LOG, 1));
        Map<Item, List<RecipePlanEntry>> recipes = new HashMap<>();
        recipes.put(Items.CHEST, List.of(recipe(Items.CHEST, 1,
                List.of(List.of(Items.OAK_LOG, Items.BIRCH_LOG), List.of(Items.OAK_LOG)))));

        ResourcePlanResult result = new ResourceDependencyPlanner(output -> recipes.getOrDefault(output, List.of()))
                .plan(GetItemRequest.of(Items.CHEST, 1), inventory);

        require(result.status() == ResourcePlanResult.Status.CRAFTING_STEPS,
                "alternative assignment must backtrack around fixed ingredient conflicts");
        require(result.steps().size() == 1, "conflict recipe must need one step");
        require(containsIngredient(result.steps().get(0), Items.OAK_LOG, 1), "fixed oak log must be preserved");
        require(containsIngredient(result.steps().get(0), Items.BIRCH_LOG, 1), "flexible slot must use birch log");
    }

    private static void knownTableRecipeRejectsWithRequiresTableReason() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.COBBLESTONE, 64));
        Map<Item, List<RecipePlanEntry>> recipes = new HashMap<>();
        recipes.put(Items.FURNACE, List.of(recipe(Items.FURNACE, 1,
                List.of(
                        List.of(Items.COBBLESTONE), List.of(Items.COBBLESTONE), List.of(Items.COBBLESTONE),
                        List.of(Items.COBBLESTONE), List.of(Items.COBBLESTONE), List.of(Items.COBBLESTONE),
                        List.of(Items.COBBLESTONE), List.of(Items.COBBLESTONE)
                ), 3, 3, true, "")));

        ResourcePlanResult result = new ResourceDependencyPlanner(output -> recipes.getOrDefault(output, List.of()))
                .plan(GetItemRequest.of(Items.FURNACE, 1), inventory);

        require(result.status() == ResourcePlanResult.Status.UNSUPPORTED_TARGET,
                "table recipe must not be planned in inventory-only phase");
        require(RecipePlanEntry.REQUIRES_CRAFTING_TABLE.equals(result.reason()),
                "table recipe must report requires crafting table");
        require(result.steps().isEmpty(), "table recipe must not produce synthetic steps");
    }

    private static void knownRemainderRecipeRejectsWithUnsupportedReason() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.MILK_BUCKET, 1));
        Map<Item, List<RecipePlanEntry>> recipes = new HashMap<>();
        recipes.put(Items.CAKE, List.of(recipe(Items.CAKE, 1,
                List.of(List.of(Items.MILK_BUCKET)), 1, 1, false, RecipePlanEntry.UNSUPPORTED_REMAINDER)));

        ResourcePlanResult result = new ResourceDependencyPlanner(output -> recipes.getOrDefault(output, List.of()))
                .plan(GetItemRequest.of(Items.CAKE, 1), inventory);

        require(result.status() == ResourcePlanResult.Status.UNSUPPORTED_TARGET,
                "remainder recipe must not be planned until remainders are modeled");
        require(RecipePlanEntry.UNSUPPORTED_REMAINDER.equals(result.reason()),
                "remainder recipe must report deterministic unsupported reason");
        require(result.steps().isEmpty(), "remainder recipe must not produce synthetic steps");
    }

    private static void missingMaterialsReportRequiredItemAndCount() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.OAK_LOG, 1));

        ResourcePlanResult result = planner().plan(GetItemRequest.of(Items.OAK_PLANKS, 64), inventory);
        require(result.status() == ResourcePlanResult.Status.MISSING_MATERIALS,
                "known recipe without enough ingredients must report missing materials");
        require(result.missingItems().size() == 1, "missing materials must be concise");
        require(result.missingItems().get(0).is(Items.OAK_LOG), "missing material must identify oak logs");
        require(result.missingItems().get(0).getCount() == 15, "missing material count must be exact");
    }

    private static void plannerDoesNotMutateSourceInventory() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.OAK_LOG, 8));
        List<ItemStack> snapshot = NpcInventoryTransfer.copyStacks(inventory);

        planner().plan(GetItemRequest.of(Items.STICK, 64), inventory);
        require(stacksEqual(inventory, snapshot), "planner must not mutate source inventory");
    }

    private static void applyingStepsRollsBackOnInsertionFailure() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.OAK_LOG, 64));
        for (int slot = 1; slot < NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT; slot++) {
            inventory.set(slot, new ItemStack(Items.STONE, 64));
        }
        List<ItemStack> snapshot = NpcInventoryTransfer.copyStacks(inventory);

        ResourcePlanStep step = new ResourcePlanStep(List.of(new ItemStack(Items.OAK_LOG, 16)),
                new ItemStack(Items.OAK_PLANKS, 64));
        require(!NpcInventoryTransfer.applyCraftingSteps(inventory, List.of(step)), "full output inventory must reject");
        require(stacksEqual(inventory, snapshot), "output insertion failure must rollback everything");
    }

    private static void applyingStepsRollsBackOnMissingIngredients() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.OAK_LOG, 1));
        List<ItemStack> snapshot = NpcInventoryTransfer.copyStacks(inventory);

        ResourcePlanStep step = new ResourcePlanStep(List.of(new ItemStack(Items.OAK_LOG, 16)),
                new ItemStack(Items.OAK_PLANKS, 64));
        require(!NpcInventoryTransfer.applyCraftingSteps(inventory, List.of(step)), "missing ingredients must reject");
        require(stacksEqual(inventory, snapshot), "missing ingredients must rollback everything");
    }

    private static ResourceDependencyPlanner planner() {
        return new ResourceDependencyPlanner(fakeRecipeIndex());
    }

    private static CraftingRecipeIndex fakeRecipeIndex() {
        Map<Item, List<RecipePlanEntry>> recipes = new HashMap<>();
        recipes.put(Items.OAK_PLANKS, List.of(recipe(Items.OAK_PLANKS, 4, List.of(List.of(Items.OAK_LOG)))));
        recipes.put(Items.STICK, List.of(recipe(Items.STICK, 4, List.of(plankChoices(), plankChoices()))));
        recipes.put(Items.CRAFTING_TABLE, List.of(recipe(Items.CRAFTING_TABLE, 1,
                List.of(plankChoices(), plankChoices(), plankChoices(), plankChoices()))));
        return outputItem -> recipes.getOrDefault(outputItem, List.of());
    }

    private static RecipePlanEntry recipe(Item outputItem, int outputCount, List<List<Item>> ingredients) {
        return recipe(outputItem, outputCount, ingredients, 0, 0, false, "");
    }

    private static RecipePlanEntry recipe(Item outputItem, int outputCount, List<List<Item>> ingredients,
                                          int gridWidth, int gridHeight, boolean requiresCraftingTable,
                                          String unsupportedReason) {
        return new RecipePlanEntry(
                new ItemStack(outputItem, outputCount),
                ingredients,
                "openplayer:test/" + BuiltInRegistries.ITEM.getKey(outputItem),
                "test",
                gridWidth,
                gridHeight,
                Math.max(ingredients.size(), gridWidth * gridHeight),
                requiresCraftingTable,
                unsupportedReason
        );
    }

    private static List<Item> plankChoices() {
        return List.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS, Items.ACACIA_PLANKS);
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

    private static boolean containsIngredient(ResourcePlanStep step, Item item, int count) {
        for (ItemStack stack : step.ingredients()) {
            if (stack.is(item) && stack.getCount() == count) {
                return true;
            }
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
