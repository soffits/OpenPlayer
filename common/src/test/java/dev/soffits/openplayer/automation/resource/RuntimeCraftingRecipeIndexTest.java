package dev.soffits.openplayer.automation.resource;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public final class RuntimeCraftingRecipeIndexTest {
    private RuntimeCraftingRecipeIndexTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        shapelessFiniteAlternativesIndexAsExecutableChoices();
        nbtResultIndexesAsUnsupported();
        customShapelessSubclassIndexesAsUnsupported();
    }

    private static void shapelessFiniteAlternativesIndexAsExecutableChoices() {
        ShapelessRecipe recipe = new ShapelessRecipe(
                new ResourceLocation("openplayer", "finite_plank_choices"),
                "",
                CraftingBookCategory.MISC,
                new ItemStack(Items.STICK, 4),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS))
        );

        RecipePlanEntry entry = RuntimeCraftingRecipeIndex.entryFor(recipe, new ItemStack(Items.STICK, 4));

        require(entry != null, "finite alternatives must index a recipe entry");
        require(entry.executable(), "finite alternatives without NBT or remainders must be executable");
        require("openplayer:finite_plank_choices".equals(entry.recipeId()), "recipe id must come from the recipe id");
        require("ShapelessRecipe".equals(entry.recipeKind()), "recipe kind must keep the runtime recipe class");
        require(entry.ingredients().size() == 1, "single ingredient must be indexed");
        List<Item> choices = entry.ingredients().get(0);
        require(choices.size() == 2, "finite alternatives must preserve both choices");
        require(choices.contains(Items.OAK_PLANKS), "oak plank choice must be indexed");
        require(choices.contains(Items.BIRCH_PLANKS), "birch plank choice must be indexed");
    }

    private static void nbtResultIndexesAsUnsupported() {
        ShapelessRecipe recipe = new ShapelessRecipe(
                new ResourceLocation("openplayer", "nbt_result"),
                "",
                CraftingBookCategory.MISC,
                new ItemStack(Items.STICK, 4),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_PLANKS))
        );
        ItemStack result = new ItemStack(Items.STICK, 4);
        result.getOrCreateTag().putString("openplayer", "unsupported");

        RecipePlanEntry entry = RuntimeCraftingRecipeIndex.entryFor(recipe, result);

        require(entry != null, "NBT result must index an unsupported recipe entry");
        require(!entry.executable(), "NBT result must not be executable");
        require(RecipePlanEntry.UNSUPPORTED_NBT.equals(entry.blockedReason()), "NBT result must use unsupported NBT reason");
    }

    private static void customShapelessSubclassIndexesAsUnsupported() {
        ShapelessRecipe recipe = new CustomShapelessRecipe(
                new ResourceLocation("openplayer", "custom_shapeless"),
                "",
                CraftingBookCategory.MISC,
                new ItemStack(Items.STICK, 4),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_PLANKS))
        );

        RecipePlanEntry entry = RuntimeCraftingRecipeIndex.entryFor(recipe, new ItemStack(Items.STICK, 4));

        require(entry != null, "custom shapeless subclass must index an unsupported recipe entry");
        require(!entry.executable(), "custom shapeless subclass must not be executable");
        require("unsupported custom recipe".equals(entry.blockedReason()), "custom shapeless subclass must use custom reason");
    }

    private static final class CustomShapelessRecipe extends ShapelessRecipe {
        private CustomShapelessRecipe(ResourceLocation id, String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
            super(id, group, category, result, ingredients);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
