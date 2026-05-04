package dev.soffits.openplayer.automation.resource;

import dev.soffits.openplayer.automation.navigation.LoadedAreaNavigator;
import dev.soffits.openplayer.entity.NpcInventoryTransfer;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ResourceAffordanceScanner {
    public static final double DEFAULT_DROP_RADIUS = 16.0D;
    public static final double MAX_DROP_RADIUS = 24.0D;
    public static final int DEFAULT_CANDIDATE_CAP = 8;
    public static final int MAX_CANDIDATE_CAP = 16;
    private final LoadedAreaNavigator loadedAreaNavigator = new LoadedAreaNavigator();

    public ResourceAffordanceSummary summarize(OpenPlayerNpcEntity entity, String itemId, Item item, int requestedCount) {
        return summarize(entity, itemId, item, requestedCount, DEFAULT_DROP_RADIUS, DEFAULT_CANDIDATE_CAP);
    }

    public ResourceAffordanceSummary summarize(OpenPlayerNpcEntity entity, String itemId, Item item, int requestedCount,
                                               double requestedDropRadius, int requestedCandidateCap) {
        if (entity == null || itemId == null || itemId.isBlank() || item == null || requestedCount < 1) {
            throw new IllegalArgumentException("invalid resource affordance request");
        }
        List<ItemStack> inventory = entity.inventorySnapshot();
        int carriedCount = NpcInventoryTransfer.countItem(
                inventory, item, NpcInventoryTransfer.FIRST_NORMAL_SLOT, NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT
        );
        int capacity = ResourceAffordanceSummary.normalInventoryCapacityFor(inventory, item);
        ServerLevel serverLevel = entity.level() instanceof ServerLevel level ? level : null;
        ResourceAffordanceSummary.DroppedItemScan droppedItemScan = serverLevel == null
                ? ResourceAffordanceSummary.DroppedItemScan.empty(boundedCandidateCap(requestedCandidateCap))
                : droppedItems(serverLevel, entity, item, Math.max(0, requestedCount - carriedCount),
                        requestedDropRadius, requestedCandidateCap);
        ResourceAffordanceSummary.BlockSourceAffordance blockSource = serverLevel == null
                ? ResourceAffordanceSummary.BlockSourceAffordance.unavailable("server_level_unavailable")
                : blockSourceAffordance(serverLevel, entity.position(), itemId, item);
        return new ResourceAffordanceSummary(
                itemId, item, requestedCount, carriedCount, capacity,
                droppedItemScan.visibleDroppedCount(), droppedItemScan.exactSafeDroppedCount(),
                droppedItemScan.candidateCap(), droppedItemScan.candidatesTruncated(),
                droppedItemScan.droppedItems(), List.of(), blockSource
        );
    }

    private ResourceAffordanceSummary.DroppedItemScan droppedItems(ServerLevel serverLevel,
                                                                   OpenPlayerNpcEntity entity, Item item,
                                                                   int missingCount, double requestedRadius,
                                                                   int requestedCandidateCap) {
        double radius = boundedRadius(requestedRadius);
        int cap = boundedCandidateCap(requestedCandidateCap);
        Vec3 origin = entity.position();
        double radiusSquared = radius * radius;
        List<ItemEntity> entities = serverLevel.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(origin, origin).inflate(radius),
                itemEntity -> itemEntity.isAlive()
                        && !itemEntity.hasPickUpDelay()
                        && !itemEntity.getItem().isEmpty()
                        && itemEntity.getItem().is(item)
                        && itemEntity.position().distanceToSqr(origin) <= radiusSquared
                        && serverLevel.hasChunkAt(itemEntity.blockPosition())
                        && entity.hasLineOfSight(itemEntity)
        );
        entities.sort(Comparator
                .comparingDouble((ItemEntity itemEntity) -> itemEntity.position().distanceToSqr(origin))
                .thenComparingInt(itemEntity -> itemEntity.blockPosition().getX())
                .thenComparingInt(itemEntity -> itemEntity.blockPosition().getY())
                .thenComparingInt(itemEntity -> itemEntity.blockPosition().getZ())
                .thenComparing(itemEntity -> itemEntity.getUUID().toString()));
        List<ResourceAffordanceSummary.DroppedItemAffordance> affordances = new ArrayList<>();
        int visibleDroppedCount = 0;
        int exactSafeDroppedCount = 0;
        int exactSafeStackCount = 0;
        for (int index = 0; index < entities.size(); index++) {
            ItemEntity itemEntity = entities.get(index);
            int stackCount = itemEntity.getItem().getCount();
            visibleDroppedCount += stackCount;
            if (stackCount <= missingCount) {
                exactSafeDroppedCount += stackCount;
                exactSafeStackCount++;
                if (affordances.size() < cap) {
                    affordances.add(new ResourceAffordanceSummary.DroppedItemAffordance(
                            itemEntity.getUUID().toString(),
                            itemEntity.blockPosition(),
                            stackCount,
                            itemEntity.position().distanceToSqr(origin)
                    ));
                }
            }
        }
        return new ResourceAffordanceSummary.DroppedItemScan(
                visibleDroppedCount, exactSafeDroppedCount, cap, exactSafeStackCount > cap, affordances
        );
    }

    private ResourceAffordanceSummary.BlockSourceAffordance blockSourceAffordance(ServerLevel serverLevel, Vec3 origin,
                                                                                  String itemId, Item item) {
        if (!(item instanceof BlockItem)) {
            return ResourceAffordanceSummary.BlockSourceAffordance.unavailable("not_a_block_item");
        }
        LoadedAreaNavigator.BlockSearchResult result = loadedAreaNavigator.nearestLoadedBlock(
                serverLevel, origin, itemId, DEFAULT_DROP_RADIUS
        );
        return new ResourceAffordanceSummary.BlockSourceAffordance(
                result.found(), result.diagnostics().matchedCount(), false,
                result.found() ? "visible_block_break_collect_verify" : "no_matching_visible_loaded_block",
                result.blockPos()
        );
    }

    private static double boundedRadius(double requestedRadius) {
        if (!Double.isFinite(requestedRadius) || requestedRadius <= 0.0D) {
            return DEFAULT_DROP_RADIUS;
        }
        return Math.max(1.0D, Math.min(MAX_DROP_RADIUS, Math.floor(requestedRadius)));
    }

    private static int boundedCandidateCap(int requestedCandidateCap) {
        if (requestedCandidateCap < 1) {
            return DEFAULT_CANDIDATE_CAP;
        }
        return Math.min(MAX_CANDIDATE_CAP, requestedCandidateCap);
    }
}
