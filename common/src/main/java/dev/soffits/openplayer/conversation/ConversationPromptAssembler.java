package dev.soffits.openplayer.conversation;

import dev.soffits.openplayer.character.LocalCharacterDefinition;
import java.util.List;

public final class ConversationPromptAssembler {
    public static final int MAX_MESSAGE_LENGTH = 512;

    private ConversationPromptAssembler() {
    }

    public static String assemble(LocalCharacterDefinition character, String playerMessage,
                                  List<ConversationTurn> history) {
        if (character == null) {
            throw new IllegalArgumentException("character cannot be null");
        }
        if (playerMessage == null || playerMessage.isBlank()) {
            throw new IllegalArgumentException("playerMessage cannot be blank");
        }
        if (history == null) {
            throw new IllegalArgumentException("history cannot be null");
        }

        String normalizedMessage = playerMessage.trim();
        if (normalizedMessage.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("playerMessage is too long");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("OpenPlayer selected-character conversation.\n");
        builder.append("Character: ").append(character.displayName()).append("\n");
        if (character.allowWorldActions()) {
            builder.append("Action policy: world, inventory, and combat actions are allowed for this selected character.\n");
        } else {
            builder.append("Action policy: world, inventory, and combat actions are disabled for this selected character. Do not choose BREAK_BLOCK, PLACE_BLOCK, ATTACK_NEAREST, GUARD_OWNER, DROP_ITEM, EQUIP_BEST_ITEM, EQUIP_ARMOR, USE_SELECTED_ITEM, or SWAP_TO_OFFHAND.\n");
        }
        character.optionalDescription().ifPresent(value -> builder.append("Description: ").append(value).append("\n"));
        character.optionalConversationPrompt().ifPresent(value -> builder.append("Conversation prompt: ").append(value).append("\n"));
        character.optionalConversationSettings().ifPresent(value -> builder.append("Conversation settings: ").append(value).append("\n"));
        builder.append("Return only an OpenPlayer intent JSON object accepted by the configured intent parser. ");
        builder.append("Do not include provider credentials, secrets, markdown, or free-form command text.\n");
        builder.append("When kind is CHAT, instruction must be the selected character's concise conversational reply to the player, following the conversation prompt and settings. ");
        builder.append("When kind is UNAVAILABLE, instruction may be blank or a short safe reason the selected character cannot help.\n");
        if (!history.isEmpty()) {
            builder.append("Recent bounded history:\n");
            for (ConversationTurn turn : history) {
                builder.append(turn.speaker()).append(": ").append(turn.text()).append("\n");
            }
        }
        builder.append("Player: ").append(normalizedMessage);
        return builder.toString();
    }
}
