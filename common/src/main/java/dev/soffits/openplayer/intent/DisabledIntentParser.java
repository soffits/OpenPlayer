package dev.soffits.openplayer.intent;

public final class DisabledIntentParser implements IntentParser {
    @Override
    public CommandIntent parse(String input) throws IntentParseException {
        if (input == null) {
            throw new IntentParseException("input cannot be null");
        }
        return new CommandIntent(IntentKind.UNAVAILABLE, IntentPriority.NORMAL, input);
    }
}
