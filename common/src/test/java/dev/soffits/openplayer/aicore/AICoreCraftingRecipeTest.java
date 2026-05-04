package dev.soffits.openplayer.aicore;

import java.util.Map;

public final class AICoreCraftingRecipeTest {
    private AICoreCraftingRecipeTest() {
    }

    public static void main(String[] args) {
        AICoreTestSupport.requireStatus("recipes_for", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("recipes_all", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("craft", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        ToolResult recipeQuery = MinecraftPrimitiveTools.validate(ToolCall.of("recipes_for", new ToolArguments(Map.of("itemType", "minecraft:oak_planks"))), new ToolValidationContext(true));
        AICoreTestSupport.require(recipeQuery.status() == ToolResultStatus.SUCCESS, "recipe queries must validate as server RecipeManager adapters");
        ToolResult result = MinecraftPrimitiveTools.validate(ToolCall.of("craft", new ToolArguments(Map.of("recipe", "minecraft:planks", "count", "1"))), new ToolValidationContext(true));
        AICoreTestSupport.require(result.status() == ToolResultStatus.SUCCESS, "craft must validate as an executable no-loss adapter");
        AICoreTestSupport.require(MinecraftPrimitiveTools.providerExecutableToolNames().contains("craft"), "provider executable tools must expose craft");
        AICoreTestSupport.require(MinecraftPrimitiveTools.providerExecutableToolSchemaText().contains("craft: Craft a known crafting recipe"), "provider executable schemas must expose craft");
        AICoreTestSupport.require(!AICoreToolCatalog.registry().contains(ToolName.of("get_item")), "crafting must not expose resource acquisition macro");
    }
}
