package dev.soffits.openplayer.aicore;

final class AICoreTestSupport {
    private AICoreTestSupport() {
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void requireTool(String name) {
        require(AICoreToolCatalog.registry().contains(ToolName.of(name)), "missing tool: " + name);
    }

    static void requireNoTool(String name) {
        require(!AICoreToolCatalog.registry().contains(ToolName.of(name)), "unexpected tool: " + name);
    }

    static void requireStatus(String name, CapabilityStatus status) {
        AICoreToolDefinition definition = AICoreToolCatalog.definition(ToolName.of(name)).orElseThrow();
        require(definition.capabilityStatus() == status, "unexpected status for " + name + ": " + definition.capabilityStatus());
    }

    static void requireRejected(ToolResult result, String reason) {
        require(result.status() == ToolResultStatus.REJECTED, "expected rejection");
        require(reason.equals(result.reason()), "unexpected reason: " + result.reason());
    }

    static void requireFailed(ToolResult result, String reason) {
        require(result.status() == ToolResultStatus.FAILED, "expected failure");
        require(reason.equals(result.reason()), "unexpected reason: " + result.reason());
    }
}
