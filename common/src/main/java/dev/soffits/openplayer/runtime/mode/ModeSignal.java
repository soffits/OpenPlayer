package dev.soffits.openplayer.runtime.mode;

public record ModeSignal(
        boolean stuck,
        boolean droppedItemNearby,
        boolean creeperNearby,
        boolean lowHealth,
        boolean hostileAttacking
) {
}
