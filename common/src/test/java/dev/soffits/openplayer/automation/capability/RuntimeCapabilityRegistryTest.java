package dev.soffits.openplayer.automation.capability;

public final class RuntimeCapabilityRegistryTest {
    private RuntimeCapabilityRegistryTest() {
    }

    public static void main(String[] args) {
        capabilityLinesAreBoundedAndGeneric();
        capabilityIdsAreStableAndUseful();
    }

    private static void capabilityLinesAreBoundedAndGeneric() {
        java.util.List<String> lines = RuntimeCapabilityRegistry.reportLines();
        require(!lines.isEmpty(), "capability report must not be empty");
        require(lines.size() <= RuntimeCapabilityRegistry.MAX_REPORT_LINES,
                "capability report must be bounded by line count");
        for (String line : lines) {
            require(line.length() <= RuntimeCapabilityRegistry.MAX_REPORT_LINE_LENGTH,
                    "capability report line must be bounded: " + line);
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            require(!lower.contains("speedrun"), "capability report must not encode speedrun route logic");
        }
    }

    private static void capabilityIdsAreStableAndUseful() {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (RuntimeCapabilityRegistry.RuntimeCapability capability : RuntimeCapabilityRegistry.capabilities()) {
            require(ids.add(capability.id()), "capability id must be unique: " + capability.id());
        }
        require(ids.contains("movement.loaded"), "movement capability must be reported");
        require(ids.contains("portal.loaded_transition"), "generic portal transition capability must be reported");
        require(ids.contains("portal.strategy_pack_reference"), "portal strategy diagnostic must be reported");
        require(ids.contains("status.runtime_snapshot"), "runtime status capability must be reported");
        require(ids.contains("strategy_pack.local_reference"), "docs-only strategy pack reference diagnostic must be reported");
        require(ids.contains("eye_of_ender_observation"), "missing eye observation adapter must be reported");
        require(ids.contains("end_portal_frame_interaction"), "missing End portal frame adapter must be reported");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
