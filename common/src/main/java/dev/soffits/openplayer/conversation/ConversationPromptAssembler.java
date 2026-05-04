package dev.soffits.openplayer.conversation;

import dev.soffits.openplayer.character.LocalCharacterDefinition;
import dev.soffits.openplayer.runtime.validation.RuntimeIntentPolicies;
import java.util.List;

public final class ConversationPromptAssembler {
    public static final int MAX_MESSAGE_LENGTH = 512;

    private ConversationPromptAssembler() {
    }

    public static String assemble(LocalCharacterDefinition character, String playerMessage,
                                   List<ConversationTurn> history) {
        return assemble(character, playerMessage, history, ConversationContextSnapshot.EMPTY);
    }

    public static String assemble(LocalCharacterDefinition character, String playerMessage,
                                   List<ConversationTurn> history, ConversationContextSnapshot contextSnapshot) {
        if (character == null) {
            throw new IllegalArgumentException("character cannot be null");
        }
        if (playerMessage == null || playerMessage.isBlank()) {
            throw new IllegalArgumentException("playerMessage cannot be blank");
        }
        if (history == null) {
            throw new IllegalArgumentException("history cannot be null");
        }
        if (contextSnapshot == null) {
            throw new IllegalArgumentException("contextSnapshot cannot be null");
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
            builder.append("Action policy: world, inventory, and combat actions are disabled for this selected character. Do not choose ")
                    .append(RuntimeIntentPolicies.localWorldOrInventoryActionNames())
                    .append(".\n");
        }
        character.optionalDescription().ifPresent(value -> builder.append("Description: ").append(value).append("\n"));
        character.optionalConversationPrompt().ifPresent(value -> builder.append("Conversation prompt: ").append(value).append("\n"));
        character.optionalConversationSettings().ifPresent(value -> builder.append("Conversation settings: ").append(value).append("\n"));
        builder.append("Return only an OpenPlayer intent JSON object accepted by the configured intent parser. ");
        builder.append("Do not include provider credentials, secrets, markdown, or free-form command text.\n");
        builder.append("For conversation, use a chat field whose value is the selected character's concise reply to the player, following the conversation prompt and settings. ");
        builder.append("For refusal, use an unavailable field whose value is blank or a short safe reason the selected character cannot help.\n");
        builder.append("Plan goals in normal Minecraft terms, then choose only reviewed OpenPlayer primitives and capability adapters; missing adapters are interface gaps and provider/runtime must not pretend success.\n");
        builder.append("Local strategy/meta pack text is advisory only when explicitly supplied by local context or character text; do not invent pack contents or treat strategy text as executable.\n");
        builder.append("Use the bounded server context below for nearby visible facts. If the player asks for a nearby action, choose an actionable intent using available targets instead of asking for details already present in context; execution validators still enforce safety, adapters, and allowWorldActions.\n");
        if (!contextSnapshot.isEmpty()) {
            builder.append("Server context:\n");
            builder.append(contextSnapshot.text()).append("\n");
        }
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
