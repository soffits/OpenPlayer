package dev.soffits.openplayer.automation.capability;

import java.util.ArrayList;
import java.util.List;

public final class RuntimeCapabilityRegistry {
    public static final int MAX_REPORT_LINES = 8;
    public static final int MAX_REPORT_LINE_LENGTH = 120;

    private static final List<RuntimeCapability> CAPABILITIES = List.of(
            implemented("movement.loaded", "bounded already-loaded movement, owner follow, patrol, and exploration"),
            implemented("portal.loaded_transition", "existing loaded portal observation with verified dimension transition"),
            implemented("portal.vanilla_nether_build", "vanilla carried-obsidian Nether frame build and ignite adapter"),
            implemented("inventory.visible_drop_get", "exact visible dropped-item acquisition with NPC inventory verification"),
            implemented("recipe.simple_crafting", "server recipe data with inventory crafting and crafting table gate"),
            implemented("workstation.container_transfer", "NPC deposit, stash, and withdraw through safe loaded containers"),
            implemented("farming.loaded_crop", "bounded loaded crop harvest and metadata-gated replant"),
            implemented("building.strict_primitives", "small reviewed line, wall, floor, box, and stairs block placement"),
            implemented("combat.loaded_reviewed_targets", "allowlisted loaded hostile targets only"),
            implemented("status.runtime_snapshot", "selected NPC runtime, queue, dimension, and bounded capability status"),
            diagnostic("strategy_pack.local_reference", "docs-only local advisory strategy metadata, not auto-loaded or executed"),
            diagnostic("structure.loaded_evidence", "loaded-only structure evidence and container hints without loot ownership claims"),
            diagnostic("dimension.current_affordances", "arbitrary dimension id with loaded portal, exploration, and owner-follow options"),
            missing("fishing.npc_hook", "vanilla hook path is player-bound until reviewed server-authoritative NPC hook exists"),
            missing("trading.villager", "needs reviewed UI/trade state adapter and no-loss inventory settlement"),
            missing("breeding_taming", "needs per-entity consent, item, cooldown, ownership, and result verification adapters"),
            missing("custom_portal_builders", "needs per-portal adapter beyond vanilla carried-obsidian Nether frame"),
            missing("bucket_flow_portal", "needs fluid placement, recovery, and no-grief safety adapter"),
            missing("eye_of_ender_observation", "needs reviewed item-use trajectory observation and bounded state"),
            missing("structure.long_range_locate", "long-range locate APIs and chunk-generation searches are not player-like primitives"),
            missing("end_portal_frame_interaction", "needs loaded frame state adapter and exact item insertion verification"),
            missing("boss_fight_specialized_tactics", "needs reviewed arena tactics, hazard handling, and recovery semantics"),
            missing("modded_machine_adapters", "needs first-party or documented mod-specific adapters before mutation")
    );

    private RuntimeCapabilityRegistry() {
    }

    public static List<RuntimeCapability> capabilities() {
        return CAPABILITIES;
    }

    public static List<String> reportLines() {
        List<String> lines = new ArrayList<>();
        lines.add(limit("capability_report source=registry implemented=" + count(RuntimeCapabilityStatus.IMPLEMENTED)
                + " diagnostic=" + count(RuntimeCapabilityStatus.DIAGNOSTIC_ONLY)
                + " missing_adapter=" + count(RuntimeCapabilityStatus.MISSING_ADAPTER)));
        appendGrouped(lines, RuntimeCapabilityStatus.IMPLEMENTED, "implemented");
        appendGrouped(lines, RuntimeCapabilityStatus.DIAGNOSTIC_ONLY, "diagnostic_only");
        appendGrouped(lines, RuntimeCapabilityStatus.MISSING_ADAPTER, "missing_adapter");
        return List.copyOf(lines.subList(0, Math.min(lines.size(), MAX_REPORT_LINES)));
    }

    private static void appendGrouped(List<String> lines, RuntimeCapabilityStatus status, String label) {
        StringBuilder ids = new StringBuilder();
        StringBuilder reasons = new StringBuilder();
        int lineIndex = 1;
        for (RuntimeCapability capability : CAPABILITIES) {
            if (capability.status() != status) {
                continue;
            }
            String id = capability.id();
            String reason = capability.reason();
            if (ids.length() > 0 && (ids.length() + id.length() + 1 > 72 || reasons.length() + reason.length() + 1 > 72)) {
                lines.add(limit("capability_" + label + "_" + lineIndex + " ids=" + ids + " reasons=" + reasons));
                ids.setLength(0);
                reasons.setLength(0);
                lineIndex++;
            }
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(id);
            if (reasons.length() > 0) {
                reasons.append(',');
            }
            reasons.append(compact(reason));
        }
        if (ids.length() > 0) {
            lines.add(limit("capability_" + label + "_" + lineIndex + " ids=" + ids + " reasons=" + reasons));
        }
    }

    private static int count(RuntimeCapabilityStatus status) {
        int count = 0;
        for (RuntimeCapability capability : CAPABILITIES) {
            if (capability.status() == status) {
                count++;
            }
        }
        return count;
    }

    private static RuntimeCapability implemented(String id, String reason) {
        return new RuntimeCapability(id, RuntimeCapabilityStatus.IMPLEMENTED, reason);
    }

    private static RuntimeCapability diagnostic(String id, String reason) {
        return new RuntimeCapability(id, RuntimeCapabilityStatus.DIAGNOSTIC_ONLY, reason);
    }

    private static RuntimeCapability missing(String id, String reason) {
        return new RuntimeCapability(id, RuntimeCapabilityStatus.MISSING_ADAPTER, reason);
    }

    private static String compact(String value) {
        return value.trim().replace(' ', '_').replace(';', ',').replace('/', '_');
    }

    private static String limit(String value) {
        if (value.length() <= MAX_REPORT_LINE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_REPORT_LINE_LENGTH - 14) + "... truncated";
    }

    public record RuntimeCapability(String id, RuntimeCapabilityStatus status, String reason) {
        public RuntimeCapability {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id cannot be blank");
            }
            if (status == null) {
                throw new IllegalArgumentException("status cannot be null");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason cannot be blank");
            }
        }
    }

    public enum RuntimeCapabilityStatus {
        IMPLEMENTED,
        DIAGNOSTIC_ONLY,
        MISSING_ADAPTER
    }
}
