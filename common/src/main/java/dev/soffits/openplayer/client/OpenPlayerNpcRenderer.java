package dev.soffits.openplayer.client;

import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class OpenPlayerNpcRenderer extends LivingEntityRenderer<OpenPlayerNpcEntity, PlayerModel<OpenPlayerNpcEntity>> {
    public OpenPlayerNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(OpenPlayerNpcEntity entity) {
        return DefaultPlayerSkin.getDefaultSkin(entity.deterministicSkinId());
    }
}
