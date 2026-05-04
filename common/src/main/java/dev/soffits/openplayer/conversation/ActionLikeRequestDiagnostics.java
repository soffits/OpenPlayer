package dev.soffits.openplayer.conversation;

import dev.soffits.openplayer.debug.OpenPlayerDebugEvents;
import dev.soffits.openplayer.debug.OpenPlayerRawTrace;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ActionLikeRequestDiagnostics {
    private static final Pattern ACTION_WORD = Pattern.compile(
            "(?i)(\\bbreak\\b|\\bchop\\b|\\bmine\\b|\\bcut\\b|\\bcollect\\b|\\bgather\\b|\\bgo\\b|\\bmove\\b|\\bwalk\\b|\\battack\\b|\\bplace\\b|\\bequip\\b|\\buse\\b|\\u780d|\\u6316|\\u91c7|\\u6536\\u96c6|\\u53bb|\\u8d70|\\u79fb\\u52a8|\\u653b\\u51fb|\\u653e\\u7f6e|\\u88c5\\u5907|\\u4f7f\\u7528)"
    );

    private ActionLikeRequestDiagnostics() {
    }

    public static boolean isActionLikeRequest(String playerMessage) {
        if (playerMessage == null || playerMessage.isBlank()) {
            return false;
        }
        String normalized = playerMessage.toLowerCase(Locale.ROOT).trim();
        if (normalized.length() > ConversationPromptAssembler.MAX_MESSAGE_LENGTH) {
            normalized = normalized.substring(0, ConversationPromptAssembler.MAX_MESSAGE_LENGTH);
        }
        return ACTION_WORD.matcher(normalized).find();
    }

    public static void recordChatIfActionLike(String playerMessage, CommandIntent intent, String assignmentId,
                                              String characterId, String sessionId) {
        if (intent == null || intent.kind() != IntentKind.CHAT || !isActionLikeRequest(playerMessage)) {
            return;
        }
        String detail = "automation_scheduled=false provider_returned_chat action_like_request messageLength="
                + safeLength(playerMessage) + " chatLength=" + intent.instruction().length();
        OpenPlayerDebugEvents.record("provider_parse", "diagnostic", assignmentId, characterId, sessionId, detail);
        OpenPlayerRawTrace.parseOutput("provider_chat_diagnostic", sessionId, detail);
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.trim().length();
    }
}
