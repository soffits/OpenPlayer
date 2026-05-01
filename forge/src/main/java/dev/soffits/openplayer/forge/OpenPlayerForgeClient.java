package dev.soffits.openplayer.forge;

import dev.soffits.openplayer.OpenPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class OpenPlayerForgeClient {
    private OpenPlayerForgeClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(OpenPlayerForgeClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        OpenPlayer.initializeClient();
    }
}
