package dev.soffits.openplayer.aicore;

public final class AICorePluginRegistryTest {
    private AICorePluginRegistryTest() {
    }

    public static void main(String[] args) {
        AICorePluginRegistry registry = AICorePluginRegistry.defaults();
        AICoreTestSupport.require(registry.hasPlugin("minecraft-core"), "default registry must include minecraft-core");
        AICoreTestSupport.require(registry.hasPlugin("creative-policy"), "default registry must include creative-policy");
        try {
            registry.rejectProviderOriginPluginLoad("provider-script");
            throw new AssertionError("provider-origin plugin loading must be rejected");
        } catch (IllegalArgumentException expected) {
            AICoreTestSupport.require(expected.getMessage().contains("provider-origin plugin loading is not allowed"), "unexpected plugin rejection");
        }
    }
}
