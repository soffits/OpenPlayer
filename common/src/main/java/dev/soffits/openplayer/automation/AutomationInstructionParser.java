package dev.soffits.openplayer.automation;

public final class AutomationInstructionParser {
    private static final int MAX_ID_LENGTH = 96;

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

    public static GotoInstruction parseGotoInstructionOrNull(String instruction, double defaultRadius, double maxRadius) {
        if (instruction == null || !Double.isFinite(defaultRadius) || !Double.isFinite(maxRadius)
                || defaultRadius <= 0.0D || maxRadius <= 0.0D) {
            return null;
        }
        String trimmedInstruction = instruction.trim();
        Coordinate coordinate = parseCoordinateOrNull(trimmedInstruction);
        if (coordinate != null) {
            return GotoInstruction.coordinate(coordinate);
        }
        if (trimmedInstruction.equals("owner")) {
            return GotoInstruction.owner();
        }
        String[] parts = trimmedInstruction.split("\\s+");
        if (parts.length < 2 || parts.length > 3) {
            return null;
        }
        GotoTargetKind targetKind;
        if (parts[0].equals("block")) {
            targetKind = GotoTargetKind.BLOCK;
        } else if (parts[0].equals("entity")) {
            targetKind = GotoTargetKind.ENTITY;
        } else {
            return null;
        }
        if (!isValidResourceId(parts[1])) {
            return null;
        }
        double radius = defaultRadius;
        if (parts.length == 3) {
            radius = parseOptionalRadiusOrNegative(parts[2], defaultRadius, maxRadius);
            if (radius < 0.0D) {
                return null;
            }
        }
        return new GotoInstruction(targetKind, null, parts[1], radius);
    }

    public static boolean isValidResourceId(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_ID_LENGTH) {
            return false;
        }
        int namespaceSeparator = value.indexOf(':');
        if (namespaceSeparator <= 0 || namespaceSeparator == value.length() - 1
                || value.indexOf(':', namespaceSeparator + 1) >= 0) {
            return false;
        }
        return isValidResourceNamespace(value.substring(0, namespaceSeparator))
                && isValidResourcePath(value.substring(namespaceSeparator + 1));
    }

    private static boolean isValidResourceNamespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == '-' || character == '.')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidResourcePath(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == '-' || character == '.' || character == '/')) {
                return false;
            }
        }
        return true;
    }

    public record Coordinate(double x, double y, double z) {
    }

    public enum GotoTargetKind {
        COORDINATE,
        OWNER,
        BLOCK,
        ENTITY
    }

    public record GotoInstruction(GotoTargetKind kind, Coordinate coordinate, String resourceId, double radius) {
        public GotoInstruction {
            if (kind == null) {
                throw new IllegalArgumentException("kind cannot be null");
            }
            if (kind == GotoTargetKind.COORDINATE && coordinate == null) {
                throw new IllegalArgumentException("coordinate cannot be null for coordinate goto");
            }
            if ((kind == GotoTargetKind.BLOCK || kind == GotoTargetKind.ENTITY) && !isValidResourceId(resourceId)) {
                throw new IllegalArgumentException("resourceId must be a valid namespaced id for loaded-area goto");
            }
            if (!Double.isFinite(radius) || radius < 0.0D) {
                throw new IllegalArgumentException("radius must be finite and non-negative");
            }
        }

        public static GotoInstruction coordinate(Coordinate coordinate) {
            return new GotoInstruction(GotoTargetKind.COORDINATE, coordinate, null, 0.0D);
        }

        public static GotoInstruction owner() {
            return new GotoInstruction(GotoTargetKind.OWNER, null, null, 0.0D);
        }
    }
}
