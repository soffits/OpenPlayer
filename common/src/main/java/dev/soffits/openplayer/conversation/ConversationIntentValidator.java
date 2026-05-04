package dev.soffits.openplayer.conversation;

import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentParseException;
import dev.soffits.openplayer.intent.ProviderPlanIntentCodec;

public final class ConversationIntentValidator {
    private static final int MAX_INSTRUCTION_LENGTH = 512;

    private ConversationIntentValidator() {
    }

    public static CommandIntent requireConversationIntent(CommandIntent intent) throws IntentParseException {
        if (intent == null) {
            throw new IntentParseException("conversation parser returned no intent");
        }
        String instruction = intent.instruction();
        int maxInstructionLength = intent.kind() == IntentKind.PROVIDER_PLAN
                ? ProviderPlanIntentCodec.MAX_ENCODED_LENGTH
                : MAX_INSTRUCTION_LENGTH;
        if (instruction.length() > maxInstructionLength) {
            throw new IntentParseException("conversation parser returned an oversized instruction");
        }
        for (int index = 0; index < instruction.length(); index++) {
            char character = instruction.charAt(index);
            if (Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t') {
                throw new IntentParseException("conversation parser returned a control character");
            }
        }
        return intent;
    }
}
