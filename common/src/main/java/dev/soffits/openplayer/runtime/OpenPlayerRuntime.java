package dev.soffits.openplayer.runtime;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.soffits.openplayer.OpenPlayerAutomationConfig;
import dev.soffits.openplayer.OpenPlayerIntentParserConfig;
import dev.soffits.openplayer.OpenPlayerRuntimeStatus;
import dev.soffits.openplayer.api.OpenPlayerApi;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.DisabledIntentParser;
import dev.soffits.openplayer.intent.IntentParser;
import dev.soffits.openplayer.registry.OpenPlayerEntityTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class OpenPlayerRuntime {
    private static RuntimeAiPlayerNpcService activeService;
    private static IntentParser activeIntentParser = new DisabledIntentParser();

    private OpenPlayerRuntime() {
    }

    public static void initialize() {
        OpenPlayerEntityTypes.register();
        EntityAttributeRegistry.register(OpenPlayerEntityTypes.AI_PLAYER_NPC, OpenPlayerNpcEntity::createAttributes);
        LifecycleEvent.SERVER_STARTED.register(OpenPlayerRuntime::installServerService);
        LifecycleEvent.SERVER_STOPPING.register(OpenPlayerRuntime::removeServerService);
        PlayerEvent.PLAYER_QUIT.register(OpenPlayerRuntime::stopOwnerRuntime);
    }

    public static OpenPlayerRuntimeStatus status() {
        return new OpenPlayerRuntimeStatus(OpenPlayerIntentParserConfig.status(), OpenPlayerAutomationConfig.status());
    }

    public static IntentParser intentParser() {
        return activeIntentParser;
    }

    public static void reloadIntentParser() {
        activeIntentParser = OpenPlayerIntentParserConfig.createIntentParser();
        if (activeService != null) {
            activeService.updateIntentParser(activeIntentParser);
        }
    }

    private static void installServerService(MinecraftServer server) {
        reloadIntentParser();
        activeService = new RuntimeAiPlayerNpcService(server, activeIntentParser);
        activeService.reattachPersistedNpcs();
        OpenPlayerApi.registerNpcService(activeService);
    }

    private static void removeServerService(MinecraftServer server) {
        if (activeService != null) {
            activeService.clearRuntimeSessions();
            activeService = null;
        }
        activeIntentParser = new DisabledIntentParser();
        OpenPlayerApi.registerUnavailableNpcService();
    }

    private static void stopOwnerRuntime(ServerPlayer player) {
        if (activeService != null) {
            activeService.stopOwnerRuntime(player.getUUID());
        }
    }
}
