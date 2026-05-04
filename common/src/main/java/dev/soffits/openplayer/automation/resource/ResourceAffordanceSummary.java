package dev.soffits.openplayer.automation.resource;

import dev.soffits.openplayer.entity.NpcInventoryTransfer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record ResourceAffordanceSummary(String itemId, Item item, int requestedCount, int carriedCount,
                                        int normalInventoryCapacity, int visibleDroppedCount,
                                        int exactSafeDroppedCount, int candidateCap, boolean candidatesTruncated,
                                        List<DroppedItemAffordance> droppedItems,
                                        List<WorkstationAffordance> workstations,
                                        BlockSourceAffordance blockSource) {
    private static final int MAX_DIAGNOSTIC_ENTRIES = 4;

    public ResourceAffordanceSummary {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId cannot be blank");
        }
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        if (requestedCount < 1 || carriedCount < 0 || normalInventoryCapacity < 0
                || visibleDroppedCount < 0 || exactSafeDroppedCount < 0 || candidateCap < 1) {
            throw new IllegalArgumentException("counts must be positive or non-negative");
        }
        if (exactSafeDroppedCount > visibleDroppedCount) {
            throw new IllegalArgumentException("exact-safe drops cannot exceed visible drops");
        }
        droppedItems = sortedDroppedItems(droppedItems);
        workstations = List.copyOf(workstations == null ? List.of() : workstations);
    }

    public int missingCount() {
        return Math.max(0, requestedCount - carriedCount);
    }

    public boolean canSatisfyMissingFromVisibleDrops() {
        int missing = missingCount();
        return missing > 0 && normalInventoryCapacity >= missing && exactSafeDroppedCount >= missing;
    }

    public String boundedDiagnostics(boolean containerSeen) {
        return boundedDiagnostics(containerSeen, "unknown");
    }

    public String boundedDiagnostics(boolean containerSeen, String dimensionId) {
        List<String> entries = new ArrayList<>();
        String boundedDimensionId = dimensionId == null || dimensionId.isBlank() ? "unknown" : dimensionId.trim();
        entries.add("current_dimension=" + boundedDimensionId);
        entries.add("environment=observed_loaded_world");
        if (boundedDimensionId.equals("minecraft:the_nether")) {
            entries.add("nether_recovery=water_bucket_unusable beware_lava_fire_cliffs return_requires_loaded_portal_or_player_like_portal_task");
        } else {
            entries.add("generic_dimension_recovery=loaded_portal_or_explore_or_owner_path_if_available");
        }
        entries.add("inventory=" + carriedCount + "/" + requestedCount + " capacity=" + normalInventoryCapacity);
        entries.add("visible_drops_total=" + visibleDroppedCount + " exact_safe_drops=" + exactSafeDroppedCount
                + " candidate_stacks=" + droppedItems.size() + " candidate_cap=" + candidateCap
                + " truncated=" + candidatesTruncated + " stacks=" + droppedItemsSummary());
        if (visibleDroppedCount > exactSafeDroppedCount && exactSafeDroppedCount < missingCount()) {
            entries.add("visible_drop_status=exact_safe_insufficient_or_oversized");
        }
        entries.add("workstations=" + workstationsSummary());
        entries.add("containers=" + (containerSeen ? "nearby_safe_loaded" : "none_seen"));
        if (blockSource != null && blockSource.seen()) {
            entries.add("block_sources=diagnostic_only matched=" + blockSource.matchedCount());
        }
        return String.join("; ", entries);
    }

    public static int normalInventoryCapacityFor(List<ItemStack> stacks, Item item) {
        if (item == null) {
            return 0;
        }
        int capacity = 0;
        int end = Math.min(NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT, stacks.size());
        for (int slot = NpcInventoryTransfer.FIRST_NORMAL_SLOT; slot < end; slot++) {
            ItemStack stack = stacks.get(slot);
            if (stack == null || stack.isEmpty()) {
                capacity += item.getDefaultInstance().getMaxStackSize();
            } else if (stack.is(item) && !stack.hasTag()) {
                capacity += Math.max(0, stack.getMaxStackSize() - stack.getCount());
            }
        }
        return capacity;
    }

    private static List<DroppedItemAffordance> sortedDroppedItems(List<DroppedItemAffordance> droppedItems) {
        if (droppedItems == null || droppedItems.isEmpty()) {
            return List.of();
        }
        List<DroppedItemAffordance> sorted = new ArrayList<>(droppedItems);
        sorted.sort(Comparator
                .comparingDouble(DroppedItemAffordance::distanceSquared)
                .thenComparing(affordance -> affordance.blockPos().getX())
                .thenComparing(affordance -> affordance.blockPos().getY())
                .thenComparing(affordance -> affordance.blockPos().getZ())
                .thenComparing(DroppedItemAffordance::entityId));
        return List.copyOf(sorted);
    }

    private String droppedItemsSummary() {
        if (droppedItems.isEmpty()) {
            return "none";
        }
        List<String> entries = new ArrayList<>();
        int limit = Math.min(MAX_DIAGNOSTIC_ENTRIES, droppedItems.size());
        for (int index = 0; index < limit; index++) {
            DroppedItemAffordance droppedItem = droppedItems.get(index);
            entries.add(droppedItem.count() + "@" + droppedItem.blockPos().toShortString());
        }
        if (droppedItems.size() > limit) {
            entries.add("+" + (droppedItems.size() - limit) + " more");
        }
        return String.join(", ", entries);
    }

    private String workstationsSummary() {
        if (workstations.isEmpty()) {
            return "none";
        }
        List<String> entries = new ArrayList<>();
        int limit = Math.min(MAX_DIAGNOSTIC_ENTRIES, workstations.size());
        for (int index = 0; index < limit; index++) {
            WorkstationAffordance workstation = workstations.get(index);
            entries.add(workstation.kindId() + "@" + workstation.blockPos().toShortString());
        }
        if (workstations.size() > limit) {
            entries.add("+" + (workstations.size() - limit) + " more");
        }
        return String.join(", ", entries);
    }

    public record DroppedItemAffordance(String entityId, BlockPos blockPos, int count, double distanceSquared) {
        public DroppedItemAffordance {
            if (entityId == null || entityId.isBlank() || blockPos == null || count < 1
                    || !Double.isFinite(distanceSquared) || distanceSquared < 0.0D) {
                throw new IllegalArgumentException("invalid dropped item affordance");
            }
            blockPos = blockPos.immutable();
        }
    }

    public record DroppedItemScan(int visibleDroppedCount, int exactSafeDroppedCount, int candidateCap,
                                  boolean candidatesTruncated, List<DroppedItemAffordance> droppedItems) {
        public DroppedItemScan {
            if (visibleDroppedCount < 0 || exactSafeDroppedCount < 0 || exactSafeDroppedCount > visibleDroppedCount
                    || candidateCap < 1) {
                throw new IllegalArgumentException("invalid dropped item scan");
            }
            droppedItems = sortedDroppedItems(droppedItems);
        }

        public static DroppedItemScan empty(int candidateCap) {
            return new DroppedItemScan(0, 0, candidateCap, false, List.of());
        }
    }

    public record WorkstationAffordance(String kindId, BlockPos blockPos, String adapterId) {
        public WorkstationAffordance {
            if (kindId == null || kindId.isBlank() || blockPos == null || adapterId == null || adapterId.isBlank()) {
                throw new IllegalArgumentException("invalid workstation affordance");
            }
            blockPos = blockPos.immutable();
        }
    }

    public record BlockSourceAffordance(boolean seen, int matchedCount, boolean diagnosticOnly, String reason) {
        public BlockSourceAffordance {
            if (matchedCount < 0 || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("invalid block source affordance");
            }
        }
    }

    public static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
