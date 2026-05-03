package dev.soffits.openplayer.automation.workstation;

public enum WorkstationKind {
    CRAFTING_TABLE("crafting_table"),
    FURNACE("furnace"),
    SMOKER("smoker"),
    BLAST_FURNACE("blast_furnace"),
    CAMPFIRE("campfire"),
    CUSTOM_MACHINE("custom_machine");

    private final String id;

    WorkstationKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
