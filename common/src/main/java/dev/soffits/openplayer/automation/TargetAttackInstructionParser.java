package dev.soffits.openplayer.automation;

import java.util.UUID;

public final class TargetAttackInstructionParser {
    public static final String USAGE = "ATTACK_TARGET requires instruction: [entity] <entity_type_or_uuid> [radius]";
    public static final double DEFAULT_RADIUS = 12.0D;
    public static final double MAX_RADIUS = 24.0D;

    private TargetAttackInstructionParser() {
    }

    public static TargetAttackInstruction parseOrNull(String instruction) {
        if (instruction == null) {
            return null;
        }
        String[] parts = instruction.trim().split("\\s+");
        int targetIndex = 0;
        if (parts.length >= 1 && parts[0].equals("entity")) {
            targetIndex = 1;
        }
        int remaining = parts.length - targetIndex;
        if (remaining < 1 || remaining > 2) {
            return null;
        }
        String targetId = parts[targetIndex];
        if (!isValidEntityTarget(targetId)) {
            return null;
        }
        double radius = parseRadiusOrNegative(
                remaining == 2 ? parts[targetIndex + 1] : "", DEFAULT_RADIUS, MAX_RADIUS
        );
        if (radius < 0.0D) {
            return null;
        }
        return new TargetAttackInstruction(targetId, uuidOrNull(targetId), radius);
    }

    static boolean isValidEntityTarget(String targetId) {
        return AutomationInstructionParser.isValidResourceId(targetId) || uuidOrNull(targetId) != null;
    }

    static double parseRadiusOrNegative(String instruction, double defaultRadius, double maxRadius) {
        if (instruction == null || instruction.isBlank()) {
            return defaultRadius;
        }
        try {
            double radius = Double.parseDouble(instruction.trim());
            if (!Double.isFinite(radius) || radius <= 0.0D) {
                return -1.0D;
            }
            return Math.min(radius, maxRadius);
        } catch (NumberFormatException exception) {
            return -1.0D;
        }
    }

    private static UUID uuidOrNull(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
