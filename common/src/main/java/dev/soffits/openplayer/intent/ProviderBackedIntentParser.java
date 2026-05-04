package dev.soffits.openplayer.intent;

import dev.soffits.openplayer.aicore.MinecraftPrimitiveTools;
import dev.soffits.openplayer.aicore.ToolArguments;
import dev.soffits.openplayer.aicore.ToolCall;
import dev.soffits.openplayer.aicore.ToolName;
import dev.soffits.openplayer.debug.OpenPlayerRawTrace;
import java.util.Locale;
import java.util.Optional;

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
            IntentPriority priority = parsePriority(providerIntent.priority());
            CommandIntent commandIntent = parseCommandIntent(providerIntent, priority);
            OpenPlayerRawTrace.parseOutput("provider_backed_parser", null,
                    "kind=" + providerIntent.kind() + " priority=" + providerIntent.priority()
                            + " instruction=" + providerIntent.instruction());
            return commandIntent;
        } catch (IntentParseException exception) {
            OpenPlayerRawTrace.parseRejection("provider_backed_parser", null,
                    "kind=" + providerIntent.kind() + " priority=" + providerIntent.priority()
                            + " instruction=" + providerIntent.instruction(), exception.getMessage());
            throw exception;
        }
    }

    private static CommandIntent parseCommandIntent(ProviderIntent providerIntent, IntentPriority priority)
            throws IntentParseException {
        Optional<ToolName> toolName = MinecraftPrimitiveTools.toolNameForProviderKind(providerIntent.kind());
        if (toolName.isPresent()) {
            ToolCall call = new ToolCall(toolName.get(), ToolArguments.instruction(providerIntent.instruction()));
            Optional<CommandIntent> commandIntent = MinecraftPrimitiveTools.toCommandIntent(call, priority);
            if (commandIntent.isPresent()) {
                return commandIntent.get();
            }
        }
        IntentKind kind = parseKind(providerIntent.kind());
        if (kind != IntentKind.CHAT && kind != IntentKind.UNAVAILABLE) {
            throw new IntentParseException("intent provider returned an unsupported primitive tool");
        }
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
