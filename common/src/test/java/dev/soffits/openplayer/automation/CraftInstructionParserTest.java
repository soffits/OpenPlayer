package dev.soffits.openplayer.automation;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class CraftInstructionParserTest {
    private CraftInstructionParserTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        CraftInstructionParser.CraftInstruction instruction = CraftInstructionParser.parseOrNull(
                "minecraft:iron_pickaxe 1 table 10 64 -2"
        );
        require(instruction != null, "table craft instruction must parse");
        require(instruction.recipeId().toString().equals("minecraft:iron_pickaxe"), "recipe id must be preserved");
        require(instruction.count() == 1, "count must be preserved");
        require(instruction.craftingTablePos() != null, "crafting table position must be recovered");
        require(instruction.craftingTablePos().x() == 10, "table x must be preserved");
        require(instruction.craftingTablePos().y() == 64, "table y must be preserved");
        require(instruction.craftingTablePos().z() == -2, "table z must be preserved");

        require(CraftInstructionParser.parseOrNull("minecraft:oak_planks") != null,
                "recipe-only craft instruction must stay compatible");
        require(CraftInstructionParser.parseOrNull("minecraft:oak_planks 2") != null,
                "recipe/count craft instruction must stay compatible");
        require(CraftInstructionParser.parseOrNull("minecraft:oak_planks 2 table 1 64") == null,
                "missing table coordinate must reject");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
