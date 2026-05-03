package dev.soffits.openplayer;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.soffits.openplayer.api.CommandSubmissionResult;
import dev.soffits.openplayer.api.CommandSubmissionStatus;
import dev.soffits.openplayer.network.OpenPlayerNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class OpenPlayerCommands {
    private OpenPlayerCommands() {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            dispatcher.register(Commands.literal("ai")
                    .then(Commands.literal("selected")
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(context -> selectedUnavailable(context.getSource().getPlayerOrException()))))
                    .then(Commands.argument("assignmentId", StringArgumentType.word())
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(context -> submit(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "assignmentId"),
                                            StringArgumentType.getString(context, "message")
                                    )))));
            dispatcher.register(Commands.literal("aichat")
                    .then(Commands.argument("assignmentId", StringArgumentType.word())
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(context -> submit(
                                            context.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(context, "assignmentId"),
                                            StringArgumentType.getString(context, "message")
                                    )))));
        });
    }

    private static int selectedUnavailable(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("commands.openplayer.ai.selected_unavailable"));
        return 0;
    }

    private static int submit(ServerPlayer player, String assignmentId, String message) {
        CommandSubmissionResult result = OpenPlayerNetworking.submitAssignmentCommandTextResult(player, assignmentId, message);
        if (result.status() != CommandSubmissionStatus.ACCEPTED) {
            player.sendSystemMessage(Component.translatable("commands.openplayer.ai.rejected"));
            return 0;
        }
        player.sendSystemMessage(Component.translatable("commands.openplayer.ai.sent", assignmentId));
        return 1;
    }
}
