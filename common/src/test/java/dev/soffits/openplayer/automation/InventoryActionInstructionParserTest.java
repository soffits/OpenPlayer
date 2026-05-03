package dev.soffits.openplayer.automation;

import dev.soffits.openplayer.automation.InventoryActionInstructionParser.ParsedItemInstruction;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

public final class InventoryActionInstructionParserTest {
    private InventoryActionInstructionParserTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        acceptsItemCountAndOwnerSyntax();
        rejectsInvalidItemCountSyntax();
        rejectsInvalidTargetsForGive();
    }

    private static void acceptsItemCountAndOwnerSyntax() {
        ParsedItemInstruction itemOnly = InventoryActionInstructionParser.parseItemOnlyOrNull("minecraft:bread");
        require(itemOnly != null, "item-only syntax must parse");
        require(itemOnly.item() == Items.BREAD, "item-only syntax must resolve bread");
        require(itemOnly.count() == 1, "item-only syntax must default count to one");

        ParsedItemInstruction counted = InventoryActionInstructionParser.parseItemCountOrNull(" minecraft:bread   3 ", true);
        require(counted != null, "count syntax must parse with whitespace variants");
        require(counted.item() == Items.BREAD, "count syntax must resolve bread");
        require(counted.count() == 3, "count syntax must preserve requested count");
        require(!counted.ownerTarget(), "count syntax without owner token must use default owner target");

        ParsedItemInstruction owner = InventoryActionInstructionParser.parseItemCountOrNull("minecraft:bread 3 owner", true);
        require(owner != null, "owner target syntax must parse");
        require(owner.ownerTarget(), "owner token must be recorded");

        ParsedItemInstruction ownerWithoutCount = InventoryActionInstructionParser.parseItemCountOrNull("minecraft:bread owner", true);
        require(ownerWithoutCount != null, "owner target syntax without count must parse");
        require(ownerWithoutCount.count() == 1, "owner target without count must default count to one");
    }

    private static void rejectsInvalidItemCountSyntax() {
        require(InventoryActionInstructionParser.parseItemCountOrNull(null, true) == null, "null must reject");
        require(InventoryActionInstructionParser.parseItemCountOrNull("", true) == null, "blank must reject");
        require(InventoryActionInstructionParser.parseItemCountOrNull("minecraft:bread 1 owner extra", true) == null,
                "extra tokens must reject");
        require(InventoryActionInstructionParser.parseItemCountOrNull("minecraft:bread many", true) == null,
                "non-numeric count must reject");
        require(InventoryActionInstructionParser.parseItemCountOrNull("minecraft:bread 1.5", true) == null,
                "decimal count must reject");
        require(InventoryActionInstructionParser.parseItemCountOrNull("minecraft:bread 0", true) == null,
                "zero count must reject");
        require(InventoryActionInstructionParser.parseItemCountOrNull("minecraft:bread -1", true) == null,
                "negative count must reject");
        require(InventoryActionInstructionParser.parseItemCountOrNull("minecraft:cobblestone 65", true) == null,
                "one-stack MVP item/count syntax must reject counts above the vanilla max stack");
        require(InventoryActionInstructionParser.parseItemCountOrNull("minecraft:not_a_real_item", true) == null,
                "unknown item id must reject");
        require(InventoryActionInstructionParser.parseItemCountOrNull("minecraft:air", true) == null,
                "air item id must reject");
    }

    private static void rejectsInvalidTargetsForGive() {
        require(InventoryActionInstructionParser.parseItemCountOrNull("minecraft:bread 1 Steve", true) == null,
                "non-owner target must reject");
        require(InventoryActionInstructionParser.parseItemCountOrNull("minecraft:bread owner", false) == null,
                "owner token must reject when targets are not allowed");
        require(InventoryActionInstructionParser.parseItemOnlyOrNull("minecraft:bread 3") == null,
                "item-only parser must reject count syntax");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
