package dev.soffits.openplayer;

import dev.soffits.openplayer.intent.DisabledIntentParser;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

public final class OpenPlayerIntentParserConfigTest {
    private OpenPlayerIntentParserConfigTest() {
    }

    public static void main(String[] args) throws Exception {
        resolvesJvmBeforeEnvironmentBeforeUiConfig();
        rejectsInvalidEndpointAndOversizedValues();
        rejectsEnabledIncompleteProviderConfig();
        preservesBlankApiKeyUnlessClearIsExplicit();
        rejectsEnabledClearWithoutHigherPriorityApiKey();
        rejectsClearedUiKeyWhenHigherPriorityEnabledRemainsTrue();
        acceptsEnabledConfigCompletedByHigherPriorityValues();
        statusAndParserCreationDoNotRequireLoaderContext();
    }

    private static void resolvesJvmBeforeEnvironmentBeforeUiConfig() throws Exception {
        Path configPath = Files.createTempDirectory("openplayer-provider-test").resolve("provider.properties");
        saveProviderConfig(configPath, new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(
                true,
                "https://ui.example.invalid/v1/chat/completions",
                "ui-model",
                "ui-key",
                false
        ));
        Map<String, String> properties = Map.of(OpenPlayerIntentParserConfig.MODEL_KEY, "jvm-model");
        Map<String, String> environment = Map.of(OpenPlayerIntentParserConfig.MODEL_KEY, "env-model");
        OpenPlayerIntentParserConfig.ResolvedConfigValue resolved = OpenPlayerIntentParserConfig.configValue(
                OpenPlayerIntentParserConfig.MODEL_KEY,
                OpenPlayerIntentParserConfig.PROVIDER_MODEL_PROPERTY,
                properties::get,
                environment::get,
                configPath
        );
        require("jvm-model".equals(resolved.value()), "JVM property must win over environment and UI config");
        require("JVM property".equals(resolved.source()), "JVM source label must be reported");

        resolved = OpenPlayerIntentParserConfig.configValue(
                OpenPlayerIntentParserConfig.ENDPOINT_KEY,
                OpenPlayerIntentParserConfig.PROVIDER_ENDPOINT_PROPERTY,
                Map.<String, String>of()::get,
                Map.of(OpenPlayerIntentParserConfig.ENDPOINT_KEY, "https://env.example.invalid/v1/chat/completions")::get,
                configPath
        );
        require("https://env.example.invalid/v1/chat/completions".equals(resolved.value()), "Environment must win over UI config");
        require("environment".equals(resolved.source()), "Environment source label must be reported");

        resolved = OpenPlayerIntentParserConfig.configValue(
                OpenPlayerIntentParserConfig.API_KEY_KEY,
                OpenPlayerIntentParserConfig.PROVIDER_API_KEY_PROPERTY,
                Map.<String, String>of()::get,
                Map.<String, String>of()::get,
                configPath
        );
        require("ui-key".equals(resolved.value()), "UI config must be used as fallback");
        require("UI config".equals(resolved.source()), "UI source label must be reported");
    }

    private static void rejectsInvalidEndpointAndOversizedValues() throws Exception {
        Path configPath = Files.createTempDirectory("openplayer-provider-test").resolve("provider.properties");
        OpenPlayerIntentParserConfig.ProviderConfigSaveResult invalidEndpoint = saveProviderConfig(
                configPath,
                new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(true, "not-absolute", "model", "key", false)
        );
        require(!invalidEndpoint.accepted(), "Invalid endpoint must be rejected");
        OpenPlayerIntentParserConfig.ProviderConfigSaveResult oversizedModel = saveProviderConfig(
                configPath,
                new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(true, "https://example.invalid", "m".repeat(129), "key", false)
        );
        require(!oversizedModel.accepted(), "Oversized model must be rejected");
    }

    private static void preservesBlankApiKeyUnlessClearIsExplicit() throws Exception {
        Path configPath = Files.createTempDirectory("openplayer-provider-test").resolve("provider.properties");
        saveProviderConfig(configPath, new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(
                true,
                "https://example.invalid/v1/chat/completions",
                "model-a",
                "secret-a",
                false
        ));
        OpenPlayerIntentParserConfig.ProviderConfigSaveResult preserved = saveProviderConfig(configPath, new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(
                true,
                "https://example.invalid/v1/chat/completions",
                "model-b",
                "",
                false
        ));
        require(preserved.accepted(), "Enabled provider config must accept a blank API key when preserving an existing key");
        require("secret-a".equals(load(configPath).getProperty(OpenPlayerIntentParserConfig.PROVIDER_API_KEY_PROPERTY)), "Blank API key must preserve existing key");
        saveProviderConfig(configPath, new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(
                false,
                "https://example.invalid/v1/chat/completions",
                "model-b",
                "",
                true
        ));
        require(load(configPath).getProperty(OpenPlayerIntentParserConfig.PROVIDER_API_KEY_PROPERTY) == null, "Explicit clear must remove API key");
    }

    private static void rejectsEnabledIncompleteProviderConfig() throws Exception {
        Path configPath = Files.createTempDirectory("openplayer-provider-test").resolve("provider.properties");
        OpenPlayerIntentParserConfig.ProviderConfigSaveResult result = saveProviderConfig(
                configPath,
                new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(true, "", "model", "key", false)
        );
        require(!result.accepted(), "Enabled provider config must reject a missing endpoint");

        result = saveProviderConfig(
                configPath,
                new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(true, "https://example.invalid/v1/chat/completions", "", "key", false)
        );
        require(!result.accepted(), "Enabled provider config must reject a missing model");

        result = saveProviderConfig(
                configPath,
                new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(true, "https://example.invalid/v1/chat/completions", "model", "", false)
        );
        require(!result.accepted(), "Enabled provider config must reject a missing API key");
    }

    private static void rejectsEnabledClearWithoutHigherPriorityApiKey() throws Exception {
        Path configPath = Files.createTempDirectory("openplayer-provider-test").resolve("provider.properties");
        saveProviderConfig(configPath, new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(
                true,
                "https://example.invalid/v1/chat/completions",
                "model-a",
                "secret-a",
                false
        ));
        OpenPlayerIntentParserConfig.ProviderConfigSaveResult result = saveProviderConfig(configPath, new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(
                true,
                "https://example.invalid/v1/chat/completions",
                "model-a",
                "",
                true
        ));
        require(!result.accepted(), "Enabled provider config must reject clearing the API key without a higher-priority key");
        require("secret-a".equals(load(configPath).getProperty(OpenPlayerIntentParserConfig.PROVIDER_API_KEY_PROPERTY)), "Rejected clear must preserve the existing key");
    }

    private static void rejectsClearedUiKeyWhenHigherPriorityEnabledRemainsTrue() throws Exception {
        Path configPath = Files.createTempDirectory("openplayer-provider-test").resolve("provider.properties");
        saveProviderConfig(configPath, new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(
                false,
                "https://ui.example.invalid/v1/chat/completions",
                "ui-model",
                "secret-a",
                false
        ));
        Map<String, String> environment = Map.of(
                OpenPlayerIntentParserConfig.ENABLED_KEY, "true",
                OpenPlayerIntentParserConfig.ENDPOINT_KEY, "https://env.example.invalid/v1/chat/completions",
                OpenPlayerIntentParserConfig.MODEL_KEY, "env-model"
        );
        OpenPlayerIntentParserConfig.ProviderConfigSaveResult result = OpenPlayerIntentParserConfig.saveProviderConfig(
                configPath,
                new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(false, "", "", "", true),
                Map.<String, String>of()::get,
                environment::get
        );
        require(!result.accepted(), "Higher-priority enabled=true must reject clearing the only API key");
        require("secret-a".equals(load(configPath).getProperty(OpenPlayerIntentParserConfig.PROVIDER_API_KEY_PROPERTY)), "Rejected effective config must preserve the existing API key");
    }

    private static void acceptsEnabledConfigCompletedByHigherPriorityValues() throws Exception {
        Path configPath = Files.createTempDirectory("openplayer-provider-test").resolve("provider.properties");
        Map<String, String> properties = Map.of(OpenPlayerIntentParserConfig.API_KEY_KEY, "jvm-key");
        Map<String, String> environment = Map.of(
                OpenPlayerIntentParserConfig.ENDPOINT_KEY, "https://env.example.invalid/v1/chat/completions",
                OpenPlayerIntentParserConfig.MODEL_KEY, "env-model",
                OpenPlayerIntentParserConfig.API_KEY_KEY, "env-key"
        );
        OpenPlayerIntentParserConfig.ProviderConfigSaveResult result = OpenPlayerIntentParserConfig.saveProviderConfig(
                configPath,
                new OpenPlayerIntentParserConfig.ProviderConfigSaveRequest(true, "", "", "", true),
                properties::get,
                environment::get
        );
        require(result.accepted(), "Enabled provider config must accept missing UI fields completed by JVM or environment values");
        Properties saved = load(configPath);
        require("true".equals(saved.getProperty(OpenPlayerIntentParserConfig.PROVIDER_ENABLED_PROPERTY)), "Enabled UI fallback must be saved");
        require(saved.getProperty(OpenPlayerIntentParserConfig.PROVIDER_ENDPOINT_PROPERTY) == null, "Blank endpoint input must not persist when environment supplies it");
        require(saved.getProperty(OpenPlayerIntentParserConfig.PROVIDER_MODEL_PROPERTY) == null, "Blank model input must not persist when environment supplies it");
        require(saved.getProperty(OpenPlayerIntentParserConfig.PROVIDER_API_KEY_PROPERTY) == null, "Explicit clear must not persist a UI API key when JVM supplies it");
    }

    private static void statusAndParserCreationDoNotRequireLoaderContext() {
        IntentParserRuntimeStatus status = OpenPlayerIntentParserConfig.status();
        require(!status.enabled(), "Intent parser must be disabled without config");
        require("not configured".equals(status.endpointSource()), "Missing endpoint source must be reported without loader context");
        require("not configured".equals(status.modelSource()), "Missing model source must be reported without loader context");
        require("not configured".equals(status.apiKeySource()), "Missing API key source must be reported without loader context");
        require(OpenPlayerIntentParserConfig.createIntentParser() instanceof DisabledIntentParser, "Missing config must create disabled parser without loader context");
    }

    private static Properties load(Path path) throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private static OpenPlayerIntentParserConfig.ProviderConfigSaveResult saveProviderConfig(
            Path configPath,
            OpenPlayerIntentParserConfig.ProviderConfigSaveRequest request
    ) {
        return OpenPlayerIntentParserConfig.saveProviderConfig(
                configPath,
                request,
                Map.<String, String>of()::get,
                Map.<String, String>of()::get
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
