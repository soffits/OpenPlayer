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


abstract class RuntimeAiPlayerNpcServiceSessionBase extends RuntimeAiPlayerNpcServicePlannerBase {
    protected RuntimeAiPlayerNpcServiceSessionBase(MinecraftServer server, IntentParser intentParser) {
        super(server, intentParser);
    }

    protected abstract void reattachPersistedNpcs();

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
                cancelPlannerSession(session.sessionId(), "owner runtime stopped");
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
        List<String> lines = new ArrayList<>();
        lines.add(
                limitStatusLine("selected_assignment=" + safeAssignmentId + " source=selected_npc status=active active="
                        + active + " queued=" + snapshot.queuedCommandCount() + " paused=" + snapshot.paused()));
        lines.add(
                limitStatusLine("selected_controller=" + snapshot.monitorStatus().name().toLowerCase(java.util.Locale.ROOT)
                        + " reason=" + safeStatusValue(snapshot.monitorReason()) + " ticks="
                        + snapshot.elapsedTicks() + "/" + snapshot.maxTicks()));
        lines.add(limitStatusLine("selected_queue source=selected_npc kinds=" + queuedKinds(snapshot)));
        lines.add(limitStatusLine("selected_planner state=" + plannerState(sessionId)
                + " autonomousEnabled=true budgets=iterations:" + plannerConfig.maxIterations()
                + ",providerCalls:" + plannerConfig.maxProviderCalls()
                + ",toolSteps:" + plannerConfig.maxToolSteps()
                + ",noProgress:" + plannerConfig.maxNoProgressCount()));
        lines.addAll(EffectiveRuntimeProfileFormatter.statusLines(effectiveRuntimeProfile(session)));
        return List.copyOf(lines);
    }

    protected String plannerState(NpcSessionId sessionId) {
        InteractivePlannerSession plannerSession = plannerSessions.get(sessionId);
        if (plannerSession == null) {
            return "idle";
        }
        return plannerSession.isCancelled() ? "cancelled" : "planning";
    }

    private EffectiveRuntimeProfile effectiveRuntimeProfile(RuntimeAiPlayerNpcSession session) {
        String movementPolicy = session.spec().profile().movementPolicy();
        String selectedPolicy = movementPolicy == null || movementPolicy.isBlank()
                ? "openplayer:companion_safe" : movementPolicy;
        List<AutomationMode> enabledModes = session.spec().allowWorldActions()
                ? List.of(AutomationMode.UNSTUCK, AutomationMode.OPPORTUNISTIC_ITEM_COLLECTION, AutomationMode.DANGER_AVOIDANCE)
                : List.of(AutomationMode.UNSTUCK, AutomationMode.DANGER_AVOIDANCE);
        List<AutomationMode> disabledModes = session.spec().allowWorldActions()
                ? List.of(AutomationMode.SELF_DEFENSE)
                : List.of(AutomationMode.OPPORTUNISTIC_ITEM_COLLECTION, AutomationMode.SELF_DEFENSE);
        return new EffectiveRuntimeProfile(
                selectedPolicy,
                enabledModes,
                disabledModes,
                "server_intent_parser",
                Map.of(
                        "allowWorldActions", session.spec().allowWorldActions(),
                        "autonomousPlanner", true,
                        "providerBypassesValidation", false
                ),
                Map.of("rawProviderTraceInStatus", "false")
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

    protected static String queuedKinds(AutomationControllerSnapshot snapshot) {
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

    protected static String safeStatusValue(String value) {
        return safeToken(value == null || value.isBlank() ? "none" : value, 64);
    }

    protected static String safeToken(String value, int maxLength) {
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

    protected static String limitStatusLine(String line) {
        if (line.length() <= 120) {
            return line;
        }
        return line.substring(0, 106) + "... truncated";
    }

    protected ServerLevel levelFor(NpcSpawnLocation location) {
        ResourceLocation dimensionId = new ResourceLocation(location.dimension());
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) {
            throw new IllegalArgumentException("Unknown dimension: " + location.dimension());
        }
        return level;
    }

    protected OpenPlayerNpcEntity entityFor(RuntimeAiPlayerNpcSession session) {
        ServerLevel level = levelFor(session.spec().spawnLocation());
        Entity entity = level.getEntity(session.entityUuid());
        if (!(entity instanceof OpenPlayerNpcEntity npcEntity)) {
            return null;
        }
        return npcEntity.isAlive() ? npcEntity : null;
    }

    protected RuntimeAiPlayerNpcSession existingSession(RuntimeNpcIdentityKey identityKey) {
        NpcSessionId sessionId = sessionIdsByIdentity.get(identityKey);
        return sessionId == null ? null : sessions.get(sessionId);
    }

    protected AiPlayerNpcSpec persistedSpec(ServerLevel level, OpenPlayerNpcEntity entity) {
        return new AiPlayerNpcSpec(
                new dev.soffits.openplayer.api.NpcRoleId(entity.persistedRoleId().orElseThrow()),
                new NpcOwnerId(entity.persistedOwnerId().orElseThrow()),
                new dev.soffits.openplayer.api.NpcProfileSpec(
                        entity.persistedProfileName().orElseThrow(),
                        entity.persistedProfileSkinTexture().orElse(null),
                        entity.persistedMovementPolicy().orElse(null)
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

    protected OpenPlayerNpcEntity persistedEntityFor(ServerLevel level, AiPlayerNpcSpec spec) {
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

    protected void removeIndexes(NpcSessionId sessionId) {
        RuntimeAiPlayerNpcSession session = sessions.remove(sessionId);
        if (session != null) {
            sessionIdsByIdentity.remove(RuntimeNpcIdentityKey.from(session.spec()));
        }
    }

    protected void relocate(OpenPlayerNpcEntity entity, NpcSpawnLocation location) {
        entity.teleportTo(location.x(), location.y(), location.z());
        entity.setYRot(0.0F);
        entity.setXRot(0.0F);
    }

    protected void applyProfile(OpenPlayerNpcEntity entity, AiPlayerNpcSpec spec) {
        entity.setPersistedIdentity(
                spec.ownerId(),
                spec.roleId().value(),
                spec.profile().name(),
                spec.profile().skinTexture(),
                spec.allowWorldActions(),
                spec.profile().movementPolicy()
        );
        entity.setCustomName(net.minecraft.network.chat.Component.literal(spec.profile().name()));
        entity.setCustomNameVisible(true);
    }

    protected RuntimeContextSnapshot buildRuntimeContextSnapshot(OpenPlayerNpcEntity entity) {
        return RuntimeContextSnapshotBuilder.build(entity);
    }
}
