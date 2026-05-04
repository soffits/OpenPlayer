package dev.soffits.openplayer.aicore;

import dev.soffits.openplayer.entity.NpcInventoryTransfer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AICoreInventoryWindowTransactionTest {
    private AICoreInventoryWindowTransactionTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        AICoreTestSupport.requireStatus("transfer", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("open_block", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("move_slot_item", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("set_quick_bar_slot", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("unequip", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        ToolResult transfer = MinecraftPrimitiveTools.validate(ToolCall.of("transfer", new ToolArguments(Map.of("options", "{\"direction\":\"deposit\",\"itemType\":\"minecraft:dirt\",\"count\":2}"))), new ToolValidationContext(true));
        AICoreTestSupport.require(transfer.status() == ToolResultStatus.SUCCESS, "transfer must validate with complete bounded options");
        requireRejectedTransferOptions("{}", "transfer_options_require_direction_itemType_count");
        requireRejectedTransferOptions("{\"direction\":\"deposit\",\"itemType\":\"minecraft:dirt\"}", "transfer_options_require_direction_itemType_count");
        requireRejectedTransferOptions("{\"direction\":\"deposit\",\"itemType\":\"minecraft:dirt\",\"count\":\"many\"}", "Argument has invalid integer value: count");
        requireRejectedTransferOptions("{\"direction\":\"deposit\",\"itemType\":\"minecraft:dirt\",\"count\":1.5}", "Argument has invalid integer value: count");
        requireRejectedTransferOptions("{\"direction\":\"deposit\",\"itemType\":\"minecraft:dirt\",\"count\":0}", "count must be between 1 and 256");
        requireRejectedTransferOptions("{\"direction\":\"deposit\",\"itemType\":\"minecraft:dirt\",\"count\":257}", "count must be between 1 and 256");
        requireRejectedTransferOptions("{\"direction\":\"sideways\",\"itemType\":\"minecraft:dirt\",\"count\":1}", "unsupported_transfer_direction");
        ToolResult slot = MinecraftPrimitiveTools.validate(ToolCall.of("set_quick_bar_slot", new ToolArguments(Map.of("slot", "300"))), new ToolValidationContext(true));
        AICoreTestSupport.requireRejected(slot, "slot must be between 0 and 8");
        ToolResult unequip = MinecraftPrimitiveTools.validate(ToolCall.of("unequip", new ToolArguments(Map.of("destination", "head"))), new ToolValidationContext(true));
        AICoreTestSupport.require(unequip.status() == ToolResultStatus.SUCCESS, "unequip must validate as a no-loss inventory-local adapter");
        requireAtomicDepositRollback();
    }

    private static void requireRejectedTransferOptions(String options, String reason) {
        ToolResult transfer = MinecraftPrimitiveTools.validate(ToolCall.of("transfer", new ToolArguments(Map.of("options", options))), new ToolValidationContext(true));
        AICoreTestSupport.requireRejected(transfer, reason);
    }

    private static void requireAtomicDepositRollback() {
        List<ItemStack> npcStacks = new ArrayList<>();
        List<ItemStack> containerStacks = new ArrayList<>();
        for (int slot = 0; slot < NpcInventoryTransfer.INVENTORY_SIZE; slot++) {
            npcStacks.add(ItemStack.EMPTY);
        }
        npcStacks.set(0, new ItemStack(Items.DIRT, 2));
        containerStacks.add(new ItemStack(Items.STONE, 64));
        boolean transferred = NpcInventoryTransfer.depositExactItem(npcStacks, containerStacks, Items.DIRT, 2);
        AICoreTestSupport.require(!transferred, "deposit must reject when the container cannot fit the full stack");
        AICoreTestSupport.require(npcStacks.get(0).is(Items.DIRT) && npcStacks.get(0).getCount() == 2, "failed deposit must roll back NPC inventory");
        AICoreTestSupport.require(containerStacks.get(0).is(Items.STONE) && containerStacks.get(0).getCount() == 64, "failed deposit must roll back container inventory");
    }
}
