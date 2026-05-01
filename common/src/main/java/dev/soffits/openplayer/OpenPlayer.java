package dev.soffits.openplayer;

import dev.soffits.openplayer.api.OpenPlayerApi;

public final class OpenPlayer {
    private static boolean initialized;
    private static boolean clientInitialized;

    private OpenPlayer() {
    }

    public static void initialize() {
        OpenPlayerApi.registerUnavailableNpcService();
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
