package dev.soffits.openplayer.automation.resource;

import java.util.ArrayList;
import java.util.List;

public final class EndgameTaskTreeStatusFormatter {
    public static final int DEFAULT_MAX_LINES = 8;
    public static final int DEFAULT_MAX_LINE_LENGTH = 120;

    private EndgameTaskTreeStatusFormatter() {
    }

    public static List<String> visibleLines(EndgameTaskTree tree) {
        return visibleLines(tree, DEFAULT_MAX_LINES, DEFAULT_MAX_LINE_LENGTH);
    }

    public static List<String> visibleViewerDiagnosticLines(EndgameTaskTree tree) {
        return visibleViewerDiagnosticLines(tree, DEFAULT_MAX_LINES, DEFAULT_MAX_LINE_LENGTH);
    }

    public static List<String> visibleViewerDiagnosticLines(EndgameTaskTree tree, int maxLines, int maxLineLength) {
        return visibleLines(tree, maxLines, maxLineLength, true);
    }

    public static List<String> visibleLines(EndgameTaskTree tree, int maxLines, int maxLineLength) {
        return visibleLines(tree, maxLines, maxLineLength, false);
    }

    private static List<String> visibleLines(EndgameTaskTree tree, int maxLines, int maxLineLength, boolean viewerSnapshot) {
        if (tree == null) {
            throw new IllegalArgumentException("tree cannot be null");
        }
        if (maxLines < 2) {
            throw new IllegalArgumentException("maxLines must be at least 2");
        }
        if (maxLineLength < 32) {
            throw new IllegalArgumentException("maxLineLength must be at least 32");
        }
        List<EndgameTaskNode> nodes = new ArrayList<>();
        collect(nodes, tree.root(), 0);
        List<EndgameTaskNode> visibleNodes = prioritizedNodes(tree.root(), nodes, maxLines - 1);
        List<String> lines = new ArrayList<>();
        lines.add(limit("active_task=diagnostic_snapshot status=not_queued source="
                + (viewerSnapshot ? "viewer_diagnostics" : "status_snapshot")
                + " execution=stop_clears_runtime_tasks", maxLineLength));
        for (EndgameTaskNode node : visibleNodes) {
            lines.add(limit("task=" + node.id() + " status=" + node.status().name()
                    + sourceSuffix(node, viewerSnapshot) + " detail=" + compact(node.detail()), maxLineLength));
        }
        if (nodes.size() > visibleNodes.size()) {
            lines.set(lines.size() - 1, limit("truncated=true omitted_nodes=" + (nodes.size() - visibleNodes.size())
                    + " reason=bounded_status_surface", maxLineLength));
        }
        return List.copyOf(lines);
    }

    private static String sourceSuffix(EndgameTaskNode node, boolean viewerSnapshot) {
        if (!viewerSnapshot) {
            return "";
        }
        return switch (node.id()) {
            case "resource_prep", "blaze_resources", "pearl_eye_resources", "food_and_blocks" ->
                    " source=viewer_inventory";
            case "endgame_phase21", "vanilla_nether_prep", "current_dimension_recovery" ->
                    " source=current_viewer_dimension";
            default -> " source=diagnostic_only";
        };
    }

    private static List<EndgameTaskNode> prioritizedNodes(EndgameTaskNode root, List<EndgameTaskNode> nodes, int limit) {
        List<EndgameTaskNode> selected = new ArrayList<>();
        addIfRoom(selected, root, limit);
        addChildIfPresent(selected, root, "resource_prep", limit);
        addChildIfPresent(selected, root, "stronghold_estimation_search", limit);
        addChildIfPresent(selected, root, "end_portal_prep", limit);
        addChildIfPresent(selected, root, "end_travel", limit);
        addChildIfPresent(selected, root, "dragon_fight_primitives", limit);
        addChildIfPresent(selected, root, "current_dimension_recovery", limit);
        for (EndgameTaskNode node : nodes) {
            addIfRoom(selected, node, limit);
        }
        return selected;
    }

    private static void addChildIfPresent(List<EndgameTaskNode> selected, EndgameTaskNode root, String id, int limit) {
        for (EndgameTaskNode child : root.children()) {
            if (child.id().equals(id)) {
                addIfRoom(selected, child, limit);
                return;
            }
        }
    }

    private static void addIfRoom(List<EndgameTaskNode> selected, EndgameTaskNode node, int limit) {
        if (selected.size() >= limit || selected.contains(node)) {
            return;
        }
        selected.add(node);
    }

    private static void collect(List<EndgameTaskNode> nodes, EndgameTaskNode node, int depth) {
        nodes.add(node);
        if (depth >= 2) {
            return;
        }
        for (EndgameTaskNode child : node.children()) {
            collect(nodes, child, depth + 1);
        }
    }

    private static String compact(String value) {
        return value.trim().replace(' ', '_').replace(';', ',');
    }

    private static String limit(String value, int maxLineLength) {
        if (value.length() <= maxLineLength) {
            return value;
        }
        return value.substring(0, maxLineLength - 14) + "... truncated";
    }
}
