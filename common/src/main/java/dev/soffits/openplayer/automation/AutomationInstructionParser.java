package dev.soffits.openplayer.automation;

public final class AutomationInstructionParser {
    private AutomationInstructionParser() {
    }

    public static Coordinate parseCoordinate(String instruction) {
        if (instruction == null) {
            throw new IllegalArgumentException("Instruction must contain three coordinates: x y z");
        }
        String trimmedInstruction = instruction.trim();
        String[] parts = trimmedInstruction.split("\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Instruction must contain three coordinates: x y z");
        }
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Coordinates must be finite numbers");
            }
            return new Coordinate(x, y, z);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Coordinates must be numbers", exception);
        }
    }

    public static Coordinate parseCoordinateOrNull(String instruction) {
        try {
            return parseCoordinate(instruction);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static boolean isBlankInstruction(String instruction) {
        return instruction == null || instruction.trim().isEmpty();
    }

    public static Coordinate parseBoundedCoordinateOrNull(String instruction,
                                                          double originX,
                                                          double originY,
                                                          double originZ,
                                                          double maxDistance) {
        Coordinate coordinate = parseCoordinateOrNull(instruction);
        if (coordinate == null || !Double.isFinite(maxDistance) || maxDistance <= 0.0D) {
            return null;
        }
        double deltaX = coordinate.x() - originX;
        double deltaY = coordinate.y() - originY;
        double deltaZ = coordinate.z() - originZ;
        if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > maxDistance * maxDistance) {
            return null;
        }
        return coordinate;
    }

    public static double parseOptionalRadiusOrNegative(String instruction, double defaultRadius, double maxRadius) {
        if (instruction == null) {
            return -1.0D;
        }
        String trimmedInstruction = instruction.trim();
        if (trimmedInstruction.isEmpty()) {
            return defaultRadius;
        }
        try {
            double radius = Double.parseDouble(trimmedInstruction);
            if (!Double.isFinite(radius) || radius <= 0.0D) {
                return -1.0D;
            }
            return Math.min(radius, maxRadius);
        } catch (NumberFormatException exception) {
            return -1.0D;
        }
    }

    public record Coordinate(double x, double y, double z) {
    }
}
