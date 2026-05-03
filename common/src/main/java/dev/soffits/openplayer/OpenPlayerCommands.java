package dev.soffits.openplayer;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.network.OpenPlayerNetworking;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class OpenPlayerCommands {
    private OpenPlayerCommands() {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            dispatcher.register(openPlayerRoot("openplayer"));
            dispatcher.register(legacyChatRoot("ai"));
            dispatcher.register(legacyChatRoot("aichat"));
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> openPlayerRoot(String name) {
        return Commands.literal(name)
                .then(Commands.literal("chat")
                        .then(assignmentArgument()
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(context -> chat(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "assignmentId"),
                                                StringArgumentType.getString(context, "message")
                                        )))))
                .then(Commands.literal("follow")
                        .then(assignmentArgument()
                                .executes(context -> intent(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "assignmentId"),
                                        IntentKind.FOLLOW_OWNER
                                ))))
                .then(Commands.literal("stop")
                        .then(assignmentArgument()
                                .executes(context -> intent(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "assignmentId"),
                                        IntentKind.STOP
                                ))))
                .then(Commands.literal("spawn")
                        .then(assignmentArgument()
                                .executes(context -> sendResult(
                                        context.getSource().getPlayerOrException(),
                                        OpenPlayerNetworking.spawnAssignmentResult(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "assignmentId")
                                        )
                                ))))
                .then(Commands.literal("despawn")
                        .then(assignmentArgument()
                                .executes(context -> sendResult(
                                        context.getSource().getPlayerOrException(),
                                        OpenPlayerNetworking.despawnAssignmentResult(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "assignmentId")
                                        )
                                ))))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource().getPlayerOrException())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> legacyChatRoot(String name) {
        return Commands.literal(name)
                .then(Commands.literal("selected")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> selectedUnavailable(context.getSource().getPlayerOrException()))))
                .then(assignmentArgument()
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> chat(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "assignmentId"),
                                        StringArgumentType.getString(context, "message")
                                ))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> assignmentArgument() {
        return Commands.argument("assignmentId", StringArgumentType.word()).suggests((context, builder) -> suggestAssignments(builder));
    }

    private static CompletableFuture<Suggestions> suggestAssignments(SuggestionsBuilder builder) {
        for (String assignmentId : OpenPlayerNetworking.localAssignmentIds()) {
            builder.suggest(assignmentId);
        }
        return builder.buildFuture();
    }

    private static int selectedUnavailable(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("commands.openplayer.ai.selected_unavailable"));
        return 0;
    }

    private static int chat(ServerPlayer player, String assignmentId, String message) {
        CommandSubmissionResult result = OpenPlayerNetworking.submitAssignmentCommandTextResult(player, assignmentId, message);
        if (result.status() == CommandSubmissionStatus.ACCEPTED) {
            player.sendSystemMessage(Component.translatable("commands.openplayer.chat.reply", assignmentId, result.message()));
            return 1;
        }
        return sendResult(player, result);
    }

    private static int intent(ServerPlayer player, String assignmentId, IntentKind intentKind) {
        return sendResult(player, OpenPlayerNetworking.submitAssignmentIntentResult(player, assignmentId, intentKind));
    }

    private static int status(ServerPlayer player) {
        List<String> lines = OpenPlayerNetworking.assignmentStatusLines(player);
        if (lines.isEmpty()) {
            player.sendSystemMessage(Component.translatable("commands.openplayer.status.empty"));
            return 0;
        }
        player.sendSystemMessage(Component.translatable("commands.openplayer.status.header"));
        for (String line : lines) {
            player.sendSystemMessage(Component.translatable("commands.openplayer.status.line", line));
        }
        return 1;
    }

    private static int sendResult(ServerPlayer player, CommandSubmissionResult result) {
        player.sendSystemMessage(Component.translatable("commands.openplayer.result", result.message()));
        return result.status() == CommandSubmissionStatus.ACCEPTED ? 1 : 0;
    }
}
