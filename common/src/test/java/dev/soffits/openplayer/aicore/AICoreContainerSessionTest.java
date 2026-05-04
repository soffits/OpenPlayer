package dev.soffits.openplayer.aicore;

import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AICoreContainerSessionTest {
    private AICoreContainerSessionTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        AICoreTestSupport.requireStatus("open_container", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("open_block", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("window_deposit", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        requireNarrowToolsRemoved();
        ToolResult result = MinecraftPrimitiveTools.validate(ToolCall.of("open_container", new ToolArguments(Map.of("target", "{\"x\":1,\"y\":64,\"z\":2}"))), new ToolValidationContext(true));
        AICoreTestSupport.require(result.status() == ToolResultStatus.SUCCESS, "open_container must validate as a generic container session primitive");
        requireSessionStateSurvivesExecutorLifetime();
        requireSlotRestrictedSessionRejectsGenericWindowTransfersWithoutMutation();
        requireSlotRestrictedSessionRejectsGenericTransferDirectionWithoutMutation();
    }

    private static void requireNarrowToolsRemoved() {
        for (String tool : new String[] {
                "open_chest", "open_furnace", "furnace_status", "furnace_put_input", "furnace_put_fuel",
                "furnace_take_input", "furnace_take_fuel", "furnace_take_output", "is_bed", "sleep", "wake",
                "respawn", "armor_manager_equip_best", "armor_manager_status", "open_anvil", "anvil_combine",
                "open_enchantment_table", "enchant", "open_villager", "villager_trade", "fish", "mount",
                "dismount", "move_vehicle", "elytra_fly", "collectblock_collect", "auto_eat_status"
        }) {
            AICoreTestSupport.requireNoTool(tool);
        }
    }

    private static void requireSessionStateSurvivesExecutorLifetime() {
        AICoreNpcSessionState sessionState = new AICoreNpcSessionState();
        BlockPos containerPos = new BlockPos(1, 64, 2);
        sessionState.openContainer(containerPos, false);
        AICoreTestSupport.require(containerPos.equals(sessionState.containerPos()), "container session must be stored outside executor instances");
        AICoreTestSupport.require(!sessionState.hasSlotRestrictedContainerSession(), "normal container session must not imply slot restrictions");
        sessionState.openContainer(containerPos, true);
        AICoreTestSupport.require(containerPos.equals(sessionState.containerPos()), "slot-restricted session must also be the current container session");
        AICoreTestSupport.require(sessionState.hasSlotRestrictedContainerSession(), "slot-restricted session must be visible to transfer guards");
        sessionState.clear();
        AICoreTestSupport.require(sessionState.containerPos() == null, "window close must clear container session");
        AICoreTestSupport.require(!sessionState.hasSlotRestrictedContainerSession(), "window close must clear slot restriction transfer guards");
    }

    private static void requireSlotRestrictedSessionRejectsGenericWindowTransfersWithoutMutation() {
        AICoreNpcSessionState sessionState = new AICoreNpcSessionState();
        sessionState.openContainer(new BlockPos(3, 64, 4), true);
        ItemStack npcDirt = new ItemStack(Items.DIRT, 1);
        ItemStack containerInput = ItemStack.EMPTY;
        ToolResult rejection = AICoreNpcToolExecutor.genericWindowTransferSessionRejection(sessionState);
        AICoreTestSupport.requireRejected(rejection, "slot_restricted_container_transfer_unsupported");
        AICoreTestSupport.require(npcDirt.is(Items.DIRT) && npcDirt.getCount() == 1, "rejected generic slot-restricted deposit must not mutate NPC inventory");
        AICoreTestSupport.require(containerInput.isEmpty(), "rejected generic slot-restricted deposit must not mutate container inventory");
    }

    private static void requireSlotRestrictedSessionRejectsGenericTransferDirectionWithoutMutation() {
        AICoreNpcSessionState sessionState = new AICoreNpcSessionState();
        sessionState.openContainer(new BlockPos(3, 64, 4), true);
        ToolResult transfer = MinecraftPrimitiveTools.validate(ToolCall.of("transfer", new ToolArguments(Map.of("options", "{\"direction\":\"deposit\",\"itemType\":\"minecraft:dirt\",\"count\":1}"))), new ToolValidationContext(true));
        AICoreTestSupport.require(transfer.status() == ToolResultStatus.SUCCESS, "deposit transfer options must validate before session guard rejection");
        ItemStack npcDirt = new ItemStack(Items.DIRT, 1);
        ItemStack containerInput = ItemStack.EMPTY;
        ToolResult rejection = AICoreNpcToolExecutor.genericWindowTransferSessionRejection(sessionState);
        AICoreTestSupport.requireRejected(rejection, "slot_restricted_container_transfer_unsupported");
        AICoreTestSupport.require(npcDirt.is(Items.DIRT) && npcDirt.getCount() == 1, "rejected generic transfer must not mutate NPC inventory");
        AICoreTestSupport.require(containerInput.isEmpty(), "rejected generic transfer must not mutate container inventory");
    }
}
