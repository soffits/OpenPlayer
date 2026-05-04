package dev.soffits.openplayer.entity;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

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
        exactDepositMovesRequestedCountAtomically();
        exactDepositPreservesStackData();
        exactDepositRejectsIncompatibleTaggedMergeAtomically();
        exactDepositFullContainerRollsBack();
        exactWithdrawMovesRequestedCountAtomically();
        exactWithdrawPreservesStackData();
        exactWithdrawFullNpcInventoryRollsBack();
        depositAllRequiresEveryNormalStackToFit();
        containerTransferIgnoresArmorAndOffhand();
        craftNormalInventoryCommitsOnlyCompleteTransaction();
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

    private static void craftNormalInventoryCommitsOnlyCompleteTransaction() {
        NonNullList<ItemStack> stacks = emptyInventory();
        stacks.set(0, new ItemStack(Items.OAK_LOG, 1));
        require(NpcInventoryTransfer.craftNormalInventory(
                        stacks,
                        List.of(Ingredient.of(Items.OAK_LOG)),
                        new ItemStack(Items.OAK_PLANKS, 4),
                        1
                ),
                "crafting must commit when inputs and output space are available");
        require(stacks.get(0).is(Items.OAK_PLANKS) && stacks.get(0).getCount() == 4,
                "crafting must insert the recipe result");

        List<ItemStack> snapshot = NpcInventoryTransfer.copyStacks(stacks);
        require(!NpcInventoryTransfer.craftNormalInventory(
                        stacks,
                        List.of(Ingredient.of(Items.OAK_LOG)),
                        new ItemStack(Items.OAK_PLANKS, 4),
                        1
                ),
                "crafting must reject missing inputs");
        require(stacksEqual(stacks, snapshot), "missing-input craft rejection must not mutate inventory");
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
