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
import dev.soffits.openplayer.runtime.planner.PlannerPrimitiveProgress;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;


public final class OpenPlayerNetworking extends OpenPlayerNetworkingBase {
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
        CommandSubmissionResult result = submitSelectedCommandTextAsync(sender, trimmedAssignmentId, trimmedCommandText);
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
        CommandSubmissionResult result = submitSelectedCommandTextAsync(sender, trimmedAssignmentId, trimmedCommandText);
        OpenPlayerDebugEvents.record("command_submission", result.status().name(), trimmedAssignmentId, null, null, result.message());
        sendCharacterListResponse(sender);
        sendStatusResponse(sender);
        return result;
    }

    private static CommandSubmissionResult submitSelectedCommandTextAsync(ServerPlayer sender, String assignmentId,
                                                                         String commandText) {
        UUID senderId = sender.getUUID();
        MinecraftServer server = sender.server;
        return COMPANION_LIFECYCLE_MANAGER.submitSelectedCommandTextAsync(server, senderId, assignmentId, commandText, progress -> {
            ServerPlayer player = server.getPlayerList().getPlayer(senderId);
            if (player == null) {
                return;
            }
            sendAcceptedChatReply(player, assignmentId, progressComponent(progress));
            sendCharacterListResponse(player);
            sendStatusResponse(player);
        }, result -> {
            ServerPlayer player = server.getPlayerList().getPlayer(senderId);
            if (player == null) {
                return;
            }
            OpenPlayerDebugEvents.record("command_submission", result.status().name(), assignmentId, null, null,
                    result.message());
            sendSelectedCommandTextResultMessage(player, assignmentId, result);
            sendCharacterListResponse(player);
            sendStatusResponse(player);
        });
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
            sendAcceptedChatReply(sender, assignmentId, result.message());
            return;
        }
        if (result.status() == CommandSubmissionStatus.REJECTED || result.status() == CommandSubmissionStatus.UNAVAILABLE
                || result.status() == CommandSubmissionStatus.UNKNOWN_SESSION) {
            sender.sendSystemMessage(Component.translatable("commands.openplayer.result", result.message()));
        }
    }

    public static void sendAcceptedChatReply(ServerPlayer sender, String assignmentId, String message) {
        for (String chunk : ConversationReplyText.displayChunks(message)) {
            sender.sendSystemMessage(Component.translatable("commands.openplayer.chat.reply", assignmentId, chunk));
        }
    }

    public static void sendAcceptedChatReply(ServerPlayer sender, String assignmentId, Component message) {
        sender.sendSystemMessage(Component.translatable("commands.openplayer.chat.reply", assignmentId, message));
    }

    private static Component progressComponent(PlannerPrimitiveProgress.Display progress) {
        return Component.translatable(progress.translationKey(), (Object[]) progress.args());
    }

}
