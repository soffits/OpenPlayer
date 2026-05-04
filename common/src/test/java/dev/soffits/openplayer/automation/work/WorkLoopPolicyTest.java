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
        validatesBoundedRepeatParsing();
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

    private static void validatesBoundedRepeatParsing() {
        WorkInstruction blankFarm = WorkRepeatPolicy.parseRadiusInstructionOrNull(
                "", FarmingWorkPolicy.DEFAULT_RADIUS, FarmingWorkPolicy.MAX_RADIUS
        );
        require(blankFarm != null && blankFarm.value() == FarmingWorkPolicy.DEFAULT_RADIUS
                && blankFarm.repeatCount() == 1, "blank farm instruction should use defaults");

        WorkInstruction positionalFarm = WorkRepeatPolicy.parseRadiusInstructionOrNull(
                "8", FarmingWorkPolicy.DEFAULT_RADIUS, FarmingWorkPolicy.MAX_RADIUS
        );
        require(positionalFarm != null && positionalFarm.value() == 8.0D && positionalFarm.repeatCount() == 1,
                "positional farm radius should parse");

        WorkInstruction namedFarm = WorkRepeatPolicy.parseRadiusInstructionOrNull(
                "radius=8 repeat=3", FarmingWorkPolicy.DEFAULT_RADIUS, FarmingWorkPolicy.MAX_RADIUS
        );
        require(namedFarm != null && namedFarm.value() == 8.0D && namedFarm.repeatCount() == 3,
                "named farm radius and repeat should parse");

        WorkInstruction countAlias = WorkRepeatPolicy.parseRadiusInstructionOrNull(
                "radius=8 count=2", FarmingWorkPolicy.DEFAULT_RADIUS, FarmingWorkPolicy.MAX_RADIUS
        );
        require(countAlias != null && countAlias.repeatCount() == 2,
                "count should be a repeat alias for valued work instructions");

        require(WorkRepeatPolicy.parseRadiusInstructionOrNull(
                "radius=8 repeat=6", FarmingWorkPolicy.DEFAULT_RADIUS, FarmingWorkPolicy.MAX_RADIUS
        ) == null, "repeat above cap should reject");
        require(WorkRepeatPolicy.parseRadiusInstructionOrNull(
                "8 repeat=2", FarmingWorkPolicy.DEFAULT_RADIUS, FarmingWorkPolicy.MAX_RADIUS
        ) == null, "positional numeric radius should not mix with named repeat tokens");

        WorkInstruction fish = WorkRepeatPolicy.parseDurationSecondsInstructionOrNull(
                "duration=45 repeat=2",
                FishingWorkPolicy.DEFAULT_DURATION_TICKS / 20.0D,
                FishingWorkPolicy.MAX_DURATION_TICKS / 20.0D
        );
        require(fish != null && fish.value() == 45.0D && fish.repeatCount() == 2,
                "fish duration syntax should parse even though runtime rejects execution");

        WorkRepeatPolicy.InventoryRepeatInstruction inventory = WorkRepeatPolicy.parseInventoryRepeatInstructionOrNull(
                "minecraft:wheat 4 repeat=3"
        );
        require(inventory != null && inventory.itemInstruction().equals("minecraft:wheat 4")
                && inventory.repeatCount() == 3, "inventory repeat suffix should parse without changing item count");
        require(WorkRepeatPolicy.parseInventoryRepeatInstructionOrNull("minecraft:wheat repeat=0") == null,
                "inventory repeat zero should reject");
        require(WorkRepeatPolicy.parseInventoryRepeatInstructionOrNull("minecraft:wheat count=2") == null,
                "inventory count key should reject to avoid item-count ambiguity");

        require(WorkRepeatPolicy.shouldQueueNextRepeat(true, 2, true),
                "repeat should requeue when allowed, remaining, and world actions enabled");
        require(!WorkRepeatPolicy.shouldQueueNextRepeat(true, 2, false),
                "repeat should stop when world actions are disabled before the next iteration");
        require(!WorkRepeatPolicy.shouldQueueNextRepeat(true, 1, true),
                "repeat should stop at the bounded final iteration");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
