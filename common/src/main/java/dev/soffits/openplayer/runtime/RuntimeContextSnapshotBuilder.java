package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.api.NpcSessionStatus;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.runtime.context.RuntimeAgentSnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeContextSnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.BlockTargetSnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.RuntimeEntitySnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.RuntimeNamedEntitySnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeWorldSnapshot;
import dev.soffits.openplayer.runtime.perception.ServerWorldPerceptionScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class RuntimeContextSnapshotBuilder {
    private RuntimeContextSnapshotBuilder() {
    }

    static RuntimeContextSnapshot build(OpenPlayerNpcEntity entity) {
        ServerLevel level = (ServerLevel) entity.level();
        BlockPos center = entity.blockPosition();
        RuntimeWorldSnapshot world = new RuntimeWorldSnapshot(
                level.dimension().location().toString(),
                center.getX(),
                center.getY(),
                center.getZ(),
                level.getDayTime() % 24000L,
                level.isDay(),
                level.isRaining(),
                level.isThundering(),
                level.getDifficulty().getKey()
        );
        RuntimeAgentSnapshot agent = new RuntimeAgentSnapshot(
                NpcSessionStatus.ACTIVE.name().toLowerCase(java.util.Locale.ROOT),
                Math.round(entity.getHealth()),
                Math.round(entity.getMaxHealth()),
                entity.getAirSupply(),
                "unsupported",
                "unsupported",
                activeEffectsSummary(entity),
                physicalStatus(entity),
                "unsupported",
                Boolean.toString(entity.isSprinting()),
                itemName(entity.getMainHandItem()),
                itemName(entity.getOffhandItem()),
                armorSummary(entity),
                inventorySummary(entity)
        );
        RuntimeNearbySnapshot nearby = nearbySnapshot(level, entity, center);
        return new RuntimeContextSnapshot(world, agent, nearby, ServerWorldPerceptionScanner.scanNpcArea(level, center));
    }

    private static RuntimeNearbySnapshot nearbySnapshot(ServerLevel level, OpenPlayerNpcEntity entity, BlockPos center) {
        BlockScanSnapshot blocks = nearbyBlocks(level, center);
        return new RuntimeNearbySnapshot(
                blocks.counts(),
                blocks.targets(),
                nearbyDroppedItemCounts(level, entity),
                nearbyHostiles(level, entity),
                nearbyPlayers(level, entity),
                nearbyNpcs(level, entity)
        );
    }

    private static BlockScanSnapshot nearbyBlocks(ServerLevel level, BlockPos center) {
        Map<String, Integer> counts = new TreeMap<>();
        List<BlockTargetSnapshot> targets = new ArrayList<>();
        int radius = 6;
        for (BlockPos blockPos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 3, radius))) {
            BlockState state = level.getBlockState(blockPos);
            if (state.isAir()) {
                continue;
            }
            Block block = state.getBlock();
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            counts.merge(id, 1, Integer::sum);
            targets.add(new BlockTargetSnapshot(
                    id,
                    blockPos.getX(),
                    blockPos.getY(),
                    blockPos.getZ(),
                    blockDistanceSquared(center, blockPos)
            ));
        }
        return new BlockScanSnapshot(counts, targets);
    }

    private static String activeEffectsSummary(OpenPlayerNpcEntity entity) {
        List<String> values = new ArrayList<>();
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            String id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect()).toString();
            values.add(id + " amplifier=" + effect.getAmplifier() + " durationTicks=" + boundedEffectDuration(effect.getDuration()));
        }
        values.sort(String::compareTo);
        if (values.isEmpty()) {
            return "none";
        }
        int limit = Math.min(8, values.size());
        List<String> limited = new ArrayList<>(values.subList(0, limit));
        if (values.size() > limit) {
            limited.add("more=" + (values.size() - limit));
        }
        return String.join(", ", limited);
    }

    private static int boundedEffectDuration(int duration) {
        return Math.max(0, Math.min(duration, 72000));
    }

    private static String physicalStatus(OpenPlayerNpcEntity entity) {
        return "onFire=" + entity.isOnFire()
                + ", inWater=" + entity.isInWater()
                + ", onGround=" + entity.onGround()
                + ", fallDistance=" + Math.round(Math.max(0.0F, Math.min(entity.fallDistance, 256.0F)));
    }

    private static double blockDistanceSquared(BlockPos first, BlockPos second) {
        int dx = first.getX() - second.getX();
        int dy = first.getY() - second.getY();
        int dz = first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static Map<String, Integer> nearbyDroppedItemCounts(ServerLevel level, OpenPlayerNpcEntity entity) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(12.0D),
                itemEntity -> itemEntity.isAlive() && !itemEntity.getItem().isEmpty())) {
            ItemStack stack = itemEntity.getItem();
            counts.merge(itemName(stack), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static List<RuntimeEntitySnapshot> nearbyHostiles(ServerLevel level, OpenPlayerNpcEntity entity) {
        List<RuntimeEntitySnapshot> hostiles = new ArrayList<>();
        for (Monster monster : level.getEntitiesOfClass(Monster.class, entity.getBoundingBox().inflate(32.0D), Monster::isAlive)) {
            hostiles.add(new RuntimeEntitySnapshot(entityName(monster), distanceMeters(entity, monster), direction(entity, monster)));
        }
        return hostiles;
    }

    private static List<RuntimeNamedEntitySnapshot> nearbyPlayers(ServerLevel level, OpenPlayerNpcEntity entity) {
        List<RuntimeNamedEntitySnapshot> players = new ArrayList<>();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, entity.getBoundingBox().inflate(64.0D), ServerPlayer::isAlive)) {
            players.add(new RuntimeNamedEntitySnapshot(player.getGameProfile().getName(), distanceMeters(entity, player), direction(entity, player)));
        }
        return players;
    }

    private static List<RuntimeNamedEntitySnapshot> nearbyNpcs(ServerLevel level, OpenPlayerNpcEntity entity) {
        List<RuntimeNamedEntitySnapshot> npcs = new ArrayList<>();
        for (OpenPlayerNpcEntity npc : level.getEntitiesOfClass(OpenPlayerNpcEntity.class, entity.getBoundingBox().inflate(32.0D),
                npc -> npc.isAlive() && npc != entity)) {
            String name = npc.persistedProfileName().orElse("OpenPlayer NPC");
            npcs.add(new RuntimeNamedEntitySnapshot(name, distanceMeters(entity, npc), direction(entity, npc)));
        }
        return npcs;
    }

    private static Map<String, Integer> inventorySummary(OpenPlayerNpcEntity entity) {
        Map<String, Integer> counts = new TreeMap<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = entity.getInventoryItem(slot);
            if (!stack.isEmpty()) {
                counts.merge(itemName(stack), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static List<String> armorSummary(OpenPlayerNpcEntity entity) {
        List<String> values = new ArrayList<>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                values.add(slot.getName() + "=" + itemName(stack));
            }
        }
        return values;
    }

    private static String itemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String entityName(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    private static long distanceMeters(Entity origin, Entity target) {
        double dx = target.getX() - origin.getX();
        double dy = target.getY() - origin.getY();
        double dz = target.getZ() - origin.getZ();
        return Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private static String direction(Entity origin, Entity target) {
        double dx = target.getX() - origin.getX();
        double dy = target.getY() - origin.getY();
        double dz = target.getZ() - origin.getZ();
        return horizontalDirection(dx, dz) + verticalDirection(dy);
    }

    private static String horizontalDirection(double dx, double dz) {
        if (Math.abs(dx) < 2.0D && Math.abs(dz) < 2.0D) {
            return "near";
        }
        String northSouth = dz < -2.0D ? "north" : dz > 2.0D ? "south" : "";
        String eastWest = dx > 2.0D ? "east" : dx < -2.0D ? "west" : "";
        if (northSouth.isEmpty()) {
            return eastWest;
        }
        if (eastWest.isEmpty()) {
            return northSouth;
        }
        return northSouth + "-" + eastWest;
    }

    private static String verticalDirection(double dy) {
        if (dy > 2.0D) {
            return "+above";
        }
        if (dy < -2.0D) {
            return "+below";
        }
        return "";
    }

    private record BlockScanSnapshot(Map<String, Integer> counts, List<BlockTargetSnapshot> targets) {
    }
}
