package dev.soffits.openplayer.automation;

enum EntityInteractionCapability {
    UNAVAILABLE("capability_unavailable");

    private final String id;

    EntityInteractionCapability(String id) {
        this.id = id;
    }

    String id() {
        return id;
    }
}
