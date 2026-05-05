package dev.soffits.openplayer.aicore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CapabilityScopedToolDocs {
    private static final int MAX_DOCS = 64;

    private CapabilityScopedToolDocs() {
    }

    public static String forObjective(String objective) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (AICoreToolDefinition definition : definitionsForObjective(objective)) {
            if (count++ > 0) {
                builder.append("; ");
            }
            builder.append(definition.schema().name().value()).append(": ")
                    .append(definition.schema().description());
        }
        return builder.toString();
    }

    public static List<AICoreToolDefinition> definitionsForObjective(String objective) {
        ArrayList<AICoreToolDefinition> selected = new ArrayList<>();
        for (String group : foregroundGroups(objective)) {
            for (AICoreToolDefinition definition : AICoreToolCatalog.definitions()) {
                if (selected.size() >= MAX_DOCS) {
                    return List.copyOf(selected);
                }
                if (isUsable(definition.capabilityStatus()) && group.equals(definition.group())) {
                    selected.add(definition);
                }
            }
        }
        return List.copyOf(selected);
    }

    private static List<String> foregroundGroups(String objective) {
        String text = objective == null ? "" : objective.toLowerCase(Locale.ROOT);
        ArrayList<String> groups = new ArrayList<>();
        groups.add("core_state");
        if (text.contains("furnace") || text.contains("craft") || text.contains("deliver") || text.contains("throw")) {
            groups.add("world_query");
            groups.add("block_mutation");
            groups.add("inventory_window");
            groups.add("recipes_crafting");
            groups.add("containers");
            groups.add("movement_pathfinder");
        } else {
            groups.add("world_query");
            groups.add("movement_pathfinder");
            groups.add("inventory_window");
            groups.add("openplayer_bridge");
        }
        return List.copyOf(groups);
    }

    private static boolean isUsable(CapabilityStatus status) {
        return status == CapabilityStatus.IMPLEMENTED || status == CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS;
    }
}
