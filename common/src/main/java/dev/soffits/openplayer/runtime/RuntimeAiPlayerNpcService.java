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
import dev.soffits.openplayer.automation.AutomationControllerSnapshot;
import dev.soffits.openplayer.conversation.ConversationContextSnapshot;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.debug.OpenPlayerRawTrace;
import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentParseException;
import dev.soffits.openplayer.intent.IntentParser;
import dev.soffits.openplayer.registry.OpenPlayerEntityTypes;
import dev.soffits.openplayer.runtime.context.RuntimeContextFormatter;
import dev.soffits.openplayer.runtime.context.RuntimeContextSnapshot;
import dev.soffits.openplayer.runtime.mode.AutomationMode;
import dev.soffits.openplayer.runtime.profile.EffectiveRuntimeProfile;
import dev.soffits.openplayer.runtime.profile.EffectiveRuntimeProfileFormatter;
import dev.soffits.openplayer.runtime.planner.InteractivePlannerConfig;
import dev.soffits.openplayer.runtime.planner.InteractivePlannerSession;
import dev.soffits.openplayer.runtime.planner.PlannerObservation;
import dev.soffits.openplayer.runtime.planner.PlannerObservationStatus;
import dev.soffits.openplayer.runtime.planner.PlannerTurnResult;
import dev.soffits.openplayer.runtime.planner.PlannerTurnStatus;
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


public final class RuntimeAiPlayerNpcService extends RuntimeAiPlayerNpcServiceSessionBase {
    public RuntimeAiPlayerNpcService(MinecraftServer server, IntentParser intentParser) {
        super(server, intentParser);
    }

    public synchronized void updateIntentParser(IntentParser intentParser) {
        if (intentParser == null) {
            throw new IllegalArgumentException("intentParser cannot be null");
        }
        this.intentParser = intentParser;
    }

    synchronized IntentParser intentParser() {
        return intentParser;
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
        cancelPlannerSession(sessionId, "despawned");
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
        if (command.intent().kind() == dev.soffits.openplayer.intent.IntentKind.STOP) {
            cancelPlannerSession(sessionId, "stop requested");
        }
        try {
            return entity.submitRuntimeCommand(command);
        } catch (IllegalArgumentException exception) {
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, exception.getMessage());
        }
    }

    @Override
    public CommandSubmissionResult submitCommandText(NpcSessionId sessionId, String input) {
        return submitPlannedCommandText(sessionId,
                new PlannerCommandTextRequest(input, "", null, null, "command_text"), null);
    }

    @Override
    public CommandSubmissionResult submitPlannedCommandText(NpcSessionId sessionId, PlannerCommandTextRequest request,
                                                            PlannerCommandTextCallbacks callbacks) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        OpenPlayerRawTrace.commandText("runtime_service", request.assignmentId(), request.characterId(),
                sessionId.value().toString(), request.userRequest());
        synchronized (this) {
            reattachPersistedNpcs();
            if (!sessions.containsKey(sessionId)) {
                OpenPlayerDebugEvents.record("command_text", "unknown_session", null, null,
                        sessionId.value().toString(), "submit_command_text");
                return new CommandSubmissionResult(CommandSubmissionStatus.UNKNOWN_SESSION, "Unknown NPC session");
            }
        }
        InteractivePlannerSession plannerSession = new InteractivePlannerSession(UUID.randomUUID(), request.userRequest(),
                request.providerPrompt(), plannerConfig);
        synchronized (this) {
            cancelPlannerSession(sessionId, "replaced by new request");
            plannerSessions.put(sessionId, plannerSession);
            if (callbacks != null) {
                plannerCallbacks.put(sessionId, callbacks);
            }
            plannerRequests.put(sessionId, request);
        }
        schedulePlannerProviderCall(sessionId, plannerSession);
        return new CommandSubmissionResult(CommandSubmissionStatus.ACCEPTED, "Command text queued for interactive provider planning");
    }

    protected static CommandIntent parseCommandText(IntentParser parser, NpcSessionId sessionId, String input,
                                                  PlannerCommandTextRequest request) {
        try {
            String source = request == null ? "command_text" : request.source();
            String assignmentId = request == null ? null : request.assignmentId();
            String characterId = request == null ? null : request.characterId();
            OpenPlayerDebugEvents.record("provider_parse", "attempted", assignmentId, characterId,
                    sessionId.value().toString(), "source=" + source + " messageLength=" + input.trim().length());
            OpenPlayerRawTrace.parseInput("runtime_service", sessionId.value().toString(), input);
            return parser.parse(input);
        } catch (IntentParseException exception) {
            throw new CommandTextParseRuntimeException(exception);
        }
    }

}
