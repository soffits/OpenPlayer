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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class OpenPlayerCommands {
    private static final List<IntentKind> QUEUE_SUGGESTED_INTENT_KINDS = List.of(
            IntentKind.STOP,
            IntentKind.MOVE,
            IntentKind.LOOK,
            IntentKind.FOLLOW_OWNER,
            IntentKind.GUARD_OWNER,
            IntentKind.PATROL,
            IntentKind.COLLECT_ITEMS,
            IntentKind.EQUIP_BEST_ITEM,
            IntentKind.EQUIP_ARMOR,
            IntentKind.USE_SELECTED_ITEM,
            IntentKind.SWAP_TO_OFFHAND,
            IntentKind.DROP_ITEM,
            IntentKind.BREAK_BLOCK,
            IntentKind.PLACE_BLOCK,
            IntentKind.INTERACT,
            IntentKind.ATTACK_NEAREST,
            IntentKind.ATTACK_TARGET,
            IntentKind.REPORT_STATUS,
            IntentKind.GOTO,
            IntentKind.INVENTORY_QUERY,
            IntentKind.EQUIP_ITEM,
            IntentKind.GIVE_ITEM,
            IntentKind.DEPOSIT_ITEM,
            IntentKind.STASH_ITEM,
            IntentKind.WITHDRAW_ITEM,
            IntentKind.GET_ITEM,
            IntentKind.SMELT_ITEM,
            IntentKind.COLLECT_FOOD,
            IntentKind.FARM_NEARBY,
            IntentKind.FISH,
            IntentKind.DEFEND_OWNER,
            IntentKind.PAUSE,
            IntentKind.UNPAUSE,
            IntentKind.RESET_MEMORY,
            IntentKind.BODY_LANGUAGE,
            IntentKind.BUILD_STRUCTURE,
            IntentKind.LOCATE_LOADED_BLOCK,
            IntentKind.LOCATE_LOADED_ENTITY,
            IntentKind.FIND_LOADED_BIOME
    );

    private OpenPlayerCommands() {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            dispatcher.register(openPlayerRoot("openplayer"));
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
                .then(Commands.literal("queue")
                        .then(assignmentArgument()
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestQueueIntentKinds(builder))
                                        .executes(context -> queueIntent(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "assignmentId"),
                                                StringArgumentType.getString(context, "kind"),
                                                ""
                                        ))
                                        .then(Commands.argument("instruction", StringArgumentType.greedyString())
                                                .executes(context -> queueIntent(
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "assignmentId"),
                                                        StringArgumentType.getString(context, "kind"),
                                                        StringArgumentType.getString(context, "instruction")
                                                ))))))
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

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> assignmentArgument() {
        return Commands.argument("assignmentId", StringArgumentType.word()).suggests((context, builder) -> suggestAssignments(builder));
    }

    private static CompletableFuture<Suggestions> suggestAssignments(SuggestionsBuilder builder) {
        for (String assignmentId : OpenPlayerNetworking.localAssignmentIds()) {
            builder.suggest(assignmentId);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestQueueIntentKinds(SuggestionsBuilder builder) {
        for (IntentKind kind : QUEUE_SUGGESTED_INTENT_KINDS) {
            builder.suggest(kind.name().toLowerCase(Locale.ROOT));
        }
        return builder.buildFuture();
    }

    static IntentKind parseQueueIntentKind(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            IntentKind kind = IntentKind.valueOf(input.trim().toUpperCase(Locale.ROOT));
            return QUEUE_SUGGESTED_INTENT_KINDS.contains(kind) ? kind : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    static List<IntentKind> queueSuggestedIntentKinds() {
        return QUEUE_SUGGESTED_INTENT_KINDS;
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

    private static int queueIntent(ServerPlayer player, String assignmentId, String kindName, String instruction) {
        IntentKind kind = parseQueueIntentKind(kindName);
        if (kind == null) {
            player.sendSystemMessage(Component.translatable("commands.openplayer.queue.invalid_kind", kindName));
            return 0;
        }
        return sendResult(player, OpenPlayerNetworking.submitAssignmentQueuedIntentResult(
                player,
                assignmentId,
                kind,
                instruction == null ? "" : instruction
        ));
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
