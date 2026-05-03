package dev.soffits.openplayer.automation.building;

public record BuildSize(int x, int y, int z) {
    public BuildSize {
        if (x < 1 || y < 1 || z < 1) {
            throw new IllegalArgumentException("size dimensions must be positive");
        }
    }
}
