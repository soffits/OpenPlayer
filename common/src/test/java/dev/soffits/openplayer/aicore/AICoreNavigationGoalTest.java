package dev.soffits.openplayer.aicore;

import java.util.Map;

public final class AICoreNavigationGoalTest {
    private AICoreNavigationGoalTest() {
    }

    public static void main(String[] args) {
        new AICoreGoal("goal_block", new AICoreVec3(1.0D, 64.0D, 2.0D), 0.0D, "");
        new AICoreGoal("goal_follow", null, 3.0D, "entity-1");
        ToolResult result = MinecraftPrimitiveTools.validate(ToolCall.of("pathfinder_goto", new ToolArguments(Map.of("goal", "{\"type\":\"goal_block\",\"x\":1,\"y\":64,\"z\":2}"))), new ToolValidationContext(true));
        AICoreTestSupport.require(result.status() == ToolResultStatus.SUCCESS, "pathfinder_goto goal_block must validate through loaded-area goto bridge");
        ToolResult setControl = MinecraftPrimitiveTools.validate(ToolCall.of("set_control_state", new ToolArguments(Map.of("control", "forward", "state", "true"))), new ToolValidationContext(true));
        AICoreTestSupport.require(setControl.status() == ToolResultStatus.SUCCESS, "set_control_state must validate as visible control state without fake motion");
        ToolResult wait = MinecraftPrimitiveTools.validate(ToolCall.of("wait_for_ticks", new ToolArguments(Map.of("ticks", "20"))), new ToolValidationContext(true));
        AICoreTestSupport.require(wait.status() == ToolResultStatus.SUCCESS, "wait_for_ticks must validate as a bounded tick wait request");
    }
}
