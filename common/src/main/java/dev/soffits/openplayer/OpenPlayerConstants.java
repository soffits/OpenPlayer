package dev.soffits.openplayer;

import net.minecraft.resources.ResourceLocation;

public final class OpenPlayerConstants {
    public static final String MOD_ID = "openplayer";
    public static final String MOD_NAME = "OpenPlayer";
    public static final String DEFAULT_NETWORK_NPC_ROLE_ID = "network_request";
    public static final String DEFAULT_NETWORK_NPC_PROFILE_SUFFIX = " OpenPlayer NPC";
    public static final ResourceLocation SPAWN_REQUEST_PACKET_ID = id("spawn_request");
    public static final ResourceLocation DESPAWN_REQUEST_PACKET_ID = id("despawn_request");
    public static final ResourceLocation FOLLOW_OWNER_REQUEST_PACKET_ID = id("follow_owner_request");
    public static final ResourceLocation STOP_REQUEST_PACKET_ID = id("stop_request");

    private OpenPlayerConstants() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
