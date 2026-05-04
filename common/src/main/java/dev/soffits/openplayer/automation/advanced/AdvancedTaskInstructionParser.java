package dev.soffits.openplayer.automation.advanced;

import dev.soffits.openplayer.automation.AutomationInstructionParser;

public final class AdvancedTaskInstructionParser {
    public static final String LOCATE_LOADED_BLOCK_USAGE =
            "LOCATE_LOADED_BLOCK requires instruction: <block_or_item_id> [radius]";
    public static final String LOCATE_LOADED_ENTITY_USAGE =
            "LOCATE_LOADED_ENTITY requires instruction: <entity_type_id> [radius]";
    public static final String FIND_LOADED_BIOME_USAGE =
            "FIND_LOADED_BIOME requires instruction: <biome_id> [radius]";
    public static final String EXPLORE_CHUNKS_USAGE =
            "EXPLORE_CHUNKS requires blank, reset, clear, or instruction: radius=<blocks> steps=<count>";
    public static final double DEFAULT_RADIUS = 16.0D;
    public static final double MAX_RADIUS = 32.0D;
    public static final double EXPLORE_DEFAULT_RADIUS = 32.0D;
    public static final double EXPLORE_MAX_RADIUS = 64.0D;
    public static final int EXPLORE_DEFAULT_STEPS = 1;
    public static final int EXPLORE_MAX_STEPS = 8;

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

    public static ExploreChunksInstruction parseExploreChunksOrNull(String instruction) {
        if (instruction == null || instruction.trim().isEmpty()) {
            return ExploreChunksInstruction.navigate(EXPLORE_DEFAULT_RADIUS, EXPLORE_DEFAULT_STEPS);
        }
        String trimmedInstruction = instruction.trim();
        if (trimmedInstruction.equals("reset") || trimmedInstruction.equals("clear")) {
            return ExploreChunksInstruction.reset();
        }
        double radius = EXPLORE_DEFAULT_RADIUS;
        int steps = EXPLORE_DEFAULT_STEPS;
        String[] parts = trimmedInstruction.split("\\s+");
        for (String part : parts) {
            int separator = part.indexOf('=');
            if (separator <= 0 || separator == part.length() - 1) {
                return null;
            }
            String key = part.substring(0, separator);
            String value = part.substring(separator + 1);
            if (key.equals("radius")) {
                radius = AutomationInstructionParser.parseOptionalRadiusOrNegative(
                        value, EXPLORE_DEFAULT_RADIUS, EXPLORE_MAX_RADIUS
                );
                if (radius < 0.0D) {
                    return null;
                }
            } else if (key.equals("steps")) {
                steps = parseStepsOrNegative(value);
                if (steps < 0) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return ExploreChunksInstruction.navigate(radius, steps);
    }

    private static int parseStepsOrNegative(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                return -1;
            }
            return Math.min(parsed, EXPLORE_MAX_STEPS);
        } catch (NumberFormatException exception) {
            return -1;
        }
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

    public record ExploreChunksInstruction(double radius, int steps, boolean resetOnly) {
        public ExploreChunksInstruction {
            if (!resetOnly && (!Double.isFinite(radius) || radius <= 0.0D || radius > EXPLORE_MAX_RADIUS)) {
                throw new IllegalArgumentException("radius must be finite and within loaded exploration bounds");
            }
            if (!resetOnly && (steps <= 0 || steps > EXPLORE_MAX_STEPS)) {
                throw new IllegalArgumentException("steps must be within loaded exploration bounds");
            }
        }

        public static ExploreChunksInstruction navigate(double radius, int steps) {
            return new ExploreChunksInstruction(radius, steps, false);
        }

        public static ExploreChunksInstruction reset() {
            return new ExploreChunksInstruction(0.0D, 0, true);
        }
    }
}
