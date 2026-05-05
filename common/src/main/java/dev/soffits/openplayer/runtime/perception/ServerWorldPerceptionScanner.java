package dev.soffits.openplayer.runtime.perception;

import dev.soffits.openplayer.runtime.perception.WorldPerceptionClassifier.BlockSample;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionClassifier.ColumnSample;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.BlockCoordinate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.state.BlockState;

public final class ServerWorldPerceptionScanner {
    public static final int DEFAULT_HAZARD_LIMIT = 10;
    public static final int DEFAULT_SAFE_SPOT_LIMIT = 8;
    public static final int DEFAULT_CLUSTER_LIMIT = 10;

    private ServerWorldPerceptionScanner() {
    }

    public static WorldPerceptionSnapshot scanNpcArea(ServerLevel level, BlockPos origin) {
        return scan(level, origin, WorldPerceptionClassifier.DEFAULT_SCAN_RADIUS,
                WorldPerceptionClassifier.DEFAULT_VERTICAL_DOWN, WorldPerceptionClassifier.DEFAULT_VERTICAL_UP, "npc");
    }

    public static WorldPerceptionSnapshot scan(ServerLevel level, BlockPos origin, int requestedRadius, int verticalDown, int verticalUp, String source) {
        if (level == null) {
            throw new IllegalArgumentException("level cannot be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("origin cannot be null");
        }
        int radius = Math.max(1, Math.min(requestedRadius, WorldPerceptionClassifier.MAX_SCAN_RADIUS));
        int down = Math.max(1, Math.min(verticalDown, WorldPerceptionClassifier.DEFAULT_VERTICAL_DOWN));
        int up = Math.max(1, Math.min(verticalUp, WorldPerceptionClassifier.DEFAULT_VERTICAL_UP));
        BlockCoordinate originCoordinate = coordinate(origin);
        ArrayList<BlockSample> objectAndHazardBlocks = new ArrayList<>();
        ArrayList<ColumnSample> columns = new ArrayList<>();
        int scannedBlocks = 0;
        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                BlockPos columnOrigin = new BlockPos(x, origin.getY(), z);
                if (!level.hasChunkAt(columnOrigin)) {
                    continue;
                }
                columns.add(sampleColumn(level, origin, x, z, down, up));
                for (int y = origin.getY() - down; y <= origin.getY() + up; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(pos)) {
                        continue;
                    }
                    scannedBlocks++;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    if (WorldPerceptionClassifier.isPotentialObjectBlock(id) || WorldPerceptionClassifier.isHazardBlock(id)) {
                        objectAndHazardBlocks.add(new BlockSample(id, coordinate(pos)));
                    }
                }
            }
        }
        appendHostileHazards(level, origin, radius, objectAndHazardBlocks);
        objectAndHazardBlocks.sort(Comparator.<BlockSample>comparingDouble(sample -> WorldPerceptionClassifier.distance(originCoordinate, sample.position()))
                .thenComparing(BlockSample::id).thenComparing(sample -> sample.position().compact()));
        boolean capped = requestedRadius > radius || verticalDown > down || verticalUp > up;
        return new WorldPerceptionSnapshot(
                source == null || source.isBlank() ? "npc" : source,
                level.dimension().location().toString(),
                originCoordinate,
                radius,
                down,
                up,
                scannedBlocks,
                columns.size(),
                capped,
                objectAndHazardBlocks.size() > WorldPerceptionFormatter.CLUSTER_LIMIT,
                WorldPerceptionClassifier.terrainPatch(columns, originCoordinate),
                WorldPerceptionClassifier.hazards(objectAndHazardBlocks, columns, originCoordinate, DEFAULT_HAZARD_LIMIT),
                WorldPerceptionClassifier.safeStandSpots(columns, originCoordinate, DEFAULT_SAFE_SPOT_LIMIT),
                WorldPerceptionClassifier.objectClusters(objectAndHazardBlocks, columns, originCoordinate, DEFAULT_CLUSTER_LIMIT)
        );
    }

    private static ColumnSample sampleColumn(ServerLevel level, BlockPos origin, int x, int z, int down, int up) {
        int bestY = origin.getY();
        boolean found = false;
        boolean hazardous = false;
        String evidence = "no_standable_surface";
        for (int y = origin.getY() + up; y >= origin.getY() - down; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos head = feet.above();
            BlockPos floor = feet.below();
            if (!level.hasChunkAt(feet) || !level.hasChunkAt(head) || !level.hasChunkAt(floor)) {
                continue;
            }
            BlockState floorState = level.getBlockState(floor);
            BlockState feetState = level.getBlockState(feet);
            BlockState headState = level.getBlockState(head);
            boolean clearBody = feetState.getCollisionShape(level, feet).isEmpty() && headState.getCollisionShape(level, head).isEmpty();
            boolean solidFloor = !floorState.getCollisionShape(level, floor).isEmpty();
            if (clearBody && solidFloor) {
                bestY = y;
                found = true;
                hazardous = hazardousColumn(level, feet, floor, feetState, floorState);
                evidence = BuiltInRegistries.BLOCK.getKey(floorState.getBlock()).toString();
                break;
            }
        }
        int dropDepth = dropDepth(level, new BlockPos(x, bestY, z), down);
        return new ColumnSample(new BlockCoordinate(x, bestY, z), found, hazardous, bestY - origin.getY(), dropDepth, evidence);
    }

    private static boolean hazardousColumn(ServerLevel level, BlockPos feet, BlockPos floor, BlockState feetState, BlockState floorState) {
        String feetId = BuiltInRegistries.BLOCK.getKey(feetState.getBlock()).toString();
        String floorId = BuiltInRegistries.BLOCK.getKey(floorState.getBlock()).toString();
        return WorldPerceptionClassifier.isHazardBlock(feetId)
                || WorldPerceptionClassifier.isHazardBlock(floorId)
                || level.getFluidState(feet).is(FluidTags.WATER)
                || level.getFluidState(feet).is(FluidTags.LAVA)
                || level.getFluidState(floor).is(FluidTags.LAVA);
    }

    private static int dropDepth(ServerLevel level, BlockPos feet, int maxDepth) {
        int depth = 0;
        for (int offset = 1; offset <= maxDepth; offset++) {
            BlockPos pos = feet.below(offset);
            if (!level.hasChunkAt(pos)) {
                break;
            }
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                break;
            }
            depth++;
        }
        return depth;
    }

    private static void appendHostileHazards(ServerLevel level, BlockPos origin, int radius, List<BlockSample> samples) {
        for (Monster monster : level.getEntitiesOfClass(Monster.class,
                new net.minecraft.world.phys.AABB(origin).inflate(radius), Monster::isAlive)) {
            samples.add(new BlockSample("entity:" + BuiltInRegistries.ENTITY_TYPE.getKey(monster.getType()), coordinate(monster.blockPosition())));
        }
    }

    private static BlockCoordinate coordinate(BlockPos pos) {
        return new BlockCoordinate(pos.getX(), pos.getY(), pos.getZ());
    }
}
