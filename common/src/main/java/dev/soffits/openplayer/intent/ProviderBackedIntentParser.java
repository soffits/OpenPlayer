package dev.soffits.openplayer.intent;

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
            providerIntent = provider.parseIntent(input);
        } catch (IntentProviderException exception) {
            throw new IntentParseException("intent provider failed", exception);
        } catch (RuntimeException exception) {
            throw new IntentParseException("intent provider returned an invalid response", exception);
        }

        if (providerIntent == null) {
            throw new IntentParseException("intent provider returned no intent");
        }

        IntentKind kind = parseKind(providerIntent.kind());
        IntentPriority priority = parsePriority(providerIntent.priority());
        return new CommandIntent(kind, priority, providerIntent.instruction());
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
