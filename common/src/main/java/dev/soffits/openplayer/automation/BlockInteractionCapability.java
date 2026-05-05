package dev.soffits.openplayer.automation;

enum BlockInteractionCapability {
    LEVER("lever"),
    BUTTON("button"),
    DOOR("door"),
    TRAPDOOR("trapdoor"),
    FENCE_GATE("fence_gate"),
    BELL("bell"),
    NOTE_BLOCK("note_block"),
    LOADED_CONTAINER("loaded_container"),
    UNAVAILABLE("capability_unavailable");

    private final String id;

    BlockInteractionCapability(String id) {
        this.id = id;
    }

    String id() {
        return id;
    }
}
