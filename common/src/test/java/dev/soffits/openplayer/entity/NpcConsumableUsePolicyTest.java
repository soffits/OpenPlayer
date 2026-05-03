package dev.soffits.openplayer.entity;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class NpcConsumableUsePolicyTest {
    private NpcConsumableUsePolicyTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        allowsFoodWithoutRemainder();
        rejectsDrinkAndContainerRemainders();
        rejectsEmptyAndNonConsumableStacks();
    }

    private static void allowsFoodWithoutRemainder() {
        require(
                OpenPlayerNpcEntity.canUseSelectedMainHandItemLocally(new ItemStack(Items.APPLE)),
                "ordinary food without a remainder must be allowed"
        );
    }

    private static void rejectsDrinkAndContainerRemainders() {
        require(
                !OpenPlayerNpcEntity.canUseSelectedMainHandItemLocally(new ItemStack(Items.POTION)),
                "drink items must be rejected until NPC remainder insertion is explicit"
        );
        require(
                !OpenPlayerNpcEntity.canUseSelectedMainHandItemLocally(new ItemStack(Items.RABBIT_STEW)),
                "food with a container remainder must be rejected"
        );
    }

    private static void rejectsEmptyAndNonConsumableStacks() {
        require(
                !OpenPlayerNpcEntity.canUseSelectedMainHandItemLocally(ItemStack.EMPTY),
                "empty stacks must be rejected"
        );
        require(
                !OpenPlayerNpcEntity.canUseSelectedMainHandItemLocally(new ItemStack(Items.STONE)),
                "non-consumable stacks must be rejected"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
