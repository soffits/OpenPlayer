package dev.soffits.openplayer.intent;

public record ProviderIntent(String kind, String priority, String instruction, String toolJson, boolean plan) {
    public ProviderIntent(String kind, String priority, String instruction) {
        this(kind, priority, instruction, "", false);
    }

    public ProviderIntent {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        if (priority == null) {
            throw new IllegalArgumentException("priority cannot be null");
        }
        if (instruction == null) {
            throw new IllegalArgumentException("instruction cannot be null");
        }
        if (toolJson == null) {
            throw new IllegalArgumentException("toolJson cannot be null");
        }
    }

    public static ProviderIntent structuredTool(String priority, String toolJson) {
        return new ProviderIntent("TOOL", defaultPriority(priority), "", toolJson, false);
    }

    public static ProviderIntent structuredPlan(String priority, String toolJson) {
        return new ProviderIntent("PLAN", defaultPriority(priority), "", toolJson, true);
    }

    public boolean hasStructuredToolJson() {
        return !toolJson.isBlank();
    }

    private static String defaultPriority(String priority) {
        return priority == null || priority.isBlank() ? "NORMAL" : priority;
    }
}
