package dev.soffits.openplayer.automation.resource;

import java.util.List;

public record EndgameTaskNode(
        String id,
        EndgameTaskStatus status,
        String detail,
        List<EndgameTaskNode> children
) {
    public EndgameTaskNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        detail = detail == null || detail.isBlank() ? "none" : detail.trim();
        children = children == null ? List.of() : List.copyOf(children);
    }

    public static EndgameTaskNode leaf(String id, EndgameTaskStatus status, String detail) {
        return new EndgameTaskNode(id, status, detail, List.of());
    }
}
