package dev.soffits.openplayer.automation;

public final class InteractionInstructionParser {
    public static final String USAGE = "INTERACT requires instruction: block <x> <y> <z> or entity <entity_type_or_uuid> [radius]";
    public static final double DEFAULT_RADIUS = 4.0D;
    public static final double MAX_RADIUS = 8.0D;

    private InteractionInstructionParser() {
    }

    public static InteractionInstruction parseOrNull(String instruction) {
        if (instruction == null) {
            return null;
        }
        String[] parts = instruction.trim().split("\\s+");
        if (parts.length == 4 && parts[0].equals("block")) {
            AutomationInstructionParser.Coordinate coordinate = AutomationInstructionParser.parseCoordinateOrNull(
                    parts[1] + " " + parts[2] + " " + parts[3]
            );
            if (coordinate == null) {
                return null;
            }
            return new InteractionInstruction(
                    InteractionInstruction.InteractionTargetKind.BLOCK, coordinate, null, 0.0D
            );
        }
        if ((parts.length == 2 || parts.length == 3) && parts[0].equals("entity")) {
            if (!TargetAttackInstructionParser.isValidEntityTarget(parts[1])) {
                return null;
            }
            double radius = TargetAttackInstructionParser.parseRadiusOrNegative(
                    parts.length == 3 ? parts[2] : "", DEFAULT_RADIUS, MAX_RADIUS
            );
            if (radius < 0.0D) {
                return null;
            }
            return new InteractionInstruction(
                    InteractionInstruction.InteractionTargetKind.ENTITY, null, parts[1], radius
            );
        }
        return null;
    }
}
