package dev.soffits.openplayer.automation.work;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class FarmingWorkPolicy {
    public static final double DEFAULT_RADIUS = 8.0D;
    public static final double MAX_RADIUS = 16.0D;

    private FarmingWorkPolicy() {
    }

    public static boolean isSupportedCrop(BlockState blockState) {
        return replantItem(blockState) != null;
    }

    public static boolean isMature(BlockState blockState) {
        if (blockState == null) {
            return false;
        }
        Block block = blockState.getBlock();
        if (block instanceof CropBlock cropBlock) {
            return cropBlock.isMaxAge(blockState);
        }
        return block == Blocks.NETHER_WART && blockState.getValue(NetherWartBlock.AGE) >= 3;
    }

    public static Item replantItem(BlockState blockState) {
        if (blockState == null) {
            return null;
        }
        Block block = blockState.getBlock();
        if (block == Blocks.WHEAT) {
            return Items.WHEAT_SEEDS;
        }
        if (block == Blocks.CARROTS) {
            return Items.CARROT;
        }
        if (block == Blocks.POTATOES) {
            return Items.POTATO;
        }
        if (block == Blocks.BEETROOTS) {
            return Items.BEETROOT_SEEDS;
        }
        if (block == Blocks.NETHER_WART) {
            return Items.NETHER_WART;
        }
        return null;
    }

    public static BlockState replantState(BlockState harvestedState) {
        if (harvestedState == null) {
            return null;
        }
        Block block = harvestedState.getBlock();
        if (block instanceof CropBlock cropBlock) {
            return cropBlock.getStateForAge(0);
        }
        if (block == Blocks.NETHER_WART) {
            return Blocks.NETHER_WART.defaultBlockState();
        }
        return null;
    }
}
