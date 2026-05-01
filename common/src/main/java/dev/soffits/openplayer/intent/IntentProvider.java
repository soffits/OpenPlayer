package dev.soffits.openplayer.intent;

public interface IntentProvider {
    ProviderIntent parseIntent(String input) throws IntentProviderException;
}
