package dev.soffits.openplayer.automation.resource;

import dev.soffits.openplayer.automation.workstation.WorkstationCapability;
import dev.soffits.openplayer.automation.workstation.WorkstationDiagnostics;
import dev.soffits.openplayer.automation.workstation.WorkstationKind;
import dev.soffits.openplayer.automation.workstation.WorkstationTarget;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class WorkstationCapabilityTest {
    private WorkstationCapabilityTest() {
    }

    public static void main(String[] args) {
        workstationAdapterAvailabilityIsExplicit();
        diagnosticsAreBoundedAndDeterministic();
        unavailableAdaptersHaveDeterministicDiagnostics();
    }

    private static void workstationAdapterAvailabilityIsExplicit() {
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
