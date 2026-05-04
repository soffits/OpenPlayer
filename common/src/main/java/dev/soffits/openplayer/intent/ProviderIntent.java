package dev.soffits.openplayer.intent;

public record ProviderIntent(Type type, String priority, String message, String toolJson) {
    public enum Type {
        TOOL,
        PLAN,
        CHAT,
        UNAVAILABLE
    }

    public ProviderIntent {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        if (priority == null) {
            throw new IllegalArgumentException("priority cannot be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
        if (toolJson == null) {
            throw new IllegalArgumentException("toolJson cannot be null");
        }
    }

    public static ProviderIntent structuredTool(String priority, String toolJson) {
        return new ProviderIntent(Type.TOOL, defaultPriority(priority), "", toolJson);
    }

    public static ProviderIntent structuredPlan(String priority, String toolJson) {
        return new ProviderIntent(Type.PLAN, defaultPriority(priority), "", toolJson);
    }

    public static ProviderIntent chat(String priority, String message) {
        return new ProviderIntent(Type.CHAT, defaultPriority(priority), message, "");
    }

    public static ProviderIntent unavailable(String priority, String reason) {
        return new ProviderIntent(Type.UNAVAILABLE, defaultPriority(priority), reason, "");
    }

    public boolean hasStructuredToolJson() {
        return !toolJson.isBlank();
    }

    public boolean plan() {
        return type == Type.PLAN;
    }

    private static String defaultPriority(String priority) {
        return priority == null || priority.isBlank() ? "NORMAL" : priority;
    }
}
