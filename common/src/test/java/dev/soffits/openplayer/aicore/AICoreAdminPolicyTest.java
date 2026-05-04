package dev.soffits.openplayer.aicore;

public final class AICoreAdminPolicyTest {
    private AICoreAdminPolicyTest() {
    }

    public static void main(String[] args) {
        for (String tool : new String[] {"creative_set_inventory_slot", "creative_clear_inventory", "creative_fly_to", "set_command_block"}) {
            AICoreTestSupport.requireStatus(tool, CapabilityStatus.POLICY_REJECTED);
            AICoreTestSupport.requireRejected(MinecraftPrimitiveTools.validate(ToolCall.of(tool, ToolArguments.empty()), new ToolValidationContext(true)), "rejected_admin_capability");
        }
    }
}
