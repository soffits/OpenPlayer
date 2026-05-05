package dev.soffits.openplayer.runtime.profile;

import dev.soffits.openplayer.runtime.mode.AutomationMode;
import java.util.List;
import java.util.Map;

public record EffectiveRuntimeProfile(
        String selectedPolicy,
        List<AutomationMode> enabledLocalModes,
        List<AutomationMode> disabledLocalModes,
        String providerSource,
        Map<String, Boolean> permissionGates,
        Map<String, String> runtimeOverrides
) {
    public EffectiveRuntimeProfile {
        selectedPolicy = sanitize(selectedPolicy, 96);
        enabledLocalModes = List.copyOf(enabledLocalModes == null ? List.of() : enabledLocalModes);
        disabledLocalModes = List.copyOf(disabledLocalModes == null ? List.of() : disabledLocalModes);
        providerSource = sanitize(providerSource, 96);
        permissionGates = Map.copyOf(permissionGates == null ? Map.of() : permissionGates);
        runtimeOverrides = Map.copyOf(runtimeOverrides == null ? Map.of() : runtimeOverrides);
    }

    private static String sanitize(String value, int maxLength) {
        String source = value == null || value.isBlank() ? "none" : value.trim();
        source = source.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return source.length() <= maxLength ? source : source.substring(0, maxLength);
    }
}
