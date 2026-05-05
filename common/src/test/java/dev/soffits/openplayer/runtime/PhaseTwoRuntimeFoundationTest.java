package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.automation.policy.BlockSafetyPolicy;
import dev.soffits.openplayer.automation.policy.EntitySafetyPolicy;
import dev.soffits.openplayer.automation.policy.MovementProfile;
import dev.soffits.openplayer.runtime.action.ActionLifecycleState;
import dev.soffits.openplayer.runtime.action.ActionResultSummary;
import dev.soffits.openplayer.runtime.action.LongActionTracker;
import dev.soffits.openplayer.runtime.mode.AutomationMode;
import dev.soffits.openplayer.runtime.mode.ModeScheduler;
import dev.soffits.openplayer.runtime.mode.ModeSchedulerConfig;
import dev.soffits.openplayer.runtime.mode.ModeSignal;
import dev.soffits.openplayer.runtime.mode.ObstacleDecisionKind;
import dev.soffits.openplayer.runtime.mode.ObstacleSafetyClassifier;
import dev.soffits.openplayer.runtime.objective.BlockObjectiveValidator;
import dev.soffits.openplayer.runtime.objective.BuildObjectiveValidator;
import dev.soffits.openplayer.runtime.objective.DeliveryObjectiveValidator;
import dev.soffits.openplayer.runtime.objective.InventoryObjectiveValidator;
import dev.soffits.openplayer.runtime.objective.ObjectiveProgress;
import dev.soffits.openplayer.runtime.planner.PlannerJobStatus;
import dev.soffits.openplayer.runtime.planner.PlannerMailbox;
import dev.soffits.openplayer.runtime.profile.EffectiveRuntimeProfile;
import dev.soffits.openplayer.runtime.profile.EffectiveRuntimeProfileFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceLocation;

public final class PhaseTwoRuntimeFoundationTest {
    private PhaseTwoRuntimeFoundationTest() {
    }

    public static void main(String[] args) {
        modeSchedulerAppliesPolicyAndTaskGates();
        obstacleClassifierOnlyClearsLowRiskPolicyMatches();
        actionLifecycleExposesTransitionsAndRetryBudget();
        plannerMailboxAllowsOneActiveJobPerOwnerAndCancels();
        objectiveValidatorsRequireRealObservedState();
        effectiveProfileStatusDoesNotExposeSecretsOrRawProviderTrace();
    }

    private static void modeSchedulerAppliesPolicyAndTaskGates() {
        ModeScheduler scheduler = new ModeScheduler();
        MovementProfile profile = profile(true, true);
        ModeSignal signal = new ModeSignal(true, true, true, false, true);
        List<AutomationMode> enabled = scheduler.enabledModes(
                new ModeSchedulerConfig(true, true, true, true, true, profile), signal);

        require(enabled.contains(AutomationMode.UNSTUCK), "stuck signal must enable bounded unstuck mode");
        require(enabled.contains(AutomationMode.OPPORTUNISTIC_ITEM_COLLECTION), "item signal must enable gated pickup mode");
        require(enabled.contains(AutomationMode.DANGER_AVOIDANCE), "creeper signal must enable local avoidance");
        require(enabled.contains(AutomationMode.SELF_DEFENSE), "hostile attack must enable gated self-defense");

        List<AutomationMode> disabledByPolicy = scheduler.enabledModes(
                new ModeSchedulerConfig(true, false, true, true, true, profile), signal);
        require(!disabledByPolicy.contains(AutomationMode.OPPORTUNISTIC_ITEM_COLLECTION),
                "pickup mode must require world-action permission");
        require(!disabledByPolicy.contains(AutomationMode.SELF_DEFENSE),
                "self-defense mode must require combat and world-action permission");

        List<AutomationMode> disabledPlanner = scheduler.enabledModes(
                new ModeSchedulerConfig(false, true, true, true, true, profile), signal);
        require(disabledPlanner.isEmpty(), "explicit planner disable must suppress local modes");
    }

    private static void obstacleClassifierOnlyClearsLowRiskPolicyMatches() {
        MovementProfile profile = profile(true, true);
        require(ObstacleSafetyClassifier.classify("minecraft:dirt", profile, true, true).kind()
                        == ObstacleDecisionKind.CLEAR_LOW_RISK,
                "low-risk policy block may be cleared when task and profile permit it");
        require(ObstacleSafetyClassifier.classify("minecraft:chest", profile, true, true).kind()
                        == ObstacleDecisionKind.OBSERVE_DANGEROUS,
                "containers and never-break blocks must become observations");
        require(ObstacleSafetyClassifier.classify("minecraft:dirt", profile, false, true).kind()
                        == ObstacleDecisionKind.OBSERVE_BLOCKED,
                "world-action gate must block obstacle clearing");
        require(ObstacleSafetyClassifier.classify("minecraft:diamond_block", profile, true, true).kind()
                        == ObstacleDecisionKind.OBSERVE_DANGEROUS,
                "ambiguous valuable blocks must not be cleared by default");
    }

    private static void actionLifecycleExposesTransitionsAndRetryBudget() {
        LongActionTracker tracker = new LongActionTracker();
        tracker.startExecuting("collect dropped item", 10L, 80, 1, Optional.of("item-1"));
        require(tracker.status().state() == ActionLifecycleState.EXECUTING, "started action must execute");
        tracker.noteProgress(20L, new ActionResultSummary(false, "moved closer"));
        require(tracker.status().lastProgressTick() == 20L, "progress tick must update");
        require(tracker.retry("target moved"), "first retry must consume retry budget");
        require(tracker.status().state() == ActionLifecycleState.RETRYING, "retry must expose retrying state");
        require(tracker.status().retriesRemaining() == 0, "retry budget must be decremented");
        require(!tracker.retry("still blocked"), "second retry must fail with no budget");
        require(tracker.status().state() == ActionLifecycleState.BLOCKED, "exhausted retries must block truthfully");
    }

    private static void plannerMailboxAllowsOneActiveJobPerOwnerAndCancels() {
        PlannerMailbox mailbox = new PlannerMailbox();
        CompletableFuture<String> future = new CompletableFuture<>();
        mailbox.submit("npc-1", future);
        require(mailbox.snapshot("npc-1").orElseThrow().status() == PlannerJobStatus.ACTIVE,
                "submitted planning job must be visible as active");
        requireThrowsIllegalState(() -> mailbox.submit("npc-1", new CompletableFuture<>()),
                "mailbox must reject a second active job for one owner");
        mailbox.cancel("npc-1", "user interrupted");
        require(mailbox.snapshot("npc-1").orElseThrow().status() == PlannerJobStatus.CANCELLED,
                "cancelled planning job must expose cancellation");
        mailbox.reapFinished();
        require(mailbox.snapshot("npc-1").isEmpty(), "finished jobs should be reapable from status mailbox");
    }

    private static void objectiveValidatorsRequireRealObservedState() {
        ObjectiveProgress inventory = InventoryObjectiveValidator.validate(
                Map.of("minecraft:cobblestone", 5), Map.of("minecraft:cobblestone", 8));
        require(!inventory.completed(), "inventory objective must not complete while items are missing");
        require(inventory.missingItems().get("minecraft:cobblestone") == 3, "missing count must be precise");

        ObjectiveProgress delivery = DeliveryObjectiveValidator.validate(
                Map.of("minecraft:furnace", 0), Map.of("minecraft:furnace", 1), Map.of("minecraft:furnace", 1));
        require(delivery.completed(), "delivery objective must validate target inventory delta");
        ObjectiveProgress missingDeliveryAdapter = DeliveryObjectiveValidator.validate(Map.of(), null, Map.of("minecraft:furnace", 1));
        require(!missingDeliveryAdapter.supported(), "missing delivery adapter must report unsupported");

        ObjectiveProgress block = BlockObjectiveValidator.validate(
                Map.of("1 64 1", "minecraft:crafting_table"), "1 64 1", "minecraft:furnace");
        require(!block.completed(), "block objective must not complete on mismatch");
        require(block.blockerReasons().get(0).contains("actual=minecraft:crafting_table"),
                "block objective must report actual observed block");
        require(!BuildObjectiveValidator.placeholder().supported(), "future build validator must be truthful placeholder");
    }

    private static void effectiveProfileStatusDoesNotExposeSecretsOrRawProviderTrace() {
        EffectiveRuntimeProfile profile = new EffectiveRuntimeProfile(
                "openplayer:companion_safe",
                List.of(AutomationMode.UNSTUCK, AutomationMode.DANGER_AVOIDANCE),
                List.of(AutomationMode.SELF_DEFENSE),
                "server_intent_parser",
                Map.of("allowWorldActions", true, "providerBypassesValidation", false),
                Map.of("rawProviderTraceInStatus", "false")
        );
        String status = String.join("\n", EffectiveRuntimeProfileFormatter.statusLines(profile));
        require(status.contains("effective_policy selected=openplayer:companion_safe"),
                "status must report selected policy");
        require(status.contains("danger_avoidance"), "status must report enabled local modes");
        require(status.contains("providerBypassesValidation=false"), "status must report validation gate");
        require(!status.toLowerCase(java.util.Locale.ROOT).contains("api_key"), "status must not expose secret markers");
        require(!status.contains("raw provider trace"), "status must not expose raw provider traces");
    }

    private static MovementProfile profile(boolean breakObstacles, boolean selfDefense) {
        return new MovementProfile(
                new ResourceLocation("openplayer:test"),
                breakObstacles,
                false,
                3,
                true,
                true,
                new BlockSafetyPolicy(
                        set("minecraft:chest", "minecraft:barrel", "minecraft:furnace", "minecraft:white_bed"),
                        set("minecraft:lava"),
                        set("minecraft:dirt", "minecraft:gravel")
                ),
                new EntitySafetyPolicy(
                        set("minecraft:creeper"),
                        selfDefense ? set("minecraft:zombie") : set(),
                        set("minecraft:villager", "minecraft:player")
                )
        );
    }

    private static java.util.Set<String> set(String... values) {
        return java.util.Set.of(values);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireThrowsIllegalState(Runnable runnable, String message) {
        try {
            runnable.run();
            throw new AssertionError(message);
        } catch (IllegalStateException expected) {
            require(!expected.getMessage().isBlank(), "exception should explain failure");
        }
    }
}
