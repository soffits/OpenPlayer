package dev.soffits.openplayer.aicore;

public record AgentObservation(WorldSnapshot world, InventorySnapshot inventory, PrimitiveAction action) {
    public AgentObservation {
        if (world == null) {
            throw new IllegalArgumentException("world snapshot cannot be null");
        }
        if (inventory == null) {
            throw new IllegalArgumentException("inventory snapshot cannot be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("primitive action cannot be null");
        }
    }
}
