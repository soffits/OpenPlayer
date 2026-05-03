package dev.soffits.openplayer.automation.resource;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmeltingRecipe;

public final class RuntimeSmeltingRecipeIndexTest {
    private RuntimeSmeltingRecipeIndexTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        safeRecipeIndexesFiniteInput();
        missingInputRejectsPlan();
        nbtResultRejectsPlan();
        nbtInputRejectsPlan();
        customSmeltingRecipeRejectsPlan();
    }

    private static void safeRecipeIndexesFiniteInput() {
        SmeltingRecipe recipe = recipe("iron", Ingredient.of(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT));
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.RAW_IRON, 2));

        SmeltingPlan plan = RuntimeSmeltingRecipeIndex.planFor(
                recipe, new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, 2, inventory
        );

        require(plan != null, "safe smelting recipe with carried input must plan");
        require(plan.inputItem() == Items.RAW_IRON, "plan must choose raw iron input");
        require(plan.inputCount() == 2, "plan must request one input per output");
        require(plan.outputCount() == 2, "plan must preserve requested output count");
        require(plan.cookingTimeTicks() == 200, "plan must preserve recipe cooking time");
    }

    private static void missingInputRejectsPlan() {
        SmeltingRecipe recipe = recipe("iron", Ingredient.of(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT));
        require(RuntimeSmeltingRecipeIndex.planFor(
                recipe, new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, 1, emptyInventory()
        ) == null, "missing input must reject smelting plan");
    }

    private static void nbtResultRejectsPlan() {
        ItemStack result = new ItemStack(Items.IRON_INGOT);
        result.getOrCreateTag().putString("openplayer", "unsupported");
        SmeltingRecipe recipe = recipe("tagged_result", Ingredient.of(Items.RAW_IRON), result);
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.RAW_IRON));

        require(RuntimeSmeltingRecipeIndex.planFor(
                recipe, new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, 1, inventory
        ) == null, "NBT result must reject smelting plan");
    }

    private static void nbtInputRejectsPlan() {
        ItemStack taggedInput = new ItemStack(Items.RAW_IRON);
        taggedInput.getOrCreateTag().putString("openplayer", "unsupported");
        SmeltingRecipe recipe = recipe("tagged_input", Ingredient.of(taggedInput), new ItemStack(Items.IRON_INGOT));
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.RAW_IRON));

        require(RuntimeSmeltingRecipeIndex.planFor(
                recipe, new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, 1, inventory
        ) == null, "NBT ingredient alternative must reject smelting plan");
    }

    private static void customSmeltingRecipeRejectsPlan() {
        SmeltingRecipe recipe = new CustomSmeltingRecipe(
                new ResourceLocation("openplayer", "custom_iron"),
                Ingredient.of(Items.RAW_IRON),
                new ItemStack(Items.IRON_INGOT)
        );
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.RAW_IRON));

        require(RuntimeSmeltingRecipeIndex.planFor(
                recipe, RegistryAccess.EMPTY, new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, 1, inventory
        ) == null, "custom smelting recipe subclasses must reject smelting plan");
    }

    private static SmeltingRecipe recipe(String name, Ingredient ingredient, ItemStack result) {
        return new SmeltingRecipe(
                new ResourceLocation("openplayer", name),
                "",
                CookingBookCategory.MISC,
                ingredient,
                result,
                0.0F,
                200
        );
    }

    private static final class CustomSmeltingRecipe extends SmeltingRecipe {
        private CustomSmeltingRecipe(ResourceLocation id, Ingredient ingredient, ItemStack result) {
            super(id, "", CookingBookCategory.MISC, ingredient, result, 0.0F, 200);
        }
    }

    private static NonNullList<ItemStack> emptyInventory() {
        return NonNullList.withSize(36, ItemStack.EMPTY);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
