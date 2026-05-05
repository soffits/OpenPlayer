package dev.soffits.openplayer.runtime.profile;

import java.util.LinkedHashMap;
import java.util.Map;

public record ModelRoleConfig(String chatRole, String planningRole, String toolReasoningRole,
                              Map<String, String> credentialStatus) {
    public ModelRoleConfig {
        chatRole = sanitize(chatRole);
        planningRole = sanitize(planningRole);
        toolReasoningRole = sanitize(toolReasoningRole);
        credentialStatus = redact(credentialStatus);
    }

    public Map<String, String> status() {
        LinkedHashMap<String, String> status = new LinkedHashMap<>();
        status.put("chat", chatRole);
        status.put("planning", planningRole);
        status.put("tool_reasoning", toolReasoningRole);
        status.putAll(credentialStatus);
        status.put("validators_bypassed", "false");
        return Map.copyOf(status);
    }

    private static String sanitize(String value) {
        String sanitized = value == null || value.isBlank() ? "default" : value.trim();
        sanitized = sanitized.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return sanitized.length() <= 80 ? sanitized : sanitized.substring(0, 80);
    }

    private static Map<String, String> redact(Map<String, String> source) {
        LinkedHashMap<String, String> redacted = new LinkedHashMap<>();
        if (source == null) {
            return Map.of();
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = sanitize(entry.getKey()).toLowerCase(java.util.Locale.ROOT);
            redacted.put(key, entry.getValue() == null || entry.getValue().isBlank() ? "missing" : "configured_redacted");
        }
        return Map.copyOf(redacted);
    }
}
