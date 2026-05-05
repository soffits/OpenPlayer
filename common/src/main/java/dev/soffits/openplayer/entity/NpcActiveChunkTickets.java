package dev.soffits.openplayer.entity;

import dev.soffits.openplayer.OpenPlayerConstants;
import dev.soffits.openplayer.entity.NpcChunkTicketModel.TicketChange;
import dev.soffits.openplayer.entity.NpcChunkTicketModel.TicketKey;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

public final class NpcActiveChunkTickets {
    private static final TicketType<UUID> NPC_ACTIVE_TICKET = TicketType.create(
            "openplayer_npc_active",
            Comparator.comparing(UUID::toString),
            OpenPlayerConstants.NPC_ACTIVE_CHUNK_TICKET_TIMEOUT_TICKS
    );

    private final UUID npcId;
    private final NpcChunkTicketModel model = new NpcChunkTicketModel();
    private ServerLevel activeLevel;

    public NpcActiveChunkTickets(UUID npcId) {
        if (npcId == null) {
            throw new IllegalArgumentException("npcId cannot be null");
        }
        this.npcId = npcId;
    }

    public void update(ServerLevel level, BlockPos blockPos) {
        if (level == null) {
            throw new IllegalArgumentException("level cannot be null");
        }
        if (blockPos == null) {
            throw new IllegalArgumentException("blockPos cannot be null");
        }
        ChunkPos chunkPos = new ChunkPos(blockPos);
        TicketKey desiredTicket = new TicketKey(level.dimension().location().toString(), chunkPos.x, chunkPos.z);
        if (activeLevel != null && activeLevel != level) {
            apply(activeLevel, model.release());
        }
        activeLevel = level;
        TicketChange change = model.update(desiredTicket, level.getGameTime(),
                OpenPlayerConstants.NPC_ACTIVE_CHUNK_TICKET_REFRESH_TICKS);
        apply(level, change);
    }

    public void release() {
        if (activeLevel == null) {
            model.release();
            return;
        }
        apply(activeLevel, model.release());
        activeLevel = null;
    }

    private void apply(ServerLevel level, TicketChange change) {
        if (!change.hasChange()) {
            return;
        }
        if (change.addTicket() != null) {
            ChunkPos chunkPos = new ChunkPos(change.addTicket().chunkX(), change.addTicket().chunkZ());
            level.getChunkSource().addRegionTicket(NPC_ACTIVE_TICKET, chunkPos,
                    OpenPlayerConstants.NPC_ACTIVE_CHUNK_TICKET_RADIUS, npcId);
        }
        if (change.removeTicket() != null) {
            ChunkPos chunkPos = new ChunkPos(change.removeTicket().chunkX(), change.removeTicket().chunkZ());
            level.getChunkSource().removeRegionTicket(NPC_ACTIVE_TICKET, chunkPos,
                    OpenPlayerConstants.NPC_ACTIVE_CHUNK_TICKET_RADIUS, npcId);
        }
    }
}
