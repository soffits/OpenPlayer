package dev.soffits.openplayer;

import net.minecraft.resources.ResourceLocation;

public final class OpenPlayerConstants {
    public static final String MOD_ID = "openplayer";
    public static final String MOD_NAME = "OpenPlayer";
    public static final String DEFAULT_NETWORK_NPC_ROLE_ID = "network_request";
    public static final String DEFAULT_NETWORK_NPC_PROFILE_SUFFIX = " OpenPlayer NPC";
    public static final String LOCAL_CHARACTER_SESSION_ROLE_PREFIX = "openplayer-local-character-";
    public static final String LOCAL_ASSIGNMENT_SESSION_ROLE_PREFIX = "openplayer-local-assignment-";
    public static final ResourceLocation SPAWN_REQUEST_PACKET_ID = id("spawn_request");
    public static final ResourceLocation DESPAWN_REQUEST_PACKET_ID = id("despawn_request");
    public static final ResourceLocation FOLLOW_OWNER_REQUEST_PACKET_ID = id("follow_owner_request");
    public static final ResourceLocation STOP_REQUEST_PACKET_ID = id("stop_request");
    public static final ResourceLocation COMMAND_TEXT_REQUEST_PACKET_ID = id("command_text_request");
    public static final ResourceLocation STATUS_REQUEST_PACKET_ID = id("status_request");
    public static final ResourceLocation STATUS_RESPONSE_PACKET_ID = id("status_response");
    public static final ResourceLocation CHARACTER_LIST_REQUEST_PACKET_ID = id("character_list_request");
    public static final ResourceLocation CHARACTER_LIST_RESPONSE_PACKET_ID = id("character_list_response");
    public static final ResourceLocation CHARACTER_EXPORT_REQUEST_PACKET_ID = id("character_export_request");
    public static final ResourceLocation CHARACTER_IMPORT_REQUEST_PACKET_ID = id("character_import_request");
    public static final ResourceLocation CHARACTER_FILE_OPERATION_RESPONSE_PACKET_ID = id("character_file_operation_response");

    private OpenPlayerConstants() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
