package dev.soffits.openplayer.automation.navigation;

public record NavigationTarget(
        NavigationTargetKind kind,
        String summary
) {
    public NavigationTarget {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        summary = NavigationSnapshot.boundedStatus(summary);
    }

    public static NavigationTarget none() {
        return new NavigationTarget(NavigationTargetKind.NONE, "none");
    }

    public static NavigationTarget position(double x, double y, double z) {
        return new NavigationTarget(NavigationTargetKind.POSITION, coordinateSummary("pos", x, y, z));
    }

    public static NavigationTarget block(int x, int y, int z) {
        return new NavigationTarget(NavigationTargetKind.BLOCK, "block(" + x + ',' + y + ',' + z + ")");
    }

    public static NavigationTarget entity(String typeId) {
        return new NavigationTarget(NavigationTargetKind.ENTITY, "entity(" + safeId(typeId) + ")");
    }

    public static NavigationTarget owner() {
        return new NavigationTarget(NavigationTargetKind.OWNER, "owner");
    }

    private static String coordinateSummary(String prefix, double x, double y, double z) {
        return prefix + '(' + formatCoordinate(x) + ',' + formatCoordinate(y) + ',' + formatCoordinate(z) + ')';
    }

    private static String formatCoordinate(double value) {
        if (!Double.isFinite(value)) {
            return "nan";
        }
        return String.valueOf(Math.round(value * 10.0D) / 10.0D);
    }

    private static String safeId(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length() && builder.length() < 48; index++) {
            char character = value.charAt(index);
            if ((character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == ':' || character == '/' || character == '.' || character == '-') {
                builder.append(character);
            } else {
                builder.append('_');
            }
        }
        return builder.isEmpty() ? "unknown" : builder.toString();
    }
}
