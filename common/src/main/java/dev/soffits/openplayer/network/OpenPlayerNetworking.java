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
import dev.soffits.openplayer.character.LocalCharacterDefinition;
import dev.soffits.openplayer.character.LocalCharacterListEntry;
import dev.soffits.openplayer.character.LocalCharacterListView;
import dev.soffits.openplayer.character.LocalCharacterRepositoryResult;
import dev.soffits.openplayer.character.OpenPlayerLocalCharacters;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentPriority;
import dev.soffits.openplayer.runtime.OpenPlayerRuntime;
import io.netty.buffer.Unpooled;
import java.util.Optional;
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
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.CHARACTER_LIST_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveCharacterListRequest
        );
    }

    private static void receiveSpawnRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String characterId = readOptionalCharacterId(buffer);
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                handleSpawnRequest(player, characterId);
            }
        });
    }

    private static void receiveDespawnRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String characterId = readOptionalCharacterId(buffer);
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                handleDespawnRequest(player, characterId);
            }
        });
    }

    private static void receiveFollowOwnerRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String characterId = readOptionalCharacterId(buffer);
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                submitNetworkNpcCommand(player, characterId, IntentKind.FOLLOW_OWNER);
            }
        });
    }

    private static void receiveStopRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String characterId = readOptionalCharacterId(buffer);
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                submitNetworkNpcCommand(player, characterId, IntentKind.STOP);
            }
        });
    }

    private static void receiveCommandTextRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String firstValue = buffer.readUtf(MAX_COMMAND_TEXT_LENGTH);
        String characterId;
        String commandText;
        if (buffer.isReadable()) {
            characterId = firstValue.trim().isEmpty() ? null : firstValue.trim();
            commandText = buffer.readUtf(MAX_COMMAND_TEXT_LENGTH);
        } else {
            characterId = null;
            commandText = firstValue;
        }
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                submitNetworkNpcCommandText(player, characterId, commandText);
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

    private static void receiveCharacterListRequest(FriendlyByteBuf ignoredBuffer, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                sendCharacterListResponse(player);
            }
        });
    }

    private static void handleSpawnRequest(ServerPlayer sender, String characterId) {
        NpcSpawnLocation location = new NpcSpawnLocation(
                sender.serverLevel().dimension().location().toString(),
                sender.getX(),
                sender.getY(),
                sender.getZ()
        );
        if (characterId != null && !characterId.isBlank()) {
            findCharacter(characterId).ifPresent(character -> OpenPlayerApi.npcService().spawn(character.toNpcSpec(
                    new NpcOwnerId(sender.getUUID()),
                    location
            )));
            sendCharacterListResponse(sender);
            return;
        }
        AiPlayerNpcSpec spec = new AiPlayerNpcSpec(
                new NpcRoleId(OpenPlayerConstants.DEFAULT_NETWORK_NPC_ROLE_ID),
                new NpcOwnerId(sender.getUUID()),
                new NpcProfileSpec(sender.getGameProfile().getName() + OpenPlayerConstants.DEFAULT_NETWORK_NPC_PROFILE_SUFFIX),
                location
        );
        OpenPlayerApi.npcService().spawn(spec);
        sendCharacterListResponse(sender);
    }

    private static void handleDespawnRequest(ServerPlayer sender, String characterId) {
        AiPlayerNpcService service = OpenPlayerApi.npcService();
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (isSenderNetworkNpcSession(sender, session, characterId)) {
                service.despawn(session.sessionId());
            }
        }
        sendCharacterListResponse(sender);
    }

    private static void submitNetworkNpcCommand(ServerPlayer sender, String characterId, IntentKind intentKind) {
        AiPlayerNpcService service = OpenPlayerApi.npcService();
        AiPlayerNpcCommand command = new AiPlayerNpcCommand(
                UUID.randomUUID(),
                new CommandIntent(intentKind, IntentPriority.HIGH, intentKind.name())
        );
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (isSenderNetworkNpcSession(sender, session, characterId)) {
                service.submitCommand(session.sessionId(), command);
            }
        }
        sendCharacterListResponse(sender);
    }

    private static void submitNetworkNpcCommandText(ServerPlayer sender, String characterId, String commandText) {
        if (commandText == null || commandText.isBlank() || commandText.length() > MAX_COMMAND_TEXT_LENGTH) {
            return;
        }
        AiPlayerNpcService service = OpenPlayerApi.npcService();
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (isSenderNetworkNpcSession(sender, session, characterId)) {
                service.submitCommandText(session.sessionId(), commandText);
            }
        }
        sendCharacterListResponse(sender);
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

    private static void sendCharacterListResponse(ServerPlayer player) {
        LocalCharacterRepositoryResult result = OpenPlayerLocalCharacters.repository().loadAll();
        LocalCharacterListView view = LocalCharacterListView.fromRepositoryResult(result, character -> lifecycleStatus(player, character));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(view.characters().size());
        for (LocalCharacterListEntry character : view.characters()) {
            buffer.writeUtf(character.id(), 64);
            buffer.writeUtf(character.displayName(), 32);
            buffer.writeUtf(character.description(), 1024);
            buffer.writeUtf(character.skinStatus(), 64);
            buffer.writeUtf(character.lifecycleStatus(), 64);
            buffer.writeUtf(character.conversationStatus(), 64);
        }
        buffer.writeVarInt(view.errors().size());
        for (String error : view.errors()) {
            buffer.writeUtf(error, 512);
        }
        NetworkManager.sendToPlayer(player, OpenPlayerConstants.CHARACTER_LIST_RESPONSE_PACKET_ID, buffer);
    }

    private static String lifecycleStatus(ServerPlayer player, LocalCharacterDefinition character) {
        for (AiPlayerNpcSession session : OpenPlayerApi.npcService().listSessions()) {
            if (matchesCharacterSession(player, session, character)) {
                return OpenPlayerApi.npcService().status(session.sessionId()).name().toLowerCase(java.util.Locale.ROOT);
            }
        }
        return "despawned";
    }

    private static Optional<LocalCharacterDefinition> findCharacter(String characterId) {
        LocalCharacterRepositoryResult result = OpenPlayerLocalCharacters.repository().loadAll();
        for (LocalCharacterDefinition character : result.characters()) {
            if (character.id().equals(characterId)) {
                return Optional.of(character);
            }
        }
        return Optional.empty();
    }

    private static boolean isSenderNetworkNpcSession(ServerPlayer sender, AiPlayerNpcSession session, String characterId) {
        if (characterId != null && !characterId.isBlank()) {
            Optional<LocalCharacterDefinition> character = findCharacter(characterId);
            return character.isPresent() && matchesCharacterSession(sender, session, character.get());
        }
        return isLegacyDefaultNetworkNpcSession(sender.getUUID(), sender.getGameProfile().getName(), session);
    }

    static boolean isLegacyDefaultNetworkNpcSession(UUID ownerId, String ownerProfileName, AiPlayerNpcSession session) {
        return session.spec().ownerId().value().equals(ownerId)
                && session.spec().roleId().value().equals(OpenPlayerConstants.DEFAULT_NETWORK_NPC_ROLE_ID)
                && session.spec().profile().name().equals(defaultNetworkNpcProfileName(ownerProfileName));
    }

    static String defaultNetworkNpcProfileName(String ownerProfileName) {
        return ownerProfileName + OpenPlayerConstants.DEFAULT_NETWORK_NPC_PROFILE_SUFFIX;
    }

    private static boolean matchesCharacterSession(ServerPlayer sender, AiPlayerNpcSession session, LocalCharacterDefinition character) {
        return matchesLocalCharacterSession(sender.getUUID(), session, character);
    }

    static boolean matchesLocalCharacterSession(UUID ownerId, AiPlayerNpcSession session, LocalCharacterDefinition character) {
        return session.spec().ownerId().value().equals(ownerId)
                && session.spec().roleId().value().equals(character.toSessionRoleId().value());
    }

    private static String readOptionalCharacterId(FriendlyByteBuf buffer) {
        if (!buffer.isReadable()) {
            return null;
        }
        String characterId = buffer.readUtf(64).trim();
        return characterId.isEmpty() ? null : characterId;
    }
}
