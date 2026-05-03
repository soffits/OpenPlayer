package dev.soffits.openplayer.automation.navigation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LoadedAreaNavigator {
    public static final int DEFAULT_RADIUS = 16;
    public static final int MAX_RADIUS = 32;
    public static final int MAX_BLOCK_POSITIONS = 32768;
    public static final int MAX_ENTITY_CANDIDATES = 128;

    public BlockSearchResult nearestLoadedBlock(ServerLevel serverLevel, Vec3 origin, String blockOrItemId, double requestedRadius) {
        if (serverLevel == null || origin == null) {
            return BlockSearchResult.notFound(SearchDiagnostics.invalid("server_or_origin_unavailable"));
        }
        Block block = resolveBlock(blockOrItemId);
        if (block == null) {
            return BlockSearchResult.notFound(SearchDiagnostics.invalid("unknown_block_or_block_item:" + safeId(blockOrItemId)));
        }
        int radius = boundedRadius(requestedRadius);
        BlockPos originPos = BlockPos.containing(origin);
        List<SearchCandidate<BlockPos>> candidates = new ArrayList<>();
        int scanned = 0;
        int skippedUnloaded = 0;
        int matched = 0;
        int radiusSquared = radius * radius;
        for (int y = originPos.getY() - radius; y <= originPos.getY() + radius && scanned < MAX_BLOCK_POSITIONS; y++) {
            for (int x = originPos.getX() - radius; x <= originPos.getX() + radius && scanned < MAX_BLOCK_POSITIONS; x++) {
                for (int z = originPos.getZ() - radius; z <= originPos.getZ() + radius && scanned < MAX_BLOCK_POSITIONS; z++) {
                    int deltaX = x - originPos.getX();
                    int deltaY = y - originPos.getY();
                    int deltaZ = z - originPos.getZ();
                    int blockDistanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                    if (blockDistanceSquared > radiusSquared) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!serverLevel.hasChunkAt(pos)) {
                        skippedUnloaded++;
                        continue;
                    }
                    scanned++;
                    BlockState blockState = serverLevel.getBlockState(pos);
                    if (blockState.is(block)) {
                        matched++;
                        candidates.add(new SearchCandidate<>(pos.immutable(), Vec3.atCenterOf(pos).distanceToSqr(origin), pos));
                    }
                }
            }
        }
        SearchDiagnostics diagnostics = new SearchDiagnostics(radius, scanned, skippedUnloaded, matched, scanned >= MAX_BLOCK_POSITIONS);
        SearchCandidate<BlockPos> nearest = nearestCandidate(candidates);
        if (nearest == null) {
            return BlockSearchResult.notFound(diagnostics);
        }
        return new BlockSearchResult(nearest.value(), diagnostics);
    }

    public EntitySearchResult nearestLoadedEntity(ServerLevel serverLevel, Vec3 origin, String entityTypeId, double requestedRadius,
                                                  Predicate<Entity> predicate) {
        if (serverLevel == null || origin == null) {
            return EntitySearchResult.notFound(SearchDiagnostics.invalid("server_or_origin_unavailable"));
        }
        EntityType<?> entityType = resolveEntityType(entityTypeId);
        if (entityType == null) {
            return EntitySearchResult.notFound(SearchDiagnostics.invalid("unknown_entity_type:" + safeId(entityTypeId)));
        }
        int radius = boundedRadius(requestedRadius);
        AABB bounds = new AABB(origin, origin).inflate(radius);
        double radiusSquared = radius * radius;
        List<SearchCandidate<Entity>> candidates = new ArrayList<>();
        int scanned = 0;
        int skippedUnloaded = 0;
        int matched = 0;
        List<Entity> entities = serverLevel.getEntities((Entity) null, bounds, entity -> entity.getType() == entityType
                && entity.position().distanceToSqr(origin) <= radiusSquared
                && (predicate == null || predicate.test(entity)));
        for (Entity candidate : entities) {
            if (scanned >= MAX_ENTITY_CANDIDATES) {
                break;
            }
            if (!serverLevel.hasChunkAt(candidate.blockPosition())) {
                skippedUnloaded++;
                continue;
            }
            scanned++;
            matched++;
            candidates.add(new SearchCandidate<>(candidate, candidate.position().distanceToSqr(origin), candidate.blockPosition()));
        }
        SearchDiagnostics diagnostics = new SearchDiagnostics(
                radius, scanned, skippedUnloaded, matched, entities.size() > MAX_ENTITY_CANDIDATES
        );
        SearchCandidate<Entity> nearest = nearestCandidate(candidates);
        if (nearest == null) {
            return EntitySearchResult.notFound(diagnostics);
        }
        return new EntitySearchResult(nearest.value(), diagnostics);
    }

    public static <T> SearchCandidate<T> nearestCandidate(List<SearchCandidate<T>> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .min(Comparator
                        .comparingDouble(SearchCandidate<T>::distanceSquared)
                        .thenComparingInt(candidate -> candidate.tieBreakPos().getY())
                        .thenComparingInt(candidate -> candidate.tieBreakPos().getX())
                        .thenComparingInt(candidate -> candidate.tieBreakPos().getZ()))
                .orElse(null);
    }

    private static int boundedRadius(double requestedRadius) {
        if (!Double.isFinite(requestedRadius) || requestedRadius <= 0.0D) {
            return DEFAULT_RADIUS;
        }
        return (int) Math.max(1, Math.min(MAX_RADIUS, Math.floor(requestedRadius)));
    }

    private static Block resolveBlock(String blockOrItemId) {
        ResourceLocation resourceLocation = resourceLocationOrNull(blockOrItemId);
        if (resourceLocation == null) {
            return null;
        }
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(resourceLocation);
        if (block.isPresent()) {
            return block.get();
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(resourceLocation);
        if (item.isPresent() && item.get() instanceof BlockItem blockItem) {
            return blockItem.getBlock();
        }
        return null;
    }

    private static EntityType<?> resolveEntityType(String entityTypeId) {
        ResourceLocation resourceLocation = resourceLocationOrNull(entityTypeId);
        if (resourceLocation == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(resourceLocation).orElse(null);
    }

    private static ResourceLocation resourceLocationOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new ResourceLocation(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String safeId(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length() && builder.length() < 48; index++) {
            char character = value.charAt(index);
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')
                    || character == '_' || character == ':' || character == '/' || character == '.' || character == '-') {
                builder.append(character);
            } else {
                builder.append('_');
            }
        }
        return builder.isEmpty() ? "unknown" : builder.toString();
    }

    public record SearchCandidate<T>(T value, double distanceSquared, BlockPos tieBreakPos) {
        public SearchCandidate {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null");
            }
            if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0D) {
                throw new IllegalArgumentException("distanceSquared must be finite and non-negative");
            }
            if (tieBreakPos == null) {
                throw new IllegalArgumentException("tieBreakPos cannot be null");
            }
        }
    }

    public record SearchDiagnostics(int radius, int scannedCount, int skippedUnloadedCount, int matchedCount,
                                    boolean capped, String rejectionReason) {
        public SearchDiagnostics(int radius, int scannedCount, int skippedUnloadedCount, int matchedCount, boolean capped) {
            this(radius, scannedCount, skippedUnloadedCount, matchedCount, capped, "none");
        }

        public SearchDiagnostics {
            if (radius < 0 || scannedCount < 0 || skippedUnloadedCount < 0 || matchedCount < 0) {
                throw new IllegalArgumentException("diagnostic counts must be non-negative");
            }
            rejectionReason = NavigationSnapshot.boundedStatus(rejectionReason);
        }

        public static SearchDiagnostics invalid(String reason) {
            return new SearchDiagnostics(0, 0, 0, 0, false, reason);
        }

        public String summary() {
            return "radius=" + radius
                    + " scanned=" + scannedCount
                    + " skippedUnloaded=" + skippedUnloadedCount
                    + " matched=" + matchedCount
                    + " capped=" + capped
                    + " reason=" + rejectionReason;
        }
    }

    public record BlockSearchResult(BlockPos blockPos, SearchDiagnostics diagnostics) {
        public BlockSearchResult {
            if (diagnostics == null) {
                throw new IllegalArgumentException("diagnostics cannot be null");
            }
        }

        public static BlockSearchResult notFound(SearchDiagnostics diagnostics) {
            return new BlockSearchResult(null, diagnostics);
        }

        public boolean found() {
            return blockPos != null;
        }
    }

    public record EntitySearchResult(Entity entity, SearchDiagnostics diagnostics) {
        public EntitySearchResult {
            if (diagnostics == null) {
                throw new IllegalArgumentException("diagnostics cannot be null");
            }
        }

        public static EntitySearchResult notFound(SearchDiagnostics diagnostics) {
            return new EntitySearchResult(null, diagnostics);
        }

        public boolean found() {
            return entity != null;
        }
    }
}
