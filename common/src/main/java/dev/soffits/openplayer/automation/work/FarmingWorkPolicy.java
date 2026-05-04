package dev.soffits.openplayer.automation.work;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class FarmingWorkPolicy {
    public static final double DEFAULT_RADIUS = 8.0D;
    public static final double MAX_RADIUS = 16.0D;

    private FarmingWorkPolicy() {
    }

    public static boolean isSupportedCrop(BlockGetter blockGetter, BlockPos blockPos, BlockState blockState) {
        return replantPlan(blockGetter, blockPos, blockState) != null;
    }

    public static boolean isMature(BlockState blockState) {
        if (blockState == null) {
            return false;
        }
        Block block = blockState.getBlock();
        if (block instanceof CropBlock cropBlock) {
            return cropBlock.isMaxAge(blockState);
        }
        return isMatureNetherWart(blockState);
    }

    public static FarmingReplantPlan replantPlan(BlockGetter blockGetter, BlockPos blockPos, BlockState harvestedState) {
        if (blockGetter == null || blockPos == null || harvestedState == null) {
            return null;
        }
        if (!(harvestedState.getBlock() instanceof CropBlock) && !isNetherWartAdapter(harvestedState)) {
            return null;
        }
        ItemStack cloneStack = harvestedState.getBlock().getCloneItemStack(blockGetter, blockPos, harvestedState);
        if (cloneStack.isEmpty()) {
            return null;
        }
        Block replantBlock = Block.byItem(cloneStack.getItem());
        if (replantBlock == Blocks.AIR || replantBlock != harvestedState.getBlock()) {
            return null;
        }
        BlockState replantState = initialGrowthState(replantBlock.defaultBlockState());
        return new FarmingReplantPlan(cloneStack.getItem(), replantState, isNetherWartAdapter(harvestedState));
    }

    private static BlockState initialGrowthState(BlockState blockState) {
        for (Property<?> property : blockState.getProperties()) {
            if ("age".equals(property.getName()) && property instanceof IntegerProperty integerProperty) {
                Integer initialAge = integerProperty.getPossibleValues().stream().min(Integer::compareTo).orElse(null);
                if (initialAge != null) {
                    return blockState.setValue(integerProperty, initialAge);
                }
            }
        }
        return blockState;
    }

    private static boolean isMatureNetherWart(BlockState blockState) {
        return isNetherWartAdapter(blockState) && blockState.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
    }

    private static boolean isNetherWartAdapter(BlockState blockState) {
        return blockState != null && blockState.getBlock() == Blocks.NETHER_WART;
    }

    public record FarmingReplantPlan(Item item, BlockState state, boolean adapterException) {
        public FarmingReplantPlan {
            if (item == null) {
                throw new IllegalArgumentException("item cannot be null");
            }
            if (state == null) {
                throw new IllegalArgumentException("state cannot be null");
            }
        }
    }
}
