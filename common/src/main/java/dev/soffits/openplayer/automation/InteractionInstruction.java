package dev.soffits.openplayer.automation;

import java.util.UUID;

public record InteractionInstruction(InteractionTargetKind kind, AutomationInstructionParser.Coordinate coordinate,
                                     String targetId, double radius) {
    public InteractionInstruction {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        if (kind == InteractionTargetKind.BLOCK && coordinate == null) {
            throw new IllegalArgumentException("coordinate cannot be null for block interaction");
        }
        if (kind == InteractionTargetKind.ENTITY && !TargetAttackInstructionParser.isValidEntityTarget(targetId)) {
            throw new IllegalArgumentException("targetId must be a valid entity type id or UUID");
        }
        if (!Double.isFinite(radius) || radius < 0.0D) {
            throw new IllegalArgumentException("radius must be finite and non-negative");
        }
    }

    public enum InteractionTargetKind {
        BLOCK,
        ENTITY
    }

    public boolean targetsUuid() {
        return targetUuid() != null;
    }

    public UUID targetUuid() {
        if (targetId == null) {
            return null;
        }
        try {
            return UUID.fromString(targetId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
