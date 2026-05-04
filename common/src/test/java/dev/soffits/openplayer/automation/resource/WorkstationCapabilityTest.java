package dev.soffits.openplayer.automation.resource;

import dev.soffits.openplayer.automation.workstation.WorkstationCapability;
import dev.soffits.openplayer.automation.workstation.WorkstationDiagnostics;
import dev.soffits.openplayer.automation.workstation.WorkstationKind;
import dev.soffits.openplayer.automation.workstation.WorkstationTarget;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmeltingRecipe;

public final class WorkstationCapabilityTest {
    private WorkstationCapabilityTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        dynamicSmeltingRecipeIsSeparateFromAdapterAvailability();
        diagnosticsAreBoundedAndDeterministic();
        unavailableAdaptersHaveDeterministicDiagnostics();
    }

    private static void dynamicSmeltingRecipeIsSeparateFromAdapterAvailability() {
        SmeltingRecipe recipe = new SmeltingRecipe(
                new ResourceLocation("openplayer", "iron"),
                "",
                CookingBookCategory.MISC,
                Ingredient.of(Items.RAW_IRON),
                new ItemStack(Items.IRON_INGOT),
                0.0F,
                200
        );
        NonNullList<ItemStack> inventory = NonNullList.withSize(36, ItemStack.EMPTY);
        inventory.set(0, new ItemStack(Items.RAW_IRON));

        SmeltingPlan plan = RuntimeSmeltingRecipeIndex.planFor(
                recipe, new ResourceLocation("minecraft", "iron_ingot"), Items.IRON_INGOT, 1, inventory
        );

        require(plan != null, "dynamic smelting recipe lookup must still plan safe recipes");
        require(WorkstationCapability.VANILLA_FURNACE.supportsVanillaSmelting(),
                "vanilla furnace adapter must expose smelting capability");
        require(WorkstationCapability.SMOKER.hasSafeAdapter() && WorkstationCapability.SMOKER.supportsVanillaSmelting(),
                "smoker execution should expose the reviewed furnace-compatible adapter");
        require(WorkstationCapability.BLAST_FURNACE.hasSafeAdapter()
                        && WorkstationCapability.BLAST_FURNACE.supportsVanillaSmelting(),
                "blast furnace execution should expose the reviewed furnace-compatible adapter");
    }

    private static void diagnosticsAreBoundedAndDeterministic() {
        List<WorkstationTarget> targets = List.of(
                new WorkstationTarget(new BlockPos(5, 64, 0), WorkstationCapability.VANILLA_FURNACE, null),
                new WorkstationTarget(new BlockPos(1, 64, 0), WorkstationCapability.CRAFTING_TABLE, null),
                new WorkstationTarget(new BlockPos(3, 64, 0), WorkstationCapability.CRAFTING_TABLE, null),
                new WorkstationTarget(new BlockPos(2, 64, 0), WorkstationCapability.CRAFTING_TABLE, null),
                new WorkstationTarget(new BlockPos(4, 64, 0), WorkstationCapability.CRAFTING_TABLE, null)
        );

        String summary = WorkstationDiagnostics.targetSummary(targets);

        require(("crafting_table@1, 64, 0, crafting_table@2, 64, 0, crafting_table@3, 64, 0, "
                        + "crafting_table@4, 64, 0, +1 more").equals(summary),
                "summary must be deterministic and bounded");
    }

    private static void unavailableAdaptersHaveDeterministicDiagnostics() {
        require("no loaded nearby furnace workstation with vanilla_furnace adapter".equals(
                        WorkstationDiagnostics.noLoadedTarget(WorkstationCapability.VANILLA_FURNACE)),
                "missing vanilla furnace diagnostic must be deterministic");
        require("custom_machine adapter unavailable for modded:machine_recipe; add a safe workstation adapter before execution"
                        .equals(WorkstationDiagnostics.adapterUnavailable(
                                WorkstationKind.CUSTOM_MACHINE, "modded:machine_recipe")),
                "custom machine diagnostic must require an adapter seam");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
