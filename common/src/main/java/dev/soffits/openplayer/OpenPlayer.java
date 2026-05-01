package dev.soffits.openplayer;

import dev.soffits.openplayer.api.OpenPlayerApi;
import dev.soffits.openplayer.runtime.OpenPlayerRuntime;

public final class OpenPlayer {
    private static boolean initialized;
    private static boolean clientInitialized;

    private OpenPlayer() {
    }

    public static void initialize() {
        OpenPlayerApi.registerUnavailableNpcService();
        OpenPlayerRuntime.initialize();
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
