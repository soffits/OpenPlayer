package dev.soffits.openplayer.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.registry.OpenPlayerEntityTypes;
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
        ClientTickEvent.CLIENT_POST.register(OpenPlayerClient::openControlsOnKeyPress);
    }

    private static void receiveStatusResponse(net.minecraft.network.FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        boolean parserEnabled = buffer.readBoolean();
        String endpoint = buffer.readUtf(128);
        boolean modelConfigured = buffer.readBoolean();
        boolean apiKeyPresent = buffer.readBoolean();
        String automationName = buffer.readUtf(64);
        String automationState = buffer.readUtf(64);
        context.queue(() -> OpenPlayerClientStatus.update(
                parserEnabled,
                endpoint,
                modelConfigured,
                apiKeyPresent,
                automationName,
                automationState
        ));
    }

    private static void openControlsOnKeyPress(Minecraft minecraft) {
        while (OPEN_CONTROLS.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new OpenPlayerControlScreen());
            }
        }
    }
}
