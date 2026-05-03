package dev.soffits.openplayer.automation.resource;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public record ResourcePlanStep(List<ItemStack> ingredients, ItemStack result) {
    public ResourcePlanStep {
        if (ingredients == null || ingredients.isEmpty()) {
            throw new IllegalArgumentException("ingredients cannot be empty");
        }
        List<ItemStack> ingredientCopies = new ArrayList<>(ingredients.size());
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                throw new IllegalArgumentException("ingredients cannot contain empty stacks");
            }
            ingredientCopies.add(ingredient.copy());
        }
        ingredients = List.copyOf(ingredientCopies);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("result cannot be empty");
        }
        result = result.copy();
    }
}
