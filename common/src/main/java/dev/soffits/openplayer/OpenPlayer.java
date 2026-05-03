package dev.soffits.openplayer;

import dev.soffits.openplayer.api.OpenPlayerApi;
import dev.soffits.openplayer.character.OpenPlayerLocalCharacters;
import dev.soffits.openplayer.client.OpenPlayerClient;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.debug.OpenPlayerRawTrace;
import dev.soffits.openplayer.network.OpenPlayerNetworking;
import dev.soffits.openplayer.runtime.OpenPlayerRuntime;

public final class OpenPlayer {
    private static boolean initialized;
    private static boolean clientInitialized;

    private OpenPlayer() {
    }

    public static void initialize() {
        OpenPlayerApi.registerUnavailableNpcService();
        OpenPlayerDebugEvents.configureLogDirectory(OpenPlayerLocalCharacters.openPlayerDirectory().resolve("logs"));
        OpenPlayerRawTrace.configureLogDirectory(OpenPlayerLocalCharacters.openPlayerDirectory().resolve("logs"));
        OpenPlayerRuntime.initialize();
        OpenPlayerCommands.register();
        OpenPlayerNetworking.registerServerReceivers();
        initialized = true;
    }

    public static void initializeClient() {
        OpenPlayerClient.initialize();
        clientInitialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isClientInitialized() {
        return clientInitialized;
    }
}
