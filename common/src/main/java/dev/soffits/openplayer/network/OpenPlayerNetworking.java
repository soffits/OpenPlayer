package dev.soffits.openplayer.network;

import dev.architectury.networking.NetworkManager;
import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.api.AiPlayerNpcService;
import dev.soffits.openplayer.api.AiPlayerNpcSession;
import dev.soffits.openplayer.api.AiPlayerNpcSpec;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.api.NpcProfileSpec;
import dev.soffits.openplayer.api.NpcRoleId;
import dev.soffits.openplayer.api.NpcSpawnLocation;
import dev.soffits.openplayer.api.OpenPlayerApi;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public final class OpenPlayerNetworking {
    private OpenPlayerNetworking() {
    }

    public static void registerServerReceivers() {
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.SPAWN_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveSpawnRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.DESPAWN_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveDespawnRequest
        );
    }

    private static void receiveSpawnRequest(FriendlyByteBuf ignoredBuffer, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                handleSpawnRequest(player);
            }
        });
    }

    private static void receiveDespawnRequest(FriendlyByteBuf ignoredBuffer, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                handleDespawnRequest(player);
            }
        });
    }

    private static void handleSpawnRequest(ServerPlayer sender) {
        NpcSpawnLocation location = new NpcSpawnLocation(
                sender.serverLevel().dimension().location().toString(),
                sender.getX(),
                sender.getY(),
                sender.getZ()
        );
        AiPlayerNpcSpec spec = new AiPlayerNpcSpec(
                new NpcRoleId(OpenPlayerConstants.DEFAULT_NETWORK_NPC_ROLE_ID),
                new NpcOwnerId(sender.getUUID()),
                new NpcProfileSpec(sender.getGameProfile().getName() + OpenPlayerConstants.DEFAULT_NETWORK_NPC_PROFILE_SUFFIX),
                location
        );
        OpenPlayerApi.npcService().spawn(spec);
    }

    private static void handleDespawnRequest(ServerPlayer sender) {
        AiPlayerNpcService service = OpenPlayerApi.npcService();
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (session.spec().ownerId().value().equals(sender.getUUID())) {
                service.despawn(session.sessionId());
            }
        }
    }
}
