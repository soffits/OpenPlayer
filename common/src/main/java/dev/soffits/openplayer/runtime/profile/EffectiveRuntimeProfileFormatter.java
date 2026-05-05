package dev.soffits.openplayer.runtime.profile;

import dev.soffits.openplayer.runtime.mode.AutomationMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class EffectiveRuntimeProfileFormatter {
    private EffectiveRuntimeProfileFormatter() {
    }

    public static List<String> statusLines(EffectiveRuntimeProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile cannot be null");
        }
        return List.of(
                limit("effective_policy selected=" + safe(profile.selectedPolicy())
                        + " providerSource=" + safe(profile.providerSource())),
                limit("effective_local_modes enabled=" + modes(profile.enabledLocalModes())
                        + " disabled=" + modes(profile.disabledLocalModes())),
                limit("effective_permission_gates " + booleans(profile.permissionGates())),
                limit("effective_runtime_overrides " + strings(profile.runtimeOverrides()))
        );
    }

    private static String modes(List<AutomationMode> modes) {
        if (modes.isEmpty()) {
            return "none";
        }
        List<String> names = new ArrayList<>();
        for (AutomationMode mode : modes) {
            names.add(mode.name().toLowerCase(java.util.Locale.ROOT));
        }
        names.sort(String::compareTo);
        return String.join(",", names);
    }

    private static String booleans(Map<String, Boolean> values) {
        if (values.isEmpty()) {
            return "none";
        }
        List<String> entries = new ArrayList<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                entries.add(safe(entry.getKey()) + "=" + entry.getValue()));
        return String.join(" ", entries);
    }

    private static String strings(Map<String, String> values) {
        if (values.isEmpty()) {
            return "none";
        }
        List<String> entries = new ArrayList<>();
        values.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).forEach(entry ->
                entries.add(safe(entry.getKey()) + "=" + safe(entry.getValue())));
        return String.join(" ", entries);
    }

    private static String safe(String value) {
        String source = value == null || value.isBlank() ? "none" : value.trim();
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < source.length() && builder.length() < 64; index++) {
            char character = source.charAt(index);
            if ((character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9') || character == '_' || character == '-'
                    || character == ':' || character == '.' || character == ',') {
                builder.append(character);
            } else {
                builder.append('_');
            }
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }

    private static String limit(String line) {
        return line.length() <= 120 ? line : line.substring(0, 106) + "... truncated";
    }
}
