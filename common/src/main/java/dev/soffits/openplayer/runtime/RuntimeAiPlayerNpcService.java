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
import dev.soffits.openplayer.runtime.context.RuntimeAgentSnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeContextFormatter;
import dev.soffits.openplayer.runtime.context.RuntimeContextSnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.BlockTargetSnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.RuntimeEntitySnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeNearbySnapshot.RuntimeNamedEntitySnapshot;
import dev.soffits.openplayer.runtime.context.RuntimeWorldSnapshot;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class RuntimeAiPlayerNpcService implements AiPlayerNpcService, InteractivePlannerCommandTextService {
    private final MinecraftServer server;
    private IntentParser intentParser;
    private final Map<NpcSessionId, RuntimeAiPlayerNpcSession> sessions = new LinkedHashMap<>();
    private final Map<RuntimeNpcIdentityKey, NpcSessionId> sessionIdsByIdentity = new LinkedHashMap<>();
    private final Map<NpcSessionId, InteractivePlannerSession> plannerSessions = new LinkedHashMap<>();
    private final Map<NpcSessionId, PlannerCommandTextCallbacks> plannerCallbacks = new LinkedHashMap<>();
    private final Map<NpcSessionId, PlannerCommandTextRequest> plannerRequests = new LinkedHashMap<>();
    private final InteractivePlannerConfig plannerConfig = InteractivePlannerConfig.defaults();

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

    private static CommandIntent parseCommandText(IntentParser parser, NpcSessionId sessionId, String input,
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

    private void schedulePlannerProviderCall(NpcSessionId sessionId, InteractivePlannerSession plannerSession) {
        PlannerTurnResult budget = plannerSession.beforeProviderCall();
        if (budget.status() != PlannerTurnStatus.CONTINUE) {
            if (budget.status() == PlannerTurnStatus.WAITING) {
                continuePlanner(sessionId, plannerSession, budget, 0);
                return;
            }
            finishPlannerSession(sessionId, plannerSession, budget);
            return;
        }
        IntentParser parser = intentParser();
        String prompt = plannerPrompt(sessionId, plannerSession);
        PlannerCommandTextRequest request = plannerRequest(sessionId);
        RuntimeAgentExecutor.submit(server, () -> parseCommandText(parser, sessionId, prompt, request), intent -> {
            if (!isActivePlannerSession(sessionId, plannerSession)) {
                return;
            }
            recordPlannerIntent(sessionId, intent);
            OpenPlayerDebugEvents.record("planner", "provider_success", request == null ? null : request.assignmentId(),
                    request == null ? null : request.characterId(), sessionId.value().toString(),
                    "kind=" + intent.kind().name() + " instructionLength=" + intent.instruction().length());
            OpenPlayerRawTrace.parseOutput("planner", sessionId.value().toString(),
                    "kind=" + intent.kind().name() + " priority=" + intent.priority().name()
                            + " instruction=" + intent.instruction());
            PlannerTurnResult result = plannerSession.handleIntent(intent, plannerRuntime(sessionId));
            continuePlanner(sessionId, plannerSession, result, 0);
        }, exception -> {
            if (!isActivePlannerSession(sessionId, plannerSession)) {
                return;
            }
            OpenPlayerDebugEvents.record("planner", "provider_rejected", request == null ? null : request.assignmentId(),
                    request == null ? null : request.characterId(), sessionId.value().toString(),
                    "provider response could not be parsed");
            String message = exception instanceof CommandTextParseRuntimeException parseException
                    ? parseException.parseException().getMessage()
                    : exception.getMessage();
            OpenPlayerRawTrace.parseRejection("planner", sessionId.value().toString(), prompt, message);
            plannerSession.cancel("provider parse failed");
            finishPlannerSession(sessionId, plannerSession,
                    new PlannerTurnResult(PlannerTurnStatus.STOPPED, "Planner stopped: provider parse failed"));
        });
    }

    private String plannerPrompt(NpcSessionId sessionId, InteractivePlannerSession plannerSession) {
        synchronized (this) {
            RuntimeAiPlayerNpcSession session = sessions.get(sessionId);
            if (session == null) {
                return plannerSession.nextPrompt("NPC session is unavailable");
            }
            OpenPlayerNpcEntity entity = entityFor(session);
            if (entity == null) {
                return plannerSession.nextPrompt("NPC entity is unavailable");
            }
            return plannerSession.nextPrompt(RuntimeContextFormatter.format(buildRuntimeContextSnapshot(entity))
                    + "\n" + automationObservationDetail(entity.runtimeCommandSnapshot()));
        }
    }

    private InteractivePlannerSession.PlannerRuntime plannerRuntime(NpcSessionId sessionId) {
        return new InteractivePlannerSession.PlannerRuntime() {
            @Override
            public boolean allowWorldActions() {
                synchronized (RuntimeAiPlayerNpcService.this) {
                    RuntimeAiPlayerNpcSession session = sessions.get(sessionId);
                    return session != null && session.spec().allowWorldActions();
                }
            }

            @Override
            public PlannerObservation submit(CommandIntent intent) {
                AiPlayerNpcCommand command = new AiPlayerNpcCommand(UUID.randomUUID(), intent);
                PlannerCommandTextCallbacks callbacks = plannerCallbacks(sessionId);
                CommandSubmissionResult result = submitCommand(sessionId, command);
                if (result.status() == CommandSubmissionStatus.ACCEPTED && callbacks != null) {
                    callbacks.submittedCommandRecorder().accept(command);
                }
                OpenPlayerNpcEntity entity;
                synchronized (RuntimeAiPlayerNpcService.this) {
                    RuntimeAiPlayerNpcSession session = sessions.get(sessionId);
                    entity = session == null ? null : entityFor(session);
                }
                boolean activeOrQueued = false;
                String automationDetail = "automation unavailable";
                if (entity != null) {
                    AutomationControllerSnapshot snapshot = entity.runtimeCommandSnapshot();
                    activeOrQueued = snapshot.active() || snapshot.queuedCommandCount() > 0;
                    automationDetail = automationObservationDetail(snapshot);
                }
                PlannerObservationStatus status = plannerStatus(result.status(), activeOrQueued);
                return PlannerObservation.of(status,
                        "kind=" + intent.kind().name() + " submission=" + result.status().name().toLowerCase(java.util.Locale.ROOT)
                                + " message=" + result.message() + " " + automationDetail,
                        activeOrQueued);
            }
        };
    }

    private void continuePlanner(NpcSessionId sessionId, InteractivePlannerSession plannerSession,
                                 PlannerTurnResult result, int pollCount) {
        if (result.status() == PlannerTurnStatus.CONTINUE) {
            schedulePlannerProviderCall(sessionId, plannerSession);
            return;
        }
        if (result.status() == PlannerTurnStatus.WAITING) {
            if (toolWaitBudgetExhausted(plannerSession.config(), pollCount)) {
                PlannerObservation observed = plannerObservation(sessionId);
                if (observed.activeOrQueued()) {
                    finishPlannerSession(sessionId, plannerSession,
                            plannerSession.stopWaitingBudgetExhausted(observed));
                    return;
                }
                continuePlanner(sessionId, plannerSession, plannerSession.observeWaiting(observed), pollCount);
                return;
            }
            if (plannerSession.config().maxPollsPerTool() > 0
                    && pollCount >= plannerSession.config().maxPollsPerTool()) {
                PlannerObservation observed = plannerObservation(sessionId);
                PlannerTurnResult observedResult = plannerSession.observeWaiting(observed);
                if (observedResult.status() != PlannerTurnStatus.WAITING) {
                    continuePlanner(sessionId, plannerSession, observedResult, pollCount);
                    return;
                }
            }
            schedulePlannerPoll(sessionId, plannerSession, pollCount + 1);
            return;
        }
        finishPlannerSession(sessionId, plannerSession, result);
    }

    private static boolean toolWaitBudgetExhausted(InteractivePlannerConfig config, int pollCount) {
        long waitedMillis = Math.multiplyExact((long) pollCount, config.pollDelay().toMillis());
        return waitedMillis >= config.maxToolWait().toMillis();
    }

    private void schedulePlannerPoll(NpcSessionId sessionId, InteractivePlannerSession plannerSession, int pollCount) {
        RuntimeAgentExecutor.submit(server, () -> {
            try {
                Thread.sleep(plannerSession.config().pollDelay().toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("planner poll interrupted", exception);
            }
            return pollCount;
        }, ignored -> {
            if (!isActivePlannerSession(sessionId, plannerSession)) {
                return;
            }
            PlannerTurnResult result = plannerSession.observeWaiting(plannerObservation(sessionId));
            continuePlanner(sessionId, plannerSession, result, pollCount);
        }, exception -> {
            if (!isActivePlannerSession(sessionId, plannerSession)) {
                return;
            }
            plannerSession.cancel("planner poll failed");
            finishPlannerSession(sessionId, plannerSession,
                    new PlannerTurnResult(PlannerTurnStatus.STOPPED, "Planner stopped: poll failed"));
        });
    }

    private synchronized PlannerObservation plannerObservation(NpcSessionId sessionId) {
        RuntimeAiPlayerNpcSession session = sessions.get(sessionId);
        if (session == null) {
            return PlannerObservation.of(PlannerObservationStatus.UNAVAILABLE, "NPC session is unavailable", false);
        }
        OpenPlayerNpcEntity entity = entityFor(session);
        if (entity == null) {
            return PlannerObservation.of(PlannerObservationStatus.UNAVAILABLE, "NPC entity is unavailable", false);
        }
        AutomationControllerSnapshot snapshot = entity.runtimeCommandSnapshot();
        boolean activeOrQueued = snapshot.active() || snapshot.queuedCommandCount() > 0;
        PlannerObservationStatus status = activeOrQueued ? PlannerObservationStatus.ACTIVE : plannerSnapshotStatus(snapshot);
        return PlannerObservation.of(status, automationObservationDetail(snapshot), activeOrQueued);
    }

    private synchronized boolean isActivePlannerSession(NpcSessionId sessionId, InteractivePlannerSession plannerSession) {
        return plannerSessions.get(sessionId) == plannerSession && !plannerSession.isCancelled();
    }

    private synchronized void cancelPlannerSession(NpcSessionId sessionId, String reason) {
        InteractivePlannerSession plannerSession = plannerSessions.remove(sessionId);
        plannerCallbacks.remove(sessionId);
        plannerRequests.remove(sessionId);
        if (plannerSession != null) {
            plannerSession.cancel(reason);
            OpenPlayerDebugEvents.record("planner", "cancelled", null, null,
                    sessionId.value().toString(), OpenPlayerDebugEvents.sanitizeDetail(reason));
        }
    }

    private synchronized void finishPlannerSession(NpcSessionId sessionId, InteractivePlannerSession plannerSession,
                                                    PlannerTurnResult result) {
        PlannerCommandTextCallbacks callbacks = null;
        if (plannerSessions.get(sessionId) == plannerSession) {
            plannerSessions.remove(sessionId);
            callbacks = plannerCallbacks.remove(sessionId);
            plannerRequests.remove(sessionId);
        }
        OpenPlayerDebugEvents.record("planner", result.status().name().toLowerCase(java.util.Locale.ROOT), null, null,
                sessionId.value().toString(), OpenPlayerDebugEvents.sanitizeDetail(result.message()));
        if (callbacks != null) {
            CommandSubmissionStatus status = result.status() == PlannerTurnStatus.FINISHED
                    ? CommandSubmissionStatus.ACCEPTED : CommandSubmissionStatus.REJECTED;
            callbacks.completion().accept(new CommandSubmissionResult(status, result.message()));
        }
    }

    private synchronized PlannerCommandTextRequest plannerRequest(NpcSessionId sessionId) {
        return plannerRequests.get(sessionId);
    }

    private synchronized PlannerCommandTextCallbacks plannerCallbacks(NpcSessionId sessionId) {
        return plannerCallbacks.get(sessionId);
    }

    private void recordPlannerIntent(NpcSessionId sessionId, CommandIntent intent) {
        PlannerCommandTextCallbacks callbacks = plannerCallbacks(sessionId);
        if (callbacks != null) {
            callbacks.acceptedIntentRecorder().accept(intent);
        }
    }

    private static PlannerObservationStatus plannerStatus(CommandSubmissionStatus status, boolean activeOrQueued) {
        return switch (status) {
            case ACCEPTED -> activeOrQueued ? PlannerObservationStatus.QUEUED : PlannerObservationStatus.COMPLETED;
            case REJECTED -> PlannerObservationStatus.REJECTED;
            case UNKNOWN_SESSION -> PlannerObservationStatus.UNAVAILABLE;
            case UNAVAILABLE -> PlannerObservationStatus.UNAVAILABLE;
        };
    }

    private static PlannerObservationStatus plannerSnapshotStatus(AutomationControllerSnapshot snapshot) {
        return switch (snapshot.monitorStatus()) {
            case COMPLETED, IDLE -> PlannerObservationStatus.COMPLETED;
            case CANCELLED -> PlannerObservationStatus.CANCELLED;
            case TIMED_OUT, STUCK -> PlannerObservationStatus.TIMED_OUT;
            case ACTIVE -> PlannerObservationStatus.ACTIVE;
        };
    }

    private static String automationObservationDetail(AutomationControllerSnapshot snapshot) {
        String active = snapshot.active() ? snapshot.activeKind().name() : "idle";
        return "automation active=" + active
                + " queued=" + snapshot.queuedCommandCount()
                + " paused=" + snapshot.paused()
                + " monitor=" + snapshot.monitorStatus().name().toLowerCase(java.util.Locale.ROOT)
                + " reason=" + safeStatusValue(snapshot.monitorReason())
                + " ticks=" + snapshot.elapsedTicks() + "/" + snapshot.maxTicks();
    }

    private static final class CommandTextParseRuntimeException extends RuntimeException {
        private final IntentParseException parseException;

        private CommandTextParseRuntimeException(IntentParseException parseException) {
            super(parseException);
            this.parseException = parseException;
        }

        private IntentParseException parseException() {
            return parseException;
        }
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

    public synchronized List<String> selectedRuntimeStatusLines(NpcSessionId sessionId, String assignmentId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        String safeAssignmentId = safeToken(assignmentId == null || assignmentId.isBlank() ? "unknown" : assignmentId.trim(), 48);
        reattachPersistedNpcs();
        RuntimeAiPlayerNpcSession session = sessions.get(sessionId);
        if (session == null) {
            return List.of(limitStatusLine("selected_assignment=" + safeAssignmentId
                    + " source=selected_npc status=despawned active=idle queued=0"));
        }
        OpenPlayerNpcEntity entity = entityFor(session);
        if (entity == null) {
            return List.of(limitStatusLine("selected_assignment=" + safeAssignmentId
                    + " source=selected_npc status=entity_unavailable active=idle queued=0"));
        }
        AutomationControllerSnapshot snapshot = entity.runtimeCommandSnapshot();
        String active = snapshot.active() ? snapshot.activeKind().name() : "idle";
        return List.of(
                limitStatusLine("selected_assignment=" + safeAssignmentId + " source=selected_npc status=active active="
                        + active + " queued=" + snapshot.queuedCommandCount() + " paused=" + snapshot.paused()),
                limitStatusLine("selected_controller=" + snapshot.monitorStatus().name().toLowerCase(java.util.Locale.ROOT)
                        + " reason=" + safeStatusValue(snapshot.monitorReason()) + " ticks="
                        + snapshot.elapsedTicks() + "/" + snapshot.maxTicks()),
                limitStatusLine("selected_queue source=selected_npc kinds=" + queuedKinds(snapshot))
        );
    }

    synchronized void clearRuntimeSessions() {
        for (Map.Entry<NpcSessionId, InteractivePlannerSession> entry : plannerSessions.entrySet()) {
            entry.getValue().cancel("runtime cleared");
        }
        plannerSessions.clear();
        plannerCallbacks.clear();
        plannerRequests.clear();
        for (RuntimeAiPlayerNpcSession session : sessions.values()) {
            OpenPlayerNpcEntity entity = entityFor(session);
            if (entity != null) {
                entity.stopRuntimeCommands();
            }
        }
        sessions.clear();
        sessionIdsByIdentity.clear();
    }

    private static String queuedKinds(AutomationControllerSnapshot snapshot) {
        if (snapshot.queuedKinds().isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < snapshot.queuedKinds().size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(snapshot.queuedKinds().get(index).name());
        }
        return builder.toString();
    }

    private static String safeStatusValue(String value) {
        return safeToken(value == null || value.isBlank() ? "none" : value, 64);
    }

    private static String safeToken(String value, int maxLength) {
        StringBuilder builder = new StringBuilder();
        String source = value == null ? "unknown" : value.trim();
        for (int index = 0; index < source.length() && builder.length() < maxLength; index++) {
            char character = source.charAt(index);
            if ((character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9') || character == '_' || character == '-'
                    || character == ':' || character == '.' || character == ',' || character == '=') {
                builder.append(character);
            } else {
                builder.append('_');
            }
        }
        return builder.isEmpty() ? "unknown" : builder.toString();
    }

    private static String limitStatusLine(String line) {
        if (line.length() <= 120) {
            return line;
        }
        return line.substring(0, 106) + "... truncated";
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
                "unsupported",
                "unsupported",
                activeEffectsSummary(entity),
                physicalStatus(entity),
                "unsupported",
                Boolean.toString(entity.isSprinting()),
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

    private static String activeEffectsSummary(OpenPlayerNpcEntity entity) {
        List<String> values = new ArrayList<>();
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            String id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect()).toString();
            values.add(id + " amplifier=" + effect.getAmplifier() + " durationTicks=" + boundedEffectDuration(effect.getDuration()));
        }
        values.sort(String::compareTo);
        if (values.isEmpty()) {
            return "none";
        }
        int limit = Math.min(8, values.size());
        List<String> limited = new ArrayList<>(values.subList(0, limit));
        if (values.size() > limit) {
            limited.add("more=" + (values.size() - limit));
        }
        return String.join(", ", limited);
    }

    private static int boundedEffectDuration(int duration) {
        return Math.max(0, Math.min(duration, 72000));
    }

    private static String physicalStatus(OpenPlayerNpcEntity entity) {
        return "onFire=" + entity.isOnFire()
                + ", inWater=" + entity.isInWater()
                + ", onGround=" + entity.onGround()
                + ", fallDistance=" + Math.round(Math.max(0.0F, Math.min(entity.fallDistance, 256.0F)));
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
