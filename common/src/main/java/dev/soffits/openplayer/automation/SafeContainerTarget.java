package dev.soffits.openplayer.automation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;

record SafeContainerTarget(BlockPos blockPos, Container container) {
}
