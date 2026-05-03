package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.AiPlayerNpcService;
import dev.soffits.openplayer.api.AiPlayerNpcSession;
import dev.soffits.openplayer.api.AiPlayerNpcSpec;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.api.NpcSessionId;
import dev.soffits.openplayer.api.NpcSessionStatus;
import dev.soffits.openplayer.api.NpcSpawnLocation;
import dev.soffits.openplayer.conversation.ConversationContextSnapshot;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.debug.OpenPlayerRawTrace;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentParseException;
import dev.soffits.openplayer.intent.IntentParser;
import dev.soffits.openplayer.registry.OpenPlayerEntityTypes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class RuntimeAiPlayerNpcService implements AiPlayerNpcService {
    private final MinecraftServer server;
    private IntentParser intentParser;
    private final Map<NpcSessionId, RuntimeAiPlayerNpcSession> sessions = new LinkedHashMap<>();
    private final Map<RuntimeNpcIdentityKey, NpcSessionId> sessionIdsByIdentity = new LinkedHashMap<>();

    public RuntimeAiPlayerNpcService(MinecraftServer server, IntentParser intentParser) {
        if (server == null) {
            throw new IllegalArgumentException("server cannot be null");
        }
        if (intentParser == null) {
            throw new IllegalArgumentException("intentParser cannot be null");
        }
        this.server = server;
        this.intentParser = intentParser;
    }

    public synchronized void updateIntentParser(IntentParser intentParser) {
        if (intentParser == null) {
            throw new IllegalArgumentException("intentParser cannot be null");
        }
        this.intentParser = intentParser;
    }

    public synchronized void reattachPersistedNpcs() {
        int reattached = 0;
        int invalid = 0;
        int duplicates = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof OpenPlayerNpcEntity npcEntity) {
                    if (!npcEntity.hasValidPersistedIdentity()) {
                        npcEntity.stopRuntimeCommands();
                        npcEntity.discard();
                        invalid++;
                        continue;
                    }
                    AiPlayerNpcSpec spec = persistedSpec(level, npcEntity);
                    RuntimeNpcIdentityKey identityKey = RuntimeNpcIdentityKey.from(spec);
                    RuntimeAiPlayerNpcSession existingSession = existingSession(identityKey);
                    if (existingSession != null) {
                        if (existingSession.entityUuid().equals(npcEntity.getUUID())) {
                            continue;
                        }
                        if (entityFor(existingSession) == null) {
                            existingSession.update(spec, npcEntity.getUUID());
                            applyProfile(npcEntity, spec);
                            reattached++;
                            continue;
                        }
                        npcEntity.stopRuntimeCommands();
                        npcEntity.discard();
                        duplicates++;
                        continue;
                    }
                    NpcSessionId staleSessionId = sessionIdsByIdentity.remove(identityKey);
                    if (staleSessionId != null) {
                        sessions.remove(staleSessionId);
                    }
                    NpcSessionId sessionId = new NpcSessionId(UUID.randomUUID());
                    RuntimeAiPlayerNpcSession session = new RuntimeAiPlayerNpcSession(this, sessionId, spec, npcEntity.getUUID());
                    sessions.put(sessionId, session);
                    sessionIdsByIdentity.put(identityKey, sessionId);
                    applyProfile(npcEntity, spec);
                    reattached++;
                }
            }
        }
        if (reattached > 0) {
            OpenPlayerDebugEvents.record("npc_lifecycle", "persisted_reattached", null, null, null,
                    "count=" + reattached);
        }
        if (duplicates > 0) {
            OpenPlayerDebugEvents.record("npc_lifecycle", "persisted_duplicate_removed", null, null, null,
                    "count=" + duplicates);
        }
        if (invalid > 0) {
            OpenPlayerDebugEvents.record("npc_lifecycle", "persisted_invalid_removed", null, null, null,
                    "count=" + invalid);
        }
    }

    @Override
    public synchronized AiPlayerNpcSession spawn(AiPlayerNpcSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec cannot be null");
        }
        reattachPersistedNpcs();
        RuntimeNpcIdentityKey identityKey = RuntimeNpcIdentityKey.from(spec);
        NpcSessionId existingSessionId = sessionIdsByIdentity.get(identityKey);
        RuntimeAiPlayerNpcSession existingSession = existingSession(identityKey);
        ServerLevel level = levelFor(spec.spawnLocation());
        if (existingSession != null) {
            OpenPlayerNpcEntity existingEntity = entityFor(existingSession);
            if (existingEntity != null) {
                if (existingEntity.level().dimension().equals(level.dimension())) {
                    relocate(existingEntity, spec.spawnLocation());
                    applyProfile(existingEntity, spec);
                    existingSession.update(spec, existingEntity.getUUID());
                    return existingSession;
                }
                OpenPlayerNpcEntity replacementEntity = spawnEntity(level, spec);
                existingEntity.discard();
                existingSession.update(spec, replacementEntity.getUUID());
                sessionIdsByIdentity.put(identityKey, existingSession.sessionId());
                return existingSession;
            }
            removeIndexes(existingSession.sessionId());
        } else if (existingSessionId != null) {
            sessionIdsByIdentity.remove(identityKey);
        }

        OpenPlayerNpcEntity entity = persistedEntityFor(level, spec);
        if (entity == null) {
            entity = spawnEntity(level, spec);
        } else {
            relocate(entity, spec.spawnLocation());
            applyProfile(entity, spec);
        }
        NpcSessionId sessionId = new NpcSessionId(UUID.randomUUID());
        RuntimeAiPlayerNpcSession session = new RuntimeAiPlayerNpcSession(this, sessionId, spec, entity.getUUID());
        sessions.put(sessionId, session);
        sessionIdsByIdentity.put(identityKey, sessionId);
        return session;
    }

    private OpenPlayerNpcEntity spawnEntity(ServerLevel level, AiPlayerNpcSpec spec) {
        OpenPlayerNpcEntity entity = OpenPlayerEntityTypes.AI_PLAYER_NPC.get().create(level);
        if (entity == null) {
            throw new IllegalStateException("Unable to create OpenPlayer NPC entity");
        }
        NpcSpawnLocation location = spec.spawnLocation();
        entity.moveTo(location.x(), location.y(), location.z(), 0.0F, 0.0F);
        applyProfile(entity, spec);

        if (!level.addFreshEntity(entity)) {
            throw new IllegalStateException("Unable to spawn OpenPlayer NPC entity");
        }
        return entity;
    }

    @Override
    public synchronized boolean despawn(NpcSessionId sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        reattachPersistedNpcs();
        RuntimeAiPlayerNpcSession session = sessions.get(sessionId);
        if (session == null) {
            return false;
        }
        removeIndexes(sessionId);
        OpenPlayerNpcEntity entity = entityFor(session);
        if (entity != null) {
            entity.stopRuntimeCommands();
            entity.discard();
        }
        return true;
    }

    @Override
    public synchronized List<AiPlayerNpcSession> listSessions() {
        reattachPersistedNpcs();
        return List.copyOf(new ArrayList<>(sessions.values()));
    }

    @Override
    public synchronized CommandSubmissionResult submitCommand(NpcSessionId sessionId, AiPlayerNpcCommand command) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        if (command == null) {
            throw new IllegalArgumentException("command cannot be null");
        }
        reattachPersistedNpcs();
        if (!sessions.containsKey(sessionId)) {
            OpenPlayerDebugEvents.record("command_submission", "unknown_session", null, null,
                    sessionId.value().toString(), "submit_command");
            return new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION, "Unknown NPC session");
        }
        RuntimeAiPlayerNpcSession session = sessions.get(sessionId);
        OpenPlayerNpcEntity entity = entityFor(session);
        if (entity == null) {
            OpenPlayerDebugEvents.record("command_submission", "rejected", null, null,
                    sessionId.value().toString(), "NPC session entity is unavailable");
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, "NPC session entity is unavailable");
        }
        try {
            return entity.submitRuntimeCommand(command);
        } catch (IllegalArgumentException exception) {
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, exception.getMessage());
        }
    }

    @Override
    public CommandSubmissionResult submitCommandText(NpcSessionId sessionId, String input) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        if (input == null) {
            throw new IllegalArgumentException("input cannot be null");
        }
        OpenPlayerRawTrace.commandText("runtime_service", null, null, sessionId.value().toString(), input);
        synchronized (this) {
            reattachPersistedNpcs();
            if (!sessions.containsKey(sessionId)) {
                OpenPlayerDebugEvents.record("command_text", "unknown_session", null, null,
                        sessionId.value().toString(), "submit_command_text");
                return new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION, "Unknown NPC session");
            }
        }
        CommandIntent intent;
        try {
            OpenPlayerDebugEvents.record("provider_parse", "attempted", null, null,
                    sessionId.value().toString(), "source=command_text messageLength=" + input.trim().length());
            OpenPlayerRawTrace.parseInput("runtime_service", sessionId.value().toString(), input);
            intent = intentParser.parse(input);
            OpenPlayerDebugEvents.record("provider_parse", "success", null, null,
                    sessionId.value().toString(), "kind=" + intent.kind().name() + " instructionLength=" + intent.instruction().length());
            OpenPlayerRawTrace.parseOutput("runtime_service", sessionId.value().toString(),
                    "kind=" + intent.kind().name() + " priority=" + intent.priority().name()
                            + " instruction=" + intent.instruction());
        } catch (IntentParseException exception) {
            OpenPlayerDebugEvents.record("provider_parse", "rejected", null, null,
                    sessionId.value().toString(), "Unable to parse command text");
            OpenPlayerRawTrace.parseRejection("runtime_service", sessionId.value().toString(), input, exception.getMessage());
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, "Unable to parse command text");
        }
        return submitCommand(sessionId, new AiPlayerNpcCommand(UUID.randomUUID(), intent));
    }

    @Override
    public synchronized NpcSessionStatus status(NpcSessionId sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        reattachPersistedNpcs();
        RuntimeAiPlayerNpcSession session = sessions.get(sessionId);
        if (session == null) {
            return NpcSessionStatus.DESPAWNED;
        }
        return entityFor(session) == null ? NpcSessionStatus.DESPAWNED : NpcSessionStatus.ACTIVE;
    }

    public synchronized void stopOwnerRuntime(UUID ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId cannot be null");
        }
        reattachPersistedNpcs();
        for (RuntimeAiPlayerNpcSession session : sessions.values()) {
            if (session.spec().ownerId().value().equals(ownerId)) {
                OpenPlayerNpcEntity entity = entityFor(session);
                if (entity != null) {
                    entity.stopRuntimeCommands();
                }
            }
        }
    }

    public synchronized ConversationContextSnapshot conversationContextSnapshot(NpcSessionId sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        reattachPersistedNpcs();
        RuntimeAiPlayerNpcSession session = sessions.get(sessionId);
        if (session == null) {
            return ConversationContextSnapshot.EMPTY;
        }
        OpenPlayerNpcEntity entity = entityFor(session);
        if (entity == null) {
            return ConversationContextSnapshot.EMPTY;
        }
        return new ConversationContextSnapshot(buildConversationContext(session, entity));
    }

    synchronized void clearRuntimeSessions() {
        for (RuntimeAiPlayerNpcSession session : sessions.values()) {
            OpenPlayerNpcEntity entity = entityFor(session);
            if (entity != null) {
                entity.stopRuntimeCommands();
            }
        }
        sessions.clear();
        sessionIdsByIdentity.clear();
    }

    private ServerLevel levelFor(NpcSpawnLocation location) {
        ResourceLocation dimensionId = new ResourceLocation(location.dimension());
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) {
            throw new IllegalArgumentException("Unknown dimension: " + location.dimension());
        }
        return level;
    }

    private OpenPlayerNpcEntity entityFor(RuntimeAiPlayerNpcSession session) {
        ServerLevel level = levelFor(session.spec().spawnLocation());
        Entity entity = level.getEntity(session.entityUuid());
        if (!(entity instanceof OpenPlayerNpcEntity npcEntity)) {
            return null;
        }
        return npcEntity.isAlive() ? npcEntity : null;
    }

    private RuntimeAiPlayerNpcSession existingSession(RuntimeNpcIdentityKey identityKey) {
        NpcSessionId sessionId = sessionIdsByIdentity.get(identityKey);
        return sessionId == null ? null : sessions.get(sessionId);
    }

    private AiPlayerNpcSpec persistedSpec(ServerLevel level, OpenPlayerNpcEntity entity) {
        return new AiPlayerNpcSpec(
                new dev.soffits.openplayer.api.NpcRoleId(entity.persistedRoleId().orElseThrow()),
                new NpcOwnerId(entity.persistedOwnerId().orElseThrow()),
                new dev.soffits.openplayer.api.NpcProfileSpec(
                        entity.persistedProfileName().orElseThrow(),
                        entity.persistedProfileSkinTexture().orElse(null)
                ),
                new NpcSpawnLocation(
                        level.dimension().location().toString(),
                        entity.getX(),
                        entity.getY(),
                        entity.getZ()
                ),
                entity.allowWorldActions()
        );
    }

    private OpenPlayerNpcEntity persistedEntityFor(ServerLevel level, AiPlayerNpcSpec spec) {
        RuntimeNpcIdentityKey identityKey = RuntimeNpcIdentityKey.from(spec);
        OpenPlayerNpcEntity matchedEntity = null;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof OpenPlayerNpcEntity npcEntity) || !npcEntity.hasValidPersistedIdentity()) {
                continue;
            }
            RuntimeNpcIdentityKey persistedIdentityKey = RuntimeNpcIdentityKey.from(
                    npcEntity.persistedOwnerId().orElseThrow(),
                    npcEntity.persistedRoleId().orElseThrow(),
                    npcEntity.persistedProfileName().orElseThrow()
            );
            if (!persistedIdentityKey.equals(identityKey)) {
                continue;
            }
            if (matchedEntity == null) {
                matchedEntity = npcEntity;
            } else {
                npcEntity.stopRuntimeCommands();
                npcEntity.discard();
                OpenPlayerDebugEvents.record("npc_lifecycle", "persisted_duplicate_removed", null, null, null,
                        "duplicate_identity");
            }
        }
        return matchedEntity;
    }

    private void removeIndexes(NpcSessionId sessionId) {
        RuntimeAiPlayerNpcSession session = sessions.remove(sessionId);
        if (session != null) {
            sessionIdsByIdentity.remove(RuntimeNpcIdentityKey.from(session.spec()));
        }
    }

    private void relocate(OpenPlayerNpcEntity entity, NpcSpawnLocation location) {
        entity.teleportTo(location.x(), location.y(), location.z());
        entity.setYRot(0.0F);
        entity.setXRot(0.0F);
    }

    private void applyProfile(OpenPlayerNpcEntity entity, AiPlayerNpcSpec spec) {
        entity.setPersistedIdentity(
                spec.ownerId(),
                spec.roleId().value(),
                spec.profile().name(),
                spec.profile().skinTexture(),
                spec.allowWorldActions()
        );
        entity.setCustomName(net.minecraft.network.chat.Component.literal(spec.profile().name()));
        entity.setCustomNameVisible(true);
    }

    private String buildConversationContext(RuntimeAiPlayerNpcSession session, OpenPlayerNpcEntity entity) {
        ServerLevel level = (ServerLevel) entity.level();
        BlockPos center = entity.blockPosition();
        StringBuilder builder = new StringBuilder();
        builder.append("world: dimension=").append(level.dimension().location())
                .append(", npcPosition=").append(center.getX()).append(",").append(center.getY()).append(",").append(center.getZ())
                .append(", dayTime=").append(level.getDayTime() % 24000L)
                .append(", isDay=").append(level.isDay())
                .append(", raining=").append(level.isRaining())
                .append(", thundering=").append(level.isThundering())
                .append(", difficulty=").append(level.getDifficulty().getKey())
                .append("\n");
        builder.append("agent: status=").append(status(session.sessionId()).name().toLowerCase(java.util.Locale.ROOT))
                .append(", health=").append(Math.round(entity.getHealth())).append("/").append(Math.round(entity.getMaxHealth()))
                .append(", air=").append(entity.getAirSupply())
                .append(", mainhand=").append(itemName(entity.getMainHandItem()))
                .append(", offhand=").append(itemName(entity.getOffhandItem()))
                .append(", armor=").append(armorSummary(entity))
                .append(", inventory=").append(inventorySummary(entity))
                .append("\n");
        builder.append("nearbyBlocks: ").append(nearbyBlockSummary(level, center)).append("\n");
        builder.append("nearbyDroppedItems: ").append(nearbyDroppedItemsSummary(level, entity)).append("\n");
        builder.append("nearbyHostiles: ").append(nearbyHostilesSummary(level, entity)).append("\n");
        builder.append("nearbyPlayers: ").append(nearbyPlayersSummary(level, entity)).append("\n");
        builder.append("nearbyOpenPlayerNpcs: ").append(nearbyNpcsSummary(level, entity));
        return builder.toString();
    }

    private static String nearbyBlockSummary(ServerLevel level, BlockPos center) {
        Map<String, Integer> counts = new TreeMap<>();
        List<BlockTarget> targets = new ArrayList<>();
        int radius = 6;
        for (BlockPos blockPos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 3, radius))) {
            BlockState state = level.getBlockState(blockPos);
            if (state.isAir()) {
                continue;
            }
            Block block = state.getBlock();
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            counts.merge(id, 1, Integer::sum);
            targets.add(new BlockTarget(id, blockPos.immutable(), blockDistanceSquared(center, blockPos)));
        }
        targets.sort(Comparator.comparingDouble(BlockTarget::distanceSquared)
                .thenComparing(BlockTarget::id)
                .thenComparingInt(target -> target.position().getX())
                .thenComparingInt(target -> target.position().getY())
                .thenComparingInt(target -> target.position().getZ()));
        List<String> targetValues = new ArrayList<>();
        for (int index = 0; index < Math.min(12, targets.size()); index++) {
            BlockTarget target = targets.get(index);
            BlockPos position = target.position();
            targetValues.add(target.id() + " @ " + position.getX() + " " + position.getY() + " " + position.getZ());
        }
        String nearestTargets = targetValues.isEmpty() ? "none" : String.join(", ", targetValues);
        return "counts=[" + countedSummary(counts, 16) + "]; nearestTargets=[" + nearestTargets + "]";
    }

    private record BlockTarget(String id, BlockPos position, double distanceSquared) {
    }

    private static double blockDistanceSquared(BlockPos first, BlockPos second) {
        int dx = first.getX() - second.getX();
        int dy = first.getY() - second.getY();
        int dz = first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static String nearbyDroppedItemsSummary(ServerLevel level, OpenPlayerNpcEntity entity) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(12.0D),
                itemEntity -> itemEntity.isAlive() && !itemEntity.getItem().isEmpty())) {
            ItemStack stack = itemEntity.getItem();
            counts.merge(itemName(stack), stack.getCount(), Integer::sum);
        }
        return countedSummary(counts, 8);
    }

    private static String nearbyHostilesSummary(ServerLevel level, OpenPlayerNpcEntity entity) {
        List<String> lines = new ArrayList<>();
        for (Monster monster : level.getEntitiesOfClass(Monster.class, entity.getBoundingBox().inflate(32.0D), Monster::isAlive)) {
            lines.add(entityName(monster) + " " + relativeSummary(entity, monster));
        }
        lines.sort(String::compareTo);
        return limitedList(lines, 8);
    }

    private static String nearbyPlayersSummary(ServerLevel level, OpenPlayerNpcEntity entity) {
        List<String> lines = new ArrayList<>();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, entity.getBoundingBox().inflate(64.0D), ServerPlayer::isAlive)) {
            lines.add(player.getGameProfile().getName() + " " + relativeSummary(entity, player));
        }
        lines.sort(String::compareTo);
        return limitedList(lines, 8);
    }

    private static String nearbyNpcsSummary(ServerLevel level, OpenPlayerNpcEntity entity) {
        List<String> lines = new ArrayList<>();
        for (OpenPlayerNpcEntity npc : level.getEntitiesOfClass(OpenPlayerNpcEntity.class, entity.getBoundingBox().inflate(32.0D),
                npc -> npc.isAlive() && npc != entity)) {
            String name = npc.persistedProfileName().orElse("OpenPlayer NPC");
            lines.add(name + " " + relativeSummary(entity, npc));
        }
        lines.sort(String::compareTo);
        return limitedList(lines, 8);
    }

    private static String inventorySummary(OpenPlayerNpcEntity entity) {
        Map<String, Integer> counts = new TreeMap<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = entity.getInventoryItem(slot);
            if (!stack.isEmpty()) {
                counts.merge(itemName(stack), stack.getCount(), Integer::sum);
            }
        }
        return countedSummary(counts, 12);
    }

    private static String armorSummary(OpenPlayerNpcEntity entity) {
        List<String> values = new ArrayList<>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                values.add(slot.getName() + "=" + itemName(stack));
            }
        }
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String countedSummary(Map<String, Integer> counts, int limit) {
        if (counts.isEmpty()) {
            return "none";
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));
        List<String> values = new ArrayList<>();
        for (int index = 0; index < Math.min(limit, entries.size()); index++) {
            Map.Entry<String, Integer> entry = entries.get(index);
            values.add(entry.getKey() + " x" + entry.getValue());
        }
        if (entries.size() > limit) {
            values.add("more=" + (entries.size() - limit));
        }
        return String.join(", ", values);
    }

    private static String limitedList(List<String> values, int limit) {
        if (values.isEmpty()) {
            return "none";
        }
        List<String> limited = new ArrayList<>(values.subList(0, Math.min(limit, values.size())));
        if (values.size() > limit) {
            limited.add("more=" + (values.size() - limit));
        }
        return String.join(", ", limited);
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

    private static String relativeSummary(Entity origin, Entity target) {
        double dx = target.getX() - origin.getX();
        double dy = target.getY() - origin.getY();
        double dz = target.getZ() - origin.getZ();
        long distance = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
        return "distance=" + distance + "m direction=" + horizontalDirection(dx, dz)
                + verticalDirection(dy);
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
}
