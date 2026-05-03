package dev.soffits.openplayer.automation.workstation;

public record WorkstationCapability(WorkstationKind kind, boolean supportsCraftingTableRecipes,
                                    boolean supportsVanillaSmelting, boolean hasSafeAdapter,
                                    String adapterId) {
    public static final WorkstationCapability CRAFTING_TABLE = new WorkstationCapability(
            WorkstationKind.CRAFTING_TABLE, true, false, true, "vanilla_crafting_table"
    );
    public static final WorkstationCapability VANILLA_FURNACE = new WorkstationCapability(
            WorkstationKind.FURNACE, false, true, true, "vanilla_furnace"
    );
    public static final WorkstationCapability SMOKER_UNAVAILABLE = unavailable(WorkstationKind.SMOKER);
    public static final WorkstationCapability BLAST_FURNACE_UNAVAILABLE = unavailable(WorkstationKind.BLAST_FURNACE);
    public static final WorkstationCapability CAMPFIRE_UNAVAILABLE = unavailable(WorkstationKind.CAMPFIRE);
    public static final WorkstationCapability CUSTOM_MACHINE_UNAVAILABLE = unavailable(WorkstationKind.CUSTOM_MACHINE);

    public static WorkstationCapability unavailable(WorkstationKind kind) {
        return new WorkstationCapability(kind, false, false, false, "unavailable");
    }
}
