package dev.soffits.openplayer.network;

import dev.architectury.networking.NetworkManager;
import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.OpenPlayerRuntimeStatus;
import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.AiPlayerNpcService;
import dev.soffits.openplayer.api.AiPlayerNpcSession;
import dev.soffits.openplayer.api.AiPlayerNpcSpec;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.api.NpcProfileSpec;
import dev.soffits.openplayer.api.NpcRoleId;
import dev.soffits.openplayer.api.NpcSpawnLocation;
import dev.soffits.openplayer.api.OpenPlayerApi;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentPriority;
import dev.soffits.openplayer.runtime.OpenPlayerRuntime;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public final class OpenPlayerNetworking {
    private static final int MAX_COMMAND_TEXT_LENGTH = 512;

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
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.FOLLOW_OWNER_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveFollowOwnerRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.STOP_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveStopRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.COMMAND_TEXT_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveCommandTextRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.STATUS_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveStatusRequest
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

    private static void receiveFollowOwnerRequest(FriendlyByteBuf ignoredBuffer, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                submitDefaultNetworkNpcCommand(player, IntentKind.FOLLOW_OWNER);
            }
        });
    }

    private static void receiveStopRequest(FriendlyByteBuf ignoredBuffer, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                submitDefaultNetworkNpcCommand(player, IntentKind.STOP);
            }
        });
    }

    private static void receiveCommandTextRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String commandText = buffer.readUtf(MAX_COMMAND_TEXT_LENGTH);
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                submitDefaultNetworkNpcCommandText(player, commandText);
            }
        });
    }

    private static void receiveStatusRequest(FriendlyByteBuf ignoredBuffer, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                sendStatusResponse(player);
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

    private static void submitDefaultNetworkNpcCommand(ServerPlayer sender, IntentKind intentKind) {
        AiPlayerNpcService service = OpenPlayerApi.npcService();
        AiPlayerNpcCommand command = new AiPlayerNpcCommand(
                UUID.randomUUID(),
                new CommandIntent(intentKind, IntentPriority.HIGH, intentKind.name())
        );
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (isSenderDefaultNetworkNpcSession(sender, session)) {
                service.submitCommand(session.sessionId(), command);
            }
        }
    }

    private static void submitDefaultNetworkNpcCommandText(ServerPlayer sender, String commandText) {
        if (commandText == null || commandText.isBlank() || commandText.length() > MAX_COMMAND_TEXT_LENGTH) {
            return;
        }
        AiPlayerNpcService service = OpenPlayerApi.npcService();
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (isSenderDefaultNetworkNpcSession(sender, session)) {
                service.submitCommandText(session.sessionId(), commandText);
            }
        }
    }

    private static void sendStatusResponse(ServerPlayer player) {
        OpenPlayerRuntimeStatus status = OpenPlayerRuntime.status();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBoolean(status.intentParser().enabled());
        buffer.writeUtf(status.intentParser().endpointStatus(), 128);
        buffer.writeBoolean(status.intentParser().modelConfigured());
        buffer.writeBoolean(status.intentParser().apiKeyPresent());
        buffer.writeUtf(status.automationBackend().name(), 64);
        buffer.writeUtf(status.automationBackend().state().name(), 64);
        NetworkManager.sendToPlayer(player, OpenPlayerConstants.STATUS_RESPONSE_PACKET_ID, buffer);
    }

    private static boolean isSenderDefaultNetworkNpcSession(ServerPlayer sender, AiPlayerNpcSession session) {
        return session.spec().ownerId().value().equals(sender.getUUID())
                && session.spec().roleId().value().equals(OpenPlayerConstants.DEFAULT_NETWORK_NPC_ROLE_ID);
    }
}
