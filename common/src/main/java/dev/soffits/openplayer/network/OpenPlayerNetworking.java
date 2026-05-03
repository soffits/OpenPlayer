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
import dev.soffits.openplayer.character.LocalCharacterListEntry;
import dev.soffits.openplayer.character.LocalCharacterListView;
import dev.soffits.openplayer.character.LocalCharacterFileOperationResult;
import dev.soffits.openplayer.character.LocalCharacterRepositoryResult;
import dev.soffits.openplayer.character.LocalSkinPathResolver;
import dev.soffits.openplayer.character.OpenPlayerLocalCharacters;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentPriority;
import dev.soffits.openplayer.runtime.CompanionLifecycleManager;
import dev.soffits.openplayer.runtime.OpenPlayerRuntime;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public final class OpenPlayerNetworking {
    private static final int MAX_COMMAND_TEXT_LENGTH = 512;
    private static final CompanionLifecycleManager COMPANION_LIFECYCLE_MANAGER = CompanionLifecycleManager.withAssignments(
            OpenPlayerApi::npcService,
            () -> OpenPlayerLocalCharacters.assignmentRepository().loadAll(OpenPlayerLocalCharacters.repository().loadAll()),
            OpenPlayerRuntime::intentParser
    );

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
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.CHARACTER_EXPORT_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveCharacterExportRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.CHARACTER_IMPORT_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveCharacterImportRequest
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

    private static void receiveCharacterExportRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String characterId = buffer.readUtf(64).trim();
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                LocalCharacterFileOperationResult result = OpenPlayerLocalCharacters.repository()
                        .exportToDirectory(OpenPlayerLocalCharacters.exportsDirectory(), characterId);
                sendCharacterFileOperationResponse(player, result);
                sendCharacterListResponse(player);
            }
        });
    }

    private static void receiveCharacterImportRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String fileName = buffer.readUtf(80).trim();
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                LocalCharacterFileOperationResult result = OpenPlayerLocalCharacters.repository()
                        .importFromDirectory(OpenPlayerLocalCharacters.importsDirectory(), fileName);
                sendCharacterFileOperationResponse(player, result);
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
            COMPANION_LIFECYCLE_MANAGER.spawnSelected(
                    new NpcOwnerId(sender.getUUID()),
                    location,
                    characterId
            );
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
        if (characterId != null && !characterId.isBlank()) {
            COMPANION_LIFECYCLE_MANAGER.despawnSelected(sender.getUUID(), characterId);
            sendCharacterListResponse(sender);
            return;
        }
        AiPlayerNpcService service = OpenPlayerApi.npcService();
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (isLegacyDefaultNetworkNpcSession(sender.getUUID(), sender.getGameProfile().getName(), session)) {
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
        if (characterId != null && !characterId.isBlank()) {
            COMPANION_LIFECYCLE_MANAGER.submitSelectedCommand(sender.getUUID(), characterId, command);
            sendCharacterListResponse(sender);
            return;
        }
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (isLegacyDefaultNetworkNpcSession(sender.getUUID(), sender.getGameProfile().getName(), session)) {
                service.submitCommand(session.sessionId(), command);
            }
        }
        sendCharacterListResponse(sender);
    }

    private static void submitNetworkNpcCommandText(ServerPlayer sender, String characterId, String commandText) {
        if (commandText == null || commandText.isBlank() || commandText.length() > MAX_COMMAND_TEXT_LENGTH) {
            return;
        }
        if (characterId != null && !characterId.isBlank()) {
            COMPANION_LIFECYCLE_MANAGER.submitSelectedCommandText(sender.getUUID(), characterId, commandText);
            sendCharacterListResponse(sender);
            return;
        }
        AiPlayerNpcService service = OpenPlayerApi.npcService();
        for (AiPlayerNpcSession session : service.listSessions()) {
            if (isLegacyDefaultNetworkNpcSession(sender.getUUID(), sender.getGameProfile().getName(), session)) {
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
        LocalCharacterRepositoryResult characterResult = OpenPlayerLocalCharacters.repository().loadAll();
        LocalCharacterListView view = LocalCharacterListView.fromAssignmentRepositoryResult(
                OpenPlayerLocalCharacters.assignmentRepository().loadAll(characterResult),
                (assignment, character) -> COMPANION_LIFECYCLE_MANAGER.lifecycleStatus(player.getUUID(), assignment),
                new LocalSkinPathResolver(OpenPlayerLocalCharacters.openPlayerDirectory()),
                character -> OpenPlayerRuntime.status().intentParser().enabled()
                        ? "available"
                        : "unavailable: parser disabled"
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(view.characters().size());
        for (LocalCharacterListEntry character : view.characters()) {
            buffer.writeUtf(character.id(), 64);
            buffer.writeUtf(character.assignmentId(), 64);
            buffer.writeUtf(character.characterId(), 64);
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

    private static void sendCharacterFileOperationResponse(ServerPlayer player, LocalCharacterFileOperationResult result) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(result.formatForClientStatus(), LocalCharacterFileOperationResult.NETWORK_RESPONSE_MAX_LENGTH);
        NetworkManager.sendToPlayer(player, OpenPlayerConstants.CHARACTER_FILE_OPERATION_RESPONSE_PACKET_ID, buffer);
    }

    static boolean isLegacyDefaultNetworkNpcSession(UUID ownerId, String ownerProfileName, AiPlayerNpcSession session) {
        return session.spec().ownerId().value().equals(ownerId)
                && session.spec().roleId().value().equals(OpenPlayerConstants.DEFAULT_NETWORK_NPC_ROLE_ID)
                && session.spec().profile().name().equals(defaultNetworkNpcProfileName(ownerProfileName));
    }

    static String defaultNetworkNpcProfileName(String ownerProfileName) {
        return ownerProfileName + OpenPlayerConstants.DEFAULT_NETWORK_NPC_PROFILE_SUFFIX;
    }

    private static String readOptionalCharacterId(FriendlyByteBuf buffer) {
        if (!buffer.isReadable()) {
            return null;
        }
        String characterId = buffer.readUtf(64).trim();
        return characterId.isEmpty() ? null : characterId;
    }
}
