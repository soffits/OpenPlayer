package dev.soffits.openplayer.automation.resource;

public record ResourcePlanningCapabilities(boolean allowCraftingTableRecipes) {
    public static final ResourcePlanningCapabilities INVENTORY_ONLY = new ResourcePlanningCapabilities(false);
    public static final ResourcePlanningCapabilities NEARBY_CRAFTING_TABLE = new ResourcePlanningCapabilities(true);
}
