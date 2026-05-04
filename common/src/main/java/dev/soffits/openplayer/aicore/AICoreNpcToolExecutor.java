package dev.soffits.openplayer.aicore;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentPriority;
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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
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
        if (tool.equals("block_at_entity_cursor")) {
            return blockAtEntityCursor(call);
        }
        if (tool.equals("can_see_block")) {
            return canSeeBlock(call);
        }
        if (tool.equals("set_control_state")) {
            return setControlState(call);
        }
        if (tool.equals("get_control_state")) {
            return getControlState(call);
        }
        if (tool.equals("clear_control_states")) {
            entity.clearAICoreControlStates();
            return ToolResult.success("clear_control_states accepted");
        }
        if (tool.equals("wait_for_ticks")) {
            return ToolResult.success("wait_for_ticks accepted", Map.of("ticks", call.arguments().values().get("ticks")));
        }
        if (tool.equals("can_dig_block")) {
            return canDigBlock(call);
        }
        if (tool.equals("dig_time")) {
            return digTime(call);
        }
        if (tool.equals("stop_digging")) {
            entity.stopUsingItem();
            return ToolResult.success("stop_digging accepted", Map.of("activeDigging", "false"));
        }
        if (tool.equals("deactivate_item")) {
            boolean wasUsingItem = entity.isUsingItem();
            entity.stopUsingItem();
            return ToolResult.success("deactivate_item accepted", Map.of("wasUsingItem", Boolean.toString(wasUsingItem), "usingHeldItem", Boolean.toString(entity.isUsingItem())));
        }
        if (tool.equals("unequip")) {
            return unequip(call);
        }
        if (tool.equals("recipes_for") || tool.equals("recipes_all")) {
            return recipes(call);
        }
        if (tool.equals("pathfinder_get_path_to") || tool.equals("pathfinder_get_path_from_to")) {
            return pathfinderDiagnostics(call);
        }
        if (tool.equals("pathfinder_set_goal")) {
            return pathfinderSetGoal(call);
        }
        if (tool.equals("open_block") || tool.equals("open_container")) {
            return openContainer(call);
        }
        if (tool.equals("window_deposit")) {
            return windowTransfer(call, true);
        }
        if (tool.equals("window_withdraw")) {
            return windowTransfer(call, false);
        }
        if (tool.equals("window_close") || tool.equals("close_window")) {
            entity.aicoreSessionState().clear();
            return ToolResult.success("window_close accepted");
        }
        if (tool.equals("move_slot_item")) {
            return moveSlotItem(call);
        }
        if (tool.equals("transfer")) {
            return transfer(call);
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

    private ToolResult blockAtEntityCursor(ToolCall call) {
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

    private ToolResult setControlState(ToolCall call) {
        Map<String, String> values = call.arguments().values();
        String control = values.get("control");
        boolean state = Boolean.parseBoolean(values.get("state"));
        if (!entity.setAICoreControlState(control, state)) {
            return ToolResult.rejected("unsupported_control_state");
        }
        return ToolResult.success("set_control_state accepted", Map.of("control", control, "state", Boolean.toString(state), "appliesMotion", "false"));
    }

    private ToolResult getControlState(ToolCall call) {
        String control = call.arguments().values().get("control");
        if (!entity.setAICoreControlState(control, entity.aicoreControlState(control))) {
            return ToolResult.rejected("unsupported_control_state");
        }
        return ToolResult.success("get_control_state accepted", Map.of("control", control, "state", Boolean.toString(entity.aicoreControlState(control))));
    }

    private ToolResult canDigBlock(ToolCall call) {
        DigPreconditions preconditions = digPreconditions(call);
        if (preconditions.failureReason() != null) {
            return ToolResult.success("can_dig_block=false", Map.of("canDig", "false", "reason", preconditions.failureReason()));
        }
        return ToolResult.success("can_dig_block=true", Map.of(
                "canDig", "true",
                "resourceId", BuiltInRegistries.BLOCK.getKey(preconditions.state().getBlock()).toString(),
                "withinReach", Boolean.toString(withinInteractionDistance(preconditions.pos()))
        ));
    }

    private ToolResult digTime(ToolCall call) {
        DigPreconditions preconditions = digPreconditions(call);
        if (preconditions.failureReason() != null) {
            return ToolResult.failed(preconditions.failureReason());
        }
        ItemStack tool = entity.getMainHandItem();
        float destroySpeed = Math.max(1.0F, tool.getDestroySpeed(preconditions.state()));
        float hardness = preconditions.state().getDestroySpeed(preconditions.level(), preconditions.pos());
        int ticks = Math.max(1, (int) Math.ceil(hardness * 30.0F / destroySpeed));
        return ToolResult.success("dig_time=" + ticks, Map.of(
                "ticks", Integer.toString(ticks),
                "hardness", Float.toString(hardness),
                "toolDestroySpeed", Float.toString(destroySpeed),
                "correctToolForDrops", Boolean.toString(tool.isCorrectToolForDrops(preconditions.state()))
        ));
    }

    private ToolResult unequip(ToolCall call) {
        EquipmentSlot slot = equipmentSlot(call.arguments().values().get("destination"));
        if (slot == null) {
            return ToolResult.rejected("unsupported_equipment_destination");
        }
        return entity.unequipToNormalInventory(slot)
                ? ToolResult.success("unequip accepted")
                : ToolResult.rejected("unequip requires equipped item and normal inventory space");
    }

    private ToolResult recipes(ToolCall call) {
        ServerLevel level = serverLevel();
        if (level == null || level.getServer() == null) {
            return ToolResult.failed("server_recipe_manager_unavailable");
        }
        ResourceLocation itemId = ResourceLocation.tryParse(call.arguments().values().get("itemType"));
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return ToolResult.rejected("unknown_item_type");
        }
        StringBuilder recipeIds = new StringBuilder();
        int count = 0;
        for (Recipe<?> recipe : level.getServer().getRecipeManager().getRecipes()) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!result.isEmpty() && BuiltInRegistries.ITEM.getKey(result.getItem()).equals(itemId)) {
                if (recipeIds.length() > 0) {
                    recipeIds.append(',');
                }
                recipeIds.append(recipe.getId());
                count++;
                if (call.name().value().equals("recipes_for") && count >= Integer.parseInt(call.arguments().values().getOrDefault("minResultCount", "1"))) {
                    break;
                }
            }
        }
        return ToolResult.success("recipes=" + count, Map.of("count", Integer.toString(count), "recipeIds", recipeIds.toString(), "itemType", itemId.toString()));
    }

    private ToolResult pathfinderDiagnostics(ToolCall call) {
        String instruction = call.name().value().equals("pathfinder_get_path_from_to")
                ? goalInstruction(call.arguments().values().get("start")) + " -> " + goalInstruction(call.arguments().values().get("goal"))
                : goalInstruction(call.arguments().values().get("goal"));
        if (instruction.isBlank() || instruction.contains("unsupported")) {
            return ToolResult.rejected("unsupported_goal_shape");
        }
        ServerLevel level = serverLevel();
        if (level == null) {
            return ToolResult.failed("server_level_unavailable");
        }
        BlockPos start = call.name().value().equals("pathfinder_get_path_from_to")
                ? blockPosFromGoal(call.arguments().values().get("start"))
                : entity.blockPosition();
        BlockPos goal = blockPosFromGoal(call.arguments().values().get("goal"));
        if (start == null || goal == null) {
            return ToolResult.rejected("unsupported_goal_shape");
        }
        boolean loaded = level.hasChunkAt(start) && level.hasChunkAt(goal);
        double distance = Math.sqrt(start.distSqr(goal));
        boolean withinLoadedAreaBounds = loaded && distance <= 256.0D;
        return ToolResult.success("pathfinder loaded-area diagnostic", Map.of(
                "diagnostic", "loaded_area_goal_bounds",
                "startChunkLoaded", Boolean.toString(level.hasChunkAt(start)),
                "goalChunkLoaded", Boolean.toString(level.hasChunkAt(goal)),
                "withinLoadedAreaBounds", Boolean.toString(withinLoadedAreaBounds),
                "distance", Double.toString(distance),
                "nodeListExposed", "false"
        ));
    }

    private ToolResult pathfinderSetGoal(ToolCall call) {
        if (Boolean.parseBoolean(call.arguments().values().getOrDefault("dynamic", "false"))) {
            return ToolResult.failed("unsupported_missing_dynamic_goal_tick_adapter");
        }
        Optional<CommandIntent> commandIntent = MinecraftPrimitiveTools.toCommandIntent(
                ToolCall.of("pathfinder_goto", new ToolArguments(Map.of("goal", call.arguments().values().get("goal")))),
                IntentPriority.NORMAL
        );
        if (commandIntent.isEmpty()) {
            return ToolResult.rejected("unsupported_goal_shape");
        }
        CommandSubmissionResult submission = commandSubmitter.apply(commandIntent.get());
        return submission.status() == CommandSubmissionStatus.ACCEPTED
                ? ToolResult.success(submission.message())
                : ToolResult.rejected(submission.message());
    }

    private ToolResult openContainer(ToolCall call) {
        ServerLevel level = serverLevel();
        if (level == null) {
            return ToolResult.failed("server_level_unavailable");
        }
        BlockPos pos = blockPosFromValuesOrTarget(call.arguments().values());
        if (pos == null) {
            return ToolResult.rejected("target_coordinates_required");
        }
        Container container = containerAt(level, pos);
        if (container == null) {
            return ToolResult.failed("unsupported_missing_loaded_container_block_entity");
        }
        entity.aicoreSessionState().openContainer(pos, container instanceof WorldlyContainer);
        return ToolResult.success("container session opened", Map.of("x", Integer.toString(pos.getX()), "y", Integer.toString(pos.getY()), "z", Integer.toString(pos.getZ()), "slots", Integer.toString(container.getContainerSize())));
    }

    private ToolResult windowTransfer(ToolCall call, boolean deposit) {
        ToolResult sessionRejection = genericWindowTransferSessionRejection(entity.aicoreSessionState());
        if (sessionRejection != null) {
            return sessionRejection;
        }
        ServerLevel level = serverLevel();
        Container container = currentContainer(level);
        if (container == null) {
            return ToolResult.failed("no_current_loaded_container_session");
        }
        Item item = item(call.arguments().values().get("itemType"));
        if (item == null) {
            return ToolResult.rejected("unknown_item_type");
        }
        Integer count = boundedCountOrNull(call.arguments().values().get("count"));
        if (count == null) {
            return ToolResult.rejected("count must be between 1 and 256");
        }
        List<ItemStack> snapshot = containerSnapshot(container);
        boolean transferred = deposit
                ? entity.depositInventoryItemTo(snapshot, item, count)
                : entity.withdrawInventoryItemFrom(snapshot, item, count);
        if (!transferred) {
            return ToolResult.rejected(deposit ? "deposit_would_not_fit_or_item_missing" : "withdraw_would_not_fit_or_item_missing");
        }
        restoreContainer(container, snapshot);
        return ToolResult.success(deposit ? "window_deposit accepted" : "window_withdraw accepted", Map.of("itemType", BuiltInRegistries.ITEM.getKey(item).toString(), "count", Integer.toString(count)));
    }

    private ToolResult moveSlotItem(ToolCall call) {
        int sourceSlot = Integer.parseInt(call.arguments().values().get("sourceSlot"));
        int destinationSlot = Integer.parseInt(call.arguments().values().get("destSlot"));
        return entity.moveInventorySlotItemNoLoss(sourceSlot, destinationSlot)
                ? ToolResult.success("move_slot_item accepted")
                : ToolResult.rejected("move_slot_item_requires_source_and_full_destination_capacity");
    }

    private ToolResult transfer(ToolCall call) {
        String options = call.arguments().values().get("options");
        String direction = jsonStringField(options, "direction");
        String itemType = jsonStringField(options, "itemType");
        String count = jsonNumberField(options, "count");
        if (direction.isBlank() || itemType.isBlank() || count.isBlank()) {
            return ToolResult.rejected("transfer_options_require_direction_itemType_count");
        }
        boolean deposit = direction.equals("deposit") || direction.equals("to_window");
        boolean withdraw = direction.equals("withdraw") || direction.equals("from_window");
        if (!deposit && !withdraw) {
            return ToolResult.rejected("unsupported_transfer_direction");
        }
        return windowTransfer(ToolCall.of(deposit ? "window_deposit" : "window_withdraw", new ToolArguments(Map.of("itemType", itemType, "count", count))), deposit);
    }

    static ToolResult genericWindowTransferSessionRejection(AICoreNpcSessionState sessionState) {
        if (sessionState != null && sessionState.hasSlotRestrictedContainerSession()) {
            return ToolResult.rejected("slot_restricted_container_transfer_unsupported");
        }
        return null;
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

    private DigPreconditions digPreconditions(ToolCall call) {
        ServerLevel level = serverLevel();
        if (level == null) {
            return DigPreconditions.failed("server_level_unavailable");
        }
        BlockPos pos = blockPos(call.arguments().values());
        if (!level.hasChunkAt(pos)) {
            return DigPreconditions.failed("target_chunk_unloaded");
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return DigPreconditions.failed("block_is_air");
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return DigPreconditions.failed("block_not_breakable");
        }
        if (!withinInteractionDistance(pos)) {
            return DigPreconditions.failed("block_out_of_reach");
        }
        return new DigPreconditions(level, pos, state, null);
    }

    private boolean withinInteractionDistance(BlockPos pos) {
        return entity.distanceToSqr(Vec3.atCenterOf(pos)) <= 4.0D * 4.0D;
    }

    private ServerLevel serverLevel() {
        return entity.level() instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    private static BlockPos blockPos(Map<String, String> values) {
        return new BlockPos(Integer.parseInt(values.get("x")), Integer.parseInt(values.get("y")), Integer.parseInt(values.get("z")));
    }

    private static BlockPos blockPosFromValuesOrTarget(Map<String, String> values) {
        if (values.containsKey("x") && values.containsKey("y") && values.containsKey("z")) {
            return blockPos(values);
        }
        String target = values.get("target");
        if (target == null || target.isBlank()) {
            return null;
        }
        String x = jsonNumberField(target, "x");
        String y = jsonNumberField(target, "y");
        String z = jsonNumberField(target, "z");
        if (x.isBlank() || y.isBlank() || z.isBlank()) {
            return null;
        }
        return new BlockPos(Integer.parseInt(x), Integer.parseInt(y), Integer.parseInt(z));
    }

    private Container containerAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos) || !withinInteractionDistance(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container container ? container : null;
    }

    private Container currentContainer(ServerLevel level) {
        BlockPos pos = entity.aicoreSessionState().containerPos();
        return pos == null ? null : containerAt(level, pos);
    }

    private static List<ItemStack> containerSnapshot(Container container) {
        ArrayList<ItemStack> stacks = new ArrayList<>(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            stacks.add(container.getItem(slot).copy());
        }
        return stacks;
    }

    private static void restoreContainer(Container container, List<ItemStack> snapshot) {
        for (int slot = 0; slot < container.getContainerSize() && slot < snapshot.size(); slot++) {
            container.setItem(slot, snapshot.get(slot).copy());
        }
        container.setChanged();
    }

    private static Item item(String itemType) {
        ResourceLocation itemId = ResourceLocation.tryParse(itemType == null ? "" : itemType);
        return itemId != null && BuiltInRegistries.ITEM.containsKey(itemId) ? BuiltInRegistries.ITEM.get(itemId) : null;
    }

    private static String goalInstruction(String goalJson) {
        if (goalJson == null || goalJson.isBlank()) {
            return "unsupported_empty_goal";
        }
        String type = jsonStringField(goalJson, "type");
        String x = jsonNumberField(goalJson, "x");
        String y = jsonNumberField(goalJson, "y");
        String z = jsonNumberField(goalJson, "z");
        if ((type.equals("goal_block") || type.equals("goal_near") || type.equals("goal_get_to_block")
                || type.equals("goal_look_at_block") || type.equals("goal_place_block"))
                && !x.isBlank() && !y.isBlank() && !z.isBlank()) {
            return x + " " + y + " " + z;
        }
        if ((type.equals("goal_xz") || type.equals("goal_near_xz")) && !x.isBlank() && !z.isBlank()) {
            return x + " 0 " + z;
        }
        return "unsupported_goal_shape";
    }

    private static BlockPos blockPosFromGoal(String goalJson) {
        String instruction = goalInstruction(goalJson);
        if (instruction.isBlank() || instruction.contains("unsupported")) {
            return null;
        }
        String[] parts = instruction.split(" ");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer boundedCountOrNull(String value) {
        try {
            int count = Integer.parseInt(value);
            return count >= 1 && count <= 256 ? count : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String jsonStringField(String json, String fieldName) {
        String quoted = "\"" + fieldName + "\"";
        int fieldIndex = json == null ? -1 : json.indexOf(quoted);
        if (fieldIndex < 0) {
            return "";
        }
        int colonIndex = json.indexOf(':', fieldIndex + quoted.length());
        int quoteStart = colonIndex < 0 ? -1 : json.indexOf('"', colonIndex + 1);
        int quoteEnd = quoteStart < 0 ? -1 : json.indexOf('"', quoteStart + 1);
        return quoteEnd > quoteStart ? json.substring(quoteStart + 1, quoteEnd) : "";
    }

    private static String jsonNumberField(String json, String fieldName) {
        String quoted = "\"" + fieldName + "\"";
        int fieldIndex = json == null ? -1 : json.indexOf(quoted);
        if (fieldIndex < 0) {
            return "";
        }
        int colonIndex = json.indexOf(':', fieldIndex + quoted.length());
        if (colonIndex < 0) {
            return "";
        }
        int index = colonIndex + 1;
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        if (index < json.length() && json.charAt(index) == '"') {
            int quoteEnd = json.indexOf('"', index + 1);
            return quoteEnd > index ? json.substring(index + 1, quoteEnd) : "";
        }
        int start = index;
        while (index < json.length()) {
            char character = json.charAt(index);
            if ((character >= '0' && character <= '9') || character == '-' || character == '+') {
                index++;
            } else {
                break;
            }
        }
        return index > start ? json.substring(start, index) : "";
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

    private static Entity entityById(ServerLevel level, String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return null;
        }
        try {
            Entity entity = level.getEntity(UUID.fromString(entityId));
            return entity != null && entity.isAlive() ? entity : null;
        } catch (IllegalArgumentException exception) {
            for (Entity candidate : level.getAllEntities()) {
                if (candidate.getStringUUID().equals(entityId) && candidate.isAlive()) {
                    return candidate;
                }
            }
            return null;
        }
    }

    private static EquipmentSlot equipmentSlot(String destination) {
        if (destination == null) {
            return null;
        }
        return switch (destination) {
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "torso", "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            case "off-hand", "offhand" -> EquipmentSlot.OFFHAND;
            default -> null;
        };
    }

    private void publish(EventType type, ToolCall call, String message) {
        LinkedHashMap<String, String> payload = new LinkedHashMap<>();
        payload.put("tool", call.name().value());
        payload.put("message", message);
        eventBus.publish(type, payload);
    }

    private record DigPreconditions(ServerLevel level, BlockPos pos, BlockState state, String failureReason) {
        private static DigPreconditions failed(String reason) {
            return new DigPreconditions(null, null, null, reason);
        }
    }
}
