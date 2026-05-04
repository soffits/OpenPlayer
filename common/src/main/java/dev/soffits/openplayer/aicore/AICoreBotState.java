package dev.soffits.openplayer.aicore;

import java.util.List;
import java.util.Map;

public record AICoreBotState(
        String username,
        AICoreVec3 position,
        AICoreVec3 spawnPoint,
        String dimension,
        String gameMode,
        boolean physicsEnabled,
        boolean raining,
        int health,
        int food,
        int oxygenLevel,
        int quickBarSlot,
        List<AICoreItemSnapshot> inventory,
        Map<String, Boolean> controlState
) {
    public AICoreBotState {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be blank");
        }
        if (position == null) {
            throw new IllegalArgumentException("position cannot be null");
        }
        if (spawnPoint == null) {
            throw new IllegalArgumentException("spawn point cannot be null");
        }
        if (dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("dimension cannot be blank");
        }
        if (gameMode == null || gameMode.isBlank()) {
            throw new IllegalArgumentException("game mode cannot be blank");
        }
        if (inventory == null) {
            throw new IllegalArgumentException("inventory cannot be null");
        }
        if (controlState == null) {
            throw new IllegalArgumentException("control state cannot be null");
        }
        inventory = List.copyOf(inventory);
        controlState = Map.copyOf(controlState);
    }

    public static AICoreBotState empty(String username) {
        return new AICoreBotState(username, new AICoreVec3(0.0D, 0.0D, 0.0D), new AICoreVec3(0.0D, 0.0D, 0.0D),
                "minecraft:overworld", "survival", false, false, 20, 20, 20, 0, List.of(),
                Map.of("forward", false, "back", false, "left", false, "right", false, "jump", false, "sprint", false, "sneak", false));
    }
}
