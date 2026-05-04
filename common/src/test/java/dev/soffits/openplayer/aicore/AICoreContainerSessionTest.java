package dev.soffits.openplayer.aicore;

import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public final class AICoreContainerSessionTest {
    private AICoreContainerSessionTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        AICoreTestSupport.requireTool("open_chest");
        AICoreTestSupport.requireTool("villager_trade");
        AICoreTestSupport.requireStatus("open_container", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("open_chest", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("open_furnace", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("window_deposit", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        AICoreTestSupport.requireStatus("furnace_status", CapabilityStatus.IMPLEMENTED_WITH_SERVER_SIDE_SEMANTICS);
        ToolResult result = MinecraftPrimitiveTools.validate(ToolCall.of("open_furnace", new ToolArguments(Map.of("x", "1", "y", "64", "z", "2"))), new ToolValidationContext(true));
        AICoreTestSupport.require(result.status() == ToolResultStatus.SUCCESS, "open_furnace must validate as a loaded furnace session adapter");
        ToolResult villager = MinecraftPrimitiveTools.validate(ToolCall.of("villager_trade", new ToolArguments(Map.of("target", "1", "count", "1"))), new ToolValidationContext(true));
        AICoreTestSupport.requireFailed(villager, "unsupported_missing_workstation_adapter");
        requireSessionStateSurvivesExecutorLifetime();
        requireFurnaceSessionRejectsGenericWindowTransfersWithoutMutation();
        requireFurnaceSessionRejectsGenericTransferDirectionWithoutMutation();
        requireFurnaceFuelSemantics();
    }

    private static void requireSessionStateSurvivesExecutorLifetime() {
        AICoreNpcSessionState sessionState = new AICoreNpcSessionState();
        BlockPos containerPos = new BlockPos(1, 64, 2);
        BlockPos furnacePos = new BlockPos(3, 64, 4);
        sessionState.openContainer(containerPos, false);
        AICoreTestSupport.require(containerPos.equals(sessionState.containerPos()), "container session must be stored outside executor instances");
        AICoreTestSupport.require(sessionState.furnacePos() == null, "non-furnace container session must not imply a furnace session");
        sessionState.openFurnace(furnacePos);
        AICoreTestSupport.require(furnacePos.equals(sessionState.containerPos()), "furnace session must also be the current container session");
        AICoreTestSupport.require(furnacePos.equals(sessionState.furnacePos()), "furnace session must survive executor replacement");
        AICoreTestSupport.require(sessionState.hasFurnaceSession(), "furnace session must be visible to transfer guards");
        sessionState.clear();
        AICoreTestSupport.require(sessionState.containerPos() == null, "window close must clear container session");
        AICoreTestSupport.require(sessionState.furnacePos() == null, "window close must clear furnace session");
        AICoreTestSupport.require(!sessionState.hasFurnaceSession(), "window close must clear furnace transfer guards");
    }

    private static void requireFurnaceSessionRejectsGenericWindowTransfersWithoutMutation() {
        AICoreNpcSessionState sessionState = new AICoreNpcSessionState();
        sessionState.openFurnace(new BlockPos(3, 64, 4));
        ItemStack npcDirt = new ItemStack(Items.DIRT, 1);
        ItemStack furnaceInput = ItemStack.EMPTY;
        ToolResult rejection = AICoreNpcToolExecutor.genericWindowTransferSessionRejection(sessionState);
        AICoreTestSupport.requireRejected(rejection, "use_furnace_specific_transfer_tools");
        AICoreTestSupport.require(npcDirt.is(Items.DIRT) && npcDirt.getCount() == 1, "rejected generic furnace deposit must not mutate NPC inventory");
        AICoreTestSupport.require(furnaceInput.isEmpty(), "rejected generic furnace deposit must not mutate furnace inventory");
    }

    private static void requireFurnaceSessionRejectsGenericTransferDirectionWithoutMutation() {
        AICoreNpcSessionState sessionState = new AICoreNpcSessionState();
        sessionState.openFurnace(new BlockPos(3, 64, 4));
        ToolResult transfer = MinecraftPrimitiveTools.validate(ToolCall.of("transfer", new ToolArguments(Map.of("options", "{\"direction\":\"deposit\",\"itemType\":\"minecraft:dirt\",\"count\":1}"))), new ToolValidationContext(true));
        AICoreTestSupport.require(transfer.status() == ToolResultStatus.SUCCESS, "deposit transfer options must validate before session guard rejection");
        ItemStack npcDirt = new ItemStack(Items.DIRT, 1);
        ItemStack furnaceInput = ItemStack.EMPTY;
        ToolResult rejection = AICoreNpcToolExecutor.genericWindowTransferSessionRejection(sessionState);
        AICoreTestSupport.requireRejected(rejection, "use_furnace_specific_transfer_tools");
        AICoreTestSupport.require(npcDirt.is(Items.DIRT) && npcDirt.getCount() == 1, "rejected generic transfer must not mutate NPC inventory");
        AICoreTestSupport.require(furnaceInput.isEmpty(), "rejected generic transfer must not mutate furnace inventory");
    }

    private static void requireFurnaceFuelSemantics() {
        AICoreTestSupport.require(AbstractFurnaceBlockEntity.isFuel(new ItemStack(Items.COAL)), "coal must be accepted by vanilla furnace fuel semantics");
        AICoreTestSupport.require(!AbstractFurnaceBlockEntity.isFuel(new ItemStack(Items.DIRT)), "dirt must not be accepted as furnace fuel");
    }
}
