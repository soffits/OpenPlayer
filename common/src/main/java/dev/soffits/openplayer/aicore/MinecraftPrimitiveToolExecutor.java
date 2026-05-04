package dev.soffits.openplayer.aicore;

import dev.soffits.openplayer.automation.AutomationCommandResult;
import dev.soffits.openplayer.automation.AutomationCommandStatus;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentPriority;
import java.util.Optional;
import java.util.function.Function;

public final class MinecraftPrimitiveToolExecutor implements ToolExecutor {
    private final Function<CommandIntent, AutomationCommandResult> commandSubmitter;

    public MinecraftPrimitiveToolExecutor(Function<CommandIntent, AutomationCommandResult> commandSubmitter) {
        if (commandSubmitter == null) {
            throw new IllegalArgumentException("commandSubmitter cannot be null");
        }
        this.commandSubmitter = commandSubmitter;
    }

    @Override
    public ToolResult execute(ToolCall call, ToolValidationContext context) {
        ToolResult validation = MinecraftPrimitiveTools.validate(call, context);
        if (validation.status() != ToolResultStatus.SUCCESS) {
            return validation;
        }
        Optional<CommandIntent> intent = MinecraftPrimitiveTools.toCommandIntent(call, IntentPriority.NORMAL);
        if (intent.isEmpty()) {
            return ToolResult.rejected("Tool is not mapped to a runtime primitive: " + call.name().value());
        }
        AutomationCommandResult result = commandSubmitter.apply(intent.get());
        if (result.status() == AutomationCommandStatus.ACCEPTED) {
            return ToolResult.success(result.message());
        }
        return ToolResult.rejected(result.message());
    }
}
