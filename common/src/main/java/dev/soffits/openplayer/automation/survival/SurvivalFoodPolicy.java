package dev.soffits.openplayer.automation.survival;

import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.food.FoodProperties;

public final class SurvivalFoodPolicy {
    private SurvivalFoodPolicy() {
    }

    public static boolean isSafeEdibleDrop(ItemStack itemStack) {
        return OpenPlayerNpcEntity.canUseSelectedMainHandItemLocally(itemStack);
    }

    public static int bestSafeFoodSlot(List<ItemStack> inventory, int startSlotInclusive, int endSlotExclusive) {
        if (inventory == null) {
            return -1;
        }
        int bestSlot = -1;
        int bestNutrition = -1;
        int end = Math.min(endSlotExclusive, inventory.size());
        for (int slot = Math.max(0, startSlotInclusive); slot < end; slot++) {
            ItemStack candidate = inventory.get(slot);
            if (!isSafeEdibleDrop(candidate)) {
                continue;
            }
            FoodProperties foodProperties = candidate.getItem().getFoodProperties();
            int nutrition = foodProperties == null ? 0 : foodProperties.getNutrition();
            if (nutrition > bestNutrition) {
                bestNutrition = nutrition;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }
}
