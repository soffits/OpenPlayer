package dev.soffits.openplayer.runtime.context;

public record RuntimeContextSnapshot(
        RuntimeWorldSnapshot world,
        RuntimeAgentSnapshot agent,
        RuntimeNearbySnapshot nearby
) {
    public RuntimeContextSnapshot {
        if (world == null) {
            throw new IllegalArgumentException("world cannot be null");
        }
        if (agent == null) {
            throw new IllegalArgumentException("agent cannot be null");
        }
        if (nearby == null) {
            throw new IllegalArgumentException("nearby cannot be null");
        }
    }
}
