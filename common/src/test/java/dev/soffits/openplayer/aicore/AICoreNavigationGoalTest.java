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
        AICoreTestSupport.requireStatus("pathfinder_get_path_to", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("pathfinder_set_goal", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreToolDefinition pathDefinition = AICoreToolCatalog.definition(ToolName.of("pathfinder_get_path_to")).orElseThrow();
        AICoreTestSupport.require(pathDefinition.schema().description().contains("non-node"), "path diagnostics must not claim mineflayer node-list parity");
        ToolResult path = MinecraftPrimitiveTools.validate(ToolCall.of("pathfinder_get_path_to", new ToolArguments(Map.of("goal", "{\"type\":\"goal_block\",\"x\":1,\"y\":64,\"z\":2}", "timeoutTicks", "20"))), new ToolValidationContext(true));
        AICoreTestSupport.require(path.status() == ToolResultStatus.SUCCESS, "pathfinder_get_path_to must validate as bounded diagnostics");
        ToolResult movements = MinecraftPrimitiveTools.validate(ToolCall.of("pathfinder_set_movements", new ToolArguments(Map.of("movements", "{}"))), new ToolValidationContext(true));
        AICoreTestSupport.requireFailed(movements, "unsupported_movement_profile_not_applied");
    }
}
