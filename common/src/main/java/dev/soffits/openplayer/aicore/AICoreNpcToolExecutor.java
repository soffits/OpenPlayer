package dev.soffits.openplayer.aicore;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentPriority;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AICoreNpcToolExecutor implements ToolExecutor {
    private static final double MAX_ENTITY_CURSOR_DISTANCE = 64.0D;

    private final OpenPlayerNpcEntity entity;
    private final AICoreEventBus eventBus;
    private final Function<CommandIntent, CommandSubmissionResult> commandSubmitter;

    public AICoreNpcToolExecutor(OpenPlayerNpcEntity entity, AICoreEventBus eventBus) {
        this(entity, eventBus, intent -> entity.submitRuntimeCommand(new AiPlayerNpcCommand(UUID.randomUUID(), intent)));
    }

    public AICoreNpcToolExecutor(OpenPlayerNpcEntity entity, AICoreEventBus eventBus, Function<CommandIntent, CommandSubmissionResult> commandSubmitter) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        if (eventBus == null) {
            throw new IllegalArgumentException("eventBus cannot be null");
        }
        if (commandSubmitter == null) {
            throw new IllegalArgumentException("commandSubmitter cannot be null");
        }
        this.entity = entity;
        this.eventBus = eventBus;
        this.commandSubmitter = commandSubmitter;
    }

    @Override
    public ToolResult execute(ToolCall call, ToolValidationContext context) {
        ToolResult validation = MinecraftPrimitiveTools.validate(call, context);
        if (validation.status() != ToolResultStatus.SUCCESS) {
            publish(EventType.ERROR, call, validation.reason());
            return validation;
        }
        ToolResult result = executeValidated(call, context);
        publish(result.status() == ToolResultStatus.SUCCESS ? EventType.MESSAGE : EventType.ERROR, call,
                result.status() == ToolResultStatus.SUCCESS ? result.summary() : result.reason());
        return result;
    }

    private ToolResult executeValidated(ToolCall call, ToolValidationContext context) {
        String tool = call.name().value();
        if (tool.equals("block_at")) {
            return blockAt(call);
        }
        if (tool.equals("block_at_cursor") || tool.equals("block_in_sight")) {
            return blockAtCursor(call);
        }
        if (tool.equals("entity_at_cursor")) {
            return entityAtCursor(call);
        }
        if (tool.equals("can_see_block")) {
            return canSeeBlock(call);
        }
        if (tool.equals("look")) {
            return look(call);
        }
        if (tool.equals("set_quick_bar_slot")) {
            return setQuickBarSlot(call);
        }
        if (tool.equals("toss_stack")) {
            return entity.dropSelectedHotbarStack() ? ToolResult.success("toss_stack accepted") : ToolResult.rejected("toss_stack requires a selected hotbar stack");
        }
        if (tool.equals("swing_arm")) {
            entity.swingMainHandAction();
            return ToolResult.success("swing_arm accepted");
        }
        Optional<CommandIntent> commandIntent = MinecraftPrimitiveTools.toCommandIntent(call, IntentPriority.NORMAL);
        if (commandIntent.isPresent()) {
            CommandSubmissionResult submission = commandSubmitter.apply(commandIntent.get());
            return submission.status() == CommandSubmissionStatus.ACCEPTED
                    ? ToolResult.success(submission.message())
                    : ToolResult.rejected(submission.message());
        }
        return ToolResult.rejected("Tool is not mapped to an NPC runtime adapter: " + tool);
    }

    private ToolResult blockAt(ToolCall call) {
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

    private ToolResult blockAtCursor(ToolCall call) {
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

    private ToolResult entityAtCursor(ToolCall call) {
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

    private ToolResult canSeeBlock(ToolCall call) {
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

    private ToolResult look(ToolCall call) {
        Map<String, String> values = call.arguments().values();
        float yaw = (float) Double.parseDouble(values.get("yaw"));
        float pitch = (float) Double.parseDouble(values.get("pitch"));
        entity.setYRot(yaw);
        entity.setXRot(Math.max(-90.0F, Math.min(90.0F, pitch)));
        entity.setYHeadRot(yaw);
        return ToolResult.success("look accepted");
    }

    private ToolResult setQuickBarSlot(ToolCall call) {
        int slot = Integer.parseInt(call.arguments().values().get("slot"));
        return entity.selectHotbarSlot(slot) ? ToolResult.success("set_quick_bar_slot accepted") : ToolResult.rejected("slot must be between 0 and 8");
    }

    private ToolResult blockResult(ServerLevel level, BlockPos pos) {
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

    private ServerLevel serverLevel() {
        return entity.level() instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    private static BlockPos blockPos(Map<String, String> values) {
        return new BlockPos(Integer.parseInt(values.get("x")), Integer.parseInt(values.get("y")), Integer.parseInt(values.get("z")));
    }

    private static double maxDistance(ToolCall call, double defaultValue) {
        Map<String, String> values = call.arguments().values();
        if (values.containsKey("maxDistance")) {
            return Double.parseDouble(values.get("maxDistance"));
        }
        if (values.containsKey("maxSteps") && values.containsKey("vectorLength")) {
            return Double.parseDouble(values.get("maxSteps")) * Double.parseDouble(values.get("vectorLength"));
        }
        return defaultValue;
    }

    private static String entityTypeId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    private void publish(EventType type, ToolCall call, String message) {
        LinkedHashMap<String, String> payload = new LinkedHashMap<>();
        payload.put("tool", call.name().value());
        payload.put("message", message);
        eventBus.publish(type, payload);
    }
}
