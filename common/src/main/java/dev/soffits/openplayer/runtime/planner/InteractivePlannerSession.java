package dev.soffits.openplayer.runtime.planner;

import dev.soffits.openplayer.aicore.MinecraftPrimitiveTools;
import dev.soffits.openplayer.aicore.CapabilityScopedToolDocs;
import dev.soffits.openplayer.aicore.ToolCall;
import dev.soffits.openplayer.aicore.ToolResult;
import dev.soffits.openplayer.aicore.ToolResultStatus;
import dev.soffits.openplayer.aicore.ToolValidationContext;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.ProviderPlanIntentCodec;
import dev.soffits.openplayer.runtime.planner.PlannerFailureClassifier.PlannerFailureKind;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidationResult;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class InteractivePlannerSession {
    private final UUID id;
    private final String userRequest;
    private final String providerPromptContext;
    private final InteractivePlannerConfig config;
    private final long startedAtMillis;
    private final List<String> observations = new ArrayList<>();
    private int iterations;
    private int providerCalls;
    private int toolSteps;
    private int noProgressCount;
    private boolean waitingForPrimitive;
    private boolean cancelled;
    private String cancelReason = "cancelled";

    public InteractivePlannerSession(UUID id, String userRequest, InteractivePlannerConfig config) {
        this(id, userRequest, "", config);
    }

    public InteractivePlannerSession(UUID id, String userRequest, String providerPromptContext,
                                     InteractivePlannerConfig config) {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException("userRequest cannot be blank");
        }
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        this.id = id;
        this.userRequest = userRequest.trim();
        this.providerPromptContext = providerPromptContext == null ? "" : providerPromptContext.trim();
        this.config = config;
        this.startedAtMillis = System.currentTimeMillis();
    }

    public UUID id() {
        return id;
    }

    public InteractivePlannerConfig config() {
        return config;
    }

    public void cancel(String reason) {
        cancelled = true;
        cancelReason = bound(reason == null || reason.isBlank() ? "cancelled" : reason, 96);
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public PlannerTurnResult beforeProviderCall() {
        PlannerTurnResult stop = stopIfNeeded();
        if (stop.status() != PlannerTurnStatus.CONTINUE) {
            return stop;
        }
        if (waitingForPrimitive) {
            return new PlannerTurnResult(PlannerTurnStatus.WAITING, "waiting for active or queued primitive");
        }
        providerCalls++;
        iterations++;
        return new PlannerTurnResult(PlannerTurnStatus.CONTINUE, "provider call accepted");
    }

    public String nextPrompt(String runtimeContext) {
        StringBuilder builder = new StringBuilder();
        builder.append("OpenPlayer interactive planner request.\n");
        builder.append("User request: ").append(userRequest).append("\n");
        builder.append("Act as a long-horizon autonomous planner over bounded Minecraft primitives. ");
        builder.append("Return one compact JSON action only: one primitive tool, chat, unavailable, or a compatibility bounded plan. ");
        builder.append("Inspect runtime inventory, nearby dropped items, nearby blocks, hostiles, players, active automation, and prior objective observations before choosing. ");
        builder.append("Decompose the user goal into the next safe primitive, wait for real observations, then continue with the next primitive. ");
        builder.append("Gather missing materials, craft missing tools or items only through available primitive capabilities, and keep pursuing explicit goals across turns. ");
        builder.append("After retryable failures, use observations and runtime context to choose an alternative primitive or diagnostic step. ");
        builder.append("Ask the user or return unavailable only when the goal is ambiguous, needs authorization, is unsafe, policy-denied, or missing a real adapter. ");
        builder.append("Treat local unstuck, danger avoidance, pickup recovery, and self-defense summaries as runtime observations; do not micromanage tick-level combat or avoidance. ");
        builder.append("Do not claim completion unless an observation says completed. Do not use hidden Java macros or removed macro tools.\n");
        String scopedToolDocs = CapabilityScopedToolDocs.forObjective(userRequest);
        if (!scopedToolDocs.isBlank()) {
            builder.append("Objective-scoped available tool docs: ").append(bound(scopedToolDocs, 240)).append("\n");
        }
        if (!providerPromptContext.isBlank()) {
            builder.append("Companion conversation context:\n")
                    .append(bound(providerPromptContext, config.maxPromptCharacters() / 2)).append("\n");
        }
        if (runtimeContext != null && !runtimeContext.isBlank()) {
            builder.append("Runtime context:\n").append(bound(runtimeContext, config.maxObservationCharacters())).append("\n");
        }
        if (!observations.isEmpty()) {
            builder.append("Observations:\n");
            for (String observation : observations) {
                builder.append("- ").append(observation).append("\n");
            }
        }
        return bound(builder.toString(), config.maxPromptCharacters());
    }

    public PlannerTurnResult handleIntent(CommandIntent intent, PlannerRuntime runtime) {
        if (intent == null) {
            throw new IllegalArgumentException("intent cannot be null");
        }
        if (runtime == null) {
            throw new IllegalArgumentException("runtime cannot be null");
        }
        PlannerTurnResult stop = stopIfNeeded();
        if (stop.status() != PlannerTurnStatus.CONTINUE) {
            return stop;
        }
        if (intent.kind() == IntentKind.CHAT) {
            addObservation(PlannerObservationStatus.COMPLETED, "final_chat=" + intent.instruction(), false);
            return new PlannerTurnResult(PlannerTurnStatus.FINISHED, bound(intent.instruction(), 160));
        }
        if (intent.kind() == IntentKind.UNAVAILABLE) {
            addObservation(PlannerObservationStatus.UNAVAILABLE, "final_unavailable=" + intent.instruction(), false);
            return new PlannerTurnResult(PlannerTurnStatus.FINISHED, bound(intent.instruction(), 160));
        }
        if (intent.kind() == IntentKind.PROVIDER_PLAN) {
            return handleProviderPlan(intent, runtime);
        }
        return handlePrimitive(intent, runtime);
    }

    public PlannerTurnResult observeWaiting(PlannerObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("observation cannot be null");
        }
        addObservation(observation.status(), observation.detail(), observation.activeOrQueued());
        PlannerTurnResult stop = stopWaitingIfNeeded(observation.activeOrQueued());
        if (stop.status() != PlannerTurnStatus.CONTINUE) {
            return stop;
        }
        if (observation.activeOrQueued()) {
            waitingForPrimitive = true;
            return new PlannerTurnResult(PlannerTurnStatus.WAITING, observation.detail());
        }
        waitingForPrimitive = false;
        PlannerFailureKind failureKind = PlannerFailureClassifier.classify(observation.status(), observation.detail());
        if (failureKind == PlannerFailureKind.TERMINAL) {
            return new PlannerTurnResult(PlannerTurnStatus.STOPPED,
                    "Planner stopped after terminal primitive observation: " + bound(observation.detail(), 96));
        }
        if (failureKind == PlannerFailureKind.RETRYABLE) {
            return stopAfterNoProgress(observation.detail());
        }
        noProgressCount = 0;
        return stopIfNeeded();
    }

    public PlannerTurnResult stopWaitingBudgetExhausted(PlannerObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("observation cannot be null");
        }
        addObservation(observation.status(), observation.detail(), observation.activeOrQueued());
        addObservation(PlannerObservationStatus.TIMED_OUT,
                observation.activeOrQueued()
                        ? "tool wait budget exhausted while primitive remains active or queued"
                        : "tool wait budget exhausted after primitive became inactive",
                observation.activeOrQueued());
        waitingForPrimitive = observation.activeOrQueued();
        return new PlannerTurnResult(PlannerTurnStatus.STOPPED,
                observation.activeOrQueued()
                        ? "Planner stopped: tool wait budget exhausted while primitive remains active or queued"
                        : "Planner stopped: tool wait budget exhausted");
    }

    private PlannerTurnResult handleProviderPlan(CommandIntent intent, PlannerRuntime runtime) {
        List<CommandIntent> steps;
        try {
            steps = ProviderPlanIntentCodec.decode(intent.instruction());
        } catch (IllegalArgumentException exception) {
            addObservation(PlannerObservationStatus.REJECTED, "provider_plan rejected: " + exception.getMessage(), false);
            return stopAfterNoProgress("provider plan rejected");
        }
        PlannerTurnResult result = new PlannerTurnResult(PlannerTurnStatus.CONTINUE, "provider plan expanded");
        for (CommandIntent step : steps) {
            result = handlePrimitive(step, runtime);
            if (result.status() != PlannerTurnStatus.CONTINUE) {
                return result;
            }
        }
        return result;
    }

    private PlannerTurnResult handlePrimitive(CommandIntent intent, PlannerRuntime runtime) {
        if (toolSteps >= config.maxToolSteps()) {
            addObservation(PlannerObservationStatus.TIMED_OUT, "tool step budget exhausted", false);
            return new PlannerTurnResult(PlannerTurnStatus.STOPPED, "Planner stopped: tool step budget exhausted");
        }
        Optional<ToolCall> toolCall = MinecraftPrimitiveTools.toToolCall(intent);
        if (toolCall.isEmpty()) {
            addObservation(PlannerObservationStatus.REJECTED, "non-primitive intent rejected: " + intent.kind().name(), false);
            return stopAfterNoProgress("non-primitive intent rejected");
        }
        ToolResult toolValidation = MinecraftPrimitiveTools.validate(toolCall.get(), new ToolValidationContext(runtime.allowWorldActions()));
        if (toolValidation.status() != ToolResultStatus.SUCCESS) {
            addObservation(statusFromToolResult(toolValidation), "tool rejected: " + toolValidation.reason(), false);
            return new PlannerTurnResult(PlannerTurnStatus.STOPPED,
                    "Planner stopped after terminal tool validation: " + bound(toolValidation.reason(), 96));
        }
        RuntimeIntentValidationResult validation = RuntimeIntentValidator.validate(intent, runtime.allowWorldActions());
        if (!validation.isAccepted()) {
            addObservation(PlannerObservationStatus.REJECTED, "runtime policy rejected: " + validation.message(), false);
            return new PlannerTurnResult(PlannerTurnStatus.STOPPED,
                    "Planner stopped after terminal runtime policy rejection: " + bound(validation.message(), 96));
        }
        toolSteps++;
        PlannerObservation submission = runtime.submit(intent);
        addObservation(submission.status(), submission.detail(), submission.activeOrQueued());
        if (submission.status() == PlannerObservationStatus.REJECTED
                || submission.status() == PlannerObservationStatus.FAILED
                || submission.status() == PlannerObservationStatus.UNAVAILABLE
                || submission.status() == PlannerObservationStatus.TIMED_OUT) {
            PlannerFailureKind failureKind = PlannerFailureClassifier.classify(submission.status(), submission.detail());
            if (failureKind == PlannerFailureKind.TERMINAL) {
                return new PlannerTurnResult(PlannerTurnStatus.STOPPED,
                        "Planner stopped after terminal primitive result: " + bound(submission.detail(), 96));
            }
            return stopAfterNoProgress(submission.detail());
        }
        noProgressCount = 0;
        if (submission.activeOrQueued()) {
            waitingForPrimitive = true;
            return new PlannerTurnResult(PlannerTurnStatus.WAITING, submission.detail());
        }
        waitingForPrimitive = false;
        return stopIfNeeded();
    }

    private PlannerTurnResult stopAfterNoProgress(String message) {
        noProgressCount++;
        if (noProgressCount >= config.maxNoProgressCount()) {
            return new PlannerTurnResult(PlannerTurnStatus.STOPPED,
                    "Planner stopped after repeated no-progress result: " + bound(message, 96));
        }
        return stopIfNeeded();
    }

    private PlannerTurnResult stopIfNeeded() {
        return stopIfNeeded(false);
    }

    private PlannerTurnResult stopIfNeeded(boolean activeOrQueued) {
        if (cancelled) {
            addObservation(PlannerObservationStatus.CANCELLED, cancelReason, activeOrQueued);
            return new PlannerTurnResult(PlannerTurnStatus.STOPPED, "Planner cancelled: " + cancelReason);
        }
        if (iterations >= config.maxIterations()) {
            addObservation(PlannerObservationStatus.TIMED_OUT, "iteration budget exhausted", activeOrQueued);
            return new PlannerTurnResult(PlannerTurnStatus.STOPPED, "Planner stopped: iteration budget exhausted");
        }
        if (providerCalls >= config.maxProviderCalls()) {
            addObservation(PlannerObservationStatus.TIMED_OUT, "provider call budget exhausted", activeOrQueued);
            return new PlannerTurnResult(PlannerTurnStatus.STOPPED, "Planner stopped: provider call budget exhausted");
        }
        long elapsedMillis = System.currentTimeMillis() - startedAtMillis;
        if (elapsedMillis > config.maxWallTime().toMillis()) {
            addObservation(PlannerObservationStatus.TIMED_OUT, "wall time budget exhausted", activeOrQueued);
            return new PlannerTurnResult(PlannerTurnStatus.STOPPED, "Planner stopped: wall time budget exhausted");
        }
        return new PlannerTurnResult(PlannerTurnStatus.CONTINUE, "continue");
    }

    private PlannerTurnResult stopWaitingIfNeeded(boolean activeOrQueued) {
        if (cancelled) {
            addObservation(PlannerObservationStatus.CANCELLED, cancelReason, activeOrQueued);
            return new PlannerTurnResult(PlannerTurnStatus.STOPPED, "Planner cancelled: " + cancelReason);
        }
        long elapsedMillis = System.currentTimeMillis() - startedAtMillis;
        if (elapsedMillis > config.maxWallTime().toMillis()) {
            addObservation(PlannerObservationStatus.TIMED_OUT, "wall time budget exhausted", activeOrQueued);
            return new PlannerTurnResult(PlannerTurnStatus.STOPPED, "Planner stopped: wall time budget exhausted");
        }
        return new PlannerTurnResult(PlannerTurnStatus.CONTINUE, "continue");
    }

    private static PlannerObservationStatus statusFromToolResult(ToolResult result) {
        if (result.status() == ToolResultStatus.FAILED) {
            return PlannerObservationStatus.FAILED;
        }
        return PlannerObservationStatus.REJECTED;
    }

    private void addObservation(PlannerObservationStatus status, String detail, boolean activeOrQueued) {
        String observation = status.name().toLowerCase(java.util.Locale.ROOT) + " "
                + bound(detail, config.maxObservationCharacters() / 2)
                + " activeOrQueued=" + activeOrQueued;
        observations.add(bound(observation, config.maxObservationCharacters()));
        while (joinedObservationLength() > config.maxObservationCharacters() && observations.size() > 1) {
            observations.remove(0);
        }
    }

    private int joinedObservationLength() {
        int length = 0;
        for (String observation : observations) {
            length += observation.length() + 1;
        }
        return length;
    }

    private static String bound(String value, int maxLength) {
        String source = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        if (source.length() <= maxLength) {
            return source;
        }
        if (maxLength <= 1) {
            return source.substring(0, maxLength);
        }
        return source.substring(0, maxLength - 1) + "~";
    }

    public interface PlannerRuntime {
        boolean allowWorldActions();

        PlannerObservation submit(CommandIntent intent);
    }
}
