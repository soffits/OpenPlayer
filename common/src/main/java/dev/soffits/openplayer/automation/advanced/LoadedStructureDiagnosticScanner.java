package dev.soffits.openplayer.automation.advanced;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class LoadedStructureDiagnosticScanner {
    public static final String SOURCE = "loaded_scan";
    public static final int MAX_CHECKED_POSITIONS = 131072;
    public static final int MAX_INSPECTED_POSITIONS = 65536;
    private static final int VERTICAL_RADIUS = 16;
    private static final int VILLAGE_NEARBY_DISTANCE = 20;
    private static final int CONTAINER_HINT_DISTANCE = 12;

    public StructureDiagnosticResult scan(ServerLevel serverLevel, Vec3 origin, String structureId, double requestedRadius) {
        if (serverLevel == null || origin == null) {
            return StructureDiagnosticResult.notFound(
                    structureId, 0, StructureScanDiagnostics.invalid("server_or_origin_unavailable")
            );
        }
        ResourceLocation resourceLocation = resourceLocationOrNull(structureId);
        int radius = boundedRadius(requestedRadius);
        if (resourceLocation == null || !resourceLocation.toString().equals("minecraft:village")) {
            return StructureDiagnosticResult.unsupported(
                    structureId, radius, StructureScanDiagnostics.invalid("unsupported_structure")
            );
        }
        return scanVillage(serverLevel, origin, structureId, radius);
    }

    private StructureDiagnosticResult scanVillage(ServerLevel serverLevel, Vec3 origin, String structureId, int radius) {
        BlockPos originPos = BlockPos.containing(origin);
        ScanState scanState = new ScanState();
        int yMin = Math.max(serverLevel.getMinBuildHeight(), originPos.getY() - VERTICAL_RADIUS);
        int yMax = Math.min(serverLevel.getMaxBuildHeight() - 1, originPos.getY() + VERTICAL_RADIUS);
        int radiusSquared = radius * radius;
        for (int y = yMin; y <= yMax && !scanState.isCapped(); y++) {
            for (int x = originPos.getX() - radius; x <= originPos.getX() + radius
                    && !scanState.isCapped(); x++) {
                for (int z = originPos.getZ() - radius; z <= originPos.getZ() + radius
                        && !scanState.isCapped(); z++) {
                    int deltaX = x - originPos.getX();
                    int deltaZ = z - originPos.getZ();
                    if (deltaX * deltaX + deltaZ * deltaZ > radiusSquared) {
                        continue;
                    }
                    scanState.checkedPositions++;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!serverLevel.hasChunkAt(pos)) {
                        scanState.skippedUnloadedPositions++;
                        continue;
                    }
                    scanState.inspectedPositions++;
                    scanState.loadedChunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
                    BlockState state = serverLevel.getBlockState(pos);
                    if (isBed(state)) {
                        scanState.beds.add(pos.immutable());
                    } else if (state.is(Blocks.BELL)) {
                        scanState.bells.add(pos.immutable());
                    } else if (isVillageWorkstation(state)) {
                        scanState.workstations.add(pos.immutable());
                    }
                    if (isDiagnosticContainer(serverLevel, pos, state)) {
                        scanState.containers.add(pos.immutable());
                    }
                }
            }
        }
        StructureSighting sighting = villageSightingOrNull(origin, scanState);
        StructureScanDiagnostics diagnostics = new StructureScanDiagnostics(
                SOURCE,
                radius,
                scanState.checkedPositions,
                scanState.inspectedPositions,
                scanState.loadedChunks.size(),
                scanState.skippedUnloadedPositions,
                scanState.evidenceCandidates(),
                scanState.isCapped(),
                "none"
        );
        if (sighting == null) {
            return StructureDiagnosticResult.notFound(structureId, radius, diagnostics);
        }
        ContainerHint containerHint = nearestContainerHintOrNull(sighting.position(), scanState.containers);
        return StructureDiagnosticResult.evidenceFound(structureId, radius, sighting, containerHint, diagnostics);
    }

    private static StructureSighting villageSightingOrNull(Vec3 origin, ScanState scanState) {
        List<StructureSighting> sightings = new ArrayList<>();
        for (BlockPos bell : scanState.bells) {
            BlockPos bed = nearestWithin(bell, scanState.beds, VILLAGE_NEARBY_DISTANCE);
            if (bed != null) {
                sightings.add(new StructureSighting("village_bell_and_bed", bell, distance(origin, bell)));
            }
        }
        for (BlockPos bed : scanState.beds) {
            BlockPos workstation = nearestWithin(bed, scanState.workstations, VILLAGE_NEARBY_DISTANCE);
            if (workstation != null && scanState.beds.size() >= 2) {
                sightings.add(new StructureSighting("village_beds_and_workstation", bed, distance(origin, bed)));
            }
        }
        return sightings.stream()
                .min(Comparator
                        .comparingDouble(StructureSighting::distance)
                        .thenComparingInt(sighting -> sighting.position().getY())
                        .thenComparingInt(sighting -> sighting.position().getX())
                        .thenComparingInt(sighting -> sighting.position().getZ()))
                .orElse(null);
    }

    private static ContainerHint nearestContainerHintOrNull(BlockPos sightingPos, List<BlockPos> containers) {
        BlockPos container = nearestWithin(sightingPos, containers, CONTAINER_HINT_DISTANCE);
        if (container == null) {
            return null;
        }
        return new ContainerHint(container, Math.sqrt(container.distSqr(sightingPos)));
    }

    private static BlockPos nearestWithin(BlockPos origin, List<BlockPos> candidates, int maxDistance) {
        int maxDistanceSquared = maxDistance * maxDistance;
        return candidates.stream()
                .filter(candidate -> candidate.distSqr(origin) <= maxDistanceSquared)
                .min(Comparator
                        .comparingDouble((BlockPos candidate) -> candidate.distSqr(origin))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .orElse(null);
    }

    private static boolean isBed(BlockState state) {
        return state.getBlock() instanceof BedBlock;
    }

    private static boolean isVillageWorkstation(BlockState state) {
        return state.is(Blocks.COMPOSTER)
                || state.is(Blocks.BARREL)
                || state.is(Blocks.BLAST_FURNACE)
                || state.is(Blocks.BREWING_STAND)
                || state.is(Blocks.CARTOGRAPHY_TABLE)
                || state.is(Blocks.CAULDRON)
                || state.is(Blocks.FLETCHING_TABLE)
                || state.is(Blocks.GRINDSTONE)
                || state.is(Blocks.LECTERN)
                || state.is(Blocks.LOOM)
                || state.is(Blocks.SMITHING_TABLE)
                || state.is(Blocks.SMOKER)
                || state.is(Blocks.STONECUTTER);
    }

    private static boolean isDiagnosticContainer(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        if (!state.is(Blocks.CHEST) && !state.is(Blocks.BARREL)) {
            return false;
        }
        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        return blockEntity instanceof Container;
    }

    private static double distance(Vec3 origin, BlockPos pos) {
        return Vec3.atCenterOf(pos).distanceTo(origin);
    }

    private static int boundedRadius(double requestedRadius) {
        if (!Double.isFinite(requestedRadius) || requestedRadius <= 0.0D) {
            return (int) AdvancedTaskInstructionParser.STRUCTURE_DEFAULT_RADIUS;
        }
        return (int) Math.max(1, Math.min(AdvancedTaskInstructionParser.STRUCTURE_MAX_RADIUS, Math.floor(requestedRadius)));
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

    private static final class ScanState {
        private final List<BlockPos> beds = new ArrayList<>();
        private final List<BlockPos> bells = new ArrayList<>();
        private final List<BlockPos> workstations = new ArrayList<>();
        private final List<BlockPos> containers = new ArrayList<>();
        private final Set<Long> loadedChunks = new HashSet<>();
        private int checkedPositions;
        private int inspectedPositions;
        private int skippedUnloadedPositions;

        private boolean isCapped() {
            return checkedPositions >= MAX_CHECKED_POSITIONS || inspectedPositions >= MAX_INSPECTED_POSITIONS;
        }

        private int evidenceCandidates() {
            return beds.size() + bells.size() + workstations.size();
        }
    }

    public record StructureDiagnosticResult(String structureId, String status, int radius, StructureSighting sighting,
                                            ContainerHint containerHint,
                                            StructureScanDiagnostics diagnostics) {
        public StructureDiagnosticResult {
            if (structureId == null || structureId.isBlank()) {
                structureId = "unknown";
            }
            if (status == null || status.isBlank()) {
                throw new IllegalArgumentException("status cannot be blank");
            }
            if (diagnostics == null) {
                throw new IllegalArgumentException("diagnostics cannot be null");
            }
        }

        public static StructureDiagnosticResult unsupported(
                String structureId,
                int radius,
                StructureScanDiagnostics diagnostics
        ) {
            return new StructureDiagnosticResult(structureId, "unsupported_structure", radius, null, null, diagnostics);
        }

        public static StructureDiagnosticResult notFound(String structureId, int radius, StructureScanDiagnostics diagnostics) {
            return new StructureDiagnosticResult(structureId, "not_found", radius, null, null, diagnostics);
        }

        public static StructureDiagnosticResult evidenceFound(String structureId, int radius, StructureSighting sighting,
                                                              ContainerHint containerHint,
                                                              StructureScanDiagnostics diagnostics) {
            return new StructureDiagnosticResult(
                    structureId, "evidence_found", radius, sighting, containerHint, diagnostics
            );
        }

        public String summary() {
            StringBuilder builder = new StringBuilder();
            builder.append("target=").append(structureId)
                    .append(" status=").append(status)
                    .append(" source=").append(LoadedStructureDiagnosticScanner.SOURCE)
                    .append(" diagnostics=").append(diagnostics.summary());
            if (sighting != null) {
                builder.append(" evidence=").append(sighting.evidenceKind())
                        .append(" pos=").append(sighting.position().toShortString())
                        .append(" distance=").append(String.format(java.util.Locale.ROOT, "%.1f", sighting.distance()));
            }
            builder.append(" note=loaded_world_evidence_only_no_confirmed_structure_membership");
            if (containerHint != null) {
                builder.append(" container_hint=diagnostic_only")
                        .append(" container=").append(containerHint.containerPos().toShortString())
                        .append(" container_distance=")
                        .append(String.format(java.util.Locale.ROOT, "%.1f", containerHint.distanceFromSighting()))
                        .append(" container_note=no_item_movement_no_ownership_or_structure_membership_guarantee")
                        .append(" next=inspect/use reviewed container transfer only after explicit owner decision");
            } else {
                builder.append(" container_hint=diagnostic_only container=none")
                        .append(" container_note=no_item_movement_no_ownership_or_structure_membership_guarantee")
                        .append(" next=inspect/use reviewed container transfer only after explicit owner decision");
            }
            return builder.toString();
        }
    }

    public record StructureSighting(String evidenceKind, BlockPos position, double distance) {
        public StructureSighting {
            if (evidenceKind == null || evidenceKind.isBlank()) {
                throw new IllegalArgumentException("evidenceKind cannot be blank");
            }
            if (position == null) {
                throw new IllegalArgumentException("position cannot be null");
            }
            if (!Double.isFinite(distance) || distance < 0.0D) {
                throw new IllegalArgumentException("distance must be finite and non-negative");
            }
        }
    }

    public record ContainerHint(BlockPos containerPos, double distanceFromSighting) {
        public ContainerHint {
            if (containerPos == null) {
                throw new IllegalArgumentException("containerPos cannot be null");
            }
            if (!Double.isFinite(distanceFromSighting) || distanceFromSighting < 0.0D) {
                throw new IllegalArgumentException("distanceFromSighting must be finite and non-negative");
            }
        }
    }

    public record StructureScanDiagnostics(String source, int radius, int checkedPositions, int inspectedLoadedPositions,
                                            int inspectedLoadedChunks, int skippedUnloadedPositions,
                                            int evidenceCandidates, boolean capped, String reason) {
        public StructureScanDiagnostics {
            if (source == null || source.isBlank()) {
                source = SOURCE;
            }
            if (radius < 0 || checkedPositions < 0 || inspectedLoadedPositions < 0 || inspectedLoadedChunks < 0
                    || skippedUnloadedPositions < 0 || evidenceCandidates < 0) {
                throw new IllegalArgumentException("diagnostic counts must be non-negative");
            }
            if (reason == null || reason.isBlank()) {
                reason = "none";
            }
        }

        public static StructureScanDiagnostics invalid(String reason) {
            return new StructureScanDiagnostics(SOURCE, 0, 0, 0, 0, 0, 0, false, reason);
        }

        public String summary() {
            return "source=" + source
                    + " radius=" + radius
                    + " checked_positions=" + checkedPositions
                    + " inspected_loaded_positions=" + inspectedLoadedPositions
                    + " inspected_loaded_chunks=" + inspectedLoadedChunks
                    + " skipped_unloaded_positions=" + skippedUnloadedPositions
                    + " candidates=" + evidenceCandidates
                    + " capped=" + capped
                    + " reason=" + reason;
        }
    }
}
