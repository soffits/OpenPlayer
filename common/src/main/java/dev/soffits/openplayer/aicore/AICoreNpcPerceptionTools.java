package dev.soffits.openplayer.aicore;

import dev.soffits.openplayer.automation.CraftInstructionParser;
import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentPriority;
import dev.soffits.openplayer.runtime.perception.ServerWorldPerceptionScanner;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionClassifier;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionFormatter;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.ObjectCluster;
import dev.soffits.openplayer.runtime.perception.WorldPerceptionSnapshot.SafeStandSpot;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;


abstract class AICoreNpcPerceptionTools extends AICoreNpcToolExecutorBase {
    protected AICoreNpcPerceptionTools(OpenPlayerNpcEntity entity, AICoreEventBus eventBus, Function<CommandIntent, CommandSubmissionResult> commandSubmitter) {
        super(entity, eventBus, commandSubmitter);
    }

protected ToolResult observeArea(ToolCall call) {
    ServerLevel level = serverLevel();
    if (level == null) {
        return ToolResult.failed("server_level_unavailable");
    }
    BlockPos origin = call.arguments().values().containsKey("x") ? blockPos(call.arguments().values()) : entity.blockPosition();
    if (!level.hasChunkAt(origin)) {
        return ToolResult.failed("origin_chunk_unloaded");
    }
    WorldPerceptionSnapshot snapshot = ServerWorldPerceptionScanner.scan(level, origin, perceptionRadius(call),
            WorldPerceptionClassifier.DEFAULT_VERTICAL_DOWN, WorldPerceptionClassifier.DEFAULT_VERTICAL_UP, "npc_query");
    if (call.name().value().equals("detect_nearby_structures")) {
        return ToolResult.success("loaded object clusters", Map.of("clusters", clusters(snapshot, ""), "loaded", "true"));
    }
    if (call.name().value().equals("find_workstations")) {
        return ToolResult.success("loaded workstation and storage evidence", Map.of("clusters", clusters(snapshot, "work"), "loaded", "true"));
    }
    return ToolResult.success("loaded area perception", Map.of("perception", WorldPerceptionFormatter.compact(snapshot)));
}

protected ToolResult findSafeStandNear(ToolCall call) {
    ServerLevel level = serverLevel();
    if (level == null) {
        return ToolResult.failed("server_level_unavailable");
    }
    BlockPos origin = blockPos(call.arguments().values());
    if (!level.hasChunkAt(origin)) {
        return ToolResult.failed("origin_chunk_unloaded");
    }
    WorldPerceptionSnapshot snapshot = ServerWorldPerceptionScanner.scan(level, origin, perceptionRadius(call),
            WorldPerceptionClassifier.DEFAULT_VERTICAL_DOWN, WorldPerceptionClassifier.DEFAULT_VERTICAL_UP, "npc_query");
    StringBuilder builder = new StringBuilder();
    for (SafeStandSpot spot : snapshot.safeStandSpots()) {
        if (builder.length() > 0) {
            builder.append(';');
        }
        builder.append("pos=").append(spot.position().compact()).append(",reason=").append(spot.reason())
                .append(",distance=").append(String.format(java.util.Locale.ROOT, "%.1f", spot.distance()))
                .append(",score=").append(spot.score());
    }
    return ToolResult.success("loaded safe stand candidates", Map.of(
            "target", origin.getX() + "," + origin.getY() + "," + origin.getZ(),
            "candidates", builder.length() == 0 ? "none" : builder.toString(),
            "loaded", "true"));
}

protected static String clusters(WorldPerceptionSnapshot snapshot, String filter) {
    StringBuilder builder = new StringBuilder();
    for (ObjectCluster cluster : snapshot.objectClusters()) {
        if (!filter.isBlank() && !cluster.kind().contains("work") && !cluster.kind().contains("storage")) {
            continue;
        }
        if (builder.length() > 0) {
            builder.append(';');
        }
        builder.append("kind=").append(cluster.kind()).append(",center=").append(cluster.center().compact())
                .append(",count=").append(cluster.count()).append(",reachable=").append(cluster.reachable())
                .append(",standable=").append(cluster.standable()).append(",confidence=").append(cluster.confidence());
    }
    return builder.length() == 0 ? "none" : builder.toString();
}

protected static int perceptionRadius(ToolCall call) {
    String value = call.arguments().values().getOrDefault("radius", Integer.toString(WorldPerceptionClassifier.DEFAULT_SCAN_RADIUS));
    try {
        return Math.max(1, Math.min(WorldPerceptionClassifier.MAX_SCAN_RADIUS, Integer.parseInt(value)));
    } catch (NumberFormatException exception) {
        return WorldPerceptionClassifier.DEFAULT_SCAN_RADIUS;
    }
}

protected ToolResult blockAt(ToolCall call) {
    ServerLevel level = serverLevel();
    if (level == null) {
        return ToolResult.failed("server_level_unavailable");
    }
    BlockPos pos = blockPos(call.arguments().values());
    if (!level.hasChunkAt(pos)) {
        return ToolResult.failed("target_chunk_unloaded");
    }
    return blockResult(level, pos);
}

protected ToolResult blockAtCursor(ToolCall call) {
    ServerLevel level = serverLevel();
    if (level == null) {
        return ToolResult.failed("server_level_unavailable");
    }
    double maxDistance = maxDistance(call, "block_in_sight".equals(call.name().value()) ? 16.0D : 256.0D);
    Vec3 eye = entity.getEyePosition();
    Vec3 target = eye.add(entity.getViewVector(1.0F).normalize().scale(maxDistance));
    BlockHitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
    if (hit.getType() != HitResult.Type.BLOCK || !level.hasChunkAt(hit.getBlockPos())) {
        return ToolResult.failed("no_loaded_block_in_cursor");
    }
    return blockResult(level, hit.getBlockPos());
}

protected ToolResult entityAtCursor(ToolCall call) {
    ServerLevel level = serverLevel();
    if (level == null) {
        return ToolResult.failed("server_level_unavailable");
    }
    double maxDistance = Math.min(MAX_ENTITY_CURSOR_DISTANCE, maxDistance(call, 3.5D));
    Vec3 eye = entity.getEyePosition();
    Vec3 direction = entity.getViewVector(1.0F).normalize();
    Vec3 target = eye.add(direction.scale(maxDistance));
    AABB bounds = entity.getBoundingBox().expandTowards(direction.scale(maxDistance)).inflate(1.0D);
    EntityHitResult best = null;
    double bestDistance = maxDistance * maxDistance;
    List<Entity> candidates = level.getEntities(entity, bounds, candidate -> candidate.isAlive() && candidate != entity && level.hasChunkAt(candidate.blockPosition()));
    for (Entity candidate : candidates) {
        Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3D).clip(eye, target);
        if (hit.isPresent()) {
            double distance = eye.distanceToSqr(hit.get());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new EntityHitResult(candidate, hit.get());
            }
        }
    }
    if (best == null) {
        return ToolResult.failed("no_loaded_entity_in_cursor");
    }
    Entity hitEntity = best.getEntity();
    return ToolResult.success("loaded entity " + entityTypeId(hitEntity), Map.of(
            "id", hitEntity.getUUID().toString(),
            "type", entityTypeId(hitEntity)
    ));
}

protected ToolResult blockAtEntityCursor(ToolCall call) {
    ServerLevel level = serverLevel();
    if (level == null) {
        return ToolResult.failed("server_level_unavailable");
    }
    Map<String, String> values = call.arguments().values();
    Entity source = entityById(level, values.get("entityId"));
    if (source == null) {
        return ToolResult.failed("entity_not_loaded");
    }
    if (!level.hasChunkAt(source.blockPosition())) {
        return ToolResult.failed("entity_chunk_unloaded");
    }
    double maxDistance = maxDistance(call, 256.0D);
    Vec3 eye = source.getEyePosition();
    Vec3 direction = source.getViewVector(1.0F).normalize();
    Vec3 target = eye.add(direction.scale(maxDistance));
    BlockHitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
    if (hit.getType() != HitResult.Type.BLOCK || !level.hasChunkAt(hit.getBlockPos())) {
        return ToolResult.failed("no_loaded_block_in_entity_cursor");
    }
    return blockResult(level, hit.getBlockPos());
}

protected ToolResult canSeeBlock(ToolCall call) {
    ServerLevel level = serverLevel();
    if (level == null) {
        return ToolResult.failed("server_level_unavailable");
    }
    BlockPos pos = blockPos(call.arguments().values());
    if (!level.hasChunkAt(pos)) {
        return ToolResult.failed("target_chunk_unloaded");
    }
    Vec3 eye = entity.getEyePosition();
    BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
    boolean visible = hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    return ToolResult.success("can_see_block=" + visible, Map.of("visible", Boolean.toString(visible)));
}

protected ToolResult blockResult(ServerLevel level, BlockPos pos) {
    BlockState state = level.getBlockState(pos);
    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
    return ToolResult.success("loaded block " + id, Map.of(
            "resourceId", id.toString(),
            "x", Integer.toString(pos.getX()),
            "y", Integer.toString(pos.getY()),
            "z", Integer.toString(pos.getZ()),
            "loaded", "true"
    ));
}
}
