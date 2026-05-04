package dev.soffits.openplayer.automation.advanced;

import dev.soffits.openplayer.automation.AutomationInstructionParser;

public final class AdvancedTaskInstructionParser {
    public static final String LOCATE_LOADED_BLOCK_USAGE =
            "LOCATE_LOADED_BLOCK requires instruction: <block_or_item_id> [radius]";
    public static final String LOCATE_LOADED_ENTITY_USAGE =
            "LOCATE_LOADED_ENTITY requires instruction: <entity_type_id> [radius]";
    public static final String FIND_LOADED_BIOME_USAGE =
            "FIND_LOADED_BIOME requires instruction: <biome_id> [radius]";
    public static final String LOCATE_STRUCTURE_USAGE =
            "LOCATE_STRUCTURE requires instruction: <structure_id> [radius] [source=loaded]";
    public static final String EXPLORE_CHUNKS_USAGE =
            "EXPLORE_CHUNKS requires blank, reset, clear, or instruction: radius=<blocks> steps=<count>";
    public static final String USE_PORTAL_USAGE =
            "USE_PORTAL requires blank or instruction: radius=<blocks> target=<minecraft:the_nether|minecraft:overworld> build=<true|false>";
    public static final String TRAVEL_NETHER_USAGE =
            "TRAVEL_NETHER requires blank or instruction: radius=<blocks> build=<true|false>";
    public static final String LOCATE_STRONGHOLD_USAGE =
            "LOCATE_STRONGHOLD requires blank or instruction: source=diagnostic";
    public static final String END_GAME_TASK_USAGE =
            "END_GAME_TASK requires blank or instruction: plan, prepare, stronghold, portal, travel, dragon, or recovery";
    public static final double DEFAULT_RADIUS = 16.0D;
    public static final double MAX_RADIUS = 32.0D;
    public static final double PORTAL_DEFAULT_RADIUS = 16.0D;
    public static final double PORTAL_MAX_RADIUS = 24.0D;
    public static final double EXPLORE_DEFAULT_RADIUS = 32.0D;
    public static final double EXPLORE_MAX_RADIUS = 64.0D;
    public static final double STRUCTURE_DEFAULT_RADIUS = 32.0D;
    public static final double STRUCTURE_MAX_RADIUS = 64.0D;
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

    public static LocateStructureInstruction parseLocateStructureOrNull(String instruction) {
        if (instruction == null) {
            return null;
        }
        String trimmedInstruction = instruction.trim();
        if (trimmedInstruction.isEmpty()) {
            return null;
        }
        String[] parts = trimmedInstruction.split("\\s+");
        if (parts.length < 1 || parts.length > 3 || !AutomationInstructionParser.isValidResourceId(parts[0])) {
            return null;
        }
        double radius = STRUCTURE_DEFAULT_RADIUS;
        boolean sourceSeen = false;
        boolean radiusSeen = false;
        for (int index = 1; index < parts.length; index++) {
            String part = parts[index];
            if (part.equals("source=loaded")) {
                if (sourceSeen) {
                    return null;
                }
                sourceSeen = true;
            } else {
                if (radiusSeen) {
                    return null;
                }
                radius = AutomationInstructionParser.parseOptionalRadiusOrNegative(
                        part, STRUCTURE_DEFAULT_RADIUS, STRUCTURE_MAX_RADIUS
                );
                if (radius < 0.0D) {
                    return null;
                }
                radiusSeen = true;
            }
        }
        return new LocateStructureInstruction(parts[0], radius);
    }

    public static PortalTravelInstruction parseUsePortalOrNull(String instruction) {
        return parsePortalTravelOrNull(instruction, false);
    }

    public static PortalTravelInstruction parseTravelNetherOrNull(String instruction) {
        return parsePortalTravelOrNull(instruction, true);
    }

    public static StrongholdDiagnosticInstruction parseLocateStrongholdOrNull(String instruction) {
        if (instruction == null || instruction.trim().isEmpty() || instruction.trim().equals("source=diagnostic")) {
            return new StrongholdDiagnosticInstruction();
        }
        return null;
    }

    public static EndGameTaskInstruction parseEndGameTaskOrNull(String instruction) {
        if (instruction == null || instruction.trim().isEmpty()) {
            return new EndGameTaskInstruction("plan");
        }
        String trimmedInstruction = instruction.trim();
        return switch (trimmedInstruction) {
            case "plan", "prepare", "stronghold", "portal", "travel", "dragon", "recovery" ->
                    new EndGameTaskInstruction(trimmedInstruction);
            default -> null;
        };
    }

    private static PortalTravelInstruction parsePortalTravelOrNull(String instruction, boolean travelNether) {
        double radius = PORTAL_DEFAULT_RADIUS;
        String targetDimensionId = travelNether ? "minecraft:the_nether" : null;
        Boolean build = travelNether ? null : Boolean.FALSE;
        boolean radiusSeen = false;
        boolean targetSeen = false;
        boolean buildSeen = false;
        if (instruction != null && !instruction.trim().isEmpty()) {
            String[] parts = instruction.trim().split("\\s+");
            for (String part : parts) {
                int separator = part.indexOf('=');
                if (separator <= 0 || separator == part.length() - 1) {
                    return null;
                }
                String key = part.substring(0, separator);
                String value = part.substring(separator + 1);
                if (key.equals("radius")) {
                    if (radiusSeen) {
                        return null;
                    }
                    radius = parsePortalRadiusOrNegative(value);
                    if (radius < 0.0D) {
                        return null;
                    }
                    radiusSeen = true;
                } else if (key.equals("target") && !travelNether) {
                    if (targetSeen || !isSupportedPortalDimension(value)) {
                        return null;
                    }
                    targetDimensionId = value;
                    targetSeen = true;
                } else if (key.equals("build")) {
                    if (buildSeen) {
                        return null;
                    }
                    build = parseBooleanOrNull(value);
                    if (build == null) {
                        return null;
                    }
                    buildSeen = true;
                } else {
                    return null;
                }
            }
        }
        return new PortalTravelInstruction(radius, targetDimensionId, targetSeen, build, buildSeen, travelNether);
    }

    private static boolean isSupportedPortalDimension(String value) {
        return value.equals("minecraft:the_nether") || value.equals("minecraft:overworld");
    }

    private static Boolean parseBooleanOrNull(String value) {
        if (value.equals("true")) {
            return Boolean.TRUE;
        }
        if (value.equals("false")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static double parsePortalRadiusOrNegative(String value) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed <= 0.0D || parsed > PORTAL_MAX_RADIUS) {
                return -1.0D;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            return -1.0D;
        }
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

    public record LocateStructureInstruction(String structureId, double radius) {
        public LocateStructureInstruction {
            if (!AutomationInstructionParser.isValidResourceId(structureId)) {
                throw new IllegalArgumentException("structureId must be a valid namespaced id");
            }
            if (!Double.isFinite(radius) || radius <= 0.0D || radius > STRUCTURE_MAX_RADIUS) {
                throw new IllegalArgumentException("radius must be finite and within loaded structure diagnostic bounds");
            }
        }
    }

    public record PortalTravelInstruction(
            double radius,
            String targetDimensionId,
            boolean explicitTarget,
            Boolean build,
            boolean explicitBuild,
            boolean travelNether
    ) {
        public PortalTravelInstruction {
            if (!Double.isFinite(radius) || radius <= 0.0D || radius > PORTAL_MAX_RADIUS) {
                throw new IllegalArgumentException("radius must be finite and within portal travel bounds");
            }
            if (targetDimensionId != null && !isSupportedPortalDimension(targetDimensionId)) {
                throw new IllegalArgumentException("targetDimensionId must be a supported portal dimension");
            }
        }
    }

    public record StrongholdDiagnosticInstruction() {
    }

    public record EndGameTaskInstruction(String phase) {
        public EndGameTaskInstruction {
            if (phase == null || phase.isBlank()) {
                throw new IllegalArgumentException("phase cannot be blank");
            }
        }
    }
}
