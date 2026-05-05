package dev.soffits.openplayer.network;

import dev.architectury.networking.NetworkManager;
import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.OpenPlayerIntentParserConfig;
import dev.soffits.openplayer.OpenPlayerRuntimeStatus;
import dev.soffits.openplayer.api.AiPlayerNpcCommand;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.api.NpcOwnerId;
import dev.soffits.openplayer.api.NpcSpawnLocation;
import dev.soffits.openplayer.api.OpenPlayerApi;
import dev.soffits.openplayer.automation.capability.RuntimeCapabilityRegistry;
import dev.soffits.openplayer.character.LocalCharacterListEntry;
import dev.soffits.openplayer.character.LocalCharacterListView;
import dev.soffits.openplayer.character.LocalCharacterFileOperationResult;
import dev.soffits.openplayer.character.LocalAssignmentDefinition;
import dev.soffits.openplayer.character.LocalAssignmentRepositoryResult;
import dev.soffits.openplayer.character.LocalCharacterDefinition;
import dev.soffits.openplayer.character.LocalCharacterRepositoryResult;
import dev.soffits.openplayer.character.LocalSkinPathResolver;
import dev.soffits.openplayer.character.OpenPlayerLocalCharacters;
import dev.soffits.openplayer.conversation.ConversationReplyText;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvent;
import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.debug.OpenPlayerRawTrace;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentParseException;
import dev.soffits.openplayer.intent.IntentPriority;
import dev.soffits.openplayer.intent.IntentProviderException;
import dev.soffits.openplayer.intent.IntentParser;
import dev.soffits.openplayer.runtime.CompanionLifecycleManager;
import dev.soffits.openplayer.runtime.OpenPlayerRuntime;
import dev.soffits.openplayer.runtime.RuntimeAgentExecutor;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;


class OpenPlayerNetworkingBase {
    protected static final int MAX_COMMAND_TEXT_LENGTH = 512;
    static final int STATUS_LINE_NETWORK_MAX_LENGTH = 128;
    protected static final String PROVIDER_TEST_PROMPT = "Test OpenPlayer provider connectivity. Return REPORT_STATUS with NORMAL priority and blank instruction.";
    protected static final CompanionLifecycleManager COMPANION_LIFECYCLE_MANAGER = CompanionLifecycleManager.withAssignments(
            OpenPlayerApi::npcService,
            () -> OpenPlayerLocalCharacters.assignmentRepository().loadAll(OpenPlayerLocalCharacters.repository().loadAll()),
            OpenPlayerRuntime::intentParser
    );

    public static CommandSubmissionResult spawnAssignmentResult(ServerPlayer sender, String assignmentId) {
        if (sender == null || assignmentId == null || assignmentId.isBlank()) {
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, "Unknown local assignment");
        }
        CommandSubmissionResult result = COMPANION_LIFECYCLE_MANAGER.spawnSelectedAssignment(
                new NpcOwnerId(sender.getUUID()),
                new NpcSpawnLocation(
                        sender.serverLevel().dimension().location().toString(),
                        sender.getX(),
                        sender.getY(),
                        sender.getZ()
                ),
                assignmentId.trim()
        );
        sendCharacterListResponse(sender);
        sendStatusResponse(sender);
        return result;
    }

    public static CommandSubmissionResult despawnAssignmentResult(ServerPlayer sender, String assignmentId) {
        if (sender == null || assignmentId == null || assignmentId.isBlank()) {
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, "Unknown local assignment");
        }
        CommandSubmissionResult result = COMPANION_LIFECYCLE_MANAGER.despawnSelectedAssignment(sender.getUUID(), assignmentId.trim());
        sendCharacterListResponse(sender);
        sendStatusResponse(sender);
        return result;
    }

    public static CommandSubmissionResult submitAssignmentIntentResult(ServerPlayer sender, String assignmentId,
                                                                       IntentKind intentKind) {
        if (sender == null || assignmentId == null || assignmentId.isBlank() || intentKind == null) {
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, "Unknown local assignment");
        }
        CommandSubmissionResult result = COMPANION_LIFECYCLE_MANAGER.submitSelectedCommand(
                sender.getUUID(),
                assignmentId.trim(),
                new AiPlayerNpcCommand(UUID.randomUUID(), shortcutIntent(intentKind))
        );
        sendCharacterListResponse(sender);
        sendStatusResponse(sender);
        return result;
    }

    public static CommandSubmissionResult submitAssignmentQueuedIntentResult(ServerPlayer sender, String assignmentId,
                                                                            IntentKind intentKind, String instruction) {
        if (sender == null || assignmentId == null || assignmentId.isBlank() || intentKind == null) {
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, "Unknown local assignment");
        }
        String normalizedInstruction = instruction == null ? "" : instruction.trim();
        if (normalizedInstruction.length() > MAX_COMMAND_TEXT_LENGTH) {
            OpenPlayerDebugEvents.record("command_submission", "rejected", assignmentId, null, null,
                    "kind=" + intentKind.name() + " instruction_too_long");
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, "Queued command instruction was too long");
        }
        CommandSubmissionResult result = COMPANION_LIFECYCLE_MANAGER.submitSelectedCommand(
                sender.getUUID(),
                assignmentId.trim(),
                new AiPlayerNpcCommand(
                        UUID.randomUUID(),
                        new CommandIntent(intentKind, IntentPriority.HIGH, normalizedInstruction)
                )
        );
        OpenPlayerDebugEvents.record("command_submission", result.status().name(), assignmentId.trim(), null, null,
                "kind=" + intentKind.name() + " instructionLength=" + normalizedInstruction.length()
                        + " message=" + result.message());
        sendCharacterListResponse(sender);
        sendStatusResponse(sender);
        return result;
    }

    protected static CommandIntent shortcutIntent(IntentKind intentKind) {
        return new CommandIntent(intentKind, IntentPriority.HIGH, shortcutInstruction(intentKind));
    }

    protected static String shortcutInstruction(IntentKind intentKind) {
        return switch (intentKind) {
            case STOP,
                    REPORT_STATUS,
                    FOLLOW_OWNER,
                    COLLECT_ITEMS,
                    SWAP_TO_OFFHAND,
                    DROP_ITEM,
                    ATTACK_NEAREST,
                    GUARD_OWNER -> "";
            case MOVE,
                    LOOK,
                    PATROL,
                    BREAK_BLOCK,
                    PLACE_BLOCK,
                    INTERACT,
                    CHAT,
                    UNAVAILABLE,
                    OBSERVE,
                    INVENTORY_QUERY,
                    EQUIP_ITEM,
                    GIVE_ITEM,
                    DEPOSIT_ITEM,
                    STASH_ITEM,
                    WITHDRAW_ITEM,
                    ATTACK_TARGET,
                    PAUSE,
                    UNPAUSE,
                    RESET_MEMORY,
                    BODY_LANGUAGE,
                    CRAFT,
                    PROVIDER_PLAN -> intentKind.name();
            case GOTO -> "1 64 1";
            case LOCATE_LOADED_BLOCK -> "minecraft:oak_log";
            case LOCATE_LOADED_ENTITY -> "minecraft:zombie";
            case FIND_LOADED_BIOME -> "minecraft:plains";
        };
    }

    public static List<String> localAssignmentIds() {
        LocalAssignmentRepositoryResult result = OpenPlayerLocalCharacters.assignmentRepository()
                .loadAll(OpenPlayerLocalCharacters.repository().loadAll());
        java.util.ArrayList<String> assignmentIds = new java.util.ArrayList<>();
        for (LocalAssignmentDefinition assignment : result.assignments()) {
            assignmentIds.add(assignment.id());
        }
        return List.copyOf(assignmentIds);
    }

    public static List<String> assignmentStatusLines(ServerPlayer sender) {
        if (sender == null) {
            return List.of();
        }
        LocalAssignmentRepositoryResult result = OpenPlayerLocalCharacters.assignmentRepository()
                .loadAll(OpenPlayerLocalCharacters.repository().loadAll());
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (LocalAssignmentDefinition assignment : result.assignments()) {
            lines.add(assignment.id() + ": " + COMPANION_LIFECYCLE_MANAGER.lifecycleStatus(sender.getUUID(), assignment));
        }
        return List.copyOf(lines);
    }

    protected static void handleProviderConfigSaveRequest(ServerPlayer sender, OpenPlayerIntentParserConfig.ProviderConfigSaveRequest request) {
        if (!canSaveProviderConfig(sender)) {
            sendSafeStatusMessage(sender, "Provider config save rejected: permission required");
            sendStatusResponse(sender);
            return;
        }
        OpenPlayerIntentParserConfig.ProviderConfigSaveResult result = OpenPlayerIntentParserConfig.saveProviderConfig(request);
        String statusMessage = result.message();
        if (result.accepted()) {
            try {
                OpenPlayerRuntime.reloadIntentParser();
            } catch (IllegalStateException exception) {
                statusMessage = "Provider config saved; parser auto-enables when endpoint, model, and API key resolve";
            }
        }
        sendSafeStatusMessage(sender, statusMessage);
        sendStatusResponse(sender);
        sendCharacterListResponse(sender);
    }

    protected static void handleProviderTestRequest(ServerPlayer sender) {
        OpenPlayerDebugEvents.record("provider_test", "requested", null, null, null, "permission_check");
        if (!canSaveProviderConfig(sender)) {
            OpenPlayerDebugEvents.record("provider_test", "permission_required", null, null, null, "permission_required");
            sendProviderTestResponse(sender, "permission_required", "");
            sendStatusResponse(sender);
            return;
        }
        if (!OpenPlayerRuntime.status().intentParser().enabled()) {
            OpenPlayerDebugEvents.record("provider_test", "not_configured", null, null, null, "parser_disabled");
            sendProviderTestResponse(sender, "not_configured", "");
            sendStatusResponse(sender);
            sendCharacterListResponse(sender);
            return;
        }
        try {
            OpenPlayerRuntime.reloadIntentParser();
        } catch (IllegalStateException exception) {
            OpenPlayerDebugEvents.record("provider_test", "not_configured", null, null, null, "reload_failed");
            sendProviderTestResponse(sender, "not_configured", "");
            sendStatusResponse(sender);
            sendCharacterListResponse(sender);
            return;
        }
        IntentParser intentParser = OpenPlayerRuntime.intentParser();
        UUID senderId = sender.getUUID();
        MinecraftServer server = sender.server;
        sendProviderTestResponse(sender, "running", "");
        sendStatusResponse(sender);
        RuntimeAgentExecutor.submit(server, () -> parseProviderTest(intentParser), intent -> {
            ServerPlayer player = server.getPlayerList().getPlayer(senderId);
            if (player == null) {
                return;
            }
            if (intent == null || intent.kind() == null || intent.priority() == null) {
                OpenPlayerDebugEvents.record("provider_test", "invalid", null, null, null, "missing_intent_fields");
                sendProviderTestResponse(player, "invalid", "");
            } else {
                OpenPlayerDebugEvents.record("provider_parse", "success", null, null, null,
                        "kind=" + intent.kind().name() + " instructionLength=" + intent.instruction().length());
                OpenPlayerDebugEvents.record("provider_test", "success", null, null, null, "kind=" + intent.kind().name());
                sendProviderTestResponse(player, "success", intent.kind().name());
            }
            sendStatusResponse(player);
            sendCharacterListResponse(player);
        }, exception -> {
            ServerPlayer player = server.getPlayerList().getPlayer(senderId);
            if (player == null) {
                return;
            }
            IntentParseException parseException = exception instanceof ProviderTestParseRuntimeException providerException
                    ? providerException.parseException()
                    : new IntentParseException("intent provider failed", exception);
            String code = providerFailureCode(parseException);
            String detail = providerFailureDetail(parseException);
            OpenPlayerDebugEvents.record("provider_test", code, null, null, null,
                    detail.isBlank() ? code : "detail=" + detail);
            sendProviderTestResponse(player, code, detail);
            sendStatusResponse(player);
            sendCharacterListResponse(player);
        });
    }

    protected static CommandIntent parseProviderTest(IntentParser intentParser) {
        try {
            OpenPlayerDebugEvents.record("provider_parse", "attempted", null, null, null, "source=provider_test prompt=connectivity");
            return intentParser.parse(PROVIDER_TEST_PROMPT);
        } catch (IntentParseException exception) {
            throw new ProviderTestParseRuntimeException(exception);
        }
    }

    protected static void sendStatusResponse(ServerPlayer player) {
        sendStatusResponse(player, "");
    }

    protected static void sendStatusResponse(ServerPlayer player, String selectedAssignmentId) {
        OpenPlayerRuntimeStatus status = OpenPlayerRuntime.status();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBoolean(status.intentParser().enabled());
        writeBoundedUtf(buffer, status.intentParser().endpointStatus(), 128);
        writeBoundedUtf(buffer, status.intentParser().endpointValue(), OpenPlayerIntentParserConfig.MAX_ENDPOINT_LENGTH);
        writeBoundedUtf(buffer, status.intentParser().endpointSource(), 32);
        buffer.writeBoolean(status.intentParser().modelConfigured());
        writeBoundedUtf(buffer, status.intentParser().modelValue(), OpenPlayerIntentParserConfig.MAX_MODEL_LENGTH);
        writeBoundedUtf(buffer, status.intentParser().modelSource(), 32);
        buffer.writeBoolean(status.intentParser().apiKeyPresent());
        writeBoundedUtf(buffer, status.intentParser().apiKeySource(), 32);
        writeBoundedUtf(buffer, status.automationBackend().name(), 64);
        writeBoundedUtf(buffer, status.automationBackend().state().name(), 64);
        List<OpenPlayerDebugEvent> debugEvents = canViewDebugEvents(player) ? OpenPlayerDebugEvents.recent(6) : List.of();
        buffer.writeVarInt(debugEvents.size());
        for (OpenPlayerDebugEvent debugEvent : debugEvents) {
            writeBoundedUtf(buffer, debugEvent.compactLine(), OpenPlayerDebugEvents.MAX_NETWORK_LINE_LENGTH);
        }
        List<String> capabilityStatusLines = capabilityStatusLines(player, selectedAssignmentId);
        buffer.writeVarInt(capabilityStatusLines.size());
        for (String line : capabilityStatusLines) {
            writeBoundedUtf(buffer, line, STATUS_LINE_NETWORK_MAX_LENGTH);
        }
        NetworkManager.sendToPlayer(player, OpenPlayerConstants.STATUS_RESPONSE_PACKET_ID, buffer);
    }

    protected static List<String> capabilityStatusLines(ServerPlayer player) {
        return capabilityStatusLines(player, "");
    }

    protected static List<String> capabilityStatusLines(ServerPlayer player, String selectedAssignmentId) {
        String dimensionId = player == null ? "unknown" : player.serverLevel().dimension().location().toString();
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.add(runtimeStatusLine(dimensionId));
        if (player != null && selectedAssignmentId != null && !selectedAssignmentId.isBlank()) {
            lines.addAll(COMPANION_LIFECYCLE_MANAGER.selectedRuntimeStatusLines(player.getUUID(), selectedAssignmentId));
        }
        lines.addAll(RuntimeCapabilityRegistry.reportLines());
        java.util.ArrayList<String> boundedLines = new java.util.ArrayList<>();
        int lineCount = Math.min(lines.size(), RuntimeCapabilityRegistry.MAX_REPORT_LINES);
        for (int index = 0; index < lineCount; index++) {
            boundedLines.add(boundedPacketString(lines.get(index), STATUS_LINE_NETWORK_MAX_LENGTH));
        }
        return List.copyOf(boundedLines);
    }

    protected static String runtimeStatusLine(String dimensionId) {
        String safeDimensionId = dimensionId == null || dimensionId.isBlank() ? "unknown" : dimensionId;
        return boundedPacketString("runtime_status source=current_viewer_world current_dimension=" + safeDimensionId
                + " inventory_source=not_reported selected_npc=runtime_snapshot", STATUS_LINE_NETWORK_MAX_LENGTH);
    }

    protected static String boundedPacketString(String value, int maxLength) {
        return ConversationReplyText.summary(value, maxLength);
    }

    protected static void writeBoundedUtf(FriendlyByteBuf buffer, String value, int maxLength) {
        buffer.writeUtf(boundedPacketString(value, maxLength), maxLength);
    }

    protected static void sendCharacterListResponse(ServerPlayer player) {
        LocalCharacterRepositoryResult characterResult = OpenPlayerLocalCharacters.repository().loadAll();
        LocalCharacterListView view = LocalCharacterListView.fromAssignmentRepositoryResult(
                OpenPlayerLocalCharacters.assignmentRepository().loadAll(characterResult),
                (assignment, character) -> COMPANION_LIFECYCLE_MANAGER.lifecycleStatus(player.getUUID(), assignment),
                new LocalSkinPathResolver(OpenPlayerLocalCharacters.openPlayerDirectory()),
                character -> OpenPlayerRuntime.status().intentParser().enabled()
                        ? "available"
                        : "unavailable: parser disabled",
                (assignment, character) -> COMPANION_LIFECYCLE_MANAGER.conversationEventLines(player.getUUID(), assignment)
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(view.characters().size());
        for (LocalCharacterListEntry character : view.characters()) {
            buffer.writeUtf(character.id(), 64);
            buffer.writeUtf(character.assignmentId(), 64);
            buffer.writeUtf(character.characterId(), 64);
            buffer.writeUtf(character.displayName(), 32);
            buffer.writeUtf(character.description(), 1024);
            buffer.writeUtf(character.localSkinFile(), 256);
            buffer.writeUtf(character.defaultRoleId(), 64);
            buffer.writeUtf(character.conversationPrompt(), 4096);
            buffer.writeUtf(character.conversationSettings(), 2048);
            buffer.writeBoolean(character.allowWorldActions());
            buffer.writeUtf(character.skinStatus(), 64);
            buffer.writeUtf(character.lifecycleStatus(), 64);
            buffer.writeUtf(character.conversationStatus(), 64);
            buffer.writeVarInt(character.conversationEvents().size());
            for (String event : character.conversationEvents()) {
                writeBoundedUtf(buffer, event, STATUS_LINE_NETWORK_MAX_LENGTH);
            }
        }
        buffer.writeVarInt(view.errors().size());
        for (String error : view.errors()) {
            buffer.writeUtf(error, 512);
        }
        List<String> importFileNames = OpenPlayerLocalCharacters.repository().listImportFileNames(OpenPlayerLocalCharacters.importsDirectory());
        buffer.writeVarInt(importFileNames.size());
        for (String fileName : importFileNames) {
            buffer.writeUtf(fileName, 80);
        }
        NetworkManager.sendToPlayer(player, OpenPlayerConstants.CHARACTER_LIST_RESPONSE_PACKET_ID, buffer);
    }

    protected static void sendCharacterFileOperationResponse(ServerPlayer player, LocalCharacterFileOperationResult result) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(result.formatForClientStatus(), LocalCharacterFileOperationResult.NETWORK_RESPONSE_MAX_LENGTH);
        NetworkManager.sendToPlayer(player, OpenPlayerConstants.CHARACTER_FILE_OPERATION_RESPONSE_PACKET_ID, buffer);
    }

    protected static void sendProviderTestResponse(ServerPlayer player, String code, String detail) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeBoundedUtf(buffer, code == null ? "request_failed" : code, 64);
        writeBoundedUtf(buffer, detail == null ? "" : detail, 96);
        NetworkManager.sendToPlayer(player, OpenPlayerConstants.PROVIDER_TEST_RESPONSE_PACKET_ID, buffer);
    }

    protected static void sendSafeStatusMessage(ServerPlayer player, String message) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeBoundedUtf(buffer, message, 256);
        NetworkManager.sendToPlayer(player, OpenPlayerConstants.CHARACTER_FILE_OPERATION_RESPONSE_PACKET_ID, buffer);
    }

    protected static boolean canSaveProviderConfig(ServerPlayer player) {
        return maySaveProviderConfig(player.server.isSingleplayerOwner(player.getGameProfile()), player.hasPermissions(2));
    }

    protected static boolean canViewDebugEvents(ServerPlayer player) {
        return player.server.isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(2);
    }

    protected static String providerFailureCode(IntentParseException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof IntentProviderException providerException) {
            String message = providerException.getMessage();
            if (message != null && message.contains("status ")) {
                return "http_status";
            }
            if (message != null && message.contains("timed out")) {
                return "timed_out";
            }
            if (message != null && message.contains("interrupted")) {
                return "interrupted";
            }
            return "request_failed";
        }
        return "invalid";
    }

    protected static String providerFailureDetail(IntentParseException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof IntentProviderException providerException) {
            String message = providerException.getMessage();
            if (message != null && message.contains("status ")) {
                String status = message.substring(message.lastIndexOf(' ') + 1);
                return status.matches("[0-9]{3}") ? status : "";
            }
        }
        return "";
    }

    protected static boolean maySaveProviderConfig(boolean singleplayerOwner, boolean sufficientPermission) {
        return singleplayerOwner || sufficientPermission;
    }

    protected static boolean canManageLocalProfiles(ServerPlayer player) {
        return mayManageLocalProfiles(player.server.isSingleplayerOwner(player.getGameProfile()), player.hasPermissions(2));
    }

    protected static boolean mayManageLocalProfiles(boolean singleplayerOwner, boolean sufficientPermission) {
        return singleplayerOwner || sufficientPermission;
    }

    protected static final class ProviderTestParseRuntimeException extends RuntimeException {
        private final IntentParseException parseException;

        private ProviderTestParseRuntimeException(IntentParseException parseException) {
            super(parseException);
            this.parseException = parseException;
        }

        private IntentParseException parseException() {
            return parseException;
        }
    }

    protected static void rejectLocalProfileOperation(ServerPlayer player) {
        sendSafeStatusMessage(player, "Profile operation rejected: permission required");
        sendCharacterListResponse(player);
    }

    protected static String readAssignmentId(FriendlyByteBuf buffer) {
        if (!buffer.isReadable()) {
            return "";
        }
        return buffer.readUtf(64).trim();
    }
}
