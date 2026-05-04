package dev.soffits.openplayer.aicore;

import java.util.Map;

public final class AICoreInventoryWindowTransactionTest {
    private AICoreInventoryWindowTransactionTest() {
    }

    public static void main(String[] args) {
        AICoreTestSupport.requireStatus("transfer", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER);
        ToolResult transfer = MinecraftPrimitiveTools.validate(ToolCall.of("transfer", new ToolArguments(Map.of("options", "{}"))), new ToolValidationContext(true));
        AICoreTestSupport.requireFailed(transfer, "unsupported_missing_window_adapter");
        ToolResult slot = MinecraftPrimitiveTools.validate(ToolCall.of("set_quick_bar_slot", new ToolArguments(Map.of("slot", "300"))), new ToolValidationContext(true));
        AICoreTestSupport.requireRejected(slot, "slot must be between 0 and 255");
    }
}
