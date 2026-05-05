package dev.soffits.openplayer.runtime.phase4;

import dev.soffits.openplayer.OpenPlayerConstants;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PhaseFourRuntimeFoundations {
    private PhaseFourRuntimeFoundations() {
    }

    public static final class WorkClaimRegistry {
        private final LinkedHashMap<String, WorkClaim> claims = new LinkedHashMap<>();

        public synchronized ClaimResult claim(String workKey, WorkClaimKind kind, String ownerId, long tick) {
            validateKey(workKey, "work key");
            validateKey(ownerId, "owner id");
            WorkClaim existing = claims.get(workKey);
            if (existing != null && existing.status() == WorkClaimStatus.CLAIMED) {
                return new ClaimResult(false, "work already claimed", Optional.of(existing));
            }
            WorkClaim claim = new WorkClaim(workKey, kind, ownerId, WorkClaimStatus.CLAIMED,
                    List.of(new ClaimHistoryEntry(tick, ownerId, "claimed")));
            claims.put(workKey, claim);
            return new ClaimResult(true, "claim accepted", Optional.of(claim));
        }

        public synchronized ClaimResult release(String workKey, String ownerId, long tick, String reason) {
            WorkClaim existing = claims.get(workKey);
            if (existing == null) {
                return new ClaimResult(false, "work claim missing", Optional.empty());
            }
            if (!existing.ownerId().equals(ownerId)) {
                return new ClaimResult(false, "release owner mismatch", Optional.of(existing));
            }
            WorkClaim released = existing.with(null, WorkClaimStatus.RELEASED,
                    new ClaimHistoryEntry(tick, ownerId, "released:" + sanitizeReason(reason)));
            claims.put(workKey, released);
            return new ClaimResult(true, "claim released", Optional.of(released));
        }

        public synchronized ClaimResult reassign(String workKey, String fromOwnerId, String toOwnerId, long tick, String reason) {
            WorkClaim existing = claims.get(workKey);
            if (existing == null) {
                return new ClaimResult(false, "work claim missing", Optional.empty());
            }
            if (!existing.ownerId().equals(fromOwnerId)) {
                return new ClaimResult(false, "reassign owner mismatch", Optional.of(existing));
            }
            WorkClaim reassigned = existing.with(toOwnerId, WorkClaimStatus.CLAIMED,
                    new ClaimHistoryEntry(tick, toOwnerId, "reassigned:" + sanitizeReason(reason)));
            claims.put(workKey, reassigned);
            return new ClaimResult(true, "claim reassigned", Optional.of(reassigned));
        }

        public synchronized List<WorkClaim> rebalance(Set<String> unavailableOwners, String newOwnerId, long tick) {
            validateKey(newOwnerId, "new owner id");
            Set<String> blockedOwners = unavailableOwners == null ? Set.of() : unavailableOwners;
            ArrayList<WorkClaim> changed = new ArrayList<>();
            for (Map.Entry<String, WorkClaim> entry : claims.entrySet()) {
                WorkClaim claim = entry.getValue();
                if (claim.status() == WorkClaimStatus.CLAIMED && blockedOwners.contains(claim.ownerId())) {
                    WorkClaim reassigned = claim.with(newOwnerId, WorkClaimStatus.CLAIMED,
                            new ClaimHistoryEntry(tick, newOwnerId, "rebalanced"));
                    entry.setValue(reassigned);
                    changed.add(reassigned);
                }
            }
            return List.copyOf(changed);
        }

        public synchronized Optional<WorkClaim> claimFor(String workKey) {
            return Optional.ofNullable(claims.get(workKey));
        }

        public synchronized List<WorkClaim> claims() {
            return List.copyOf(claims.values());
        }
    }

    public static final class TeamTaskBlackboard {
        private final WorkClaimRegistry workClaims = new WorkClaimRegistry();
        private final LinkedHashMap<String, String> assignments = new LinkedHashMap<>();
        private final LinkedHashSet<String> blockedActions = new LinkedHashSet<>();
        private final LinkedHashMap<String, DeliveryPromise> deliveryPromises = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> progress = new LinkedHashMap<>();

        public WorkClaimRegistry workClaims() {
            return workClaims;
        }

        public synchronized void assign(String workKey, String ownerId) {
            validateKey(workKey, "work key");
            validateKey(ownerId, "owner id");
            assignments.put(workKey, ownerId);
        }

        public synchronized void blockAction(String workKey, String reason) {
            validateKey(workKey, "work key");
            blockedActions.add(workKey + ":" + sanitizeReason(reason));
        }

        public synchronized void promiseDelivery(String key, String ownerId, String itemId, int count, long tick) {
            deliveryPromises.put(key, new DeliveryPromise(key, ownerId, itemId, count, tick, false));
        }

        public synchronized void progress(String key, int percent) {
            progress.put(key, Math.max(0, Math.min(100, percent)));
        }

        public synchronized BlackboardSnapshot snapshot() {
            return new BlackboardSnapshot(Map.copyOf(assignments), workClaims.claims(), Set.copyOf(blockedActions),
                    Map.copyOf(deliveryPromises), Map.copyOf(progress));
        }
    }

    public static final class CollaborativeBuildProjectManager {
        private final WorkClaimRegistry claimRegistry;

        public CollaborativeBuildProjectManager(WorkClaimRegistry claimRegistry) {
            this.claimRegistry = claimRegistry == null ? new WorkClaimRegistry() : claimRegistry;
        }

        public BuildProjectStatus prepare(String projectId, List<BlueprintBlock> expected, Map<BlockCoordinate, String> actual) {
            BuildDiffResult diff = BuildDiffResult.diff(expected, actual, claimRegistry.claims());
            List<BuildSection> sections = splitSections(projectId, diff.entries());
            return new BuildProjectStatus(projectId, sections, diff, false,
                    "foundation_only: real placement adapter is not wired; use validated vanilla primitives for each work item");
        }

        public Optional<BuildSection> claimNextSection(String ownerId, BuildProjectStatus status, long tick) {
            for (BuildSection section : status.sections()) {
                ClaimResult result = claimRegistry.claim(section.sectionKey(), WorkClaimKind.BUILD_SECTION, ownerId, tick);
                if (result.accepted()) {
                    return Optional.of(section);
                }
            }
            return Optional.empty();
        }

        private List<BuildSection> splitSections(String projectId, List<BuildDiffEntry> entries) {
            LinkedHashMap<String, ArrayList<BuildDiffEntry>> grouped = new LinkedHashMap<>();
            for (BuildDiffEntry entry : entries) {
                BlockCoordinate coordinate = entry.coordinate();
                int sx = Math.floorDiv(coordinate.x(), OpenPlayerConstants.BUILD_SECTION_SIZE_BLOCKS);
                int sz = Math.floorDiv(coordinate.z(), OpenPlayerConstants.BUILD_SECTION_SIZE_BLOCKS);
                String key = projectId + ":section:" + sx + ":" + sz;
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
            }
            ArrayList<BuildSection> sections = new ArrayList<>();
            for (Map.Entry<String, ArrayList<BuildDiffEntry>> entry : grouped.entrySet()) {
                sections.add(new BuildSection(entry.getKey(), List.copyOf(entry.getValue()), 0));
            }
            return List.copyOf(sections);
        }
    }

    public static final class TeamActionLifecycle {
        private final ArrayDeque<String> pendingWork;
        private final int maxWorkPerTick;
        private TeamActionState state = TeamActionState.IDLE;
        private int completed;
        private String result = "not_started";

        public TeamActionLifecycle(List<String> workKeys, int maxWorkPerTick) {
            this.pendingWork = new ArrayDeque<>(workKeys == null ? List.of() : workKeys);
            this.maxWorkPerTick = Math.max(1, maxWorkPerTick);
        }

        public void start() {
            if (state != TeamActionState.IDLE) {
                throw new IllegalStateException("team action already started");
            }
            state = pendingWork.isEmpty() ? TeamActionState.COMPLETED : TeamActionState.RUNNING;
            result = pendingWork.isEmpty() ? "completed:no_work" : "running";
        }

        public TeamActionProgress tick(long tick) {
            if (state != TeamActionState.RUNNING) {
                return progress(tick);
            }
            int budget = Math.min(maxWorkPerTick, OpenPlayerConstants.TEAM_ACTION_MAX_WORK_PER_TICK);
            int worked = 0;
            while (worked < budget && !pendingWork.isEmpty()) {
                pendingWork.removeFirst();
                completed++;
                worked++;
            }
            if (pendingWork.isEmpty()) {
                state = TeamActionState.COMPLETED;
                result = "completed";
            }
            return progress(tick);
        }

        public TeamActionProgress cancel(long tick, String reason) {
            if (state == TeamActionState.COMPLETED) {
                return progress(tick);
            }
            state = TeamActionState.CANCELLED;
            result = "cancelled:" + sanitizeReason(reason);
            return progress(tick);
        }

        public TeamActionProgress progress(long tick) {
            int total = completed + pendingWork.size();
            int percent = total == 0 ? 100 : Math.floorDiv(completed * 100, total);
            return new TeamActionProgress(state, completed, pendingWork.size(), percent, tick, result);
        }
    }

    public static final class WorldFactMemory {
        private final int maxFacts;
        private final long refreshTicks;
        private final LinkedHashMap<String, WorldFact> facts = new LinkedHashMap<>();

        public WorldFactMemory(int maxFacts, long refreshTicks) {
            this.maxFacts = Math.max(1, maxFacts);
            this.refreshTicks = Math.max(1L, refreshTicks);
        }

        public MemoryUpdateResult remember(String key, String value, String source, long tick, boolean providerText) {
            validateKey(key, "fact key");
            if (providerText) {
                return new MemoryUpdateResult(false, "raw provider text rejected");
            }
            if (facts.containsKey(key) && tick - facts.get(key).sourceTick() < refreshTicks) {
                return new MemoryUpdateResult(false, "refresh cadence not reached");
            }
            while (facts.size() >= maxFacts && !facts.containsKey(key)) {
                String first = facts.keySet().iterator().next();
                facts.remove(first);
            }
            facts.put(key, new WorldFact(key, redact(value), source == null ? "unknown" : source, tick));
            return new MemoryUpdateResult(true, "fact accepted");
        }

        public List<WorldFact> facts() {
            return List.copyOf(facts.values());
        }
    }

    public static final class IdleModeController {
        public IdleDecision decide(IdleMode mode, boolean policyAllows, boolean interrupted, boolean ownerKnown) {
            if (interrupted) {
                return new IdleDecision(IdleMode.STANDBY, false, "interrupted");
            }
            if (!policyAllows) {
                return new IdleDecision(IdleMode.STANDBY, false, "policy_denied");
            }
            if ((mode == IdleMode.FOLLOW_OWNER || mode == IdleMode.REGROUP) && !ownerKnown) {
                return new IdleDecision(IdleMode.STANDBY, false, "owner_unknown");
            }
            return new IdleDecision(mode, true, "allowed");
        }
    }

    public static final class ExperimentBoundary {
        public ExperimentDecision request(String experimentId, boolean permissionGranted, boolean normalGameplayPath) {
            validateKey(experimentId, "experiment id");
            if (normalGameplayPath) {
                return new ExperimentDecision(false, "rejected: experiments are isolated from normal gameplay");
            }
            if (!permissionGranted) {
                return new ExperimentDecision(false, "rejected: permission gate closed");
            }
            return new ExperimentDecision(false, "disabled: no script engine or arbitrary code execution is available");
        }
    }

    public record BlockCoordinate(int x, int y, int z) {
    }

    public record BlueprintBlock(BlockCoordinate coordinate, String expectedBlock) {
        public BlueprintBlock {
            if (coordinate == null) {
                throw new IllegalArgumentException("blueprint coordinate cannot be null");
            }
            validateKey(expectedBlock, "expected block");
        }
    }

    public record BuildDiffEntry(BlockCoordinate coordinate, String expectedBlock, String actualBlock,
                                 BuildActionNeeded actionNeeded, int mismatchScore, List<ClaimHistoryEntry> claimHistory) {
        public BuildDiffEntry {
            claimHistory = List.copyOf(claimHistory == null ? List.of() : claimHistory);
        }
    }

    public record BuildDiffResult(List<BuildDiffEntry> entries, int mismatchScore, int progressPercent) {
        public BuildDiffResult {
            entries = List.copyOf(entries == null ? List.of() : entries);
        }

        public static BuildDiffResult diff(List<BlueprintBlock> expected, Map<BlockCoordinate, String> actual,
                                           List<WorkClaim> claims) {
            List<BlueprintBlock> safeExpected = expected == null ? List.of() : expected;
            Map<BlockCoordinate, String> safeActual = actual == null ? Map.of() : actual;
            Map<String, List<ClaimHistoryEntry>> historyByKey = historyByKey(claims);
            ArrayList<BuildDiffEntry> entries = new ArrayList<>();
            int mismatches = 0;
            for (BlueprintBlock block : safeExpected) {
                String actualBlock = safeActual.getOrDefault(block.coordinate(), "minecraft:air");
                if (!block.expectedBlock().equals(actualBlock)) {
                    mismatches++;
                    String key = blockKey(block.coordinate());
                    entries.add(new BuildDiffEntry(block.coordinate(), block.expectedBlock(), actualBlock,
                            actionFor(actualBlock), 1, historyByKey.getOrDefault(key, List.of())));
                }
            }
            int progress = safeExpected.isEmpty() ? 100 : Math.floorDiv((safeExpected.size() - mismatches) * 100,
                    safeExpected.size());
            return new BuildDiffResult(entries, mismatches, progress);
        }

        private static Map<String, List<ClaimHistoryEntry>> historyByKey(List<WorkClaim> claims) {
            HashMap<String, List<ClaimHistoryEntry>> histories = new HashMap<>();
            for (WorkClaim claim : claims == null ? List.<WorkClaim>of() : claims) {
                histories.put(claim.workKey(), claim.history());
            }
            return histories;
        }

        private static BuildActionNeeded actionFor(String actualBlock) {
            return "minecraft:air".equals(actualBlock) ? BuildActionNeeded.PLACE_BLOCK : BuildActionNeeded.REPLACE_BLOCK;
        }
    }

    public record BuildSection(String sectionKey, List<BuildDiffEntry> workItems, int progressPercent) {
        public BuildSection {
            validateKey(sectionKey, "section key");
            workItems = List.copyOf(workItems == null ? List.of() : workItems);
        }
    }

    public record BuildProjectStatus(String projectId, List<BuildSection> sections, BuildDiffResult diff,
                                     boolean realBuildAdapterAvailable, String status) {
        public BuildProjectStatus {
            validateKey(projectId, "project id");
            sections = List.copyOf(sections == null ? List.of() : sections);
            if (diff == null) {
                throw new IllegalArgumentException("build diff cannot be null");
            }
        }
    }

    public record ClaimResult(boolean accepted, String reason, Optional<WorkClaim> claim) {
        public ClaimResult {
            claim = claim == null ? Optional.empty() : claim;
        }
    }

    public record WorkClaim(String workKey, WorkClaimKind kind, String ownerId, WorkClaimStatus status,
                            List<ClaimHistoryEntry> history) {
        public WorkClaim {
            validateKey(workKey, "work key");
            if (kind == null) {
                throw new IllegalArgumentException("work claim kind cannot be null");
            }
            if (status == WorkClaimStatus.CLAIMED) {
                validateKey(ownerId, "owner id");
            }
            if (status == null) {
                throw new IllegalArgumentException("work claim status cannot be null");
            }
            history = List.copyOf(history == null ? List.of() : history);
        }

        private WorkClaim with(String newOwnerId, WorkClaimStatus newStatus, ClaimHistoryEntry entry) {
            ArrayList<ClaimHistoryEntry> next = new ArrayList<>(history);
            next.add(entry);
            return new WorkClaim(workKey, kind, newOwnerId, newStatus, List.copyOf(next));
        }
    }

    public record ClaimHistoryEntry(long tick, String ownerId, String event) {
        public ClaimHistoryEntry {
            if (ownerId == null) {
                ownerId = "unassigned";
            }
            validateKey(event, "claim event");
        }
    }

    public record DeliveryPromise(String key, String ownerId, String itemId, int count, long promisedTick, boolean completed) {
    }

    public record BlackboardSnapshot(Map<String, String> assignments, List<WorkClaim> claims, Set<String> blockedActions,
                                     Map<String, DeliveryPromise> deliveryPromises, Map<String, Integer> progress) {
        public BlackboardSnapshot {
            assignments = Collections.unmodifiableMap(new LinkedHashMap<>(assignments));
            claims = List.copyOf(claims);
            blockedActions = Set.copyOf(blockedActions);
            deliveryPromises = Collections.unmodifiableMap(new LinkedHashMap<>(deliveryPromises));
            progress = Collections.unmodifiableMap(new LinkedHashMap<>(progress));
        }
    }

    public record TeamActionProgress(TeamActionState state, int completedWorkItems, int remainingWorkItems,
                                     int progressPercent, long tick, String result) {
    }

    public record WorldFact(String key, String value, String source, long sourceTick) {
    }

    public record MemoryUpdateResult(boolean accepted, String reason) {
    }

    public record IdleDecision(IdleMode mode, boolean active, String reason) {
    }

    public record ExperimentDecision(boolean enabled, String reason) {
    }

    public enum WorkClaimKind {
        BLOCK_POSITION,
        ENTITY,
        ITEM,
        CONTAINER,
        BUILD_SECTION,
        DELIVERY_PROMISE
    }

    public enum WorkClaimStatus {
        CLAIMED,
        RELEASED,
        BLOCKED,
        COMPLETED
    }

    public enum BuildActionNeeded {
        PLACE_BLOCK,
        REPLACE_BLOCK
    }

    public enum TeamActionState {
        IDLE,
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    public enum IdleMode {
        FOLLOW_OWNER,
        GUARD,
        REGROUP,
        STANDBY
    }

    public static String blockKey(BlockCoordinate coordinate) {
        return "block:" + coordinate.x() + ":" + coordinate.y() + ":" + coordinate.z();
    }

    private static String redact(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("api_key") || lower.contains("token") || lower.contains("secret")) {
            return "redacted";
        }
        return value;
    }

    private static String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unspecified";
        }
        return reason.replace('\n', ' ').replace('\r', ' ');
    }

    private static void validateKey(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
    }
}
