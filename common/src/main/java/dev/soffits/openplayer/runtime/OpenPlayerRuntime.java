package dev.soffits.openplayer.runtime;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.soffits.openplayer.api.OpenPlayerApi;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.registry.OpenPlayerEntityTypes;
import net.minecraft.server.MinecraftServer;

public final class OpenPlayerRuntime {
    private static RuntimeAiPlayerNpcService activeService;

    private OpenPlayerRuntime() {
    }

    public static void initialize() {
        OpenPlayerEntityTypes.register();
        EntityAttributeRegistry.register(OpenPlayerEntityTypes.AI_PLAYER_NPC, OpenPlayerNpcEntity::createAttributes);
        LifecycleEvent.SERVER_STARTED.register(OpenPlayerRuntime::installServerService);
        LifecycleEvent.SERVER_STOPPING.register(OpenPlayerRuntime::removeServerService);
    }

    private static void installServerService(MinecraftServer server) {
        activeService = new RuntimeAiPlayerNpcService(server);
        OpenPlayerApi.registerNpcService(activeService);
    }

    private static void removeServerService(MinecraftServer server) {
        if (activeService != null) {
            activeService.despawnAll();
            activeService = null;
        }
        OpenPlayerApi.registerUnavailableNpcService();
    }
}
