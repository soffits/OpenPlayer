package dev.soffits.openplayer;

public final class OpenPlayer {
    private static boolean initialized;
    private static boolean clientInitialized;

    private OpenPlayer() {
    }

    public static void initialize() {
        initialized = true;
    }

    public static void initializeClient() {
        clientInitialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isClientInitialized() {
        return clientInitialized;
    }
}
