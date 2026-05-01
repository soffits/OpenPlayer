package dev.soffits.openplayer.fabric;

import dev.soffits.openplayer.OpenPlayer;
import net.fabricmc.api.ModInitializer;

public final class OpenPlayerFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        OpenPlayer.initialize();
    }
}
