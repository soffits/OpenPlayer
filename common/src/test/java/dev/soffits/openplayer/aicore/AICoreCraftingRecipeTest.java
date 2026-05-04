package dev.soffits.openplayer.aicore;

import java.util.Map;

public final class AICoreCraftingRecipeTest {
    private AICoreCraftingRecipeTest() {
    }

    public static void main(String[] args) {
        for (String tool : new String[] {"recipes_for", "recipes_all", "craft"}) {
            AICoreTestSupport.requireStatus(tool, CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER);
        }
        ToolResult result = MinecraftPrimitiveTools.validate(ToolCall.of("craft", new ToolArguments(Map.of("recipe", "minecraft:planks", "count", "1"))), new ToolValidationContext(true));
        AICoreTestSupport.requireFailed(result, "unsupported_missing_recipe_adapter");
        AICoreTestSupport.require(!AICoreToolCatalog.registry().contains(ToolName.of("get_item")), "crafting must not expose resource acquisition macro");
    }
}
