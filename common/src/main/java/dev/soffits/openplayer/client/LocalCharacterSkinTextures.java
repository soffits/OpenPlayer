package dev.soffits.openplayer.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.character.LocalCharacterDefinition;
import dev.soffits.openplayer.character.LocalCharacterRepositoryResult;
import dev.soffits.openplayer.character.LocalSkinPathResolution;
import dev.soffits.openplayer.character.LocalSkinPathResolver;
import dev.soffits.openplayer.character.OpenPlayerLocalCharacters;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public final class LocalCharacterSkinTextures {
    private static final Map<String, CachedTexture> CACHE = new HashMap<>();

    private LocalCharacterSkinTextures() {
    }

    public static Optional<ResourceLocation> textureForRoleId(String roleId) {
        if (roleId == null || !roleId.startsWith(OpenPlayerConstants.LOCAL_CHARACTER_SESSION_ROLE_PREFIX)) {
            return Optional.empty();
        }
        String characterId = roleId.substring(OpenPlayerConstants.LOCAL_CHARACTER_SESSION_ROLE_PREFIX.length());
        if (characterId.isBlank()) {
            return Optional.empty();
        }
        LocalCharacterDefinition character = findCharacter(characterId).orElse(null);
        if (character == null || character.localSkinFile() == null) {
            return Optional.empty();
        }
        LocalSkinPathResolution resolution = new LocalSkinPathResolver(OpenPlayerLocalCharacters.openPlayerDirectory())
                .resolve(character.localSkinFile());
        if (!resolution.isResolved()) {
            return Optional.empty();
        }
        return register(character.id(), resolution.path());
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

    private static Optional<ResourceLocation> register(String characterId, Path skinPath) {
        try {
            long lastModified = Files.getLastModifiedTime(skinPath).toMillis();
            CachedTexture cached = CACHE.get(characterId);
            if (cached != null && cached.path().equals(skinPath) && cached.lastModified() == lastModified) {
                return Optional.of(cached.resourceLocation());
            }
            ResourceLocation resourceLocation = OpenPlayerConstants.id("local_skins/" + characterId);
            try (InputStream inputStream = Files.newInputStream(skinPath)) {
                NativeImage image = NativeImage.read(inputStream);
                if (!LocalSkinImageValidator.isSupportedPlayerSkinSize(image.getWidth(), image.getHeight())) {
                    image.close();
                    CACHE.remove(characterId);
                    return Optional.empty();
                }
                Minecraft.getInstance().getTextureManager().register(resourceLocation, new DynamicTexture(image));
                CACHE.put(characterId, new CachedTexture(skinPath, lastModified, resourceLocation));
                return Optional.of(resourceLocation);
            }
        } catch (IOException | RuntimeException exception) {
            CACHE.remove(characterId);
            return Optional.empty();
        }
    }

    private record CachedTexture(Path path, long lastModified, ResourceLocation resourceLocation) {
    }
}
