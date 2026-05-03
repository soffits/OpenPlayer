package dev.soffits.openplayer.intent;

import dev.soffits.openplayer.debug.OpenPlayerRawTrace;
import java.util.Locale;

public final class ProviderBackedIntentParser implements IntentParser {
    private final IntentProvider provider;

    public ProviderBackedIntentParser(IntentProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider cannot be null");
        }
        this.provider = provider;
    }

    @Override
    public CommandIntent parse(String input) throws IntentParseException {
        if (input == null) {
            throw new IntentParseException("input cannot be null");
        }

        ProviderIntent providerIntent;
        try {
            OpenPlayerRawTrace.parseInput("provider_backed_parser", null, input);
            providerIntent = provider.parseIntent(input);
        } catch (IntentProviderException exception) {
            OpenPlayerRawTrace.parseRejection("provider_backed_parser", null, input, exception.getMessage());
            throw new IntentParseException("intent provider failed", exception);
        } catch (RuntimeException exception) {
            OpenPlayerRawTrace.parseRejection("provider_backed_parser", null, input, exception.getMessage());
            throw new IntentParseException("intent provider returned an invalid response", exception);
        }

        if (providerIntent == null) {
            OpenPlayerRawTrace.parseRejection("provider_backed_parser", null, input, "intent provider returned no intent");
            throw new IntentParseException("intent provider returned no intent");
        }

        try {
            IntentKind kind = parseKind(providerIntent.kind());
            IntentPriority priority = parsePriority(providerIntent.priority());
            OpenPlayerRawTrace.parseOutput("provider_backed_parser", null,
                    "kind=" + providerIntent.kind() + " priority=" + providerIntent.priority()
                            + " instruction=" + providerIntent.instruction());
            return new CommandIntent(kind, priority, providerIntent.instruction());
        } catch (IntentParseException exception) {
            OpenPlayerRawTrace.parseRejection("provider_backed_parser", null,
                    "kind=" + providerIntent.kind() + " priority=" + providerIntent.priority()
                            + " instruction=" + providerIntent.instruction(), exception.getMessage());
            throw exception;
        }
    }

    private static IntentKind parseKind(String value) throws IntentParseException {
        String normalizedValue = normalizeEnumValue(value);
        try {
            return IntentKind.valueOf(normalizedValue);
        } catch (IllegalArgumentException exception) {
            throw new IntentParseException("intent provider returned an unknown kind", exception);
        }
    }

    private static IntentPriority parsePriority(String value) throws IntentParseException {
        String normalizedValue = normalizeEnumValue(value);
        try {
            return IntentPriority.valueOf(normalizedValue);
        } catch (IllegalArgumentException exception) {
            throw new IntentParseException("intent provider returned an unknown priority", exception);
        }
    }

    private static String normalizeEnumValue(String value) throws IntentParseException {
        if (value == null) {
            throw new IntentParseException("intent provider returned a null enum value");
        }
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            throw new IntentParseException("intent provider returned a blank enum value");
        }
        return trimmedValue.toUpperCase(Locale.ROOT);
    }
}
