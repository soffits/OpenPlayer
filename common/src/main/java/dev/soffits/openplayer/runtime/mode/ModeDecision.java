package dev.soffits.openplayer.runtime.mode;

public record ModeDecision(AutomationMode mode, boolean enabled, String reason) {
    public ModeDecision {
        if (mode == null) {
            throw new IllegalArgumentException("mode cannot be null");
        }
        reason = sanitize(reason);
    }

    private static String sanitize(String value) {
        String source = value == null || value.isBlank() ? "none" : value.trim();
        source = source.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return source.length() <= 96 ? source : source.substring(0, 96);
    }
}
