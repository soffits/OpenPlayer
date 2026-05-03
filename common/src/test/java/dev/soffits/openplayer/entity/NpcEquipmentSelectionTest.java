package dev.soffits.openplayer.entity;

import java.util.List;

public final class NpcEquipmentSelectionTest {
    private NpcEquipmentSelectionTest() {
    }

    public static void main(String[] args) {
        selectsBestUpgrade();
        rejectsEqualWeakerAndInvalidScores();
    }

    private static void selectsBestUpgrade() {
        int slot = NpcEquipmentSelection.bestReplacementSlot(List.of(1, 4, 3), Integer::doubleValue, 2.0D);
        require(slot == 1, "best replacement above current score must be selected");
    }

    private static void rejectsEqualWeakerAndInvalidScores() {
        require(!NpcEquipmentSelection.shouldReplaceArmor(2.0D, 2.0D), "equal armor must not replace");
        require(!NpcEquipmentSelection.shouldReplaceArmor(1.0D, 2.0D), "weaker armor must not replace");
        require(!NpcEquipmentSelection.shouldReplaceArmor(Double.NaN, 0.0D), "invalid armor score must not replace");
        int slot = NpcEquipmentSelection.bestReplacementSlot(List.of(0.0D, Double.NaN, 2.0D), Double::doubleValue, 2.0D);
        require(slot == -1, "no upgrade must return no slot");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
