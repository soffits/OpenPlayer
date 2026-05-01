package dev.soffits.openplayer;

import dev.soffits.openplayer.intent.DisabledIntentParser;
import dev.soffits.openplayer.intent.IntentParser;
import dev.soffits.openplayer.intent.OpenAiCompatibleIntentProvider;
import dev.soffits.openplayer.intent.ProviderBackedIntentParser;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class OpenPlayerIntentParserConfig {
    public static final String ENABLED_KEY = "OPENPLAYER_INTENT_PARSER_ENABLED";
    public static final String ENDPOINT_KEY = "OPENPLAYER_INTENT_PROVIDER_ENDPOINT";
    public static final String API_KEY_KEY = "OPENPLAYER_INTENT_PROVIDER_API_KEY";
    public static final String MODEL_KEY = "OPENPLAYER_INTENT_PROVIDER_MODEL";

    private OpenPlayerIntentParserConfig() {
    }

    public static IntentParser createIntentParser() {
        if (!enabled()) {
            return new DisabledIntentParser();
        }

        URI endpointUri = endpointUri(requiredValue(ENDPOINT_KEY));
        String apiKey = requiredValue(API_KEY_KEY);
        String model = requiredValue(MODEL_KEY);
        return new ProviderBackedIntentParser(new OpenAiCompatibleIntentProvider(endpointUri, apiKey, model));
    }

    private static boolean enabled() {
        String value = configValue(ENABLED_KEY);
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        return normalizedValue.equals("true") || normalizedValue.equals("1") || normalizedValue.equals("yes");
    }

    private static String requiredValue(String key) {
        String value = configValue(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " must be set when intent parser provider is enabled");
        }
        return value.trim();
    }

    private static URI endpointUri(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalStateException(ENDPOINT_KEY + " must be an absolute URI");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(ENDPOINT_KEY + " must be a valid URI", exception);
        }
    }

    private static String configValue(String key) {
        String propertyValue = System.getProperty(key);
        if (propertyValue != null) {
            return propertyValue;
        }
        return System.getenv(key);
    }
}
