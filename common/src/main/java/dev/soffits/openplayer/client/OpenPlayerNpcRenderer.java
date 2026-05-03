package dev.soffits.openplayer.client;

import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class OpenPlayerNpcRenderer extends LivingEntityRenderer<OpenPlayerNpcEntity, PlayerModel<OpenPlayerNpcEntity>> {
    public OpenPlayerNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        addLayer(new ElytraLayer<>(this, context.getModelSet()));
        addLayer(new ArrowLayer<>(context, this));
        addLayer(new BeeStingerLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(OpenPlayerNpcEntity entity) {
        ResourceLocation localSkinTexture = LocalCharacterSkinTextures.textureForRoleId(entity.persistedRoleId().orElse(null))
                .orElse(null);
        if (localSkinTexture != null) {
            return localSkinTexture;
        }
        String profileSkinTexture = entity.persistedProfileSkinTexture().orElse(null);
        if (profileSkinTexture != null) {
            ResourceLocation textureLocation = ResourceLocation.tryParse(profileSkinTexture);
            if (textureLocation != null) {
                return textureLocation;
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(entity.deterministicSkinId());
    }
}
