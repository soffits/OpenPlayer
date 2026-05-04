package dev.soffits.openplayer.entity;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class NpcInventoryTransferTest {
    private NpcInventoryTransferTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        matchesExactItemsOnly();
        summaryMergesDuplicateCountsInSlotOrder();
        removesExactCountAcrossStacksWithoutPartialMutation();
        insertionRejectsFullTargetWithoutMutation();
        hotbarSelectionOnlyScansHotbar();
        selectedHotbarDropCommitsOnlyAfterSpawnSuccess();
        armorEquipRejectsWrongSlotAndSwapsSafely();
        exactDepositMovesRequestedCountAtomically();
        exactDepositPreservesStackData();
        exactDepositRejectsIncompatibleTaggedMergeAtomically();
        exactDepositFullContainerRollsBack();
        exactWithdrawMovesRequestedCountAtomically();
        exactWithdrawPreservesStackData();
        exactWithdrawFullNpcInventoryRollsBack();
        depositAllRequiresEveryNormalStackToFit();
        containerTransferIgnoresArmorAndOffhand();
        furnaceStartMovesInputAndFuelAtomically();
        furnaceStartRejectsMissingInputOrFuelAtomically();
        furnaceStartRejectsOccupiedIncompatibleSlotsAtomically();
        furnaceOutputWithdrawPreservesStackDataAndCapacityAtomicity();
        furnaceRecoveryMovesOwnedStacksBestEffort();
        furnaceTransfersIgnoreArmorAndOffhand();
    }

    private static void matchesExactItemsOnly() {
        NonNullList<ItemStack> stacks = emptyInventory();
        stacks.set(0, new ItemStack(Items.BREAD, 2));
        stacks.set(1, new ItemStack(Items.APPLE, 3));

        require(NpcInventoryTransfer.countItem(stacks, Items.BREAD, 0, 31) == 2,
                "bread count must include only exact bread stacks");
        require(NpcInventoryTransfer.countItem(stacks, Items.COOKED_BEEF, 0, 31) == 0,
                "different food item must not match bread");
    }

    private static void summaryMergesDuplicateCountsInSlotOrder() {
        NonNullList<ItemStack> stacks = emptyInventory();
        stacks.set(0, new ItemStack(Items.BREAD, 2));
        stacks.set(10, new ItemStack(Items.COBBLESTONE, 4));
        stacks.set(11, new ItemStack(Items.BREAD, 3));
        stacks.set(33, new ItemStack(Items.IRON_CHESTPLATE, 1));

        String summary = NpcInventorySummary.format(stacks, 0);
        require(summary.contains("selected_hotbar=0"), "summary must include selected hotbar slot");
        require(summary.contains("0=minecraft:breadx2"), "summary must include hotbar slot stack");
        require(summary.contains("chest=minecraft:iron_chestplatex1"), "summary must include equipment stack");
        require(summary.contains("inventory=[minecraft:breadx5, minecraft:cobblestonex4]"),
                "summary must merge duplicate counts in first-seen deterministic order: " + summary);
    }

    private static void removesExactCountAcrossStacksWithoutPartialMutation() {
        NonNullList<ItemStack> stacks = emptyInventory();
        stacks.set(0, new ItemStack(Items.BREAD, 2));
        stacks.set(10, new ItemStack(Items.BREAD, 3));

        ItemStack removed = NpcInventoryTransfer.removeExactCount(stacks, Items.BREAD, 4, 0, 31);
        require(removed.getCount() == 4 && removed.is(Items.BREAD), "exact removal must return requested bread stack");
        require(stacks.get(0).isEmpty(), "first stack must be consumed first");
        require(stacks.get(10).getCount() == 1, "second stack must keep remainder");

        List<ItemStack> snapshot = NpcInventoryTransfer.copyStacks(stacks);
        ItemStack failed = NpcInventoryTransfer.removeExactCount(stacks, Items.BREAD, 4, 0, 31);
        require(failed.isEmpty(), "insufficient count must reject");
        require(stacksEqual(stacks, snapshot), "insufficient count must not partially mutate inventory");
    }

    private static void insertionRejectsFullTargetWithoutMutation() {
        NonNullList<ItemStack> stacks = NonNullList.withSize(2, ItemStack.EMPTY);
        stacks.set(0, new ItemStack(Items.STONE, 64));
        stacks.set(1, new ItemStack(Items.DIRT, 64));
        List<ItemStack> snapshot = NpcInventoryTransfer.copyStacks(stacks);

        boolean inserted = NpcInventoryTransfer.insertAll(stacks, new ItemStack(Items.BREAD, 1), 0, stacks.size());
        require(!inserted, "full target must reject insertion");
        require(stacksEqual(stacks, snapshot), "failed insertion must not mutate target inventory");
    }

    private static void hotbarSelectionOnlyScansHotbar() {
        NonNullList<ItemStack> stacks = emptyInventory();
        stacks.set(10, new ItemStack(Items.IRON_SWORD));
        require(NpcInventoryTransfer.firstHotbarSlotMatchingItem(stacks, Items.IRON_SWORD) == -1,
                "non-hotbar matching item must not be selected");
        stacks.set(8, new ItemStack(Items.IRON_SWORD));
        require(NpcInventoryTransfer.firstHotbarSlotMatchingItem(stacks, Items.IRON_SWORD) == 8,
                "hotbar scan must include slot eight");
    }

    private static void selectedHotbarDropCommitsOnlyAfterSpawnSuccess() {
        NonNullList<ItemStack> stacks = emptyInventory();
        stacks.set(2, new ItemStack(Items.COBBLESTONE, 5));

        ItemStack candidate = NpcInventoryTransfer.selectedHotbarDropStackOrEmpty(stacks, 2);
        require(candidate.is(Items.COBBLESTONE) && candidate.getCount() == 5,
                "drop candidate must copy the selected hotbar stack");
        candidate.setCount(1);
        require(stacks.get(2).getCount() == 5, "drop candidate mutation must not mutate source stack");

        require(!NpcInventoryTransfer.commitSelectedHotbarDrop(stacks, 2, false),
                "failed spawn must not commit selected hotbar drop");
        require(stacks.get(2).is(Items.COBBLESTONE) && stacks.get(2).getCount() == 5,
                "failed spawn must leave selected hotbar stack unchanged");

        require(NpcInventoryTransfer.commitSelectedHotbarDrop(stacks, 2, true),
                "successful spawn must commit selected hotbar drop");
        require(stacks.get(2).isEmpty(), "successful spawn must clear selected hotbar slot");
    }

    private static void armorEquipRejectsWrongSlotAndSwapsSafely() {
        NonNullList<ItemStack> stacks = emptyInventory();
        stacks.set(10, new ItemStack(Items.IRON_CHESTPLATE));
        stacks.set(33, new ItemStack(Items.LEATHER_CHESTPLATE));
        require(NpcInventoryTransfer.equipArmorByItem(stacks, Items.IRON_CHESTPLATE),
                "matching armor must equip");
        require(stacks.get(33).is(Items.IRON_CHESTPLATE), "target armor slot must receive selected armor");
        require(stacks.get(10).is(Items.LEATHER_CHESTPLATE), "source slot must receive previous armor");

        require(!NpcInventoryTransfer.equipArmorByItem(stacks, Items.IRON_SWORD), "non-armor must reject");
        require(!NpcInventoryTransfer.equipArmorByItem(stacks, Items.IRON_BOOTS), "missing armor item must reject");
    }

    private static void exactDepositMovesRequestedCountAtomically() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> container = NonNullList.withSize(9, ItemStack.EMPTY);
        npc.set(0, new ItemStack(Items.BREAD, 2));
        npc.set(10, new ItemStack(Items.BREAD, 3));

        require(NpcInventoryTransfer.depositExactItem(npc, container, Items.BREAD, 4),
                "exact deposit must move requested count");
        require(NpcInventoryTransfer.countItem(npc, Items.BREAD, 0, 31) == 1, "NPC must keep bread remainder");
        require(NpcInventoryTransfer.countItem(container, Items.BREAD, 0, container.size()) == 4,
                "container must receive exact bread count");
    }

    private static void exactDepositFullContainerRollsBack() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> container = NonNullList.withSize(1, new ItemStack(Items.STONE, 64));
        npc.set(0, new ItemStack(Items.BREAD, 2));
        List<ItemStack> npcSnapshot = NpcInventoryTransfer.copyStacks(npc);
        List<ItemStack> containerSnapshot = NpcInventoryTransfer.copyStacks(container);

        require(!NpcInventoryTransfer.depositExactItem(npc, container, Items.BREAD, 2),
                "full container exact deposit must reject");
        require(stacksEqual(npc, npcSnapshot), "failed exact deposit must restore NPC inventory");
        require(stacksEqual(container, containerSnapshot), "failed exact deposit must restore container");
    }

    private static void exactDepositPreservesStackData() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> container = NonNullList.withSize(9, ItemStack.EMPTY);
        ItemStack taggedBread = taggedStack(Items.BREAD, 3, "phase7a-source");
        npc.set(0, taggedBread.copy());

        require(NpcInventoryTransfer.depositExactItem(npc, container, Items.BREAD, 2),
                "exact deposit must move tagged stacks");
        require(container.get(0).getCount() == 2, "container must receive requested tagged count");
        require(ItemStack.isSameItemSameTags(container.get(0), taggedBread),
                "exact deposit must preserve source stack tag data");
        require(npc.get(0).getCount() == 1, "NPC must keep tagged remainder");
        require(ItemStack.isSameItemSameTags(npc.get(0), taggedBread),
                "exact deposit remainder must preserve source stack tag data");
    }

    private static void exactDepositRejectsIncompatibleTaggedMergeAtomically() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> container = NonNullList.withSize(1, new ItemStack(Items.BREAD, 63));
        npc.set(0, taggedStack(Items.BREAD, 2, "phase7a-incompatible"));
        List<ItemStack> npcSnapshot = NpcInventoryTransfer.copyStacks(npc);
        List<ItemStack> containerSnapshot = NpcInventoryTransfer.copyStacks(container);

        require(!NpcInventoryTransfer.depositExactItem(npc, container, Items.BREAD, 2),
                "exact deposit must reject incompatible tagged merge when no compatible capacity exists");
        require(stacksEqual(npc, npcSnapshot), "incompatible exact deposit must restore NPC inventory");
        require(stacksEqual(container, containerSnapshot), "incompatible exact deposit must restore container");
    }

    private static void exactWithdrawMovesRequestedCountAtomically() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> container = NonNullList.withSize(9, ItemStack.EMPTY);
        container.set(0, new ItemStack(Items.BREAD, 5));

        require(NpcInventoryTransfer.withdrawExactItem(npc, container, Items.BREAD, 3),
                "exact withdraw must move requested count");
        require(NpcInventoryTransfer.countItem(npc, Items.BREAD, 0, 31) == 3, "NPC must receive bread");
        require(NpcInventoryTransfer.countItem(container, Items.BREAD, 0, container.size()) == 2,
                "container must keep bread remainder");
    }

    private static void exactWithdrawFullNpcInventoryRollsBack() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> container = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int slot = 0; slot < 31; slot++) {
            npc.set(slot, new ItemStack(Items.STONE, 64));
        }
        container.set(0, new ItemStack(Items.BREAD, 1));
        List<ItemStack> npcSnapshot = NpcInventoryTransfer.copyStacks(npc);
        List<ItemStack> containerSnapshot = NpcInventoryTransfer.copyStacks(container);

        require(!NpcInventoryTransfer.withdrawExactItem(npc, container, Items.BREAD, 1),
                "full NPC inventory withdraw must reject");
        require(stacksEqual(npc, npcSnapshot), "failed withdraw must restore NPC inventory");
        require(stacksEqual(container, containerSnapshot), "failed withdraw must restore container");
    }

    private static void exactWithdrawPreservesStackData() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> container = NonNullList.withSize(9, ItemStack.EMPTY);
        ItemStack taggedBread = taggedStack(Items.BREAD, 4, "phase7a-container");
        container.set(0, taggedBread.copy());

        require(NpcInventoryTransfer.withdrawExactItem(npc, container, Items.BREAD, 3),
                "exact withdraw must move tagged stacks");
        require(npc.get(0).getCount() == 3, "NPC must receive requested tagged count");
        require(ItemStack.isSameItemSameTags(npc.get(0), taggedBread),
                "exact withdraw must preserve source stack tag data");
        require(container.get(0).getCount() == 1, "container must keep tagged remainder");
        require(ItemStack.isSameItemSameTags(container.get(0), taggedBread),
                "exact withdraw remainder must preserve source stack tag data");
    }

    private static void depositAllRequiresEveryNormalStackToFit() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> container = NonNullList.withSize(1, ItemStack.EMPTY);
        npc.set(0, new ItemStack(Items.BREAD, 1));
        npc.set(1, new ItemStack(Items.APPLE, 1));
        List<ItemStack> npcSnapshot = NpcInventoryTransfer.copyStacks(npc);
        List<ItemStack> containerSnapshot = NpcInventoryTransfer.copyStacks(container);

        require(!NpcInventoryTransfer.depositAllNormalInventory(npc, container),
                "deposit-all must reject unless every normal stack can fit");
        require(stacksEqual(npc, npcSnapshot), "failed deposit-all must restore NPC inventory");
        require(stacksEqual(container, containerSnapshot), "failed deposit-all must restore container");

        NonNullList<ItemStack> largerContainer = NonNullList.withSize(2, ItemStack.EMPTY);
        require(NpcInventoryTransfer.depositAllNormalInventory(npc, largerContainer),
                "deposit-all must succeed when every normal stack fits");
        require(NpcInventoryTransfer.countItem(npc, Items.BREAD, 0, 31) == 0, "successful deposit-all clears bread");
        require(NpcInventoryTransfer.countItem(largerContainer, Items.APPLE, 0, largerContainer.size()) == 1,
                "successful deposit-all moves apple");
    }

    private static void containerTransferIgnoresArmorAndOffhand() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> container = NonNullList.withSize(4, ItemStack.EMPTY);
        npc.set(31, new ItemStack(Items.DIAMOND_BOOTS));
        npc.set(35, new ItemStack(Items.SHIELD));

        require(NpcInventoryTransfer.depositAllNormalInventory(npc, container),
                "deposit-all with only equipment should be a no-op success");
        require(npc.get(31).is(Items.DIAMOND_BOOTS), "armor slot must not be deposited");
        require(npc.get(35).is(Items.SHIELD), "offhand slot must not be deposited");
        require(NpcInventoryTransfer.countItem(container, Items.DIAMOND_BOOTS, 0, container.size()) == 0,
                "container must not receive armor slot contents");
    }

    private static void furnaceStartMovesInputAndFuelAtomically() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> furnace = NonNullList.withSize(3, ItemStack.EMPTY);
        npc.set(0, new ItemStack(Items.RAW_IRON, 2));
        npc.set(1, new ItemStack(Items.COAL, 1));

        require(NpcInventoryTransfer.startFurnaceSmelt(npc, furnace, Items.RAW_IRON, 2, Items.COAL, 1),
                "furnace start must move exact input and fuel");
        require(npc.get(0).isEmpty() && npc.get(1).isEmpty(), "NPC input and fuel must be removed");
        require(furnace.get(0).is(Items.RAW_IRON) && furnace.get(0).getCount() == 2,
                "furnace input slot must receive raw iron");
        require(furnace.get(1).is(Items.COAL) && furnace.get(1).getCount() == 1,
                "furnace fuel slot must receive coal");
    }

    private static void furnaceStartRejectsMissingInputOrFuelAtomically() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> furnace = NonNullList.withSize(3, ItemStack.EMPTY);
        npc.set(0, new ItemStack(Items.RAW_IRON, 1));
        List<ItemStack> npcSnapshot = NpcInventoryTransfer.copyStacks(npc);
        List<ItemStack> furnaceSnapshot = NpcInventoryTransfer.copyStacks(furnace);

        require(!NpcInventoryTransfer.startFurnaceSmelt(npc, furnace, Items.RAW_IRON, 2, Items.COAL, 1),
                "missing input must reject");
        require(stacksEqual(npc, npcSnapshot), "missing input must leave NPC unchanged");
        require(stacksEqual(furnace, furnaceSnapshot), "missing input must leave furnace unchanged");

        npc.set(0, new ItemStack(Items.RAW_IRON, 2));
        npcSnapshot = NpcInventoryTransfer.copyStacks(npc);
        require(!NpcInventoryTransfer.startFurnaceSmelt(npc, furnace, Items.RAW_IRON, 2, Items.COAL, 1),
                "missing fuel must reject");
        require(stacksEqual(npc, npcSnapshot), "missing fuel must leave NPC unchanged");
        require(stacksEqual(furnace, furnaceSnapshot), "missing fuel must leave furnace unchanged");
    }

    private static void furnaceStartRejectsOccupiedIncompatibleSlotsAtomically() {
        NonNullList<ItemStack> npc = emptyInventory();
        npc.set(0, new ItemStack(Items.RAW_IRON, 1));
        npc.set(1, new ItemStack(Items.COAL, 1));
        NonNullList<ItemStack> furnace = NonNullList.withSize(3, ItemStack.EMPTY);
        furnace.set(0, new ItemStack(Items.RAW_COPPER, 1));
        requireFurnaceStartRejectedUnchanged(npc, furnace, "incompatible input must reject");

        furnace = NonNullList.withSize(3, ItemStack.EMPTY);
        furnace.set(1, new ItemStack(Items.CHARCOAL, 1));
        requireFurnaceStartRejectedUnchanged(npc, furnace, "incompatible fuel must reject");

        furnace = NonNullList.withSize(3, ItemStack.EMPTY);
        furnace.set(0, new ItemStack(Items.RAW_IRON, 1));
        requireFurnaceStartRejectedUnchanged(npc, furnace, "compatible pre-filled input must reject");

        furnace = NonNullList.withSize(3, ItemStack.EMPTY);
        furnace.set(1, new ItemStack(Items.COAL, 1));
        requireFurnaceStartRejectedUnchanged(npc, furnace, "compatible pre-filled fuel must reject");

        furnace = NonNullList.withSize(3, ItemStack.EMPTY);
        furnace.set(2, new ItemStack(Items.IRON_INGOT, 1));
        requireFurnaceStartRejectedUnchanged(npc, furnace, "occupied result must reject");
    }

    private static void furnaceOutputWithdrawPreservesStackDataAndCapacityAtomicity() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> furnace = NonNullList.withSize(3, ItemStack.EMPTY);
        ItemStack taggedOutput = taggedStack(Items.IRON_INGOT, 2, "phase7b-output");
        furnace.set(2, taggedOutput.copy());

        require(NpcInventoryTransfer.withdrawFurnaceOutput(npc, furnace, Items.IRON_INGOT, 1),
                "furnace output withdraw must move requested output");
        require(npc.get(0).getCount() == 1 && ItemStack.isSameItemSameTags(npc.get(0), taggedOutput),
                "withdrawn furnace output must preserve tags");
        require(furnace.get(2).getCount() == 1 && ItemStack.isSameItemSameTags(furnace.get(2), taggedOutput),
                "furnace output remainder must preserve tags");

        for (int slot = 0; slot < 31; slot++) {
            npc.set(slot, new ItemStack(Items.STONE, 64));
        }
        List<ItemStack> npcSnapshot = NpcInventoryTransfer.copyStacks(npc);
        List<ItemStack> furnaceSnapshot = NpcInventoryTransfer.copyStacks(furnace);
        require(!NpcInventoryTransfer.withdrawFurnaceOutput(npc, furnace, Items.IRON_INGOT, 1),
                "full NPC inventory must reject furnace output withdraw");
        require(stacksEqual(npc, npcSnapshot), "failed output withdraw must leave NPC unchanged");
        require(stacksEqual(furnace, furnaceSnapshot), "failed output withdraw must leave furnace unchanged");
    }

    private static void furnaceRecoveryMovesOwnedStacksBestEffort() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> furnace = NonNullList.withSize(3, ItemStack.EMPTY);
        ItemStack taggedOutput = taggedStack(Items.IRON_INGOT, 1, "phase7b-recovery");
        furnace.set(0, new ItemStack(Items.RAW_IRON, 1));
        furnace.set(1, new ItemStack(Items.COAL, 1));
        furnace.set(2, taggedOutput.copy());

        require(NpcInventoryTransfer.recoverFurnaceSmeltResources(
                npc, furnace, Items.RAW_IRON, Items.COAL, Items.IRON_INGOT
        ), "owned furnace resources must recover into NPC inventory when capacity exists");
        require(furnace.get(0).isEmpty() && furnace.get(1).isEmpty() && furnace.get(2).isEmpty(),
                "successful recovery must clear owned furnace slots");
        require(NpcInventoryTransfer.countItem(npc, Items.RAW_IRON, 0, 31) == 1,
                "recovery must return owned input");
        require(NpcInventoryTransfer.countItem(npc, Items.COAL, 0, 31) == 1,
                "recovery must return owned fuel");
        require(npc.get(0).getCount() == 1 && ItemStack.isSameItemSameTags(npc.get(0), taggedOutput),
                "recovery must preserve output stack tags");

        for (int slot = 0; slot < 31; slot++) {
            npc.set(slot, new ItemStack(Items.STONE, 64));
        }
        furnace.set(0, new ItemStack(Items.RAW_IRON, 1));
        List<ItemStack> npcSnapshot = NpcInventoryTransfer.copyStacks(npc);
        List<ItemStack> furnaceSnapshot = NpcInventoryTransfer.copyStacks(furnace);
        require(!NpcInventoryTransfer.recoverFurnaceSmeltResources(
                npc, furnace, Items.RAW_IRON, Items.COAL, Items.IRON_INGOT
        ), "recovery without capacity must fail atomically");
        require(stacksEqual(npc, npcSnapshot), "failed recovery must leave NPC unchanged");
        require(stacksEqual(furnace, furnaceSnapshot), "failed recovery must leave furnace unchanged");
    }

    private static void furnaceTransfersIgnoreArmorAndOffhand() {
        NonNullList<ItemStack> npc = emptyInventory();
        NonNullList<ItemStack> furnace = NonNullList.withSize(3, ItemStack.EMPTY);
        npc.set(31, new ItemStack(Items.RAW_IRON, 1));
        npc.set(35, new ItemStack(Items.COAL, 1));

        require(!NpcInventoryTransfer.startFurnaceSmelt(npc, furnace, Items.RAW_IRON, 1, Items.COAL, 1),
                "furnace start must ignore armor and offhand slots");
        require(npc.get(31).is(Items.RAW_IRON), "armor slot must remain unchanged");
        require(npc.get(35).is(Items.COAL), "offhand slot must remain unchanged");
        require(furnace.get(0).isEmpty() && furnace.get(1).isEmpty(), "furnace must remain unchanged");
    }

    private static void requireFurnaceStartRejectedUnchanged(NonNullList<ItemStack> npc,
                                                            NonNullList<ItemStack> furnace,
                                                            String message) {
        List<ItemStack> npcSnapshot = NpcInventoryTransfer.copyStacks(npc);
        List<ItemStack> furnaceSnapshot = NpcInventoryTransfer.copyStacks(furnace);
        require(!NpcInventoryTransfer.startFurnaceSmelt(npc, furnace, Items.RAW_IRON, 1, Items.COAL, 1), message);
        require(stacksEqual(npc, npcSnapshot), message + " and leave NPC unchanged");
        require(stacksEqual(furnace, furnaceSnapshot), message + " and leave furnace unchanged");
    }

    private static NonNullList<ItemStack> emptyInventory() {
        return NonNullList.withSize(NpcInventoryTransfer.INVENTORY_SIZE, ItemStack.EMPTY);
    }

    private static ItemStack taggedStack(Item item, int count, String marker) {
        ItemStack stack = new ItemStack(item, count);
        stack.getOrCreateTag().putString("openplayer_test_marker", marker);
        return stack;
    }

    private static boolean stacksEqual(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            ItemStack leftStack = left.get(index);
            ItemStack rightStack = right.get(index);
            if (!ItemStack.isSameItemSameTags(leftStack, rightStack) || leftStack.getCount() != rightStack.getCount()) {
                return false;
            }
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
