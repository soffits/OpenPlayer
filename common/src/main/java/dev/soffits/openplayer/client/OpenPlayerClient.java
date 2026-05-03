package dev.soffits.openplayer.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.character.LocalCharacterListEntry;
import dev.soffits.openplayer.character.LocalCharacterListView;
import dev.soffits.openplayer.registry.OpenPlayerEntityTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class OpenPlayerClient {
    private static final String KEY_CATEGORY = "key.categories." + OpenPlayerConstants.MOD_ID;
    private static final String OPEN_CONTROLS_KEY = "key." + OpenPlayerConstants.MOD_ID + ".open_controls";
    private static final KeyMapping OPEN_CONTROLS = new KeyMapping(
            OPEN_CONTROLS_KEY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KEY_CATEGORY
    );

    private static boolean initialized;

    private OpenPlayerClient() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        EntityRendererRegistry.register(OpenPlayerEntityTypes.AI_PLAYER_NPC, OpenPlayerNpcRenderer::new);
        KeyMappingRegistry.register(OPEN_CONTROLS);
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                OpenPlayerConstants.STATUS_RESPONSE_PACKET_ID,
                OpenPlayerClient::receiveStatusResponse
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                OpenPlayerConstants.CHARACTER_LIST_RESPONSE_PACKET_ID,
                OpenPlayerClient::receiveCharacterListResponse
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                OpenPlayerConstants.CHARACTER_FILE_OPERATION_RESPONSE_PACKET_ID,
                OpenPlayerClient::receiveCharacterFileOperationResponse
        );
        ClientTickEvent.CLIENT_POST.register(OpenPlayerClient::openControlsOnKeyPress);
    }

    private static void receiveStatusResponse(net.minecraft.network.FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        boolean parserAvailable = buffer.readBoolean();
        String endpoint = buffer.readUtf(128);
        String endpointSource = buffer.readUtf(32);
        boolean modelConfigured = buffer.readBoolean();
        String modelSource = buffer.readUtf(32);
        boolean apiKeyPresent = buffer.readBoolean();
        String apiKeySource = buffer.readUtf(32);
        String automationName = buffer.readUtf(64);
        String automationState = buffer.readUtf(64);
        context.queue(() -> OpenPlayerClientStatus.update(
                parserAvailable,
                endpoint,
                endpointSource,
                modelConfigured,
                modelSource,
                apiKeyPresent,
                apiKeySource,
                automationName,
                automationState
        ));
    }

    private static void receiveCharacterListResponse(net.minecraft.network.FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        int characterCount = buffer.readVarInt();
        List<LocalCharacterListEntry> characters = new ArrayList<>();
        for (int index = 0; index < characterCount; index++) {
            String id = buffer.readUtf(64);
            String assignmentId = buffer.readUtf(64);
            String characterId = buffer.readUtf(64);
            String displayName = buffer.readUtf(32);
            String description = buffer.readUtf(1024);
            String localSkinFile = buffer.readUtf(256);
            String defaultRoleId = buffer.readUtf(64);
            String conversationPrompt = buffer.readUtf(4096);
            String conversationSettings = buffer.readUtf(2048);
            boolean allowWorldActions = buffer.readBoolean();
            String skinStatus = buffer.readUtf(64);
            String lifecycleStatus = buffer.readUtf(64);
            String conversationStatus = buffer.readUtf(64);
            int eventCount = buffer.readVarInt();
            List<String> conversationEvents = new ArrayList<>();
            for (int eventIndex = 0; eventIndex < eventCount; eventIndex++) {
                conversationEvents.add(buffer.readUtf(128));
            }
            characters.add(new LocalCharacterListEntry(
                    id,
                    assignmentId,
                    characterId,
                    displayName,
                    description,
                    localSkinFile,
                    defaultRoleId,
                    conversationPrompt,
                    conversationSettings,
                    allowWorldActions,
                    skinStatus,
                    lifecycleStatus,
                    conversationStatus,
                    conversationEvents
            ));
        }
        int errorCount = buffer.readVarInt();
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < errorCount; index++) {
            errors.add(buffer.readUtf(512));
        }
        int importCount = buffer.isReadable() ? buffer.readVarInt() : 0;
        List<String> importFileNames = new ArrayList<>();
        for (int index = 0; index < importCount; index++) {
            importFileNames.add(buffer.readUtf(80));
        }
        context.queue(() -> {
            OpenPlayerClientStatus.updateCharacters(new LocalCharacterListView(characters, errors));
            OpenPlayerClientStatus.updateImportFileNames(importFileNames);
        });
    }

    private static void receiveCharacterFileOperationResponse(net.minecraft.network.FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String status = buffer.readUtf(256);
        context.queue(() -> OpenPlayerClientStatus.updateCharacterFileOperationStatus(status));
    }

    private static void openControlsOnKeyPress(Minecraft minecraft) {
        while (OPEN_CONTROLS.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new OpenPlayerControlScreen());
            }
        }
    }
}
