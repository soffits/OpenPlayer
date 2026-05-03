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
import dev.soffits.openplayer.runtime.context.RuntimeAgentSnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeContextFormatter;
import dev.soffits.openplayer.runtime.context.RuntimeContextSnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.BlockTargetSnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.RuntimeEntitySnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.RuntimeNamedEntitySnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeWorldSnapshot;
import java.util.ArrayList;
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
        RuntimeContextSnapshot snapshot = buildRuntimeContextSnapshot(entity);
        return new ConversationContextSnapshot(RuntimeContextFormatter.format(snapshot));
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

    private RuntimeContextSnapshot buildRuntimeContextSnapshot(OpenPlayerNpcEntity entity) {
        ServerLevel level = (ServerLevel) entity.level();
        BlockPos center = entity.blockPosition();
        RuntimeWorldSnapshot world = new RuntimeWorldSnapshot(
                level.dimension().location().toString(),
                center.getX(),
                center.getY(),
                center.getZ(),
                level.getDayTime() % 24000L,
                level.isDay(),
                level.isRaining(),
                level.isThundering(),
                level.getDifficulty().getKey()
        );
        RuntimeAgentSnapshot agent = new RuntimeAgentSnapshot(
                NpcSessionStatus.ACTIVE.name().toLowerCase(java.util.Locale.ROOT),
                Math.round(entity.getHealth()),
                Math.round(entity.getMaxHealth()),
                entity.getAirSupply(),
                itemName(entity.getMainHandItem()),
                itemName(entity.getOffhandItem()),
                armorSummary(entity),
                inventorySummary(entity)
        );
        RuntimeNearbySnapshot nearby = nearbySnapshot(level, entity, center);
        return new RuntimeContextSnapshot(world, agent, nearby);
    }

    private static RuntimeNearbySnapshot nearbySnapshot(ServerLevel level, OpenPlayerNpcEntity entity, BlockPos center) {
        BlockScanSnapshot blocks = nearbyBlocks(level, center);
        return new RuntimeNearbySnapshot(
                blocks.counts(),
                blocks.targets(),
                nearbyDroppedItemCounts(level, entity),
                nearbyHostiles(level, entity),
                nearbyPlayers(level, entity),
                nearbyNpcs(level, entity)
        );
    }

    private static BlockScanSnapshot nearbyBlocks(ServerLevel level, BlockPos center) {
        Map<String, Integer> counts = new TreeMap<>();
        List<BlockTargetSnapshot> targets = new ArrayList<>();
        int radius = 6;
        for (BlockPos blockPos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 3, radius))) {
            BlockState state = level.getBlockState(blockPos);
            if (state.isAir()) {
                continue;
            }
            Block block = state.getBlock();
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            counts.merge(id, 1, Integer::sum);
            targets.add(new BlockTargetSnapshot(
                    id,
                    blockPos.getX(),
                    blockPos.getY(),
                    blockPos.getZ(),
                    blockDistanceSquared(center, blockPos)
            ));
        }
        return new BlockScanSnapshot(counts, targets);
    }

    private record BlockScanSnapshot(Map<String, Integer> counts, List<BlockTargetSnapshot> targets) {
    }

    private static double blockDistanceSquared(BlockPos first, BlockPos second) {
        int dx = first.getX() - second.getX();
        int dy = first.getY() - second.getY();
        int dz = first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static Map<String, Integer> nearbyDroppedItemCounts(ServerLevel level, OpenPlayerNpcEntity entity) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(12.0D),
                itemEntity -> itemEntity.isAlive() && !itemEntity.getItem().isEmpty())) {
            ItemStack stack = itemEntity.getItem();
            counts.merge(itemName(stack), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static List<RuntimeEntitySnapshot> nearbyHostiles(ServerLevel level, OpenPlayerNpcEntity entity) {
        List<RuntimeEntitySnapshot> hostiles = new ArrayList<>();
        for (Monster monster : level.getEntitiesOfClass(Monster.class, entity.getBoundingBox().inflate(32.0D), Monster::isAlive)) {
            hostiles.add(new RuntimeEntitySnapshot(entityName(monster), distanceMeters(entity, monster), direction(entity, monster)));
        }
        return hostiles;
    }

    private static List<RuntimeNamedEntitySnapshot> nearbyPlayers(ServerLevel level, OpenPlayerNpcEntity entity) {
        List<RuntimeNamedEntitySnapshot> players = new ArrayList<>();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, entity.getBoundingBox().inflate(64.0D), ServerPlayer::isAlive)) {
            players.add(new RuntimeNamedEntitySnapshot(player.getGameProfile().getName(), distanceMeters(entity, player), direction(entity, player)));
        }
        return players;
    }

    private static List<RuntimeNamedEntitySnapshot> nearbyNpcs(ServerLevel level, OpenPlayerNpcEntity entity) {
        List<RuntimeNamedEntitySnapshot> npcs = new ArrayList<>();
        for (OpenPlayerNpcEntity npc : level.getEntitiesOfClass(OpenPlayerNpcEntity.class, entity.getBoundingBox().inflate(32.0D),
                npc -> npc.isAlive() && npc != entity)) {
            String name = npc.persistedProfileName().orElse("OpenPlayer NPC");
            npcs.add(new RuntimeNamedEntitySnapshot(name, distanceMeters(entity, npc), direction(entity, npc)));
        }
        return npcs;
    }

    private static Map<String, Integer> inventorySummary(OpenPlayerNpcEntity entity) {
        Map<String, Integer> counts = new TreeMap<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = entity.getInventoryItem(slot);
            if (!stack.isEmpty()) {
                counts.merge(itemName(stack), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static List<String> armorSummary(OpenPlayerNpcEntity entity) {
        List<String> values = new ArrayList<>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                values.add(slot.getName() + "=" + itemName(stack));
            }
        }
        return values;
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

    private static long distanceMeters(Entity origin, Entity target) {
        double dx = target.getX() - origin.getX();
        double dy = target.getY() - origin.getY();
        double dz = target.getZ() - origin.getZ();
        return Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private static String direction(Entity origin, Entity target) {
        double dx = target.getX() - origin.getX();
        double dy = target.getY() - origin.getY();
        double dz = target.getZ() - origin.getZ();
        return horizontalDirection(dx, dz) + verticalDirection(dy);
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
