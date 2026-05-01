package dev.soffits.openplayer.fabric;

import dev.soffits.openplayer.OpenPlayer;
import net.fabricmc.api.ClientModInitializer;

public final class OpenPlayerFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OpenPlayer.initializeClient();
    }
}
