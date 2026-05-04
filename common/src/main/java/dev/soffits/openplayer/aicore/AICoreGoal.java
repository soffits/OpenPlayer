package dev.soffits.openplayer.aicore;

import java.util.Set;

public record AICoreGoal(String type, AICoreVec3 position, double range, String entityId) {
    private static final Set<String> TYPES = Set.of(
            "goal_block", "goal_near", "goal_xz", "goal_near_xz", "goal_y", "goal_get_to_block",
            "goal_follow", "goal_place_block", "goal_look_at_block", "goal_break_block"
    );

    public AICoreGoal {
        if (type == null || !TYPES.contains(type)) {
            throw new IllegalArgumentException("unsupported navigation goal type: " + type);
        }
        if (range < 0.0D) {
            throw new IllegalArgumentException("goal range cannot be negative");
        }
        if (type.equals("goal_follow") && (entityId == null || entityId.isBlank())) {
            throw new IllegalArgumentException("goal_follow requires entityId");
        }
        if (!type.equals("goal_follow") && position == null) {
            throw new IllegalArgumentException(type + " requires position");
        }
    }
}
