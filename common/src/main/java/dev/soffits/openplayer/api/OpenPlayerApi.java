package dev.soffits.openplayer.api;

public final class OpenPlayerApi {
    private static AiPlayerNpcService service;

    private OpenPlayerApi() {
    }

    public static AiPlayerNpcService npcService() {
        if (service == null) {
            service = new UnavailableAiPlayerNpcService();
        }
        return service;
    }

    public static void registerNpcService(AiPlayerNpcService npcService) {
        if (npcService == null) {
            throw new IllegalArgumentException("npcService cannot be null");
        }
        service = npcService;
    }

    public static void registerUnavailableNpcService() {
        service = new UnavailableAiPlayerNpcService();
    }
}
