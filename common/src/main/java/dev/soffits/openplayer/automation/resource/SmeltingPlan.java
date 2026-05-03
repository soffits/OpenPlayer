package dev.soffits.openplayer.automation.resource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record SmeltingPlan(
        ResourceLocation outputItemId,
        Item outputItem,
        Item inputItem,
        int inputCount,
        int outputCount,
        int cookingTimeTicks,
        String recipeId
) {
    public SmeltingPlan {
        if (outputItemId == null) {
            throw new IllegalArgumentException("outputItemId cannot be null");
        }
        if (outputItem == null || inputItem == null) {
            throw new IllegalArgumentException("items cannot be null");
        }
        if (inputCount < 1 || outputCount < 1 || cookingTimeTicks < 1) {
            throw new IllegalArgumentException("counts and cooking time must be positive");
        }
        if (recipeId == null || recipeId.isBlank()) {
            throw new IllegalArgumentException("recipeId cannot be blank");
        }
    }

    public ItemStack requestedOutputStack() {
        return new ItemStack(outputItem, outputCount);
    }
}
