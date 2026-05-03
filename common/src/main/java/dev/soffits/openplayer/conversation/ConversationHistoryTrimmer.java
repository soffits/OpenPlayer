package dev.soffits.openplayer.conversation;

import java.util.ArrayList;
import java.util.List;

public final class ConversationHistoryTrimmer {
    private ConversationHistoryTrimmer() {
    }

    public static List<ConversationTurn> trim(List<ConversationTurn> history, int maxTurns, int maxCharacters) {
        if (history == null) {
            throw new IllegalArgumentException("history cannot be null");
        }
        if (maxTurns < 0) {
            throw new IllegalArgumentException("maxTurns cannot be negative");
        }
        if (maxCharacters < 0) {
            throw new IllegalArgumentException("maxCharacters cannot be negative");
        }
        if (maxTurns == 0 || maxCharacters == 0) {
            return List.of();
        }

        ArrayList<ConversationTurn> trimmed = new ArrayList<>();
        int usedCharacters = 0;
        for (int index = history.size() - 1; index >= 0 && trimmed.size() < maxTurns; index--) {
            ConversationTurn turn = history.get(index);
            if (turn == null) {
                continue;
            }
            int turnCharacters = turn.speaker().length() + turn.text().length();
            if (usedCharacters + turnCharacters > maxCharacters) {
                continue;
            }
            trimmed.add(0, turn);
            usedCharacters += turnCharacters;
        }
        return List.copyOf(trimmed);
    }
}
