package dev.soffits.openplayer.automation.resource;

import java.util.List;
import net.minecraft.world.item.Item;

public interface CraftingRecipeIndex {
    List<RecipePlanEntry> recipesFor(Item outputItem);
}
