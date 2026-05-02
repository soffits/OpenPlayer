package dev.soffits.openplayer.client;

import dev.architectury.networking.NetworkManager;
import dev.soffits.openplayer.OpenPlayerConstants;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public final class OpenPlayerRequestSender {
    private OpenPlayerRequestSender() {
    }

    public static void sendSpawnRequest() {
        NetworkManager.sendToServer(OpenPlayerConstants.SPAWN_REQUEST_PACKET_ID, emptyPayload());
    }

    public static void sendDespawnRequest() {
        NetworkManager.sendToServer(OpenPlayerConstants.DESPAWN_REQUEST_PACKET_ID, emptyPayload());
    }

    public static void sendFollowOwnerRequest() {
        NetworkManager.sendToServer(OpenPlayerConstants.FOLLOW_OWNER_REQUEST_PACKET_ID, emptyPayload());
    }

    public static void sendStopRequest() {
        NetworkManager.sendToServer(OpenPlayerConstants.STOP_REQUEST_PACKET_ID, emptyPayload());
    }

    private static FriendlyByteBuf emptyPayload() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }
}
