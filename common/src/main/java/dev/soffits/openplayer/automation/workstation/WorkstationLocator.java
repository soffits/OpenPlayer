package dev.soffits.openplayer.automation.workstation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class WorkstationLocator {
    public List<WorkstationTarget> loadedNearby(ServerLevel serverLevel, Vec3 entityPosition, int radius,
                                                WorkstationCapability capability) {
        List<WorkstationTarget> targets = new ArrayList<>();
        if (serverLevel == null || entityPosition == null || capability == null || !capability.hasSafeAdapter()) {
            return targets;
        }
        BlockPos center = BlockPos.containing(entityPosition);
        for (BlockPos candidate : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius)
        )) {
            WorkstationTarget target = targetAt(serverLevel, entityPosition, radius, candidate, capability);
            if (target != null) {
                targets.add(target);
            }
        }
        targets.sort(Comparator
                .comparingDouble((WorkstationTarget target) -> target.blockPos().distSqr(center))
                .thenComparingInt(target -> target.blockPos().getX())
                .thenComparingInt(target -> target.blockPos().getY())
                .thenComparingInt(target -> target.blockPos().getZ()));
        return targets;
    }

    public WorkstationTarget nearestLoaded(ServerLevel serverLevel, Vec3 entityPosition, int radius,
                                           WorkstationCapability capability) {
        List<WorkstationTarget> targets = loadedNearby(serverLevel, entityPosition, radius, capability);
        if (targets.isEmpty()) {
            return null;
        }
        return targets.get(0);
    }

    public WorkstationTarget targetAt(ServerLevel serverLevel, Vec3 entityPosition, int radius, BlockPos blockPos,
                                      WorkstationCapability capability) {
        if (serverLevel == null || entityPosition == null || blockPos == null
                || capability == null || !capability.hasSafeAdapter() || !serverLevel.hasChunkAt(blockPos)) {
            return null;
        }
        if (entityPosition.distanceToSqr(Vec3.atCenterOf(blockPos)) > radius * radius) {
            return null;
        }
        BlockState blockState = serverLevel.getBlockState(blockPos);
        if (capability.kind() == WorkstationKind.CRAFTING_TABLE && blockState.is(Blocks.CRAFTING_TABLE)) {
            return new WorkstationTarget(blockPos, capability, null);
        }
        if (capability.kind() == WorkstationKind.FURNACE && blockState.is(Blocks.FURNACE)) {
            BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
            if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
                return new WorkstationTarget(blockPos, capability, furnace);
            }
        }
        if (capability.kind() == WorkstationKind.SMOKER && blockState.is(Blocks.SMOKER)) {
            BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
            if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
                return new WorkstationTarget(blockPos, capability, furnace);
            }
        }
        if (capability.kind() == WorkstationKind.BLAST_FURNACE && blockState.is(Blocks.BLAST_FURNACE)) {
            BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
            if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
                return new WorkstationTarget(blockPos, capability, furnace);
            }
        }
        return null;
    }

    public static Container requireContainerAdapter(WorkstationTarget target) {
        if (target == null || !target.hasContainerAdapter()) {
            return null;
        }
        return target.container();
    }
}
