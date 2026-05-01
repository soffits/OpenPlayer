package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.AiPlayerNpcService;
import dev.soffits.openplayer.api.AiPlayerNpcSession;
import dev.soffits.openplayer.api.AiPlayerNpcSpec;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.api.NpcProfileSpec;
import dev.soffits.openplayer.api.NpcRoleId;
import dev.soffits.openplayer.api.NpcSessionId;
import dev.soffits.openplayer.api.NpcSessionStatus;
import dev.soffits.openplayer.api.NpcSpawnLocation;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentParseException;
import dev.soffits.openplayer.intent.IntentParser;
import dev.soffits.openplayer.registry.OpenPlayerEntityTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class RuntimeAiPlayerNpcService implements AiPlayerNpcService {
    private final MinecraftServer server;
    private final IntentParser intentParser;
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

    public synchronized void restorePersistedSessions() {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof OpenPlayerNpcEntity npcEntity && npcEntity.hasValidPersistedIdentity()) {
                    adoptPersistedEntity(level, npcEntity);
                }
            }
        }
    }

    @Override
    public synchronized AiPlayerNpcSession spawn(AiPlayerNpcSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec cannot be null");
        }
        RuntimeNpcIdentityKey identityKey = RuntimeNpcIdentityKey.from(spec);
        NpcSessionId existingSessionId = sessionIdsByIdentity.get(identityKey);
        RuntimeAiPlayerNpcSession existingSession = existingSession(identityKey);
        ServerLevel level = levelFor(spec.spawnLocation());
        if (existingSession != null) {
            OpenPlayerNpcEntity existingEntity = entityFor(existingSession);
            if (existingEntity != null) {
                if (existingEntity.level().dimension().equals(level.dimension())) {
                    relocate(existingEntity, spec.spawnLocation());
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

        OpenPlayerNpcEntity entity = spawnEntity(level, spec);
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
        entity.setPersistedIdentity(spec.ownerId(), spec.roleId().value(), spec.profile().name());
        entity.setCustomName(net.minecraft.network.chat.Component.literal(spec.profile().name()));
        entity.setCustomNameVisible(true);

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
        RuntimeAiPlayerNpcSession session = sessions.get(sessionId);
        if (session == null) {
            return false;
        }
        removeIndexes(sessionId);
        Entity entity = entityFor(session);
        if (entity != null) {
            entity.discard();
        }
        return true;
    }

    @Override
    public synchronized List<AiPlayerNpcSession> listSessions() {
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
        if (!sessions.containsKey(sessionId)) {
            return new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION, "Unknown NPC session");
        }
        RuntimeAiPlayerNpcSession session = sessions.get(sessionId);
        OpenPlayerNpcEntity entity = entityFor(session);
        if (entity == null) {
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
        synchronized (this) {
            if (!sessions.containsKey(sessionId)) {
                return new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION, "Unknown NPC session");
            }
        }
        CommandIntent intent;
        try {
            intent = intentParser.parse(input);
        } catch (IntentParseException exception) {
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, "Unable to parse command text");
        }
        return submitCommand(sessionId, new AiPlayerNpcCommand(UUID.randomUUID(), intent));
    }

    @Override
    public synchronized NpcSessionStatus status(NpcSessionId sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
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
        for (RuntimeAiPlayerNpcSession session : sessions.values()) {
            if (session.spec().ownerId().value().equals(ownerId)) {
                OpenPlayerNpcEntity entity = entityFor(session);
                if (entity != null) {
                    entity.stopRuntimeCommands();
                }
            }
        }
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

    private void adoptPersistedEntity(ServerLevel level, OpenPlayerNpcEntity entity) {
        UUID ownerId = entity.persistedOwnerId().orElseThrow();
        String roleId = entity.persistedRoleId().orElseThrow();
        String profileName = entity.persistedProfileName().orElseThrow();
        RuntimeNpcIdentityKey identityKey = RuntimeNpcIdentityKey.from(ownerId, roleId, profileName);
        if (sessionIdsByIdentity.containsKey(identityKey)) {
            entity.discard();
            return;
        }
        NpcSpawnLocation location = new NpcSpawnLocation(
                level.dimension().location().toString(),
                entity.getX(),
                entity.getY(),
                entity.getZ()
        );
        AiPlayerNpcSpec spec = new AiPlayerNpcSpec(
                new NpcRoleId(roleId),
                new NpcOwnerId(ownerId),
                new NpcProfileSpec(profileName),
                location
        );
        entity.setRuntimeOwnerId(spec.ownerId());
        NpcSessionId sessionId = new NpcSessionId(UUID.randomUUID());
        RuntimeAiPlayerNpcSession session = new RuntimeAiPlayerNpcSession(this, sessionId, spec, entity.getUUID());
        sessions.put(sessionId, session);
        sessionIdsByIdentity.put(identityKey, sessionId);
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
}
