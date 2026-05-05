package dev.soffits.openplayer.automation.capability;

import dev.soffits.openplayer.aicore.ToolName;
import dev.soffits.openplayer.aicore.ToolParameter;
import dev.soffits.openplayer.aicore.ToolSchema;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RuntimeCapabilityRegistry {
    public static final int MAX_REPORT_LINES = 8;
    public static final int MAX_REPORT_LINE_LENGTH = 120;

    private static final List<RuntimeCapability> CAPABILITIES = List.of(
            implemented("movement.loaded", "bounded already-loaded movement, owner follow, patrol, and exploration"),
            implemented("portal.loaded_transition", "existing loaded portal observation with verified dimension transition"),
            diagnostic("portal.strategy_pack_reference", "portal construction remains strategy guidance until primitive validation adapters exist"),
            implemented("inventory.visible_drop_get", "exact visible dropped-item acquisition with NPC inventory verification"),
            diagnostic("recipe.registry_query", "recipe data may inform plans without claiming Java crafting execution"),
            implemented("inventory.container_transfer", "NPC deposit, stash, and withdraw through safe loaded containers"),
            missing("farming.primitive_adapters", "crop harvest and replant chains require reviewed generic block and inventory adapters"),
            implemented("block.place_break_loaded", "reviewed loaded block placement and breaking primitives only"),
            implemented("combat.loaded_reviewed_targets", "allowlisted loaded hostile targets only"),
            implemented("status.runtime_snapshot", "selected NPC runtime, queue, dimension, and bounded capability status"),
            diagnostic("strategy_pack.local_reference", "docs-only local advisory strategy metadata, not auto-loaded or executed"),
            diagnostic("structure.loaded_evidence", "loaded-only structure evidence and container hints without loot ownership claims"),
            diagnostic("dimension.current_affordances", "arbitrary dimension id with loaded portal, exploration, and owner-follow options"),
            missing("fishing.npc_hook", "vanilla hook path is player-bound until reviewed server-authoritative NPC hook exists"),
            missing("trading.villager", "needs reviewed UI/trade state adapter and no-loss inventory settlement"),
            missing("breeding_taming", "needs per-entity consent, item, cooldown, ownership, and result verification adapters"),
            missing("custom_portal_builders", "needs per-portal adapter beyond generic block placement primitives"),
            missing("bucket_flow_portal", "needs fluid placement, recovery, and no-grief safety adapter"),
            missing("eye_of_ender_observation", "needs reviewed item-use trajectory observation and bounded state"),
            missing("structure.long_range_locate", "long-range locate APIs and chunk-generation searches are not player-like primitives"),
            missing("end_portal_frame_interaction", "needs loaded frame state adapter and exact item insertion verification"),
            missing("boss_fight_specialized_tactics", "needs reviewed arena tactics, hazard handling, and recovery semantics"),
            missing("modded_machine_adapters", "needs first-party or documented mod-specific adapters before mutation")
    );
    private static final ValidatedRegistry BUILTIN_REGISTRY = builtinRegistry();

    private RuntimeCapabilityRegistry() {
    }

    public static List<RuntimeCapability> capabilities() {
        return CAPABILITIES;
    }

    public static ValidatedRegistry builtinValidatedRegistry() {
        return BUILTIN_REGISTRY;
    }

    public static List<String> providerToolDocs() {
        return BUILTIN_REGISTRY.providerToolDocs();
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

    private static ValidatedRegistry builtinRegistry() {
        ValidatedRegistry registry = new ValidatedRegistry();
        registry.register(new CapabilityAction(
                "find_loaded_blocks",
                RuntimeCapabilityStatus.IMPLEMENTED,
                new ToolSchema(new ToolName("find_loaded_blocks"),
                        "Search already-loaded nearby blocks and return validated coordinates for later primitives.",
                        List.of(new ToolParameter("block_id", "resource_location", true, "Target block id."),
                                new ToolParameter("max_distance", "integer", false, "Bounded loaded search distance.")),
                        false),
                "loaded world query only"));
        registry.register(new CapabilityAction(
                "break_block",
                RuntimeCapabilityStatus.IMPLEMENTED,
                new ToolSchema(new ToolName("break_block"),
                        "Break one validated loaded block through reviewed survival primitive semantics.",
                        List.of(new ToolParameter("x", "integer", true, "Target block x coordinate."),
                                new ToolParameter("y", "integer", true, "Target block y coordinate."),
                                new ToolParameter("z", "integer", true, "Target block z coordinate."),
                                new ToolParameter("expected_block", "resource_location", true, "Block id that must match before mutation.")),
                        true),
                "validated loaded block primitive"));
        registry.register(new CapabilityAction(
                "place_block",
                RuntimeCapabilityStatus.IMPLEMENTED,
                new ToolSchema(new ToolName("place_block"),
                        "Place one inventory-backed block at a validated loaded coordinate; no direct setBlock success.",
                        List.of(new ToolParameter("x", "integer", true, "Target block x coordinate."),
                                new ToolParameter("y", "integer", true, "Target block y coordinate."),
                                new ToolParameter("z", "integer", true, "Target block z coordinate."),
                                new ToolParameter("block_id", "resource_location", true, "Inventory block id to place.")),
                        true),
                "validated loaded placement primitive"));
        registry.register(new CapabilityAction(
                "team_claim_work",
                RuntimeCapabilityStatus.IMPLEMENTED,
                new ToolSchema(new ToolName("team_claim_work"),
                        "Claim one structured team work key atomically before acting on it.",
                        List.of(new ToolParameter("work_key", "string", true, "Validated local work key."),
                                new ToolParameter("owner_id", "string", true, "NPC runtime owner id.")),
                        false),
                "local blackboard claim only"));
        registry.register(new CapabilityAction(
                "build_diff_status",
                RuntimeCapabilityStatus.DIAGNOSTIC_ONLY,
                new ToolSchema(new ToolName("build_diff_status"),
                        "Report blueprint mismatch entries, progress, score, and claim history without faking block edits.",
                        List.of(new ToolParameter("project_id", "string", true, "Validated build project id.")),
                        false),
                "foundation status until real build adapter is wired"));
        return registry.freeze();
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

    public record CapabilityAction(String id, RuntimeCapabilityStatus status, ToolSchema schema, String executionContract) {
        public CapabilityAction {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("capability action id cannot be blank");
            }
            if (status == null) {
                throw new IllegalArgumentException("capability action status cannot be null");
            }
            if (schema == null) {
                throw new IllegalArgumentException("capability action schema cannot be null");
            }
            if (!id.equals(schema.name().value())) {
                throw new IllegalArgumentException("capability action id must match schema name");
            }
            if (executionContract == null || executionContract.isBlank()) {
                throw new IllegalArgumentException("capability action execution contract cannot be blank");
            }
        }
    }

    public record CapabilityValidationResult(boolean accepted, String reason, Optional<CapabilityAction> action) {
        public CapabilityValidationResult {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("capability validation reason cannot be blank");
            }
            if (action == null) {
                throw new IllegalArgumentException("capability validation action cannot be null");
            }
        }
    }

    public static final class ValidatedRegistry {
        private final LinkedHashMap<String, CapabilityAction> actions;
        private boolean frozen;

        public ValidatedRegistry() {
            this.actions = new LinkedHashMap<>();
        }

        private ValidatedRegistry(LinkedHashMap<String, CapabilityAction> actions, boolean frozen) {
            this.actions = actions;
            this.frozen = frozen;
        }

        public void register(CapabilityAction action) {
            if (frozen) {
                throw new IllegalStateException("capability registry is frozen");
            }
            if (actions.putIfAbsent(action.id(), action) != null) {
                throw new IllegalArgumentException("duplicate capability action: " + action.id());
            }
        }

        public ValidatedRegistry freeze() {
            return new ValidatedRegistry(new LinkedHashMap<>(actions), true);
        }

        public Collection<CapabilityAction> actions() {
            return Collections.unmodifiableCollection(actions.values());
        }

        public Optional<CapabilityAction> action(String id) {
            return Optional.ofNullable(actions.get(id));
        }

        public CapabilityValidationResult validateProviderCall(String actionId, Map<String, String> arguments) {
            if (actionId == null || actionId.isBlank()) {
                return rejected("action id missing");
            }
            CapabilityAction action = actions.get(actionId);
            if (action == null) {
                return rejected("unregistered action id rejected");
            }
            if (action.status() != RuntimeCapabilityStatus.IMPLEMENTED) {
                return new CapabilityValidationResult(false, "capability is not executable: " + action.status(), Optional.of(action));
            }
            Map<String, String> safeArguments = arguments == null ? Map.of() : arguments;
            for (ToolParameter parameter : action.schema().parameters()) {
                if (parameter.required() && !safeArguments.containsKey(parameter.name())) {
                    return new CapabilityValidationResult(false, "missing required argument: " + parameter.name(), Optional.of(action));
                }
            }
            return new CapabilityValidationResult(true, "validated by capability registry schema", Optional.of(action));
        }

        public List<String> providerToolDocs() {
            ArrayList<String> lines = new ArrayList<>();
            for (CapabilityAction action : actions.values()) {
                if (action.status() == RuntimeCapabilityStatus.MISSING_ADAPTER) {
                    continue;
                }
                lines.add(action.id() + " status=" + action.status() + " mutates_world="
                        + action.schema().mutatesWorld() + " contract=" + action.executionContract()
                        + " params=" + requiredParameters(action.schema()));
            }
            return List.copyOf(lines);
        }

        private CapabilityValidationResult rejected(String reason) {
            return new CapabilityValidationResult(false, reason, Optional.empty());
        }

        private String requiredParameters(ToolSchema schema) {
            ArrayList<String> names = new ArrayList<>();
            for (ToolParameter parameter : schema.parameters()) {
                if (parameter.required()) {
                    names.add(parameter.name());
                }
            }
            return String.join(",", names);
        }
    }
}
