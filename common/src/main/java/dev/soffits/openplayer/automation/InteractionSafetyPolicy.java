package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

final class InteractionSafetyPolicy {
    private static final Set<String> SAFE_EMPTY_HAND_INTERACT_BLOCK_IDS = Set.of(
            "minecraft:lever",
            "minecraft:oak_trapdoor",
            "minecraft:spruce_trapdoor",
            "minecraft:birch_trapdoor",
            "minecraft:jungle_trapdoor",
            "minecraft:acacia_trapdoor",
            "minecraft:dark_oak_trapdoor",
            "minecraft:mangrove_trapdoor",
            "minecraft:cherry_trapdoor",
            "minecraft:bamboo_trapdoor",
            "minecraft:crimson_trapdoor",
            "minecraft:warped_trapdoor",
            "minecraft:oak_fence_gate",
            "minecraft:spruce_fence_gate",
            "minecraft:birch_fence_gate",
            "minecraft:jungle_fence_gate",
            "minecraft:acacia_fence_gate",
            "minecraft:dark_oak_fence_gate",
            "minecraft:mangrove_fence_gate",
            "minecraft:cherry_fence_gate",
            "minecraft:bamboo_fence_gate",
            "minecraft:crimson_fence_gate",
            "minecraft:warped_fence_gate"
    );

    private static final Set<String> SAFE_EXPLICIT_ATTACK_ENTITY_TYPE_IDS = Set.of(
            "minecraft:blaze",
            "minecraft:cave_spider",
            "minecraft:creeper",
            "minecraft:drowned",
            "minecraft:elder_guardian",
            "minecraft:ender_dragon",
            "minecraft:endermite",
            "minecraft:evoker",
            "minecraft:ghast",
            "minecraft:guardian",
            "minecraft:hoglin",
            "minecraft:husk",
            "minecraft:magma_cube",
            "minecraft:phantom",
            "minecraft:piglin_brute",
            "minecraft:pillager",
            "minecraft:ravager",
            "minecraft:shulker",
            "minecraft:silverfish",
            "minecraft:skeleton",
            "minecraft:slime",
            "minecraft:spider",
            "minecraft:stray",
            "minecraft:vex",
            "minecraft:vindicator",
            "minecraft:warden",
            "minecraft:witch",
            "minecraft:wither",
            "minecraft:wither_skeleton",
            "minecraft:zoglin",
            "minecraft:zombie",
            "minecraft:zombie_villager"
    );

    private InteractionSafetyPolicy() {
    }

    static boolean isSafeEmptyHandInteractBlock(Block block) {
        if (block == null) {
            return false;
        }
        return isSafeEmptyHandInteractBlockId(BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    static boolean isSafeEmptyHandInteractBlockId(String blockId) {
        return blockId != null && SAFE_EMPTY_HAND_INTERACT_BLOCK_IDS.contains(blockId);
    }

    static boolean isSafeExplicitAttackTarget(LivingEntity target, Entity owner, OpenPlayerNpcEntity npc) {
        if (target == null || !target.isAlive() || target == npc || target instanceof Player
                || target instanceof OpenPlayerNpcEntity) {
            return false;
        }
        if (owner != null && target.getUUID().equals(owner.getUUID())) {
            return false;
        }
        return isSafeExplicitAttackEntityTypeId(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
    }

    static boolean isSafeExplicitAttackEntityTypeId(String entityTypeId) {
        return entityTypeId != null && SAFE_EXPLICIT_ATTACK_ENTITY_TYPE_IDS.contains(entityTypeId);
    }
}
