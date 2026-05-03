package dev.soffits.openplayer;

import dev.soffits.openplayer.intent.DisabledIntentParser;
import dev.soffits.openplayer.intent.IntentParser;
import dev.soffits.openplayer.intent.OpenAiCompatibleIntentProvider;
import dev.soffits.openplayer.intent.ProviderBackedIntentParser;
import dev.soffits.openplayer.character.OpenPlayerLocalCharacters;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;

public final class OpenPlayerIntentParserConfig {
    public static final String ENDPOINT_KEY = "OPENPLAYER_INTENT_PROVIDER_ENDPOINT";
    public static final String API_KEY_KEY = "OPENPLAYER_INTENT_PROVIDER_API_KEY";
    public static final String MODEL_KEY = "OPENPLAYER_INTENT_PROVIDER_MODEL";
    public static final String PROVIDER_CONFIG_FILE_NAME = "provider.properties";
    static final String PROVIDER_ENDPOINT_PROPERTY = "endpoint";
    static final String PROVIDER_API_KEY_PROPERTY = "apiKey";
    static final String PROVIDER_MODEL_PROPERTY = "model";
    public static final int MAX_ENDPOINT_LENGTH = 512;
    public static final int MAX_MODEL_LENGTH = 128;
    public static final int MAX_API_KEY_LENGTH = 512;

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

    public static IntentParserRuntimeStatus status() {
        ResolvedConfigValue endpoint = configValue(ENDPOINT_KEY, PROVIDER_ENDPOINT_PROPERTY);
        ResolvedConfigValue model = configValue(MODEL_KEY, PROVIDER_MODEL_PROPERTY);
        ResolvedConfigValue apiKey = configValue(API_KEY_KEY, PROVIDER_API_KEY_PROPERTY);
        return new IntentParserRuntimeStatus(
                enabled(endpoint, model, apiKey),
                safeEndpointStatus(endpoint.value()),
                endpoint.source(),
                hasValue(model.value()),
                model.source(),
                hasValue(apiKey.value()),
                apiKey.source()
        );
    }

    public static ProviderConfigSaveResult saveProviderConfig(ProviderConfigSaveRequest request) {
        return saveProviderConfig(providerConfigPath(), request);
    }

    public static Path providerConfigPath() {
        return OpenPlayerLocalCharacters.openPlayerDirectory().resolve(PROVIDER_CONFIG_FILE_NAME);
    }

    private static boolean enabled() {
        ResolvedConfigValue endpoint = configValue(ENDPOINT_KEY, PROVIDER_ENDPOINT_PROPERTY);
        ResolvedConfigValue model = configValue(MODEL_KEY, PROVIDER_MODEL_PROPERTY);
        ResolvedConfigValue apiKey = configValue(API_KEY_KEY, PROVIDER_API_KEY_PROPERTY);
        return enabled(endpoint, model, apiKey);
    }

    private static boolean enabled(ResolvedConfigValue endpoint, ResolvedConfigValue model, ResolvedConfigValue apiKey) {
        return hasValue(endpoint.value()) && hasValue(model.value()) && hasValue(apiKey.value());
    }

    private static String requiredValue(String key) {
        String value = configValue(key, providerPropertyName(key)).value();
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

    private static String safeEndpointStatus(String value) {
        if (!hasValue(value)) {
            return "not configured";
        }
        try {
            URI uri = new URI(value.trim());
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "configured";
            }
            if (host.length() > 100) {
                return "configured";
            }
            return host;
        } catch (URISyntaxException exception) {
            return "configured";
        }
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private static ResolvedConfigValue configValue(String key, String providerPropertyName) {
        return configValue(
                key,
                providerPropertyName,
                OpenPlayerIntentParserConfig::systemProperty,
                OpenPlayerIntentParserConfig::environmentValue,
                OpenPlayerIntentParserConfig::providerConfigPathIfAvailable
        );
    }

    static ResolvedConfigValue configValue(
            String key,
            String providerPropertyName,
            Function<String, String> propertyResolver,
            Function<String, String> environmentResolver,
            Path providerConfigPath
    ) {
        return configValue(key, providerPropertyName, propertyResolver, environmentResolver, () -> providerConfigPath);
    }

    private static ResolvedConfigValue configValue(
            String key,
            String providerPropertyName,
            Function<String, String> propertyResolver,
            Function<String, String> environmentResolver,
            Supplier<Path> providerConfigPathResolver
    ) {
        String propertyValue = propertyResolver.apply(key);
        if (propertyValue != null) {
            return new ResolvedConfigValue(propertyValue, "JVM property");
        }
        String environmentValue = environmentResolver.apply(key);
        if (environmentValue != null) {
            return new ResolvedConfigValue(environmentValue, "environment");
        }
        Path providerConfigPath = providerConfigPathResolver.get();
        String persistedValue = readProviderProperties(providerConfigPath).getProperty(providerPropertyName);
        if (persistedValue != null) {
            return new ResolvedConfigValue(persistedValue, "UI config");
        }
        return new ResolvedConfigValue(null, "not configured");
    }

    static ProviderConfigSaveResult saveProviderConfig(Path providerConfigPath, ProviderConfigSaveRequest request) {
        return saveProviderConfig(
                providerConfigPath,
                request,
                OpenPlayerIntentParserConfig::systemProperty,
                OpenPlayerIntentParserConfig::environmentValue
        );
    }

    static ProviderConfigSaveResult saveProviderConfig(
            Path providerConfigPath,
            ProviderConfigSaveRequest request,
            Function<String, String> propertyResolver,
            Function<String, String> environmentResolver
    ) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        ProviderConfigValidation validation = ProviderConfigValidation.validate(request);
        if (!validation.accepted()) {
            return new ProviderConfigSaveResult(false, validation.message());
        }
        try {
            Properties properties = readProviderProperties(providerConfigPath);
            setOrRemove(properties, PROVIDER_ENDPOINT_PROPERTY, request.endpoint());
            setOrRemove(properties, PROVIDER_MODEL_PROPERTY, request.model());
            if (request.clearApiKey()) {
                properties.remove(PROVIDER_API_KEY_PROPERTY);
            } else if (!request.apiKey().isBlank()) {
                properties.setProperty(PROVIDER_API_KEY_PROPERTY, request.apiKey().trim());
            }
            writeProviderProperties(providerConfigPath, properties);
            return new ProviderConfigSaveResult(true, request.apiKey().isBlank() && !request.clearApiKey()
                    ? "Provider config saved; existing API key preserved"
                    : "Provider config saved");
        } catch (IOException exception) {
            return new ProviderConfigSaveResult(false, "Provider config save failed");
        }
    }

    private static String systemProperty(String key) {
        String propertyValue = System.getProperty(key);
        if (propertyValue != null) {
            return propertyValue;
        }
        return null;
    }

    private static String environmentValue(String key) {
        return System.getenv(key);
    }

    private static Path providerConfigPathIfAvailable() {
        try {
            return providerConfigPath();
        } catch (RuntimeException | AssertionError exception) {
            return null;
        }
    }

    private static String providerPropertyName(String key) {
        return switch (key) {
            case ENDPOINT_KEY -> PROVIDER_ENDPOINT_PROPERTY;
            case API_KEY_KEY -> PROVIDER_API_KEY_PROPERTY;
            case MODEL_KEY -> PROVIDER_MODEL_PROPERTY;
            default -> throw new IllegalArgumentException("Unknown intent parser config key");
        };
    }

    private static Properties readProviderProperties(Path providerConfigPath) {
        Properties properties = new Properties();
        if (providerConfigPath == null || !Files.isRegularFile(providerConfigPath)) {
            return properties;
        }
        try (InputStream inputStream = Files.newInputStream(providerConfigPath, StandardOpenOption.READ)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            return new Properties();
        }
        return properties;
    }

    private static void writeProviderProperties(Path providerConfigPath, Properties properties) throws IOException {
        Path parent = providerConfigPath.getParent();
        Files.createDirectories(parent);
        Path tempFile = Files.createTempFile(parent, "provider", ".tmp");
        try (OutputStream outputStream = Files.newOutputStream(tempFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(outputStream, "OpenPlayer provider config. JVM system properties and environment variables take priority.");
        }
        try {
            Files.move(tempFile, providerConfigPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            Files.move(tempFile, providerConfigPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void setOrRemove(Properties properties, String key, String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            properties.remove(key);
        } else {
            properties.setProperty(key, trimmed);
        }
    }

    public record ProviderConfigSaveRequest(String endpoint, String model, String apiKey, boolean clearApiKey) {
        public ProviderConfigSaveRequest {
            endpoint = endpoint == null ? "" : endpoint.trim();
            model = model == null ? "" : model.trim();
            apiKey = apiKey == null ? "" : apiKey.trim();
        }
    }

    public record ProviderConfigSaveResult(boolean accepted, String message) {
        public ProviderConfigSaveResult {
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("message cannot be blank");
            }
        }
    }

    record ResolvedConfigValue(String value, String source) {
        ResolvedConfigValue {
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("source cannot be blank");
            }
        }
    }

    record ProviderConfigValidation(boolean accepted, String message) {
        static ProviderConfigValidation validate(ProviderConfigSaveRequest request) {
            if (request.endpoint().length() > MAX_ENDPOINT_LENGTH) {
                return rejected("Provider endpoint is too long");
            }
            if (request.model().length() > MAX_MODEL_LENGTH) {
                return rejected("Provider model is too long");
            }
            if (request.apiKey().length() > MAX_API_KEY_LENGTH) {
                return rejected("Provider API key is too long");
            }
            if (!request.endpoint().isBlank()) {
                try {
                    URI uri = new URI(request.endpoint());
                    if (uri.getScheme() == null || uri.getHost() == null) {
                        return rejected("Provider endpoint must be an absolute URI");
                    }
                } catch (URISyntaxException exception) {
                    return rejected("Provider endpoint must be a valid URI");
                }
            }
            return new ProviderConfigValidation(true, "accepted");
        }

        private static ProviderConfigValidation rejected(String message) {
            return new ProviderConfigValidation(false, message);
        }
    }
}
