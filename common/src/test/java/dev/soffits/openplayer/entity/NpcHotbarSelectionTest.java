package dev.soffits.openplayer.entity;

import java.util.List;

public final class NpcHotbarSelectionTest {
    private NpcHotbarSelectionTest() {
    }

    public static void main(String[] args) {
        validatesHotbarSlotBounds();
        returnsFirstMatchingSlot();
        returnsBestScoredSlot();
        ignoresInvalidScores();
        limitsSelectionToHotbar();
    }

    private static void validatesHotbarSlotBounds() {
        require(NpcHotbarSelection.isHotbarSlot(0), "slot 0 must be a hotbar slot");
        require(NpcHotbarSelection.isHotbarSlot(8), "slot 8 must be a hotbar slot");
        require(!NpcHotbarSelection.isHotbarSlot(-1), "negative slots must be rejected");
        require(!NpcHotbarSelection.isHotbarSlot(9), "slot 9 must be outside the hotbar");
        require(NpcHotbarSelection.validatedHotbarSlot(7) == 7, "valid slots must be preserved");
        require(NpcHotbarSelection.validatedHotbarSlot(31) == 0, "invalid persisted slots must fall back to slot 0");
    }

    private static void returnsFirstMatchingSlot() {
        int slot = NpcHotbarSelection.firstMatchingSlot(List.of("air", "dirt", "stone"), "stone"::equals);
        require(slot == 2, "first matching slot must be returned");
    }

    private static void returnsBestScoredSlot() {
        int slot = NpcHotbarSelection.bestScoredSlot(List.of(1, 6, 6, 3), Integer::doubleValue);
        require(slot == 1, "equal best scores must keep the lowest slot");
    }

    private static void ignoresInvalidScores() {
        int slot = NpcHotbarSelection.bestScoredSlot(List.of(Double.NaN, -1.0D, 0.0D, 2.0D), Double::doubleValue);
        require(slot == 3, "invalid, negative, and zero scores must not select a slot");
    }

    private static void limitsSelectionToHotbar() {
        int slot = NpcHotbarSelection.firstMatchingSlot(
                List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"),
                "9"::equals
        );
        require(slot == -1, "selection must ignore inventory slots outside the hotbar");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
