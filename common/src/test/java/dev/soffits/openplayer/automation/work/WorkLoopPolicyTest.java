package dev.soffits.openplayer.automation.work;

import dev.soffits.openplayer.automation.AutomationInstructionParser;
import dev.soffits.openplayer.automation.work.FarmingWorkPolicy.FarmingReplantPlan;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
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
        requireReplantPlan(matureWheat, false, "wheat");
        requireReplantPlan(matureCarrots, false, "carrots");
        requireReplantPlan(maturePotatoes, false, "potatoes");
        requireReplantPlan(matureBeetroots, false, "beetroots");
        requireReplantPlan(matureNetherWart, true, "nether wart");
        require(!FarmingWorkPolicy.isMature(requireReplantPlan(matureWheat, false, "wheat").state()),
                "crop replant state should reset age");
        require(FarmingWorkPolicy.replantPlan(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Blocks.OAK_LOG.defaultBlockState()
        ) == null,
                "non-crops should not have replant items");
    }

    private static FarmingReplantPlan requireReplantPlan(
            BlockState matureState,
            boolean adapterException,
            String description
    ) {
        FarmingReplantPlan plan = FarmingWorkPolicy.replantPlan(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, matureState);
        require(plan != null, description + " should have a replant capability");
        require(Block.byItem(plan.item()) == matureState.getBlock(), description + " replant item should resolve to same block");
        require(plan.state().getBlock() == matureState.getBlock(), description + " replant state should use same block");
        require(plan.adapterException() == adapterException,
                description + " should report the expected adapter boundary");
        return plan;
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
