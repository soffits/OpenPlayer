package dev.soffits.openplayer.intent;

public interface IntentParser {
    CommandIntent parse(String input) throws IntentParseException;
}
