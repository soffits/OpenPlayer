package dev.soffits.openplayer.automation;

import java.util.UUID;

public record TargetAttackInstruction(String targetId, UUID targetUuid, double radius) {
    public TargetAttackInstruction {
        if (!TargetAttackInstructionParser.isValidEntityTarget(targetId)) {
            throw new IllegalArgumentException("targetId must be a valid entity type id or UUID");
        }
        if (!Double.isFinite(radius) || radius <= 0.0D) {
            throw new IllegalArgumentException("radius must be finite and positive");
        }
    }

    public boolean targetsUuid() {
        return targetUuid != null;
    }
}
