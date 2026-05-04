package dev.soffits.openplayer.aicore;

import java.util.Map;

public final class AICoreInventoryWindowTransactionTest {
    private AICoreInventoryWindowTransactionTest() {
    }

    public static void main(String[] args) {
        AICoreTestSupport.requireStatus("transfer", CapabilityStatus.UNSUPPORTED_MISSING_ADAPTER);
        AICoreTestSupport.requireStatus("set_quick_bar_slot", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("unequip", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        ToolResult transfer = MinecraftPrimitiveTools.validate(ToolCall.of("transfer", new ToolArguments(Map.of("options", "{}"))), new ToolValidationContext(true));
        AICoreTestSupport.requireFailed(transfer, "unsupported_missing_window_adapter");
        ToolResult slot = MinecraftPrimitiveTools.validate(ToolCall.of("set_quick_bar_slot", new ToolArguments(Map.of("slot", "300"))), new ToolValidationContext(true));
        AICoreTestSupport.requireRejected(slot, "slot must be between 0 and 8");
        ToolResult unequip = MinecraftPrimitiveTools.validate(ToolCall.of("unequip", new ToolArguments(Map.of("destination", "head"))), new ToolValidationContext(true));
        AICoreTestSupport.require(unequip.status() == ToolResultStatus.SUCCESS, "unequip must validate as a no-loss inventory-local adapter");
    }
}
