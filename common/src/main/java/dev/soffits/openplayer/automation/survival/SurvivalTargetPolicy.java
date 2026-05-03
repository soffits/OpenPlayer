package dev.soffits.openplayer.automation.survival;

import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;

public final class SurvivalTargetPolicy {
    private SurvivalTargetPolicy() {
    }

    public static boolean isHostileOrDangerTarget(LivingEntity target, Entity owner, OpenPlayerNpcEntity npc) {
        if (target == null || !target.isAlive() || target == npc || target instanceof Player
                || target instanceof OpenPlayerNpcEntity) {
            return false;
        }
        if (owner != null && target.getUUID().equals(owner.getUUID())) {
            return false;
        }
        return isHostileOrDangerEntityClass(target.getClass());
    }

    public static boolean isHostileOrDangerEntityClass(Class<?> entityClass) {
        if (entityClass == null) {
            return false;
        }
        return Enemy.class.isAssignableFrom(entityClass)
                && !Player.class.isAssignableFrom(entityClass)
                && !OpenPlayerNpcEntity.class.isAssignableFrom(entityClass);
    }

    public static boolean isImmediateProjectileDangerClass(Class<?> entityClass) {
        return entityClass != null && Projectile.class.isAssignableFrom(entityClass);
    }
}
