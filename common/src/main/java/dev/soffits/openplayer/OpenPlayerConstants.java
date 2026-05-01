package dev.soffits.openplayer;

import net.minecraft.resources.ResourceLocation;

public final class OpenPlayerConstants {
    public static final String MOD_ID = "openplayer";
    public static final String MOD_NAME = "OpenPlayer";

    private OpenPlayerConstants() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
