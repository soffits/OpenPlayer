package dev.soffits.openplayer.automation.advanced;

import net.minecraft.core.BlockPos;

public final class LoadedStructureDiagnosticScannerTest {
    private LoadedStructureDiagnosticScannerTest() {
    }

    public static void main(String[] args) {
        summarizesVillageEvidenceWithoutClaimingMembership();
        summarizesUnsupportedStructureAsLoadedScan();
    }

    private static void summarizesVillageEvidenceWithoutClaimingMembership() {
        LoadedStructureDiagnosticScanner.StructureSighting sighting =
                new LoadedStructureDiagnosticScanner.StructureSighting(
                        "village_bell_and_bed", new BlockPos(1, 64, 2), 3.5D
                );
        LoadedStructureDiagnosticScanner.ContainerHint containerHint =
                new LoadedStructureDiagnosticScanner.ContainerHint(new BlockPos(2, 64, 2), 1.0D);
        LoadedStructureDiagnosticScanner.StructureScanDiagnostics diagnostics =
                new LoadedStructureDiagnosticScanner.StructureScanDiagnostics(
                        LoadedStructureDiagnosticScanner.SOURCE, 32, 160, 128, 1, 32, 3, false, "none"
                );
        LoadedStructureDiagnosticScanner.StructureDiagnosticResult result =
                LoadedStructureDiagnosticScanner.StructureDiagnosticResult.evidenceFound(
                        "minecraft:village", 32, sighting, containerHint, diagnostics
                );
        String summary = result.summary();
        require(summary.contains("status=evidence_found"), "Summary should include evidence status");
        require(summary.contains("source=loaded_scan"), "Summary should include loaded scan source");
        require(summary.contains("loaded_world_evidence_only_no_confirmed_structure_membership"),
                "Summary should avoid claiming confirmed structure membership");
        require(summary.contains("evidence=village_bell_and_bed"), "Summary should include evidence kind");
        require(summary.contains("checked_positions=160"), "Summary should include checked positions");
        require(summary.contains("inspected_loaded_positions=128"), "Summary should include inspected positions");
        require(summary.contains("container_hint=diagnostic_only"), "Summary should make container hint diagnostic-only");
        require(summary.contains("no_item_movement_no_ownership_or_structure_membership_guarantee"),
                "Summary should not guarantee ownership or membership");
        require(summary.contains("explicit owner decision"), "Summary should require an explicit owner decision");
        require(!summary.contains("WITHDRAW_ITEM"), "Summary should not directly suggest WITHDRAW_ITEM");
        require(!summary.contains("loot_plan"), "Summary should not describe a loot plan");
    }

    private static void summarizesUnsupportedStructureAsLoadedScan() {
        LoadedStructureDiagnosticScanner.StructureDiagnosticResult result =
                LoadedStructureDiagnosticScanner.StructureDiagnosticResult.unsupported(
                        "minecraft:stronghold", 32,
                        LoadedStructureDiagnosticScanner.StructureScanDiagnostics.invalid("unsupported_structure")
                );
        String summary = result.summary();
        require(summary.contains("status=unsupported_structure"), "Summary should include unsupported status");
        require(summary.contains("source=loaded_scan"), "Summary should still identify loaded scan source");
        require(summary.contains("reason=unsupported_structure"), "Summary should include deterministic reason");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
