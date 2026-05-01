package dev.soffits.openplayer.forge;

import dev.soffits.openplayer.OpenPlayer;
import dev.soffits.openplayer.OpenPlayerConstants;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(OpenPlayerConstants.MOD_ID)
public final class OpenPlayerForge {
    public OpenPlayerForge() {
        OpenPlayer.initialize();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> OpenPlayerForgeClient.register(modEventBus));
    }
}
