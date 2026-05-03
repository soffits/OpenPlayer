package dev.soffits.openplayer.automation.advanced;

import dev.soffits.openplayer.automation.AutomationInstructionParser;

public final class AdvancedTaskInstructionParser {
    public static final String LOCATE_LOADED_BLOCK_USAGE =
            "LOCATE_LOADED_BLOCK requires instruction: <block_or_item_id> [radius]";
    public static final String LOCATE_LOADED_ENTITY_USAGE =
            "LOCATE_LOADED_ENTITY requires instruction: <entity_type_id> [radius]";
    public static final String FIND_LOADED_BIOME_USAGE =
            "FIND_LOADED_BIOME requires instruction: <biome_id> [radius]";
    public static final double DEFAULT_RADIUS = 16.0D;
    public static final double MAX_RADIUS = 32.0D;

    private AdvancedTaskInstructionParser() {
    }

    public static LoadedSearchInstruction parseLoadedSearchOrNull(String instruction) {
        if (instruction == null) {
            return null;
        }
        String trimmedInstruction = instruction.trim();
        if (trimmedInstruction.isEmpty()) {
            return null;
        }
        String[] parts = trimmedInstruction.split("\\s+");
        if (parts.length < 1 || parts.length > 2 || !AutomationInstructionParser.isValidResourceId(parts[0])) {
            return null;
        }
        double radius = DEFAULT_RADIUS;
        if (parts.length == 2) {
            radius = AutomationInstructionParser.parseOptionalRadiusOrNegative(parts[1], DEFAULT_RADIUS, MAX_RADIUS);
            if (radius < 0.0D) {
                return null;
            }
        }
        return new LoadedSearchInstruction(parts[0], radius);
    }

    public record LoadedSearchInstruction(String resourceId, double radius) {
        public LoadedSearchInstruction {
            if (!AutomationInstructionParser.isValidResourceId(resourceId)) {
                throw new IllegalArgumentException("resourceId must be a valid namespaced id");
            }
            if (!Double.isFinite(radius) || radius <= 0.0D || radius > MAX_RADIUS) {
                throw new IllegalArgumentException("radius must be finite and within loaded reconnaissance bounds");
            }
        }
    }
}
