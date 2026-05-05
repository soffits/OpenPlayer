package dev.soffits.openplayer.runtime;

import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.automation.capability.RuntimeCapabilityRegistry;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.BlockCoordinate;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.BlueprintBlock;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.BuildActionNeeded;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.BuildProjectStatus;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.CollaborativeBuildProjectManager;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.ExperimentBoundary;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.IdleDecision;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.IdleMode;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.IdleModeController;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.MemoryUpdateResult;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.TeamActionLifecycle;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.TeamActionProgress;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.TeamActionState;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.TeamTaskBlackboard;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.WorkClaimKind;
import dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.WorkClaimRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PhaseFourCoordinationTest {
    private PhaseFourCoordinationTest() {
    }

    public static void main(String[] args) {
        capabilityRegistrationDocsAndValidationRejectBypass();
        claimsAreAtomicReleasedAndRebalanced();
        buildDiffReportsMismatchProgressClaimHistoryAndTruthfulAdapterStatus();
        teamActionLifecycleIsTickBoundedAndDeterministic();
        worldFactMemoryRedactsAndRefreshesWithoutProviderText();
        idleModesArePolicyBoundAndInterruptible();
        experimentBoundaryRejectsDefaultExecution();
    }

    private static void capabilityRegistrationDocsAndValidationRejectBypass() {
        RuntimeCapabilityRegistry.ValidatedRegistry registry = RuntimeCapabilityRegistry.builtinValidatedRegistry();
        require(registry.action("break_block").isPresent(), "built-in break primitive must be registered by id");
        require(RuntimeCapabilityRegistry.providerToolDocs().stream().anyMatch(line -> line.contains("break_block")),
                "provider docs must be generated from registered capabilities");
        require(registry.validateProviderCall("break_block", Map.of("x", "1", "y", "64", "z", "1",
                "expected_block", OpenPlayerConstants.MINECRAFT_STONE_ID)).accepted(),
                "complete registered action call must validate");
        require(!registry.validateProviderCall("provider_created_task_123", Map.of()).accepted(),
                "raw provider-created action ids must not bypass registry validation");
        require(!registry.validateProviderCall("break_block", Map.of("x", "1")).accepted(),
                "raw action strings must not bypass required schema arguments");
        require(!registry.validateProviderCall("build_diff_status", Map.of("project_id", "hut")).accepted(),
                "diagnostic capability must not execute as a mutation path");
    }

    private static void claimsAreAtomicReleasedAndRebalanced() {
        WorkClaimRegistry registry = new WorkClaimRegistry();
        require(registry.claim("block:1:64:1", WorkClaimKind.BLOCK_POSITION, "npc-a", 1L).accepted(),
                "first claim must succeed");
        require(!registry.claim("block:1:64:1", WorkClaimKind.BLOCK_POSITION, "npc-b", 2L).accepted(),
                "duplicate claim for the same work key must be rejected");
        require(registry.release("block:1:64:1", "npc-a", 3L, "finished early").accepted(),
                "owner release must succeed");
        require(registry.claim("block:1:64:1", WorkClaimKind.BLOCK_POSITION, "npc-b", 4L).accepted(),
                "released work can be claimed by a free agent");
        require(registry.claim("container:9", WorkClaimKind.CONTAINER, "npc-a", 5L).accepted(),
                "second claim must succeed");
        require(registry.rebalance(Set.of("npc-a"), "npc-c", 6L).size() == 1,
                "rebalance must move claims from unavailable owners only");
        require(registry.claimFor("container:9").orElseThrow().ownerId().equals("npc-c"),
                "rebalance must assign stalled work to the new owner");

        TeamTaskBlackboard blackboard = new TeamTaskBlackboard();
        blackboard.assign("delivery:furnace", "npc-b");
        blackboard.promiseDelivery("delivery:furnace", "npc-b", OpenPlayerConstants.MINECRAFT_FURNACE_ID, 1, 7L);
        blackboard.blockAction("block:2:64:2", "policy denied");
        blackboard.progress("project:hut", 25);
        require(blackboard.snapshot().deliveryPromises().containsKey("delivery:furnace"),
                "blackboard must preserve structured delivery promises");
        require(blackboard.snapshot().blockedActions().stream().anyMatch(value -> value.contains("policy denied")),
                "blackboard must preserve blocked action reasons");
    }

    private static void buildDiffReportsMismatchProgressClaimHistoryAndTruthfulAdapterStatus() {
        WorkClaimRegistry claims = new WorkClaimRegistry();
        claims.claim("block:0:64:0", WorkClaimKind.BLOCK_POSITION, "npc-a", 1L);
        CollaborativeBuildProjectManager manager = new CollaborativeBuildProjectManager(claims);
        BuildProjectStatus status = manager.prepare("hut", List.of(
                        new BlueprintBlock(new BlockCoordinate(0, 64, 0), "minecraft:oak_planks"),
                        new BlueprintBlock(new BlockCoordinate(1, 64, 0), "minecraft:oak_planks")),
                Map.of(new BlockCoordinate(0, 64, 0), "minecraft:air",
                        new BlockCoordinate(1, 64, 0), "minecraft:oak_planks"));

        require(status.diff().mismatchScore() == 1, "build diff must score precise mismatches");
        require(status.diff().progressPercent() == 50, "build diff progress must be deterministic");
        require(status.diff().entries().get(0).actionNeeded() == BuildActionNeeded.PLACE_BLOCK,
                "air mismatch must request placement, not provider guesswork");
        require(!status.diff().entries().get(0).claimHistory().isEmpty(),
                "build diff must include claim history for debugging");
        require(!status.realBuildAdapterAvailable(), "foundation must not claim a real build adapter exists");
        require(status.status().contains("foundation_only"), "status must truthfully report unsupported build adapter path");
        require(manager.claimNextSection("npc-b", status, 2L).isPresent(),
                "free agent must be able to claim unfinished build sections atomically");
    }

    private static void teamActionLifecycleIsTickBoundedAndDeterministic() {
        TeamActionLifecycle action = new TeamActionLifecycle(List.of("a", "b", "c", "d", "e"), 99);
        action.start();
        TeamActionProgress first = action.tick(10L);
        require(first.completedWorkItems() == OpenPlayerConstants.TEAM_ACTION_MAX_WORK_PER_TICK,
                "team action must bound work per tick with centralized constant");
        require(first.state() == TeamActionState.RUNNING, "remaining work should keep action running");
        TeamActionProgress second = action.tick(11L);
        require(second.state() == TeamActionState.COMPLETED, "second tick must finish remaining bounded work");
        require(second.progressPercent() == 100, "completed action must report full progress");

        TeamActionLifecycle cancelled = new TeamActionLifecycle(List.of("x"), 1);
        cancelled.start();
        require(cancelled.cancel(12L, "user stop").state() == TeamActionState.CANCELLED,
                "long team action must be cancellable");
    }

    private static void worldFactMemoryRedactsAndRefreshesWithoutProviderText() {
        dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.WorldFactMemory memory =
                new dev.soffits.openplayer.runtime.phase4.PhaseFourRuntimeFoundations.WorldFactMemory(2,
                        OpenPlayerConstants.WORLD_FACT_MEMORY_REFRESH_TICKS);
        require(!memory.remember("raw", "provider said build it", "provider", 1L, true).accepted(),
                "world fact memory must reject raw provider text");
        require(memory.remember("base", "api_key=secret", "runtime_scan", 1L, false).accepted(),
                "runtime sourced fact must be accepted");
        require(memory.facts().get(0).value().equals("redacted"), "fact memory must redact secret-like values");
        MemoryUpdateResult early = memory.remember("base", "new", "runtime_scan", 2L, false);
        require(!early.accepted(), "fact refresh must be cadence bounded");
        require(memory.remember("base", "safe", "runtime_scan",
                OpenPlayerConstants.WORLD_FACT_MEMORY_REFRESH_TICKS + 2L, false).accepted(),
                "fact refresh after cadence must be accepted");
    }

    private static void idleModesArePolicyBoundAndInterruptible() {
        IdleModeController controller = new IdleModeController();
        IdleDecision denied = controller.decide(IdleMode.GUARD, false, false, true);
        require(!denied.active() && denied.mode() == IdleMode.STANDBY, "policy denied idle mode must standby");
        IdleDecision interrupted = controller.decide(IdleMode.FOLLOW_OWNER, true, true, true);
        require(!interrupted.active() && interrupted.reason().equals("interrupted"), "idle mode must be interruptible");
        require(controller.decide(IdleMode.FOLLOW_OWNER, true, false, true).active(),
                "policy allowed follow owner mode may activate when owner is known");
    }

    private static void experimentBoundaryRejectsDefaultExecution() {
        ExperimentBoundary boundary = new ExperimentBoundary();
        require(!boundary.request("script_eval", false, true).enabled(),
                "normal gameplay must reject experiments");
        require(boundary.request("script_eval", true, false).reason().contains("disabled"),
                "permission-gated experiment seam must remain disabled without script execution");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
