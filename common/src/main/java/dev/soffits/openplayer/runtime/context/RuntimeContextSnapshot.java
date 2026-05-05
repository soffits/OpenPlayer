package dev.soffits.openplayer.runtime.context;

import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot;

public record RuntimeContextSnapshot(
        RuntimeWorldSnapshot world,
        RuntimeAgentSnapshot agent,
        RuntimeNearbySnapshot nearby,
        WorldPerceptionSnapshot perception
) {
    public RuntimeContextSnapshot(RuntimeWorldSnapshot world, RuntimeAgentSnapshot agent, RuntimeNearbySnapshot nearby) {
        this(world, agent, nearby, WorldPerceptionSnapshot.EMPTY);
    }

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
        if (perception == null) {
            throw new IllegalArgumentException("perception cannot be null");
        }
    }
}
