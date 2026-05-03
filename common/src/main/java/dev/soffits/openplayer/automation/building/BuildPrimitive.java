package dev.soffits.openplayer.automation.building;

public enum BuildPrimitive {
    LINE,
    WALL,
    FLOOR,
    BOX,
    STAIRS;

    static BuildPrimitive parseOrNull(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "line" -> LINE;
            case "wall" -> WALL;
            case "floor" -> FLOOR;
            case "box" -> BOX;
            case "stairs" -> STAIRS;
            default -> null;
        };
    }
}
