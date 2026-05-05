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
import dev.soffits.openplayer.runtime.planner.PlannerPrimitiveProgress;
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


abstract class RuntimeAiPlayerNpcServicePlannerBase implements AiPlayerNpcService, InteractivePlannerCommandTextService {
    protected final MinecraftServer server;
    protected IntentParser intentParser;
    protected final Map<NpcSessionId, RuntimeAiPlayerNpcSession> sessions = new LinkedHashMap<>();
    protected final Map<RuntimeNpcIdentityKey, NpcSessionId> sessionIdsByIdentity = new LinkedHashMap<>();
    protected final Map<NpcSessionId, InteractivePlannerSession> plannerSessions = new LinkedHashMap<>();
    protected final Map<NpcSessionId, PlannerCommandTextCallbacks> plannerCallbacks = new LinkedHashMap<>();
    protected final Map<NpcSessionId, PlannerCommandTextRequest> plannerRequests = new LinkedHashMap<>();
    protected final InteractivePlannerConfig plannerConfig = InteractivePlannerConfig.defaults();

    protected RuntimeAiPlayerNpcServicePlannerBase(MinecraftServer server, IntentParser intentParser) {
        if (server == null) { throw new IllegalArgumentException("server cannot be null"); }
        if (intentParser == null) { throw new IllegalArgumentException("intentParser cannot be null"); }
        this.server = server;
        this.intentParser = intentParser;
    }

    protected void schedulePlannerProviderCall(NpcSessionId sessionId, InteractivePlannerSession plannerSession) {
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

    protected String plannerPrompt(NpcSessionId sessionId, InteractivePlannerSession plannerSession) {
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

    protected InteractivePlannerSession.PlannerRuntime plannerRuntime(NpcSessionId sessionId) {
        return new InteractivePlannerSession.PlannerRuntime() {
            @Override
            public boolean allowWorldActions() {
                synchronized (this) {
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
                synchronized (this) {
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
                if (result.status() == CommandSubmissionStatus.ACCEPTED && activeOrQueued && callbacks != null) {
                    callbacks.progress().accept(new CommandSubmissionResult(
                            CommandSubmissionStatus.ACCEPTED, PlannerPrimitiveProgress.format(intent)));
                }
                return PlannerObservation.of(status,
                        "kind=" + intent.kind().name() + " submission=" + result.status().name().toLowerCase(java.util.Locale.ROOT)
                                + " message=" + result.message() + " " + automationDetail,
                        activeOrQueued);
            }
        };
    }

    protected void continuePlanner(NpcSessionId sessionId, InteractivePlannerSession plannerSession,
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

    protected static boolean toolWaitBudgetExhausted(InteractivePlannerConfig config, int pollCount) {
        long waitedMillis = Math.multiplyExact((long) pollCount, config.pollDelay().toMillis());
        return waitedMillis >= config.maxToolWait().toMillis();
    }

    protected void schedulePlannerPoll(NpcSessionId sessionId, InteractivePlannerSession plannerSession, int pollCount) {
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

    protected synchronized PlannerObservation plannerObservation(NpcSessionId sessionId) {
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

    protected synchronized boolean isActivePlannerSession(NpcSessionId sessionId, InteractivePlannerSession plannerSession) {
        return plannerSessions.get(sessionId) == plannerSession && !plannerSession.isCancelled();
    }

    protected synchronized void cancelPlannerSession(NpcSessionId sessionId, String reason) {
        InteractivePlannerSession plannerSession = plannerSessions.remove(sessionId);
        plannerCallbacks.remove(sessionId);
        plannerRequests.remove(sessionId);
        if (plannerSession != null) {
            plannerSession.cancel(reason);
            OpenPlayerDebugEvents.record("planner", "cancelled", null, null,
                    sessionId.value().toString(), OpenPlayerDebugEvents.sanitizeDetail(reason));
        }
    }

    protected synchronized void finishPlannerSession(NpcSessionId sessionId, InteractivePlannerSession plannerSession,
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

    protected synchronized PlannerCommandTextRequest plannerRequest(NpcSessionId sessionId) {
        return plannerRequests.get(sessionId);
    }

    protected synchronized PlannerCommandTextCallbacks plannerCallbacks(NpcSessionId sessionId) {
        return plannerCallbacks.get(sessionId);
    }

    protected void recordPlannerIntent(NpcSessionId sessionId, CommandIntent intent) {
        PlannerCommandTextCallbacks callbacks = plannerCallbacks(sessionId);
        if (callbacks != null) {
            callbacks.acceptedIntentRecorder().accept(intent);
        }
    }

    protected static PlannerObservationStatus plannerStatus(CommandSubmissionStatus status, boolean activeOrQueued) {
        return switch (status) {
            case ACCEPTED -> activeOrQueued ? PlannerObservationStatus.QUEUED : PlannerObservationStatus.COMPLETED;
            case REJECTED -> PlannerObservationStatus.REJECTED;
            case UNKNOWN_SESSION -> PlannerObservationStatus.UNAVAILABLE;
            case UNAVAILABLE -> PlannerObservationStatus.UNAVAILABLE;
        };
    }

    protected static PlannerObservationStatus plannerSnapshotStatus(AutomationControllerSnapshot snapshot) {
        return switch (snapshot.monitorStatus()) {
            case COMPLETED, IDLE -> PlannerObservationStatus.COMPLETED;
            case CANCELLED -> PlannerObservationStatus.CANCELLED;
            case TIMED_OUT, STUCK -> PlannerObservationStatus.TIMED_OUT;
            case ACTIVE -> PlannerObservationStatus.ACTIVE;
        };
    }

    protected static String automationObservationDetail(AutomationControllerSnapshot snapshot) {
        String active = snapshot.active() ? snapshot.activeKind().name() : "idle";
        return "automation active=" + active
                + " queued=" + snapshot.queuedCommandCount()
                + " paused=" + snapshot.paused()
                + " monitor=" + snapshot.monitorStatus().name().toLowerCase(java.util.Locale.ROOT)
                + " reason=" + safeStatusValue(snapshot.monitorReason())
                + " ticks=" + snapshot.elapsedTicks() + "/" + snapshot.maxTicks();
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

    static final class CommandTextParseRuntimeException extends RuntimeException {
        private final IntentParseException parseException;

        CommandTextParseRuntimeException(IntentParseException parseException) {
            super(parseException);
            this.parseException = parseException;
        }

        IntentParseException parseException() {
            return parseException;
        }
    }

    abstract IntentParser intentParser();

    protected abstract OpenPlayerNpcEntity entityFor(RuntimeAiPlayerNpcSession session);

    protected abstract RuntimeContextSnapshot buildRuntimeContextSnapshot(OpenPlayerNpcEntity entity);

    protected static String safeStatusValue(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        StringBuilder builder = new StringBuilder(Math.min(value.length(), 80));
        for (int index = 0; index < value.length() && builder.length() < 80; index++) {
            char character = value.charAt(index);
            builder.append(Character.isISOControl(character) ? '_' : character);
        }
        return builder.toString();
    }
}
