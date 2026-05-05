package dev.soffits.openplayer.runtime.mode;

import java.util.ArrayList;
import java.util.List;

public final class ModeScheduler {
    public List<ModeDecision> evaluate(ModeSchedulerConfig config, ModeSignal signal) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        if (signal == null) {
            throw new IllegalArgumentException("signal cannot be null");
        }
        List<ModeDecision> decisions = new ArrayList<>();
        decisions.add(unstuck(config, signal));
        decisions.add(itemCollection(config, signal));
        decisions.add(dangerAvoidance(config, signal));
        decisions.add(selfDefense(config, signal));
        return List.copyOf(decisions);
    }

    public List<AutomationMode> enabledModes(ModeSchedulerConfig config, ModeSignal signal) {
        List<AutomationMode> modes = new ArrayList<>();
        for (ModeDecision decision : evaluate(config, signal)) {
            if (decision.enabled()) {
                modes.add(decision.mode());
            }
        }
        return List.copyOf(modes);
    }

    private static ModeDecision unstuck(ModeSchedulerConfig config, ModeSignal signal) {
        if (!config.autonomousPlannerEnabled()) {
            return new ModeDecision(AutomationMode.UNSTUCK, false, "autonomous planner disabled");
        }
        if (!signal.stuck()) {
            return new ModeDecision(AutomationMode.UNSTUCK, false, "not stuck");
        }
        return new ModeDecision(AutomationMode.UNSTUCK, true, "bounded local unstuck allowed");
    }

    private static ModeDecision itemCollection(ModeSchedulerConfig config, ModeSignal signal) {
        if (!config.autonomousPlannerEnabled()) {
            return new ModeDecision(AutomationMode.OPPORTUNISTIC_ITEM_COLLECTION, false, "autonomous planner disabled");
        }
        if (!config.allowWorldActions() || !config.taskAllowsItemCollection()) {
            return new ModeDecision(AutomationMode.OPPORTUNISTIC_ITEM_COLLECTION, false, "item collection not permitted");
        }
        if (!signal.droppedItemNearby()) {
            return new ModeDecision(AutomationMode.OPPORTUNISTIC_ITEM_COLLECTION, false, "no dropped item nearby");
        }
        return new ModeDecision(AutomationMode.OPPORTUNISTIC_ITEM_COLLECTION, true, "bounded local pickup allowed");
    }

    private static ModeDecision dangerAvoidance(ModeSchedulerConfig config, ModeSignal signal) {
        if (!config.autonomousPlannerEnabled()) {
            return new ModeDecision(AutomationMode.DANGER_AVOIDANCE, false, "autonomous planner disabled");
        }
        if (!config.movementProfile().avoidHostiles() && !signal.lowHealth()) {
            return new ModeDecision(AutomationMode.DANGER_AVOIDANCE, false, "profile does not avoid hostiles");
        }
        if (!signal.creeperNearby() && !signal.lowHealth()) {
            return new ModeDecision(AutomationMode.DANGER_AVOIDANCE, false, "no immediate danger");
        }
        return new ModeDecision(AutomationMode.DANGER_AVOIDANCE, true, "pause or avoid locally");
    }

    private static ModeDecision selfDefense(ModeSchedulerConfig config, ModeSignal signal) {
        if (!config.autonomousPlannerEnabled()) {
            return new ModeDecision(AutomationMode.SELF_DEFENSE, false, "autonomous planner disabled");
        }
        if (!config.allowWorldActions() || !config.taskAllowsCombat()) {
            return new ModeDecision(AutomationMode.SELF_DEFENSE, false, "combat not permitted");
        }
        if (config.movementProfile().entities().defendAgainst().isEmpty()) {
            return new ModeDecision(AutomationMode.SELF_DEFENSE, false, "profile has no self-defense targets");
        }
        if (!signal.hostileAttacking()) {
            return new ModeDecision(AutomationMode.SELF_DEFENSE, false, "no hostile attack");
        }
        return new ModeDecision(AutomationMode.SELF_DEFENSE, true, "simple local self-defense allowed");
    }
}
