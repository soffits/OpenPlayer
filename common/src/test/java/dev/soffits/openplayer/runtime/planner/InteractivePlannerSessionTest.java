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
        stopsWhenToolStepBudgetIsExhausted();
        rejectsRemovedToolBeforePlannerExecution();
        cancelsActiveSession();
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

    private static InteractivePlannerConfig testConfig(int maxIterations, int maxProviderCalls, int maxToolSteps) {
        return new InteractivePlannerConfig(maxIterations, maxProviderCalls, maxToolSteps, 1200, 4000, 1,
                Duration.ofSeconds(10L), Duration.ofMillis(1L), 1);
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
