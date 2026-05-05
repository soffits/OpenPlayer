package dev.soffits.openplayer.aicore;

import dev.soffits.openplayer.automation.CraftInstructionParser;
import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentPriority;
import dev.soffits.openplayer.runtime.perception.ServerWorldPerceptionScanner;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionClassifier;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionFormatter;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.ObjectCluster;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.SafeStandSpot;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;


abstract class AICoreNpcCraftingTools extends AICoreNpcPerceptionTools {
    protected AICoreNpcCraftingTools(OpenPlayerNpcEntity entity, AICoreEventBus eventBus, Function<CommandIntent, CommandSubmissionResult> commandSubmitter) {
        super(entity, eventBus, commandSubmitter);
    }

protected ToolResult canDigBlock(ToolCall call) {
    DigPreconditions preconditions = digPreconditions(call);
    if (preconditions.failureReason() != null) {
        return ToolResult.success("can_dig_block=false", Map.of("canDig", "false", "reason", preconditions.failureReason()));
    }
    return ToolResult.success("can_dig_block=true", Map.of(
            "canDig", "true",
            "resourceId", BuiltInRegistries.BLOCK.getKey(preconditions.state().getBlock()).toString(),
            "withinReach", Boolean.toString(withinInteractionDistance(preconditions.pos()))
    ));
}

protected ToolResult digTime(ToolCall call) {
    DigPreconditions preconditions = digPreconditions(call);
    if (preconditions.failureReason() != null) {
        return ToolResult.failed(preconditions.failureReason());
    }
    ItemStack tool = entity.getMainHandItem();
    float destroySpeed = Math.max(1.0F, tool.getDestroySpeed(preconditions.state()));
    float hardness = preconditions.state().getDestroySpeed(preconditions.level(), preconditions.pos());
    int ticks = Math.max(1, (int) Math.ceil(hardness * 30.0F / destroySpeed));
    return ToolResult.success("dig_time=" + ticks, Map.of(
            "ticks", Integer.toString(ticks),
            "hardness", Float.toString(hardness),
            "toolDestroySpeed", Float.toString(destroySpeed),
            "correctToolForDrops", Boolean.toString(tool.isCorrectToolForDrops(preconditions.state()))
    ));
}

protected ToolResult unequip(ToolCall call) {
    EquipmentSlot slot = equipmentSlot(call.arguments().values().get("destination"));
    if (slot == null) {
        return ToolResult.rejected("unsupported_equipment_destination");
    }
    return entity.unequipToNormalInventory(slot)
            ? ToolResult.success("unequip accepted")
            : ToolResult.rejected("unequip requires equipped item and normal inventory space");
}

protected ToolResult recipes(ToolCall call) {
    ServerLevel level = serverLevel();
    if (level == null || level.getServer() == null) {
        return ToolResult.failed("server_recipe_manager_unavailable");
    }
    ResourceLocation itemId = ResourceLocation.tryParse(call.arguments().values().get("itemType"));
    if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
        return ToolResult.rejected("unknown_item_type");
    }
    StringBuilder recipeIds = new StringBuilder();
    int count = 0;
    for (Recipe<?> recipe : level.getServer().getRecipeManager().getRecipes()) {
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (!result.isEmpty() && BuiltInRegistries.ITEM.getKey(result.getItem()).equals(itemId)) {
            if (recipeIds.length() > 0) {
                recipeIds.append(',');
            }
            recipeIds.append(recipe.getId());
            count++;
            if (call.name().value().equals("recipes_for") && count >= Integer.parseInt(call.arguments().values().getOrDefault("minResultCount", "1"))) {
                break;
            }
        }
    }
    return ToolResult.success("recipes=" + count, Map.of("count", Integer.toString(count), "recipeIds", recipeIds.toString(), "itemType", itemId.toString()));
}

protected ToolResult craft(ToolCall call) {
    ServerLevel level = serverLevel();
    if (level == null || level.getServer() == null) {
        return ToolResult.failed("server_recipe_manager_unavailable");
    }
    CraftRequest request = craftRequest(call);
    if (request == null) {
        return ToolResult.rejected("craft_requires_recipe_id_and_count");
    }
    Recipe<?> recipe = level.getServer().getRecipeManager().byKey(request.recipeId()).orElse(null);
    if (!(recipe instanceof CraftingRecipe craftingRecipe) || recipe.getType() != RecipeType.CRAFTING) {
        return ToolResult.failed("recipe_unknown_or_not_crafting");
    }
    if (!(craftingRecipe instanceof ShapedRecipe) && !(craftingRecipe instanceof ShapelessRecipe)) {
        return ToolResult.failed("recipe_unsupported");
    }
    boolean needsCraftingTable = !craftingRecipe.canCraftInDimensions(2, 2);
    if (needsCraftingTable && request.craftingTablePos() == null) {
        return ToolResult.failed("crafting_table_required");
    }
    if (request.craftingTablePos() != null && !validCraftingTable(level, request.craftingTablePos())) {
        return ToolResult.failed("crafting_table_absent_unloaded_or_unreachable");
    }
    ItemStack result = craftingRecipe.getResultItem(level.registryAccess()).copy();
    if (result.isEmpty()) {
        return ToolResult.failed("recipe_result_empty");
    }
    if (!entity.craftInventoryRecipeNoLoss(craftingRecipe.getIngredients(), result, request.count())) {
        return ToolResult.rejected("craft_inputs_missing_or_output_would_not_fit");
    }
    int craftedCount = result.getCount() * request.count();
    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
    return ToolResult.success("craft accepted", Map.of(
            "recipe", request.recipeId().toString(),
            "itemType", itemId.toString(),
            "count", Integer.toString(craftedCount)
    ));
}

protected DigPreconditions digPreconditions(ToolCall call) {
    ServerLevel level = serverLevel();
    if (level == null) {
        return DigPreconditions.failed("server_level_unavailable");
    }
    BlockPos pos = blockPos(call.arguments().values());
    if (!level.hasChunkAt(pos)) {
        return DigPreconditions.failed("target_chunk_unloaded");
    }
    BlockState state = level.getBlockState(pos);
    if (state.isAir()) {
        return DigPreconditions.failed("block_is_air");
    }
    if (state.getDestroySpeed(level, pos) < 0.0F) {
        return DigPreconditions.failed("block_not_breakable");
    }
    if (!withinInteractionDistance(pos)) {
        return DigPreconditions.failed("block_out_of_reach");
    }
    return new DigPreconditions(level, pos, state, null);
}

protected static Integer boundedCountOrNull(String value) {
    try {
        int count = Integer.parseInt(value);
        return count >= 1 && count <= 256 ? count : null;
    } catch (NumberFormatException exception) {
        return null;
    }
}

protected static CraftRequest craftRequest(ToolCall call) {
    Map<String, String> values = call.arguments().values();
    String recipeValue = values.get("recipe");
    String countValue = values.getOrDefault("count", "1");
    BlockPos craftingTablePos = null;
    if ((recipeValue == null || recipeValue.isBlank()) && !call.arguments().instruction().isBlank()) {
        CraftInstructionParser.CraftInstruction instruction = CraftInstructionParser.parseOrNull(call.arguments().instruction());
        if (instruction == null) {
            return null;
        }
        CraftInstructionParser.CraftingTablePosition tablePos = instruction.craftingTablePos();
        if (tablePos != null) {
            craftingTablePos = new BlockPos(tablePos.x(), tablePos.y(), tablePos.z());
        }
        return new CraftRequest(instruction.recipeId(), instruction.count(), craftingTablePos);
    }
    ResourceLocation recipeId = ResourceLocation.tryParse(recipeValue == null ? "" : recipeValue);
    Integer count = boundedCountOrNull(countValue);
    if (recipeId == null || recipeValue == null || !recipeValue.contains(":") || count == null) {
        return null;
    }
    String craftingTable = values.get("craftingTable");
    if (craftingTable != null && !craftingTable.isBlank()) {
        String x = AICoreJsonFields.integerField(craftingTable, "x");
        String y = AICoreJsonFields.integerField(craftingTable, "y");
        String z = AICoreJsonFields.integerField(craftingTable, "z");
        if (x.isBlank() || y.isBlank() || z.isBlank()) {
            return null;
        }
        try {
            craftingTablePos = new BlockPos(Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(z));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
    return new CraftRequest(recipeId, count, craftingTablePos);
}

protected boolean validCraftingTable(ServerLevel level, BlockPos pos) {
    return level != null
            && pos != null
            && level.hasChunkAt(pos)
            && withinInteractionDistance(pos)
            && level.getBlockState(pos).is(Blocks.CRAFTING_TABLE);
}
}
