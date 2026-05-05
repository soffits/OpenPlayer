package dev.soffits.openplayer.entity;

public final class NpcChunkTicketModel {
    private TicketKey activeTicket;
    private long lastRefreshTick = Long.MIN_VALUE;

    public TicketChange update(TicketKey desiredTicket, long gameTick, int refreshIntervalTicks) {
        if (desiredTicket == null) {
            throw new IllegalArgumentException("desiredTicket cannot be null");
        }
        if (refreshIntervalTicks < 1) {
            throw new IllegalArgumentException("refreshIntervalTicks must be positive");
        }
        if (!desiredTicket.equals(activeTicket)) {
            TicketKey previousTicket = activeTicket;
            activeTicket = desiredTicket;
            lastRefreshTick = gameTick;
            return new TicketChange(desiredTicket, previousTicket);
        }
        if (gameTick - lastRefreshTick >= refreshIntervalTicks) {
            lastRefreshTick = gameTick;
            return new TicketChange(desiredTicket, null);
        }
        return TicketChange.none();
    }

    public TicketChange release() {
        if (activeTicket == null) {
            return TicketChange.none();
        }
        TicketKey previousTicket = activeTicket;
        activeTicket = null;
        lastRefreshTick = Long.MIN_VALUE;
        return new TicketChange(null, previousTicket);
    }

    public TicketKey activeTicket() {
        return activeTicket;
    }

    public record TicketKey(String dimensionId, int chunkX, int chunkZ) {
        public TicketKey {
            if (dimensionId == null || dimensionId.isBlank()) {
                throw new IllegalArgumentException("dimensionId cannot be blank");
            }
        }
    }

    public record TicketChange(TicketKey addTicket, TicketKey removeTicket) {
        public static TicketChange none() {
            return new TicketChange(null, null);
        }

        public boolean hasChange() {
            return addTicket != null || removeTicket != null;
        }
    }
}
