package dev.soffits.openplayer.client;

public final class LocalSkinImageValidator {
    private LocalSkinImageValidator() {
    }

    public static boolean isSupportedPlayerSkinSize(int width, int height) {
        return width == 64 && (height == 32 || height == 64);
    }
}
