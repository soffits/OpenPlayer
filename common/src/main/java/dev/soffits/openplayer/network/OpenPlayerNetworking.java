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
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class OpenPlayerNetworking {
    private static final int MAX_COMMAND_TEXT_LENGTH = 512;
    private static final String PROVIDER_TEST_PROMPT = "Test OpenPlayer provider connectivity. Return REPORT_STATUS with NORMAL priority and blank instruction.";
    private static final CompanionLifecycleManager COMPANION_LIFECYCLE_MANAGER = CompanionLifecycleManager.withAssignments(
            OpenPlayerApi::npcService,
            () -> OpenPlayerLocalCharacters.assignmentRepository().loadAll(OpenPlayerLocalCharacters.repository().loadAll()),
            OpenPlayerRuntime::intentParser
    );

    private OpenPlayerNetworking() {
    }

    public static void registerServerReceivers() {
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.SPAWN_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveSpawnRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.DESPAWN_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveDespawnRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.FOLLOW_OWNER_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveFollowOwnerRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.STOP_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveStopRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.COMMAND_TEXT_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveCommandTextRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.STATUS_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveStatusRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.CHARACTER_LIST_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveCharacterListRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.CHARACTER_EXPORT_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveCharacterExportRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.CHARACTER_IMPORT_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveCharacterImportRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.CHARACTER_SAVE_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveCharacterSaveRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.CHARACTER_DELETE_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveCharacterDeleteRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.PROVIDER_CONFIG_SAVE_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveProviderConfigSaveRequest
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                OpenPlayerConstants.PROVIDER_TEST_REQUEST_PACKET_ID,
                OpenPlayerNetworking::receiveProviderTestRequest
        );
    }

    private static void receiveSpawnRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String assignmentId = readAssignmentId(buffer);
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                handleSpawnRequest(player, assignmentId);
            }
        });
    }

    private static void receiveDespawnRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String assignmentId = readAssignmentId(buffer);
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                handleDespawnRequest(player, assignmentId);
            }
        });
    }

    private static void receiveFollowOwnerRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String assignmentId = readAssignmentId(buffer);
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                submitNetworkNpcCommand(player, assignmentId, IntentKind.FOLLOW_OWNER);
            }
        });
    }

    private static void receiveStopRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String assignmentId = readAssignmentId(buffer);
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                submitNetworkNpcCommand(player, assignmentId, IntentKind.STOP);
            }
        });
    }

    private static void receiveCommandTextRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String assignmentId = readAssignmentId(buffer);
        String commandText = buffer.isReadable() ? buffer.readUtf(MAX_COMMAND_TEXT_LENGTH) : "";
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                submitNetworkNpcCommandText(player, assignmentId, commandText);
            }
        });
    }

    private static void receiveStatusRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String selectedAssignmentId = buffer.isReadable() ? buffer.readUtf(64).trim() : "";
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                sendStatusResponse(player, selectedAssignmentId);
            }
        });
    }

    private static void receiveCharacterListRequest(FriendlyByteBuf ignoredBuffer, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                sendCharacterListResponse(player);
            }
        });
    }

    private static void receiveCharacterExportRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String characterId = buffer.readUtf(64).trim();
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                if (!canManageLocalProfiles(player)) {
                    rejectLocalProfileOperation(player);
                    return;
                }
                LocalCharacterFileOperationResult result = OpenPlayerLocalCharacters.repository()
                        .exportToDirectory(OpenPlayerLocalCharacters.exportsDirectory(), characterId);
                sendCharacterFileOperationResponse(player, result);
                sendCharacterListResponse(player);
            }
        });
    }

    private static void receiveCharacterImportRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String fileName = buffer.readUtf(80).trim();
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                if (!canManageLocalProfiles(player)) {
                    rejectLocalProfileOperation(player);
                    return;
                }
                LocalCharacterFileOperationResult result = OpenPlayerLocalCharacters.repository()
                        .importFromDirectory(OpenPlayerLocalCharacters.importsDirectory(), fileName, true);
                sendCharacterFileOperationResponse(player, result);
                sendCharacterListResponse(player);
            }
        });
    }

    private static void receiveCharacterSaveRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        LocalCharacterDefinition character = new LocalCharacterDefinition(
                buffer.readUtf(64),
                buffer.readUtf(32),
                buffer.readUtf(1024),
                null,
                buffer.readUtf(256),
                buffer.readUtf(64),
                buffer.readUtf(4096),
                buffer.readUtf(2048),
                buffer.readBoolean()
        );
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                if (!canManageLocalProfiles(player)) {
                    rejectLocalProfileOperation(player);
                    return;
                }
                LocalCharacterFileOperationResult result = OpenPlayerLocalCharacters.repository().save(character);
                sendCharacterFileOperationResponse(player, result);
                sendCharacterListResponse(player);
            }
        });
    }

    private static void receiveCharacterDeleteRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String characterId = buffer.readUtf(64).trim();
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                if (!canManageLocalProfiles(player)) {
                    rejectLocalProfileOperation(player);
                    return;
                }
                LocalCharacterFileOperationResult result = OpenPlayerLocalCharacters.repository().delete(characterId);
                sendCharacterFileOperationResponse(player, result);
                sendCharacterListResponse(player);
            }
        });
    }

    private static void receiveProviderConfigSaveRequest(FriendlyByteBuf buffer, NetworkManager.PacketContext context) {
        String endpoint = buffer.readUtf(OpenPlayerIntentParserConfig.MAX_ENDPOINT_LENGTH);
        String model = buffer.readUtf(OpenPlayerIntentParserConfig.MAX_MODEL_LENGTH);
        String apiKey = buffer.readUtf(OpenPlayerIntentParserConfig.MAX_API_KEY_LENGTH);
        boolean clearApiKey = buffer.readBoolean();
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                handleProviderConfigSaveRequest(player, new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(
                        endpoint,
                        model,
                        apiKey,
                        clearApiKey
                ));
            }
        });
    }

    private static void receiveProviderTestRequest(FriendlyByteBuf ignoredBuffer, NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                handleProviderTestRequest(player);
            }
        });
    }

    private static void handleSpawnRequest(ServerPlayer sender, String assignmentId) {
        NpcSpawnLocation location = new NpcSpawnLocation(
                sender.serverLevel().dimension().location().toString(),
                sender.getX(),
                sender.getY(),
                sender.getZ()
        );
        if (assignmentId == null || assignmentId.isBlank()) {
            OpenPlayerDebugEvents.record("spawn", "rejected", null, null, null, "missing_assignment");
        } else {
            COMPANION_LIFECYCLE_MANAGER.spawnSelectedAssignment(new NpcOwnerId(sender.getUUID()), location, assignmentId);
        }
        sendCharacterListResponse(sender);
    }

    private static void handleDespawnRequest(ServerPlayer sender, String assignmentId) {
        if (assignmentId == null || assignmentId.isBlank()) {
            OpenPlayerDebugEvents.record("despawn", "rejected", null, null, null, "missing_assignment");
        } else {
            COMPANION_LIFECYCLE_MANAGER.despawnSelectedAssignment(sender.getUUID(), assignmentId);
        }
        sendCharacterListResponse(sender);
    }

    private static void submitNetworkNpcCommand(ServerPlayer sender, String assignmentId, IntentKind intentKind) {
        AiPlayerNpcCommand command = new AiPlayerNpcCommand(
                UUID.randomUUID(),
                shortcutIntent(intentKind)
        );
        if (assignmentId == null || assignmentId.isBlank()) {
            OpenPlayerDebugEvents.record("command_submission", "rejected", null, null, null, "missing_assignment");
        } else {
            CommandSubmissionResult result = COMPANION_LIFECYCLE_MANAGER.submitSelectedCommand(sender.getUUID(), assignmentId, command);
            OpenPlayerDebugEvents.record("command_submission", result.status().name(), assignmentId, null, null,
                    "kind=" + intentKind.name() + " message=" + result.message());
        }
        sendCharacterListResponse(sender);
    }

    private static void submitNetworkNpcCommandText(ServerPlayer sender, String assignmentId, String commandText) {
        if (commandText == null || commandText.isBlank() || commandText.length() > MAX_COMMAND_TEXT_LENGTH) {
            OpenPlayerDebugEvents.record("command_text", "rejected", assignmentId, null, null, "invalid_length_or_blank");
            return;
        }
        if (assignmentId == null || assignmentId.isBlank()) {
            OpenPlayerDebugEvents.record("command_text", "rejected", null, null, null, "missing_assignment");
            return;
        }
        String trimmedAssignmentId = assignmentId.trim();
        String trimmedCommandText = commandText.trim();
        OpenPlayerDebugEvents.record("command_text", "received", trimmedAssignmentId, null, null,
                "length=" + trimmedCommandText.length());
        OpenPlayerRawTrace.commandText("network_assignment", sender.getUUID().toString(), trimmedAssignmentId, null,
                trimmedCommandText);
        sendCompanionChatEcho(sender, trimmedAssignmentId, trimmedCommandText);
        CommandSubmissionResult result = COMPANION_LIFECYCLE_MANAGER.submitSelectedCommandText(
                sender.getUUID(), trimmedAssignmentId, trimmedCommandText
        );
        OpenPlayerDebugEvents.record("command_submission", result.status().name(), trimmedAssignmentId, null, null,
                result.message());
        sendSelectedCommandTextResultMessage(sender, trimmedAssignmentId, result);
        sendCharacterListResponse(sender);
        sendStatusResponse(sender);
    }

    public static CommandSubmissionResult submitAssignmentCommandTextResult(ServerPlayer sender, String assignmentId, String commandText) {
        if (sender == null || assignmentId == null || assignmentId.isBlank()
                || commandText == null || commandText.isBlank() || commandText.length() > MAX_COMMAND_TEXT_LENGTH) {
            OpenPlayerDebugEvents.record("command_text", "rejected", assignmentId, null, null, "invalid_length_or_blank");
            return new CommandSubmissionResult(CommandSubmissionStatus.REJECTED, "Message was blank or too long");
        }
        String trimmedAssignmentId = assignmentId.trim();
        String trimmedCommandText = commandText.trim();
        OpenPlayerDebugEvents.record("command_text", "received", trimmedAssignmentId, null, null,
                "length=" + trimmedCommandText.length());
        OpenPlayerRawTrace.commandText("network_assignment", sender.getUUID().toString(), trimmedAssignmentId, null,
                trimmedCommandText);
        sendCompanionChatEcho(sender, trimmedAssignmentId, trimmedCommandText);
        CommandSubmissionResult result = COMPANION_LIFECYCLE_MANAGER.submitSelectedCommandText(
                sender.getUUID(), trimmedAssignmentId, trimmedCommandText
        );
        OpenPlayerDebugEvents.record("command_submission", result.status().name(), trimmedAssignmentId, null, null, result.message());
        sendCharacterListResponse(sender);
        sendStatusResponse(sender);
        return result;
    }

    public static boolean submitAssignmentCommandText(ServerPlayer sender, String assignmentId, String commandText) {
        return submitAssignmentCommandTextResult(sender, assignmentId, commandText).status() == CommandSubmissionStatus.ACCEPTED;
    }

    private static void sendCompanionChatEcho(ServerPlayer sender, String assignmentId, String commandText) {
        sender.sendSystemMessage(Component.translatable("commands.openplayer.chat.sent", assignmentId, commandText));
    }

    private static void sendSelectedCommandTextResultMessage(ServerPlayer sender, String assignmentId,
                                                             CommandSubmissionResult result) {
        if (result.status() == CommandSubmissionStatus.ACCEPTED) {
            sender.sendSystemMessage(Component.translatable("commands.openplayer.chat.reply", assignmentId, result.message()));
            return;
        }
        if (result.status() == CommandSubmissionStatus.REJECTED || result.status() == CommandSubmissionStatus.UNAVAILABLE
                || result.status() == CommandSubmissionStatus.UNKNOWN_SESSION) {
            sender.sendSystemMessage(Component.translatable("commands.openplayer.result", result.message()));
        }
    }

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

    static CommandIntent shortcutIntent(IntentKind intentKind) {
        return new CommandIntent(intentKind, IntentPriority.HIGH, shortcutInstruction(intentKind));
    }

    private static String shortcutInstruction(IntentKind intentKind) {
        return switch (intentKind) {
            case STOP,
                    REPORT_STATUS,
                    FOLLOW_OWNER,
                    COLLECT_ITEMS,
                    EQUIP_BEST_ITEM,
                    EQUIP_ARMOR,
                    USE_SELECTED_ITEM,
                    SWAP_TO_OFFHAND,
                    DROP_ITEM,
                    ATTACK_NEAREST,
                    GUARD_OWNER,
                    COLLECT_FOOD,
                    DEFEND_OWNER -> "";
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
                    GET_ITEM,
                    SMELT_ITEM,
                    FARM_NEARBY,
                    FISH,
                    ATTACK_TARGET,
                    PAUSE,
                    UNPAUSE,
                    RESET_MEMORY,
                    BODY_LANGUAGE,
                    BUILD_STRUCTURE,
                    LOCATE_STRUCTURE,
                    EXPLORE_CHUNKS,
                    USE_PORTAL,
                    TRAVEL_NETHER -> intentKind.name();
            case GOTO -> "owner";
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

    private static void handleProviderConfigSaveRequest(ServerPlayer sender, OpenPlayerIntentParserConfig.ProviderConfigSaveRequest request) {
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

    private static void handleProviderTestRequest(ServerPlayer sender) {
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
        try {
            OpenPlayerDebugEvents.record("provider_parse", "attempted", null, null, null, "source=provider_test prompt=connectivity");
            CommandIntent intent = intentParser.parse(PROVIDER_TEST_PROMPT);
            if (intent == null || intent.kind() == null || intent.priority() == null) {
                OpenPlayerDebugEvents.record("provider_test", "invalid", null, null, null, "missing_intent_fields");
                sendProviderTestResponse(sender, "invalid", "");
                return;
            }
            OpenPlayerDebugEvents.record("provider_parse", "success", null, null, null,
                    "kind=" + intent.kind().name() + " instructionLength=" + intent.instruction().length());
            OpenPlayerDebugEvents.record("provider_test", "success", null, null, null, "kind=" + intent.kind().name());
            sendProviderTestResponse(sender, "success", intent.kind().name());
        } catch (IntentParseException exception) {
            String code = providerFailureCode(exception);
            String detail = providerFailureDetail(exception);
            OpenPlayerDebugEvents.record("provider_test", code, null, null, null,
                    detail.isBlank() ? code : "detail=" + detail);
            sendProviderTestResponse(sender, code, detail);
        } finally {
            sendStatusResponse(sender);
            sendCharacterListResponse(sender);
        }
    }

    private static void sendStatusResponse(ServerPlayer player) {
        sendStatusResponse(player, "");
    }

    private static void sendStatusResponse(ServerPlayer player, String selectedAssignmentId) {
        OpenPlayerRuntimeStatus status = OpenPlayerRuntime.status();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBoolean(status.intentParser().enabled());
        buffer.writeUtf(status.intentParser().endpointStatus(), 128);
        buffer.writeUtf(status.intentParser().endpointSource(), 32);
        buffer.writeBoolean(status.intentParser().modelConfigured());
        buffer.writeUtf(status.intentParser().modelSource(), 32);
        buffer.writeBoolean(status.intentParser().apiKeyPresent());
        buffer.writeUtf(status.intentParser().apiKeySource(), 32);
        buffer.writeUtf(status.automationBackend().name(), 64);
        buffer.writeUtf(status.automationBackend().state().name(), 64);
        List<OpenPlayerDebugEvent> debugEvents = canViewDebugEvents(player) ? OpenPlayerDebugEvents.recent(6) : List.of();
        buffer.writeVarInt(debugEvents.size());
        for (OpenPlayerDebugEvent debugEvent : debugEvents) {
            buffer.writeUtf(debugEvent.compactLine(), OpenPlayerDebugEvents.MAX_NETWORK_LINE_LENGTH);
        }
        List<String> capabilityStatusLines = capabilityStatusLines(player, selectedAssignmentId);
        buffer.writeVarInt(capabilityStatusLines.size());
        for (String line : capabilityStatusLines) {
            buffer.writeUtf(line, 128);
        }
        NetworkManager.sendToPlayer(player, OpenPlayerConstants.STATUS_RESPONSE_PACKET_ID, buffer);
    }

    static List<String> capabilityStatusLines(ServerPlayer player) {
        return capabilityStatusLines(player, "");
    }

    static List<String> capabilityStatusLines(ServerPlayer player, String selectedAssignmentId) {
        String dimensionId = player == null ? "unknown" : player.serverLevel().dimension().location().toString();
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        lines.add("runtime_status source=current_viewer_world current_dimension=" + dimensionId
                + " inventory_source=not_reported selected_npc_source=separate_runtime_snapshot");
        if (player != null && selectedAssignmentId != null && !selectedAssignmentId.isBlank()) {
            lines.addAll(COMPANION_LIFECYCLE_MANAGER.selectedRuntimeStatusLines(player.getUUID(), selectedAssignmentId));
        }
        lines.addAll(RuntimeCapabilityRegistry.reportLines());
        return List.copyOf(lines.subList(0, Math.min(lines.size(), RuntimeCapabilityRegistry.MAX_REPORT_LINES)));
    }

    private static void sendCharacterListResponse(ServerPlayer player) {
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
                buffer.writeUtf(event, 128);
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

    private static void sendCharacterFileOperationResponse(ServerPlayer player, LocalCharacterFileOperationResult result) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(result.formatForClientStatus(), LocalCharacterFileOperationResult.NETWORK_RESPONSE_MAX_LENGTH);
        NetworkManager.sendToPlayer(player, OpenPlayerConstants.CHARACTER_FILE_OPERATION_RESPONSE_PACKET_ID, buffer);
    }

    private static void sendProviderTestResponse(ServerPlayer player, String code, String detail) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(code == null ? "request_failed" : code, 64);
        buffer.writeUtf(detail == null ? "" : detail, 96);
        NetworkManager.sendToPlayer(player, OpenPlayerConstants.PROVIDER_TEST_RESPONSE_PACKET_ID, buffer);
    }

    private static void sendSafeStatusMessage(ServerPlayer player, String message) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeUtf(message, 256);
        NetworkManager.sendToPlayer(player, OpenPlayerConstants.CHARACTER_FILE_OPERATION_RESPONSE_PACKET_ID, buffer);
    }

    private static boolean canSaveProviderConfig(ServerPlayer player) {
        return maySaveProviderConfig(player.server.isSingleplayerOwner(player.getGameProfile()), player.hasPermissions(2));
    }

    private static boolean canViewDebugEvents(ServerPlayer player) {
        return player.server.isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(2);
    }

    static String providerFailureCode(IntentParseException exception) {
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

    static String providerFailureDetail(IntentParseException exception) {
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

    static boolean maySaveProviderConfig(boolean singleplayerOwner, boolean sufficientPermission) {
        return singleplayerOwner || sufficientPermission;
    }

    private static boolean canManageLocalProfiles(ServerPlayer player) {
        return mayManageLocalProfiles(player.server.isSingleplayerOwner(player.getGameProfile()), player.hasPermissions(2));
    }

    static boolean mayManageLocalProfiles(boolean singleplayerOwner, boolean sufficientPermission) {
        return singleplayerOwner || sufficientPermission;
    }

    private static void rejectLocalProfileOperation(ServerPlayer player) {
        sendSafeStatusMessage(player, "Profile operation rejected: permission required");
        sendCharacterListResponse(player);
    }

    private static String readAssignmentId(FriendlyByteBuf buffer) {
        if (!buffer.isReadable()) {
            return "";
        }
        return buffer.readUtf(64).trim();
    }
}
