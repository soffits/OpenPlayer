package dev.soffits.openplayer.aicore;

import java.util.Map;

public final class AICoreNavigationGoalTest {
    private AICoreNavigationGoalTest() {
    }

    public static void main(String[] args) {
        new AICoreGoal("goal_block", new AICoreVec3(1.0D, 64.0D, 2.0D), 0.0D, "");
        new AICoreGoal("goal_follow", null, 3.0D, "entity-1");
        ToolResult result = MinecraftPrimitiveTools.validate(ToolCall.of("pathfinder_goto", new ToolArguments(Map.of("goal", "{\"type\":\"goal_block\",\"x\":1,\"y\":64,\"z\":2}"))), new ToolValidationContext(true));
        AICoreTestSupport.requireFailed(result, "unsupported_missing_pathfinder_adapter");
    }
}
