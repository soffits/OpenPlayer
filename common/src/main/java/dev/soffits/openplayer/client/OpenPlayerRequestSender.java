package dev.soffits.openplayer.client;

import dev.architectury.networking.NetworkManager;
import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.OpenPlayerIntentParserConfig;
import dev.soffits.openplayer.character.LocalCharacterDefinition;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public final class OpenPlayerRequestSender {
    private OpenPlayerRequestSender() {
    }

    public static void sendSpawnRequest() {
        NetworkManager.sendToServer(OpenPlayerConstants.SPAWN_REQUEST_PACKET_ID, emptyPayload());
    }

    public static void sendSpawnRequest(String characterId) {
        NetworkManager.sendToServer(OpenPlayerConstants.SPAWN_REQUEST_PACKET_ID, characterPayload(characterId));
    }

    public static void sendDespawnRequest() {
        NetworkManager.sendToServer(OpenPlayerConstants.DESPAWN_REQUEST_PACKET_ID, emptyPayload());
    }

    public static void sendDespawnRequest(String characterId) {
        NetworkManager.sendToServer(OpenPlayerConstants.DESPAWN_REQUEST_PACKET_ID, characterPayload(characterId));
    }

    public static void sendFollowOwnerRequest() {
        NetworkManager.sendToServer(OpenPlayerConstants.FOLLOW_OWNER_REQUEST_PACKET_ID, emptyPayload());
    }

    public static void sendFollowOwnerRequest(String characterId) {
        NetworkManager.sendToServer(OpenPlayerConstants.FOLLOW_OWNER_REQUEST_PACKET_ID, characterPayload(characterId));
    }

    public static void sendStopRequest() {
        NetworkManager.sendToServer(OpenPlayerConstants.STOP_REQUEST_PACKET_ID, emptyPayload());
    }

    public static void sendStopRequest(String characterId) {
        NetworkManager.sendToServer(OpenPlayerConstants.STOP_REQUEST_PACKET_ID, characterPayload(characterId));
    }

    public static void sendCommandTextRequest(String commandText) {
        if (commandText == null) {
            throw new IllegalArgumentException("commandText cannot be null");
        }
        FriendlyByteBuf buffer = characterPayload(null);
        buffer.writeUtf(commandText);
        NetworkManager.sendToServer(OpenPlayerConstants.COMMAND_TEXT_REQUEST_PACKET_ID, buffer);
    }

    public static void sendCommandTextRequest(String characterId, String commandText) {
        if (commandText == null) {
            throw new IllegalArgumentException("commandText cannot be null");
        }
        FriendlyByteBuf buffer = characterPayload(characterId);
        buffer.writeUtf(commandText);
        NetworkManager.sendToServer(OpenPlayerConstants.COMMAND_TEXT_REQUEST_PACKET_ID, buffer);
    }

    public static void sendStatusRequest() {
        NetworkManager.sendToServer(OpenPlayerConstants.STATUS_REQUEST_PACKET_ID, emptyPayload());
    }

    public static void sendStatusRequest(String selectedAssignmentId) {
        if (selectedAssignmentId == null || selectedAssignmentId.isBlank()) {
            sendStatusRequest();
            return;
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(selectedAssignmentId.trim(), 64);
        NetworkManager.sendToServer(OpenPlayerConstants.STATUS_REQUEST_PACKET_ID, buffer);
    }

    public static void sendCharacterListRequest() {
        NetworkManager.sendToServer(OpenPlayerConstants.CHARACTER_LIST_REQUEST_PACKET_ID, emptyPayload());
    }

    public static void sendCharacterExportRequest(String characterId) {
        NetworkManager.sendToServer(OpenPlayerConstants.CHARACTER_EXPORT_REQUEST_PACKET_ID, characterPayload(characterId));
    }

    public static void sendCharacterImportRequest(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("fileName cannot be null");
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(fileName, 80);
        NetworkManager.sendToServer(OpenPlayerConstants.CHARACTER_IMPORT_REQUEST_PACKET_ID, buffer);
    }

    public static void sendCharacterSaveRequest(LocalCharacterDefinition character) {
        if (character == null) {
            throw new IllegalArgumentException("character cannot be null");
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(character.id(), 64);
        buffer.writeUtf(character.displayName(), 32);
        buffer.writeUtf(character.description() == null ? "" : character.description(), 1024);
        buffer.writeUtf(character.localSkinFile() == null ? "" : character.localSkinFile(), 256);
        buffer.writeUtf(character.defaultRoleId() == null ? "" : character.defaultRoleId(), 64);
        buffer.writeUtf(character.conversationPrompt() == null ? "" : character.conversationPrompt(), 4096);
        buffer.writeUtf(character.conversationSettings() == null ? "" : character.conversationSettings(), 2048);
        buffer.writeBoolean(character.allowWorldActions());
        NetworkManager.sendToServer(OpenPlayerConstants.CHARACTER_SAVE_REQUEST_PACKET_ID, buffer);
    }

    public static void sendCharacterDeleteRequest(String characterId) {
        NetworkManager.sendToServer(OpenPlayerConstants.CHARACTER_DELETE_REQUEST_PACKET_ID, characterPayload(characterId));
    }

    public static void sendProviderConfigSaveRequest(String endpoint, String model, String apiKey, boolean clearApiKey) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(endpoint == null ? "" : endpoint, OpenPlayerIntentParserConfig.MAX_ENDPOINT_LENGTH);
        buffer.writeUtf(model == null ? "" : model, OpenPlayerIntentParserConfig.MAX_MODEL_LENGTH);
        buffer.writeUtf(apiKey == null ? "" : apiKey, OpenPlayerIntentParserConfig.MAX_API_KEY_LENGTH);
        buffer.writeBoolean(clearApiKey);
        NetworkManager.sendToServer(OpenPlayerConstants.PROVIDER_CONFIG_SAVE_REQUEST_PACKET_ID, buffer);
    }

    public static void sendProviderTestRequest() {
        NetworkManager.sendToServer(OpenPlayerConstants.PROVIDER_TEST_REQUEST_PACKET_ID, emptyPayload());
    }

    private static FriendlyByteBuf emptyPayload() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static FriendlyByteBuf characterPayload(String characterId) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(characterId == null ? "" : characterId, 64);
        return buffer;
    }
}
