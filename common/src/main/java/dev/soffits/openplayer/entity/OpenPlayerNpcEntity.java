package dev.soffits.openplayer.entity;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.NpcOwnerId;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public final class OpenPlayerNpcEntity extends PathfinderMob {
    private final RuntimeCommandExecutor runtimeCommandExecutor = new RuntimeCommandExecutor(this);

    public OpenPlayerNpcEntity(EntityType<? extends OpenPlayerNpcEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void tick() {
        super.tick();
        runtimeCommandExecutor.tick();
    }

    public void setRuntimeOwnerId(NpcOwnerId ownerId) {
        runtimeCommandExecutor.setOwnerId(ownerId);
    }

    public CommandSubmissionResult submitRuntimeCommand(AiPlayerNpcCommand command) {
        return runtimeCommandExecutor.submit(command);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.1D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }
}
