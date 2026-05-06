package dev.soffits.openplayer.runtime.planner;

import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentParseException;
import dev.soffits.openplayer.intent.IntentPriority;
import dev.soffits.openplayer.intent.ProviderBackedIntentParser;
import dev.soffits.openplayer.intent.ProviderIntent;
import java.time.Duration;
import java.util.UUID;

public final class InteractivePlannerSessionTest {
    private InteractivePlannerSessionTest() {
    }

    public static void main(String[] args) throws Exception {
        feedsToolObservationIntoSecondProviderIteration();
        activePrimitiveBlocksNextProviderIteration();
        pollBudgetBoundaryObservationKeepsWaitingWhenStillActive();
        activePrimitiveStopsTruthfullyWhenToolWaitBudgetExhausts();
        completedWaitingObservationAllowsNextProviderIteration();
        retryableSubmissionFailureAllowsAlternativeProviderIteration();
        staleBreakRepeatAfterDroppedItemIsRejectedWithPickupGuidance();
        terminalBreakFailureTargetIsNotImmediatelyRepeated();
        terminalPolicyFailureStopsWithoutRetry();
        promptRequiresLongHorizonPrimitiveDecomposition();
        classifierSeparatesRetryableAndTerminalFailures();
        defaultBudgetsAllowSlowMultiTurnPlanning();
        tinyWallTimeBudgetStopsSession();
        stopsWhenToolStepBudgetIsExhausted();
        rejectsRemovedToolBeforePlannerExecution();
        cancelsActiveSession();
        formatsPrimitiveProgressAsLocalizedFallbackWithoutRawProviderArguments();
    }

    private static void activePrimitiveBlocksNextProviderIteration() {
        InteractivePlannerSession session = new InteractivePlannerSession(
                UUID.randomUUID(),
                "break a nearby block",
                testConfig(4, 4, 4)
        );
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "first provider call must be allowed");
        PlannerTurnResult first = session.handleIntent(
                new CommandIntent(IntentKind.BREAK_BLOCK, IntentPriority.NORMAL, "1 64 1"),
                new FakeRuntime(true, PlannerObservation.of(
                        PlannerObservationStatus.QUEUED,
                        "kind=BREAK_BLOCK submission=accepted automation active=BREAK_BLOCK queued=0 monitor=active",
                        true
                ))
        );
        require(first.status() == PlannerTurnStatus.WAITING,
                "active break_block submission must wait");
        PlannerTurnResult secondProviderAttempt = session.beforeProviderCall();
        require(secondProviderAttempt.status() == PlannerTurnStatus.WAITING,
                "provider call must not be accepted while primitive is active or queued");
    }

    private static void pollBudgetBoundaryObservationKeepsWaitingWhenStillActive() {
        InteractivePlannerSession session = new InteractivePlannerSession(
                UUID.randomUUID(),
                "break a nearby block",
                testConfig(4, 4, 4)
        );
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "first provider call must be allowed");
        PlannerTurnResult first = session.handleIntent(
                new CommandIntent(IntentKind.BREAK_BLOCK, IntentPriority.NORMAL, "1 64 1"),
                new FakeRuntime(true, PlannerObservation.of(
                        PlannerObservationStatus.QUEUED,
                        "kind=BREAK_BLOCK submission=accepted automation active=BREAK_BLOCK queued=0 monitor=active",
                        true
                ))
        );
        require(first.status() == PlannerTurnStatus.WAITING,
                "active break_block submission must wait");
        PlannerTurnResult boundary = session.observeWaiting(PlannerObservation.of(
                PlannerObservationStatus.ACTIVE,
                "automation active=BREAK_BLOCK queued=0 monitor=active ticks=61/120",
                true
        ));
        require(boundary.status() == PlannerTurnStatus.WAITING,
                "poll budget boundary must keep waiting when snapshot is still active");
        require(session.beforeProviderCall().status() == PlannerTurnStatus.WAITING,
                "provider call must remain blocked after active boundary observation");
    }

    private static void completedWaitingObservationAllowsNextProviderIteration() {
        InteractivePlannerSession session = new InteractivePlannerSession(
                UUID.randomUUID(),
                "break a nearby block",
                testConfig(4, 4, 4)
        );
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "first provider call must be allowed");
        PlannerTurnResult first = session.handleIntent(
                new CommandIntent(IntentKind.BREAK_BLOCK, IntentPriority.NORMAL, "1 64 1"),
                new FakeRuntime(true, PlannerObservation.of(
                        PlannerObservationStatus.QUEUED,
                        "kind=BREAK_BLOCK submission=accepted automation active=BREAK_BLOCK queued=0 monitor=active",
                        true
                ))
        );
        require(first.status() == PlannerTurnStatus.WAITING,
                "active break_block submission must wait");
        PlannerTurnResult completed = session.observeWaiting(PlannerObservation.of(
                PlannerObservationStatus.COMPLETED,
                "automation active=idle queued=0 monitor=completed ticks=106/120",
                false
        ));
        require(completed.status() == PlannerTurnStatus.CONTINUE,
                "completed primitive observation must allow replanning");
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "provider call must be allowed after primitive completion");
        String prompt = session.nextPrompt("refreshed context");
        require(prompt.contains("monitor=completed"),
                "next prompt must include completion observation");
        require(prompt.contains("refreshed context"),
                "next prompt must include refreshed runtime context");
    }

    private static void activePrimitiveStopsTruthfullyWhenToolWaitBudgetExhausts() {
        InteractivePlannerSession session = new InteractivePlannerSession(
                UUID.randomUUID(),
                "break a nearby block",
                testConfig(4, 4, 4)
        );
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "first provider call must be allowed");
        PlannerTurnResult first = session.handleIntent(
                new CommandIntent(IntentKind.BREAK_BLOCK, IntentPriority.NORMAL, "1 64 1"),
                new FakeRuntime(true, PlannerObservation.of(
                        PlannerObservationStatus.QUEUED,
                        "kind=BREAK_BLOCK submission=accepted automation active=BREAK_BLOCK queued=0 monitor=active",
                        true
                ))
        );
        require(first.status() == PlannerTurnStatus.WAITING,
                "active break_block submission must wait");
        PlannerTurnResult stopped = session.stopWaitingBudgetExhausted(PlannerObservation.of(
                PlannerObservationStatus.ACTIVE,
                "automation active=BREAK_BLOCK queued=0 monitor=active ticks=400/120",
                true
        ));
        require(stopped.status() == PlannerTurnStatus.STOPPED,
                "tool wait exhaustion must stop the planner instead of replanning");
        require(stopped.message().contains("primitive remains active or queued"),
                "tool wait exhaustion message must preserve active state truthfully");
    }

    private static void feedsToolObservationIntoSecondProviderIteration() {
        InteractivePlannerSession session = new InteractivePlannerSession(
                UUID.randomUUID(),
                "check status then answer",
                testConfig(4, 4, 4)
        );
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "first provider call must be allowed");
        String firstPrompt = session.nextPrompt("context one");
        require(firstPrompt.contains("User request: check status then answer"),
                "first prompt must include original request");
        PlannerTurnResult first = session.handleIntent(
                new CommandIntent(IntentKind.REPORT_STATUS, IntentPriority.NORMAL, ""),
                new FakeRuntime(true, PlannerObservation.of(
                        PlannerObservationStatus.COMPLETED,
                        "kind=REPORT_STATUS submission=accepted automation active=idle queued=0 monitor=completed",
                        false
                ))
        );
        require(first.status() == PlannerTurnStatus.CONTINUE,
                "completed report_status observation should allow replanning");
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "second provider call must be allowed");
        String secondPrompt = session.nextPrompt("context two");
        require(secondPrompt.contains("kind=REPORT_STATUS submission=accepted"),
                "second prompt must include tool observation");
        PlannerTurnResult second = session.handleIntent(
                new CommandIntent(IntentKind.CHAT, IntentPriority.NORMAL, "Status checked."),
                new FakeRuntime(true, PlannerObservation.of(PlannerObservationStatus.COMPLETED, "unused", false))
        );
        require(second.status() == PlannerTurnStatus.FINISHED, "final chat must finish the planner");
    }

    private static void retryableSubmissionFailureAllowsAlternativeProviderIteration() {
        InteractivePlannerSession session = new InteractivePlannerSession(
                UUID.randomUUID(),
                "collect nearby logs",
                testConfig(6, 6, 4, 4)
        );
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "first provider call must be allowed");
        PlannerTurnResult failed = session.handleIntent(
                new CommandIntent(IntentKind.MOVE, IntentPriority.NORMAL, "1 64 1"),
                new FakeRuntime(true, PlannerObservation.of(
                        PlannerObservationStatus.REJECTED,
                        "kind=MOVE submission=rejected message=MOVE target chunk is not loaded",
                        false
                ))
        );
        require(failed.status() == PlannerTurnStatus.CONTINUE,
                "retryable navigation rejection must allow another provider strategy");
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "provider call must be allowed after retryable primitive rejection");
        String prompt = session.nextPrompt("nearby loaded block target available");
        require(prompt.contains("target chunk is not loaded"),
                "retry prompt must include the rejected primitive observation");
    }

    private static void staleBreakRepeatAfterDroppedItemIsRejectedWithPickupGuidance() {
        InteractivePlannerSession session = new InteractivePlannerSession(
                UUID.randomUUID(),
                "help me make a furnace; gather and craft tools yourself",
                testConfig(8, 8, 8, 4)
        );
        CommandIntent digSpruceLog = new CommandIntent(IntentKind.BREAK_BLOCK, IntentPriority.NORMAL, "23 69 -5");
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "first provider call must be allowed");
        PlannerTurnResult started = session.handleIntent(
                digSpruceLog,
                new FakeRuntime(true, PlannerObservation.of(
                        PlannerObservationStatus.QUEUED,
                        "kind=BREAK_BLOCK submission=accepted automation active=BREAK_BLOCK queued=0 monitor=active",
                        true
                ))
        );
        require(started.status() == PlannerTurnStatus.WAITING,
                "break primitive must wait while queued or active");
        PlannerTurnResult completed = session.observeWaiting(PlannerObservation.of(
                PlannerObservationStatus.COMPLETED,
                "automation active=idle queued=0 monitor=completed reason=block=minecraft:spruce_log target=23,69,-5 inventory_delta=none nearby_drop_delta=minecraft:spruce_log x1 ticks=12/2400",
                false
        ));
        require(completed.status() == PlannerTurnStatus.CONTINUE,
                "break completion must allow provider replanning");
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "provider must be allowed to choose pickup after break completion");
        PlannerTurnResult staleRepeat = session.handleIntent(
                digSpruceLog,
                new FakeRuntime(true, PlannerObservation.of(PlannerObservationStatus.COMPLETED, "unexpected", false))
        );
        require(staleRepeat.status() == PlannerTurnStatus.CONTINUE,
                "stale repeated dig should be retryable instead of terminal or executed");
        String prompt = session.nextPrompt("nearbyDroppedItems: minecraft:spruce_log x1");
        require(prompt.contains("stale_break_target rejected: target=23,69,-5"),
                "retry prompt must show stale target rejection");
        require(prompt.contains("pickup_items_nearby matching=minecraft:spruce_log"),
                "retry prompt must guide the provider toward dropped-item pickup");
        require(prompt.contains("nearbyDroppedItems: minecraft:spruce_log x1"),
                "retry prompt must preserve truthful nearby dropped item context");
    }

    private static void terminalBreakFailureTargetIsNotImmediatelyRepeated() {
        InteractivePlannerSession session = new InteractivePlannerSession(
                UUID.randomUUID(),
                "collect useful resources",
                testConfig(8, 8, 8, 4)
        );
        CommandIntent missingBlock = new CommandIntent(IntentKind.BREAK_BLOCK, IntentPriority.NORMAL, "23 69 -5");
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "first provider call must be allowed");
        PlannerTurnResult failed = session.handleIntent(
                missingBlock,
                new FakeRuntime(true, PlannerObservation.of(
                        PlannerObservationStatus.REJECTED,
                        "kind=BREAK_BLOCK submission=rejected message=block_not_breakable",
                        false
                ))
        );
        require(failed.status() == PlannerTurnStatus.CONTINUE,
                "block_not_breakable should allow a different provider strategy");
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "provider must be allowed to choose an alternative after block_not_breakable");
        PlannerTurnResult repeated = session.handleIntent(
                missingBlock,
                new FakeRuntime(true, PlannerObservation.of(PlannerObservationStatus.COMPLETED, "unexpected", false))
        );
        require(repeated.status() == PlannerTurnStatus.CONTINUE,
                "immediate repeat of terminal break target should be retryable and not executed");
        require(session.nextPrompt("context").contains("was already processed or terminal in this objective"),
                "prompt must explain the terminal target guard");
    }

    private static void terminalPolicyFailureStopsWithoutRetry() {
        InteractivePlannerSession session = new InteractivePlannerSession(
                UUID.randomUUID(),
                "break a block",
                testConfig(6, 6, 4)
        );
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "first provider call must be allowed");
        PlannerTurnResult result = session.handleIntent(
                new CommandIntent(IntentKind.BREAK_BLOCK, IntentPriority.NORMAL, "1 64 1"),
                new FakeRuntime(true, PlannerObservation.of(
                        PlannerObservationStatus.REJECTED,
                        "runtime policy rejected: world actions are disabled",
                        false
                ))
        );
        require(result.status() == PlannerTurnStatus.STOPPED,
                "terminal policy rejection must stop without repeated provider retries");
        require(result.message().contains("terminal"), "terminal stop message must be explicit");
    }

    private static void promptRequiresLongHorizonPrimitiveDecomposition() {
        InteractivePlannerSession session = new InteractivePlannerSession(
                UUID.randomUUID(),
                "make useful progress",
                testConfig(4, 4, 4)
        );
        String prompt = session.nextPrompt("runtime context");
        require(prompt.contains("long-horizon autonomous planner"),
                "planner prompt must require long-horizon autonomy");
        require(prompt.contains("After retryable failures"),
                "planner prompt must instruct alternative strategy after retryable failures");
        require(prompt.contains("missing a real adapter"),
                "planner prompt must reserve user asks for real capability gaps");
    }

    private static void classifierSeparatesRetryableAndTerminalFailures() {
        require(PlannerFailureClassifier.classify(
                PlannerObservationStatus.REJECTED,
                "kind=MOVE submission=rejected message=MOVE target chunk is not loaded"
        ) == PlannerFailureClassifier.PlannerFailureKind.RETRYABLE,
                "unloaded navigation target should remain retryable");
        require(PlannerFailureClassifier.classify(
                PlannerObservationStatus.TIMED_OUT,
                "automation active=idle queued=0 monitor=stuck reason=path blocked"
        ) == PlannerFailureClassifier.PlannerFailureKind.RETRYABLE,
                "stuck/path timeout should allow a new strategy within planner budgets");
        require(PlannerFailureClassifier.classify(
                PlannerObservationStatus.TIMED_OUT,
                "tool wait budget exhausted while primitive remains active or queued"
        ) == PlannerFailureClassifier.PlannerFailureKind.RETRYABLE,
                "timeout details without a terminal class should remain retryable to the classifier");
        require(PlannerFailureClassifier.classify(
                PlannerObservationStatus.FAILED,
                "inventory_full while picking up item"
        ) == PlannerFailureClassifier.PlannerFailureKind.RETRYABLE,
                "underscored inventory runtime reasons should normalize to retryable");
        require(PlannerFailureClassifier.classify(
                PlannerObservationStatus.REJECTED,
                "runtime policy rejected: World actions are disabled for this OpenPlayer character"
        ) == PlannerFailureClassifier.PlannerFailureKind.TERMINAL,
                "world action policy rejection must be terminal");
        require(PlannerFailureClassifier.classify(
                PlannerObservationStatus.UNAVAILABLE,
                "missing adapter for villager trading UI"
        ) == PlannerFailureClassifier.PlannerFailureKind.TERMINAL,
                "missing adapter should be terminal instead of retrying forever");
    }

    private static void stopsWhenToolStepBudgetIsExhausted() {
        InteractivePlannerSession session = new InteractivePlannerSession(
                UUID.randomUUID(),
                "do too many things",
                testConfig(4, 4, 1)
        );
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "first provider call must be allowed");
        PlannerTurnResult first = session.handleIntent(
                new CommandIntent(IntentKind.REPORT_STATUS, IntentPriority.NORMAL, ""),
                new FakeRuntime(true, PlannerObservation.of(PlannerObservationStatus.COMPLETED, "status complete", false))
        );
        require(first.status() == PlannerTurnStatus.CONTINUE, "first tool step should fit budget");
        require(session.beforeProviderCall().status() == PlannerTurnStatus.CONTINUE,
                "second provider call must be allowed before step budget check");
        PlannerTurnResult second = session.handleIntent(
                new CommandIntent(IntentKind.INVENTORY_QUERY, IntentPriority.NORMAL, ""),
                new FakeRuntime(true, PlannerObservation.of(PlannerObservationStatus.COMPLETED, "inventory complete", false))
        );
        require(second.status() == PlannerTurnStatus.STOPPED,
                "second tool step must stop after step budget is exhausted");
        require(second.message().contains("tool step budget"), "budget stop message must be truthful");
    }

    private static void defaultBudgetsAllowSlowMultiTurnPlanning() {
        InteractivePlannerConfig config = InteractivePlannerConfig.defaults();
        require(config.maxWallTime().compareTo(Duration.ofMinutes(8L)) >= 0,
                "default wall time must cover slow multi-turn provider latency");
        require(config.maxProviderCalls() >= 24,
                "default provider call budget must cover long-horizon planner turns");
        require(config.maxToolSteps() >= 24,
                "default tool step budget must cover long-horizon primitive chains");
        require(config.maxIterations() > 0, "iteration budget must remain finite");
        require(config.maxProviderCalls() > 0, "provider call budget must remain finite");
        require(config.maxToolSteps() > 0, "tool step budget must remain finite");
        require(config.maxWallTime().compareTo(Duration.ZERO) > 0, "wall time budget must remain finite and positive");
        require(config.maxToolWait().compareTo(Duration.ZERO) > 0, "tool wait budget must remain finite and positive");
        require(config.maxPollsPerTool() > 0, "poll budget must remain finite");
        require(config.maxProviderCalls() <= 64, "provider call budget must stay bounded");
        require(config.maxToolSteps() <= 64, "tool step budget must stay bounded");
        require(config.maxWallTime().compareTo(Duration.ofMinutes(30L)) <= 0, "wall time budget must stay bounded");
    }

    private static void tinyWallTimeBudgetStopsSession() throws InterruptedException {
        InteractivePlannerSession session = new InteractivePlannerSession(
                UUID.randomUUID(),
                "wait too long",
                new InteractivePlannerConfig(4, 4, 4, 1200, 4000, 1,
                        Duration.ofMillis(1L), Duration.ofMillis(1L), Duration.ofSeconds(2L), 1)
        );
        Thread.sleep(10L);
        PlannerTurnResult result = session.beforeProviderCall();
        require(result.status() == PlannerTurnStatus.STOPPED,
                "tiny wall time budget must stop the planner");
        require(result.message().contains("wall time budget"),
                "wall time exhaustion message must be truthful");
    }

    private static void rejectsRemovedToolBeforePlannerExecution() {
        ProviderBackedIntentParser parser = new ProviderBackedIntentParser(
                input -> ProviderIntent.structuredTool("NORMAL", "{\"tool\":\"CHOP_TREE\",\"args\":{}}")
        );
        try {
            parser.parse("ignored");
            throw new AssertionError("removed narrow tool must not reach planner execution");
        } catch (IntentParseException exception) {
            require(!exception.getMessage().isBlank(),
                    "removed uppercase macro tool must be rejected by provider tool parser");
        }
    }

    private static void cancelsActiveSession() {
        InteractivePlannerSession session = new InteractivePlannerSession(
                UUID.randomUUID(),
                "move somewhere",
                testConfig(4, 4, 4)
        );
        session.cancel("stop requested");
        PlannerTurnResult result = session.beforeProviderCall();
        require(result.status() == PlannerTurnStatus.STOPPED, "cancelled session must not call provider");
        require(result.message().contains("stop requested"), "cancel reason must be preserved");
    }

    private static void formatsPrimitiveProgressAsLocalizedFallbackWithoutRawProviderArguments() {
        PlannerPrimitiveProgress.Display collect = PlannerPrimitiveProgress.fallback(
                new CommandIntent(IntentKind.COLLECT_ITEMS, IntentPriority.NORMAL, "minecraft:spruce_log 16"));
        require("commands.openplayer.progress.primitive.started.collect_items.item".equals(collect.translationKey()),
                "collect progress must use a translation key");
        require(collect.args().length == 1 && "spruce logs".equals(collect.args()[0]),
                "collect progress may expose only a safe item placeholder");
        PlannerPrimitiveProgress.Display rawCollect = PlannerPrimitiveProgress.fallback(
                new CommandIntent(IntentKind.COLLECT_ITEMS, IntentPriority.NORMAL,
                        "matching=minecraft:spruce_log maxDistance=16"));
        require("commands.openplayer.progress.primitive.started.collect_items".equals(rawCollect.translationKey()),
                "raw collect progress must use the generic collect category key");
        require(rawCollect.args().length == 0,
                "collect progress must not expose raw provider argument syntax");
        require("commands.openplayer.progress.primitive.started.inventory".equals(PlannerPrimitiveProgress.fallback(
                        new CommandIntent(IntentKind.CRAFT, IntentPriority.NORMAL, "minecraft:furnace 1")).translationKey()),
                "inventory primitives must use a category translation key");
        require("commands.openplayer.progress.primitive.started.move".equals(PlannerPrimitiveProgress.fallback(
                        new CommandIntent(IntentKind.GOTO, IntentPriority.NORMAL, "0 64 0")).translationKey()),
                "movement primitives must use a category translation key");
    }

    private static InteractivePlannerConfig testConfig(int maxIterations, int maxProviderCalls, int maxToolSteps) {
        return testConfig(maxIterations, maxProviderCalls, maxToolSteps, 1);
    }

    private static InteractivePlannerConfig testConfig(int maxIterations, int maxProviderCalls, int maxToolSteps,
                                                       int maxNoProgressCount) {
        return new InteractivePlannerConfig(maxIterations, maxProviderCalls, maxToolSteps, 1200, 4000,
                maxNoProgressCount,
                Duration.ofSeconds(10L), Duration.ofMillis(1L), Duration.ofSeconds(2L), 1);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record FakeRuntime(boolean allowWorldActions,
                               PlannerObservation observation) implements InteractivePlannerSession.PlannerRuntime {
        @Override
        public PlannerObservation submit(CommandIntent intent) {
            return observation;
        }
    }
}
