package dev.soffits.openplayer.automation.resource;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ResourceAffordanceSummaryTest {
    private ResourceAffordanceSummaryTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        exactInventoryCapacityUsesNormalInventoryOnly();
        visibleDropsCanSatisfyMissingOnlyWhenCapacityFits();
        oversizedVisibleDropsCannotSatisfyExactAcquisition();
        multipleExactSafeStacksCanSatisfyMissingCount();
        droppedItemsAreSortedDeterministically();
        diagnosticsAreBoundedAndTruthful();
        diagnosticsIncludeNetherRecoveryConstraints();
    }

    private static void exactInventoryCapacityUsesNormalInventoryOnly() {
        NonNullList<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.STICK, 63));
        inventory.set(1, new ItemStack(Items.STONE, 64));
        inventory.set(30, ItemStack.EMPTY);
        inventory.set(31, ItemStack.EMPTY);

        int capacity = ResourceAffordanceSummary.normalInventoryCapacityFor(inventory, Items.STICK);

        require(capacity == 1 + 29 * 64, "normal inventory capacity must ignore equipment slots");
    }

    private static void visibleDropsCanSatisfyMissingOnlyWhenCapacityFits() {
        ResourceAffordanceSummary summary = new ResourceAffordanceSummary(
                "minecraft:stick", Items.STICK, 8, 2, 6, 6, 6, 8, false,
                List.of(
                        new ResourceAffordanceSummary.DroppedItemAffordance("a", new BlockPos(1, 64, 0), 3, 1.0D),
                        new ResourceAffordanceSummary.DroppedItemAffordance("b", new BlockPos(2, 64, 0), 3, 4.0D)
                ),
                List.of(),
                new ResourceAffordanceSummary.BlockSourceAffordance(false, 0, true, "not_a_block_item")
        );

        require(summary.canSatisfyMissingFromVisibleDrops(), "visible exact drops and capacity must satisfy missing count");

        ResourceAffordanceSummary noCapacity = new ResourceAffordanceSummary(
                "minecraft:stick", Items.STICK, 8, 2, 5, 6, 6, 8, false,
                summary.droppedItems(), List.of(), summary.blockSource()
        );
        require(!noCapacity.canSatisfyMissingFromVisibleDrops(), "collection must reject when capacity cannot fit missing count");
    }

    private static void oversizedVisibleDropsCannotSatisfyExactAcquisition() {
        ResourceAffordanceSummary summary = new ResourceAffordanceSummary(
                "minecraft:diamond", Items.DIAMOND, 1, 0, 64, 64, 0, 8, false,
                List.of(), List.of(),
                new ResourceAffordanceSummary.BlockSourceAffordance(false, 0, true, "not_a_block_item")
        );

        require(!summary.canSatisfyMissingFromVisibleDrops(), "oversized visible drops must not satisfy exact collection");
        require(summary.boundedDiagnostics(false).contains("visible_drop_status=exact_safe_insufficient_or_oversized"),
                "diagnostics must explain visible drops are not exact-safe enough");
    }

    private static void multipleExactSafeStacksCanSatisfyMissingCount() {
        ResourceAffordanceSummary summary = new ResourceAffordanceSummary(
                "minecraft:stick", Items.STICK, 5, 0, 64, 5, 5, 8, false,
                List.of(
                        new ResourceAffordanceSummary.DroppedItemAffordance("a", new BlockPos(1, 64, 0), 2, 1.0D),
                        new ResourceAffordanceSummary.DroppedItemAffordance("b", new BlockPos(2, 64, 0), 3, 4.0D)
                ),
                List.of(),
                new ResourceAffordanceSummary.BlockSourceAffordance(false, 0, true, "not_a_block_item")
        );

        require(summary.canSatisfyMissingFromVisibleDrops(), "multiple exact-safe stacks must satisfy missing count");
    }

    private static void droppedItemsAreSortedDeterministically() {
        ResourceAffordanceSummary summary = new ResourceAffordanceSummary(
                "minecraft:stick", Items.STICK, 4, 0, 64, 3, 3, 8, false,
                List.of(
                        new ResourceAffordanceSummary.DroppedItemAffordance("z", new BlockPos(5, 64, 0), 1, 25.0D),
                        new ResourceAffordanceSummary.DroppedItemAffordance("a", new BlockPos(1, 64, 0), 1, 1.0D),
                        new ResourceAffordanceSummary.DroppedItemAffordance("b", new BlockPos(1, 64, 1), 1, 1.0D)
                ),
                List.of(),
                new ResourceAffordanceSummary.BlockSourceAffordance(false, 0, true, "not_a_block_item")
        );

        require("a".equals(summary.droppedItems().get(0).entityId()), "nearest deterministic drop must be first");
        require("b".equals(summary.droppedItems().get(1).entityId()), "tie-breaker position must be deterministic");
        require("z".equals(summary.droppedItems().get(2).entityId()), "farther drop must be last");
    }

    private static void diagnosticsAreBoundedAndTruthful() {
        ResourceAffordanceSummary summary = new ResourceAffordanceSummary(
                "minecraft:oak_log", Items.OAK_LOG, 12, 1, 64, 10, 10, 1, true,
                List.of(new ResourceAffordanceSummary.DroppedItemAffordance("a", new BlockPos(1, 64, 0), 2, 1.0D)),
                List.of(new ResourceAffordanceSummary.WorkstationAffordance(
                        "crafting_table", new BlockPos(2, 64, 0), "vanilla_crafting_table"
                )),
                new ResourceAffordanceSummary.BlockSourceAffordance(true, 3, true,
                        "generic_block_breaking_requires_safe_drop_adapter")
        );

        String diagnostics = summary.boundedDiagnostics(true);

        require(diagnostics.contains("inventory=1/12 capacity=64"), "diagnostics must include exact inventory state");
        require(diagnostics.contains("visible_drops_total=10"), "diagnostics must include uncapped visible drop count");
        require(diagnostics.contains("exact_safe_drops=10"), "diagnostics must include exact-safe drop count");
        require(diagnostics.contains("candidate_stacks=1 candidate_cap=1 truncated=true"),
                "diagnostics must include candidate cap status");
        require(diagnostics.contains("workstations=crafting_table@"), "diagnostics must include workstation summary");
        require(diagnostics.contains("containers=nearby_safe_loaded"), "diagnostics must include container observation");
        require(diagnostics.contains("block_sources=diagnostic_only matched=3"),
                "block sources must be diagnostic-only");
    }

    private static void diagnosticsIncludeNetherRecoveryConstraints() {
        ResourceAffordanceSummary summary = new ResourceAffordanceSummary(
                "minecraft:blaze_rod", Items.BLAZE_ROD, 1, 0, 64, 0, 0, 8, false,
                List.of(), List.of(),
                new ResourceAffordanceSummary.BlockSourceAffordance(false, 0, true, "not_a_block_item")
        );

        String diagnostics = summary.boundedDiagnostics(false, "minecraft:the_nether");

        require(diagnostics.contains("dimension=minecraft:the_nether"),
                "diagnostics must include current dimension");
        require(diagnostics.contains("nether_recovery=water_bucket_unusable"),
                "Nether diagnostics must include obvious recovery constraints");
    }

    private static NonNullList<ItemStack> emptyInventory() {
        return NonNullList.withSize(36, ItemStack.EMPTY);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
