package dev.soffits.openplayer.entity;

import dev.soffits.openplayer.entity.NpcChunkTicketModel.TicketChange;
import dev.soffits.openplayer.entity.NpcChunkTicketModel.TicketKey;

public final class NpcChunkTicketModelTest {
    private NpcChunkTicketModelTest() {
    }

    public static void main(String[] args) {
        addsInitialTicket();
        refreshesOnlyAfterInterval();
        refreshDoesNotRemoveActiveTicket();
        replacesMovedChunkTicket();
        replacesDimensionTicket();
        releasesActiveTicket();
        releaseClearsStaleTicketState();
    }

    private static void addsInitialTicket() {
        NpcChunkTicketModel model = new NpcChunkTicketModel();
        TicketKey key = new TicketKey("minecraft:overworld", 1, 2);
        TicketChange change = model.update(key, 10L, 20);
        require(key.equals(change.addTicket()), "initial update must add desired ticket");
        require(change.removeTicket() == null, "initial update must not remove a ticket");
    }

    private static void refreshesOnlyAfterInterval() {
        NpcChunkTicketModel model = new NpcChunkTicketModel();
        TicketKey key = new TicketKey("minecraft:overworld", 1, 2);
        model.update(key, 10L, 20);
        require(!model.update(key, 20L, 20).hasChange(), "same chunk before refresh interval must not churn tickets");
        TicketChange refresh = model.update(key, 30L, 20);
        require(key.equals(refresh.addTicket()), "same chunk after refresh interval must refresh ticket");
        require(refresh.removeTicket() == null, "refresh must not remove the active ticket");
    }

    private static void refreshDoesNotRemoveActiveTicket() {
        NpcChunkTicketModel model = new NpcChunkTicketModel();
        TicketKey key = new TicketKey("minecraft:overworld", 1, 2);
        model.update(key, 10L, 20);
        TicketChange refresh = model.update(key, 31L, 20);
        require(key.equals(refresh.addTicket()), "refresh must re-add the active center ticket");
        require(refresh.removeTicket() == null, "refresh must not remove the active center ticket");
        require(key.equals(model.activeTicket()), "refresh must preserve the active ticket model state");
    }

    private static void replacesMovedChunkTicket() {
        NpcChunkTicketModel model = new NpcChunkTicketModel();
        TicketKey first = new TicketKey("minecraft:overworld", 1, 2);
        TicketKey second = new TicketKey("minecraft:overworld", 2, 2);
        model.update(first, 10L, 20);
        TicketChange change = model.update(second, 11L, 20);
        require(second.equals(change.addTicket()), "moving chunks must add the new center ticket");
        require(first.equals(change.removeTicket()), "moving chunks must remove the old center ticket");
    }

    private static void replacesDimensionTicket() {
        NpcChunkTicketModel model = new NpcChunkTicketModel();
        TicketKey first = new TicketKey("minecraft:overworld", 1, 2);
        TicketKey second = new TicketKey("minecraft:the_nether", 1, 2);
        model.update(first, 10L, 20);
        TicketChange change = model.update(second, 11L, 20);
        require(second.equals(change.addTicket()), "dimension changes must add the new dimension ticket");
        require(first.equals(change.removeTicket()), "dimension changes must remove the old dimension ticket");
    }

    private static void releasesActiveTicket() {
        NpcChunkTicketModel model = new NpcChunkTicketModel();
        TicketKey key = new TicketKey("minecraft:overworld", 1, 2);
        model.update(key, 10L, 20);
        TicketChange change = model.release();
        require(change.addTicket() == null, "release must not add a ticket");
        require(key.equals(change.removeTicket()), "release must remove the active ticket");
        require(!model.release().hasChange(), "second release must be idempotent");
    }

    private static void releaseClearsStaleTicketState() {
        NpcChunkTicketModel model = new NpcChunkTicketModel();
        TicketKey first = new TicketKey("minecraft:overworld", 1, 2);
        TicketKey second = new TicketKey("minecraft:overworld", 3, 4);
        model.update(first, 10L, 20);
        model.release();

        require(model.activeTicket() == null, "release must clear active ticket state");
        TicketChange change = model.update(second, 11L, 20);
        require(second.equals(change.addTicket()), "new work after release must add only the new center ticket");
        require(change.removeTicket() == null, "new work after release must not remove stale tickets twice");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
