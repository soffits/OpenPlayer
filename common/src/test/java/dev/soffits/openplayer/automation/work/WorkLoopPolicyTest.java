package dev.soffits.openplayer.automation.work;

import dev.soffits.openplayer.automation.AutomationInstructionParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class WorkLoopPolicyTest {
    private WorkLoopPolicyTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        validatesCropMaturityAndReplantMapping();
        validatesFarmRadiusParsing();
        validatesFishingDurationParsing();
    }

    private static void validatesCropMaturityAndReplantMapping() {
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        CropBlock carrots = (CropBlock) Blocks.CARROTS;
        CropBlock potatoes = (CropBlock) Blocks.POTATOES;
        CropBlock beetroots = (CropBlock) Blocks.BEETROOTS;
        BlockState matureWheat = wheat.getStateForAge(7);
        BlockState youngWheat = wheat.getStateForAge(3);
        BlockState matureCarrots = carrots.getStateForAge(7);
        BlockState maturePotatoes = potatoes.getStateForAge(7);
        BlockState matureBeetroots = beetroots.getStateForAge(3);
        BlockState matureNetherWart = Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 3);

        require(FarmingWorkPolicy.isMature(matureWheat), "mature wheat should be mature");
        require(!FarmingWorkPolicy.isMature(youngWheat), "young wheat should not be mature");
        require(FarmingWorkPolicy.replantItem(matureWheat) == Items.WHEAT_SEEDS, "wheat should replant with seeds");
        require(FarmingWorkPolicy.replantItem(matureCarrots) == Items.CARROT, "carrots should replant with carrot");
        require(FarmingWorkPolicy.replantItem(maturePotatoes) == Items.POTATO, "potatoes should replant with potato");
        require(FarmingWorkPolicy.replantItem(matureBeetroots) == Items.BEETROOT_SEEDS,
                "beetroots should replant with beetroot seeds");
        require(FarmingWorkPolicy.replantItem(matureNetherWart) == Items.NETHER_WART,
                "nether wart should replant with nether wart");
        require(!FarmingWorkPolicy.isMature(FarmingWorkPolicy.replantState(matureWheat)),
                "crop replant state should reset age");
        require(FarmingWorkPolicy.replantItem(Blocks.OAK_LOG.defaultBlockState()) == null,
                "non-crops should not have replant items");
    }

    private static void validatesFarmRadiusParsing() {
        require(AutomationInstructionParser.parseOptionalRadiusOrNegative(
                "", FarmingWorkPolicy.DEFAULT_RADIUS, FarmingWorkPolicy.MAX_RADIUS
        ) == FarmingWorkPolicy.DEFAULT_RADIUS, "blank farm radius should use default");
        require(AutomationInstructionParser.parseOptionalRadiusOrNegative(
                "12", FarmingWorkPolicy.DEFAULT_RADIUS, FarmingWorkPolicy.MAX_RADIUS
        ) == 12.0D, "farm radius should parse positive values");
        require(AutomationInstructionParser.parseOptionalRadiusOrNegative(
                "120", FarmingWorkPolicy.DEFAULT_RADIUS, FarmingWorkPolicy.MAX_RADIUS
        ) == FarmingWorkPolicy.MAX_RADIUS, "farm radius should cap at max");
        require(AutomationInstructionParser.parseOptionalRadiusOrNegative(
                "0", FarmingWorkPolicy.DEFAULT_RADIUS, FarmingWorkPolicy.MAX_RADIUS
        ) < 0.0D, "zero farm radius should reject");
    }

    private static void validatesFishingDurationParsing() {
        require(FishingWorkPolicy.parseDurationTicksOrNegative("") == FishingWorkPolicy.DEFAULT_DURATION_TICKS,
                "blank fishing duration should use default");
        require(FishingWorkPolicy.parseDurationTicksOrNegative("1.5") == 30,
                "fishing seconds should convert to ticks");
        require(FishingWorkPolicy.parseDurationTicksOrNegative("999") == FishingWorkPolicy.MAX_DURATION_TICKS,
                "fishing duration should cap at max");
        require(FishingWorkPolicy.parseDurationTicksOrNegative("0") < 0,
                "zero fishing duration should reject");
        require(FishingWorkPolicy.isStopInstruction("stop"), "stop should be a fishing stop instruction");
        require(FishingWorkPolicy.isStopInstruction("cancel"), "cancel should be a fishing stop instruction");
        require(!FishingWorkPolicy.isStopInstruction(""), "blank should not be a fishing stop instruction");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
