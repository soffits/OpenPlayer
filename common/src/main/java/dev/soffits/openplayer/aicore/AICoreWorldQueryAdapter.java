package dev.soffits.openplayer.aicore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AICoreWorldQueryAdapter {
    public static final int MAX_RAY_STEPS = 512;
    public static final double MAX_RAY_DISTANCE = 256.0D;

    private final List<AICoreBlockSnapshot> loadedBlocks;
    private final List<AICoreEntitySnapshot> loadedEntities;

    public AICoreWorldQueryAdapter(List<AICoreBlockSnapshot> loadedBlocks, List<AICoreEntitySnapshot> loadedEntities) {
        this.loadedBlocks = loadedBlocks == null ? List.of() : List.copyOf(loadedBlocks);
        this.loadedEntities = loadedEntities == null ? List.of() : List.copyOf(loadedEntities);
    }

    public Optional<AICoreBlockSnapshot> blockAt(int x, int y, int z) {
        return loadedBlocks.stream()
                .filter(block -> block.loaded() && blockCoordinateEquals(block.position(), x, y, z))
                .findFirst();
    }

    public Optional<AICoreBlockSnapshot> blockAtCursor(AICoreVec3 eyePosition, AICoreVec3 viewVector, double maxDistance) {
        RaycastResult result = raycastBlocks(eyePosition, viewVector, maxDistance, 0.25D);
        return result.block();
    }

    public Optional<AICoreBlockSnapshot> blockAtEntityCursor(String entityId, double maxDistance) {
        if (entityId == null || entityId.isBlank()) {
            return Optional.empty();
        }
        Optional<AICoreEntitySnapshot> entity = loadedEntities.stream()
                .filter(candidate -> candidate.id().equals(entityId))
                .findFirst();
        if (entity.isEmpty() || entity.get().viewVector() == null) {
            return Optional.empty();
        }
        RaycastResult result = raycastBlocks(entity.get().position(), entity.get().viewVector(), maxDistance, 0.25D);
        return result.block();
    }

    public Optional<AICoreBlockSnapshot> blockInSight(AICoreVec3 eyePosition, AICoreVec3 viewVector, int maxSteps, double vectorLength) {
        RaycastResult result = raycastBlocksBySteps(eyePosition, viewVector, maxSteps, vectorLength);
        return result.block();
    }

    public Optional<AICoreEntitySnapshot> entityAtCursor(AICoreVec3 eyePosition, AICoreVec3 viewVector, double maxDistance) {
        AICoreVec3 direction = normalize(viewVector);
        double boundedDistance = boundedDistance(maxDistance);
        return loadedEntities.stream()
                .filter(entity -> distanceAlongRay(eyePosition, direction, entity.position()) >= 0.0D)
                .filter(entity -> distanceAlongRay(eyePosition, direction, entity.position()) <= boundedDistance)
                .filter(entity -> perpendicularDistanceSquared(eyePosition, direction, entity.position()) <= 0.75D * 0.75D)
                .min(Comparator
                        .comparingDouble((AICoreEntitySnapshot entity) -> distanceAlongRay(eyePosition, direction, entity.position()))
                        .thenComparing(AICoreEntitySnapshot::id));
    }

    public boolean canSeeBlock(AICoreVec3 eyePosition, int x, int y, int z) {
        Optional<AICoreBlockSnapshot> target = blockAt(x, y, z);
        if (target.isEmpty()) {
            return false;
        }
        AICoreVec3 targetCenter = new AICoreVec3(x + 0.5D, y + 0.5D, z + 0.5D);
        AICoreVec3 vector = new AICoreVec3(targetCenter.x() - eyePosition.x(), targetCenter.y() - eyePosition.y(), targetCenter.z() - eyePosition.z());
        RaycastResult result = raycastBlocks(eyePosition, vector, length(vector), 0.25D);
        return result.block().isPresent() && result.block().get().equals(target.get());
    }

    public ToolResult execute(ToolCall call, AICoreVec3 eyePosition, AICoreVec3 viewVector) {
        Map<String, String> values = call.arguments().values();
        String name = call.name().value();
        if (name.equals("block_at")) {
            Optional<AICoreBlockSnapshot> block = blockAt(intValue(values, "x"), intValue(values, "y"), intValue(values, "z"));
            return block.map(AICoreWorldQueryAdapter::blockResult).orElseGet(() -> ToolResult.failed("unloaded_or_air_block"));
        }
        if (name.equals("can_see_block")) {
            boolean visible = canSeeBlock(eyePosition, intValue(values, "x"), intValue(values, "y"), intValue(values, "z"));
            return ToolResult.success("can_see_block=" + visible, Map.of("visible", Boolean.toString(visible)));
        }
        if (name.equals("block_at_cursor")) {
            Optional<AICoreBlockSnapshot> block = blockAtCursor(eyePosition, viewVector, doubleValue(values, "maxDistance"));
            return block.map(AICoreWorldQueryAdapter::blockResult).orElseGet(() -> ToolResult.failed("no_loaded_block_in_cursor"));
        }
        if (name.equals("block_in_sight")) {
            Optional<AICoreBlockSnapshot> block = blockInSight(eyePosition, viewVector, intValue(values, "maxSteps"), doubleValue(values, "vectorLength"));
            return block.map(AICoreWorldQueryAdapter::blockResult).orElseGet(() -> ToolResult.failed("no_loaded_block_in_sight"));
        }
        if (name.equals("entity_at_cursor")) {
            Optional<AICoreEntitySnapshot> entity = entityAtCursor(eyePosition, viewVector, doubleValue(values, "maxDistance"));
            return entity.map(AICoreWorldQueryAdapter::entityResult).orElseGet(() -> ToolResult.failed("no_loaded_entity_in_cursor"));
        }
        if (name.equals("block_at_entity_cursor")) {
            Optional<AICoreBlockSnapshot> block = blockAtEntityCursor(values.get("entityId"), doubleValue(values, "maxDistance"));
            return block.map(AICoreWorldQueryAdapter::blockResult).orElseGet(() -> ToolResult.failed("unsupported_missing_entity_cursor_view_adapter"));
        }
        return ToolResult.failed("unsupported_world_query_tool");
    }

    private RaycastResult raycastBlocks(AICoreVec3 eyePosition, AICoreVec3 viewVector, double maxDistance, double stepDistance) {
        int steps = (int) Math.ceil(boundedDistance(maxDistance) / Math.max(0.05D, stepDistance));
        return raycastBlocksBySteps(eyePosition, viewVector, steps, stepDistance);
    }

    private RaycastResult raycastBlocksBySteps(AICoreVec3 eyePosition, AICoreVec3 viewVector, int maxSteps, double vectorLength) {
        AICoreVec3 direction = normalize(viewVector);
        int boundedSteps = Math.max(1, Math.min(MAX_RAY_STEPS, maxSteps));
        double boundedVectorLength = Math.max(0.05D, Math.min(4.0D, vectorLength));
        List<AICoreBlockSnapshot> hits = new ArrayList<>();
        for (int step = 1; step <= boundedSteps; step++) {
            AICoreVec3 point = new AICoreVec3(
                    eyePosition.x() + direction.x() * boundedVectorLength * step,
                    eyePosition.y() + direction.y() * boundedVectorLength * step,
                    eyePosition.z() + direction.z() * boundedVectorLength * step
            );
            blockAt(floor(point.x()), floor(point.y()), floor(point.z())).ifPresent(hits::add);
            if (!hits.isEmpty()) {
                return new RaycastResult(Optional.of(hits.get(0)));
            }
        }
        return new RaycastResult(Optional.empty());
    }

    private static ToolResult blockResult(AICoreBlockSnapshot block) {
        return ToolResult.success("loaded block " + block.resourceId(), Map.of(
                "resourceId", block.resourceId(),
                "x", Integer.toString(floor(block.position().x())),
                "y", Integer.toString(floor(block.position().y())),
                "z", Integer.toString(floor(block.position().z()))
        ));
    }

    private static ToolResult entityResult(AICoreEntitySnapshot entity) {
        return ToolResult.success("loaded entity " + entity.type(), Map.of(
                "id", entity.id(),
                "type", entity.type()
        ));
    }

    private static boolean blockCoordinateEquals(AICoreVec3 position, int x, int y, int z) {
        return floor(position.x()) == x && floor(position.y()) == y && floor(position.z()) == z;
    }

    private static AICoreVec3 normalize(AICoreVec3 vector) {
        double length = length(vector);
        if (length <= 0.0D) {
            return new AICoreVec3(0.0D, 0.0D, 1.0D);
        }
        return new AICoreVec3(vector.x() / length, vector.y() / length, vector.z() / length);
    }

    private static double length(AICoreVec3 vector) {
        return Math.sqrt(vector.x() * vector.x() + vector.y() * vector.y() + vector.z() * vector.z());
    }

    private static double boundedDistance(double maxDistance) {
        if (!Double.isFinite(maxDistance) || maxDistance <= 0.0D) {
            return 1.0D;
        }
        return Math.min(MAX_RAY_DISTANCE, maxDistance);
    }

    private static double distanceAlongRay(AICoreVec3 origin, AICoreVec3 direction, AICoreVec3 point) {
        return (point.x() - origin.x()) * direction.x() + (point.y() - origin.y()) * direction.y() + (point.z() - origin.z()) * direction.z();
    }

    private static double perpendicularDistanceSquared(AICoreVec3 origin, AICoreVec3 direction, AICoreVec3 point) {
        double along = distanceAlongRay(origin, direction, point);
        AICoreVec3 closest = new AICoreVec3(origin.x() + direction.x() * along, origin.y() + direction.y() * along, origin.z() + direction.z() * along);
        double dx = point.x() - closest.x();
        double dy = point.y() - closest.y();
        double dz = point.z() - closest.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static int intValue(Map<String, String> values, String key) {
        return Integer.parseInt(values.get(key));
    }

    private static double doubleValue(Map<String, String> values, String key) {
        return Double.parseDouble(values.get(key));
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private record RaycastResult(Optional<AICoreBlockSnapshot> block) {
    }
}
