package dev.soffits.openplayer.conversation;

import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentParseException;

public final class ConversationIntentValidator {
    private static final int MAX_INSTRUCTION_LENGTH = 512;

    private ConversationIntentValidator() {
    }

    public static CommandIntent requireActionable(CommandIntent intent) throws IntentParseException {
        if (intent == null) {
            throw new IntentParseException("conversation parser returned no intent");
        }
        if (intent.kind() == IntentKind.UNAVAILABLE) {
            throw new IntentParseException("conversation parser returned unavailable intent");
        }
        String instruction = intent.instruction();
        if (instruction.length() > MAX_INSTRUCTION_LENGTH) {
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
