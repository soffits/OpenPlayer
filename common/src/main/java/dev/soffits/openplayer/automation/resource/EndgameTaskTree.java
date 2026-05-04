package dev.soffits.openplayer.automation.resource;

import java.util.ArrayList;
import java.util.List;

public record EndgameTaskTree(EndgameTaskNode root) {
    private static final int MAX_SUMMARY_NODES = 32;

    public EndgameTaskTree {
        if (root == null) {
            throw new IllegalArgumentException("root cannot be null");
        }
    }

    public String boundedSummary() {
        List<String> parts = new ArrayList<>();
        append(parts, root, 0);
        return String.join(";", parts);
    }

    private static void append(List<String> parts, EndgameTaskNode node, int depth) {
        if (parts.size() >= MAX_SUMMARY_NODES) {
            return;
        }
        parts.add("node=" + node.id() + " status=" + node.status().name() + " detail=" + compact(node.detail()));
        if (depth >= 2) {
            return;
        }
        for (EndgameTaskNode child : node.children()) {
            append(parts, child, depth + 1);
        }
    }

    private static String compact(String value) {
        return value.trim().replace(' ', '_').replace(';', ',');
    }
}
