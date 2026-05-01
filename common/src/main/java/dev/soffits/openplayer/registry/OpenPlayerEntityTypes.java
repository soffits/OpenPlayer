package dev.soffits.openplayer.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class OpenPlayerEntityTypes {
    public static final String AI_PLAYER_NPC_NAME = "ai_player_npc";

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(
            OpenPlayerConstants.MOD_ID,
            Registries.ENTITY_TYPE
    );

    public static final RegistrySupplier<EntityType<OpenPlayerNpcEntity>> AI_PLAYER_NPC = ENTITY_TYPES.register(
            AI_PLAYER_NPC_NAME,
            () -> EntityType.Builder.of(OpenPlayerNpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(OpenPlayerConstants.id(AI_PLAYER_NPC_NAME).toString())
    );

    private OpenPlayerEntityTypes() {
    }

    public static void register() {
        ENTITY_TYPES.register();
    }
}
