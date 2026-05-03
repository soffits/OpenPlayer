package dev.soffits.openplayer.automation.survival;

import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import dev.soffits.openplayer.entity.NpcInventoryTransfer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SurvivalPolicyTest {
    private SurvivalPolicyTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        classifiesSafeFoodDrops();
        selectsDeterministicBestSafeFoodSlot();
        queriesNpcSafeFoodWithoutMutatingInventory();
        classifiesHostileDangerTargets();
        classifiesImmediateDangers();
        appliesHealthThresholds();
        appliesCooldownBackoff();
        gatesAndPrioritizesIdleSurvival();
    }

    private static void classifiesSafeFoodDrops() {
        require(SurvivalFoodPolicy.isSafeEdibleDrop(new ItemStack(Items.APPLE)),
                "ordinary food must be safe for COLLECT_FOOD");
        require(!SurvivalFoodPolicy.isSafeEdibleDrop(new ItemStack(Items.POTION)),
                "potion drops must not be safe food");
        require(!SurvivalFoodPolicy.isSafeEdibleDrop(new ItemStack(Items.RABBIT_STEW)),
                "stew/container-remainder food must not be safe food");
        require(!SurvivalFoodPolicy.isSafeEdibleDrop(new ItemStack(Items.STONE)),
                "non-food drops must not be safe food");
    }

    private static void classifiesHostileDangerTargets() {
        require(SurvivalTargetPolicy.isHostileOrDangerEntityClass(Zombie.class),
                "zombies must be classified as hostile danger targets");
        require(!SurvivalTargetPolicy.isHostileOrDangerEntityClass(Cow.class),
                "passive animals must not be classified as danger targets");
        require(!SurvivalTargetPolicy.isHostileOrDangerEntityClass(Player.class),
                "players must not be classified as default danger targets");
        require(!SurvivalTargetPolicy.isHostileOrDangerEntityClass(OpenPlayerNpcEntity.class),
                "OpenPlayer NPCs must not be classified as default danger targets");
        require(!SurvivalTargetPolicy.isHostileOrDangerEntityClass(Object.class),
                "non-Enemy classes must not be classified as danger targets");
        require(SurvivalTargetPolicy.isImmediateProjectileDangerClass(Arrow.class),
                "arrows must be classified as immediate projectile danger");
    }

    private static void selectsDeterministicBestSafeFoodSlot() {
        List<ItemStack> inventory = new ArrayList<>(Collections.nCopies(NpcInventoryTransfer.INVENTORY_SIZE, ItemStack.EMPTY));
        inventory.set(2, new ItemStack(Items.APPLE));
        inventory.set(10, new ItemStack(Items.COOKED_BEEF));
        inventory.set(11, new ItemStack(Items.RABBIT_STEW));
        inventory.set(12, new ItemStack(Items.COOKED_BEEF));

        int slot = SurvivalFoodPolicy.bestSafeFoodSlot(
                inventory,
                NpcInventoryTransfer.FIRST_NORMAL_SLOT,
                NpcInventoryTransfer.FIRST_EQUIPMENT_SLOT
        );

        require(slot == 10, "safe food selection must choose the best nutrition, then first slot");
    }

    private static void queriesNpcSafeFoodWithoutMutatingInventory() {
        List<ItemStack> inventory = new ArrayList<>(Collections.nCopies(NpcInventoryTransfer.INVENTORY_SIZE, ItemStack.EMPTY));
        inventory.set(0, new ItemStack(Items.STONE, 7));
        inventory.set(10, new ItemStack(Items.COOKED_BEEF, 2));
        inventory.set(NpcInventoryTransfer.ARMOR_FEET_SLOT, new ItemStack(Items.GOLDEN_CARROT));
        inventory.set(NpcInventoryTransfer.OFFHAND_SLOT, new ItemStack(Items.APPLE));
        List<ItemStack> snapshot = copyStacks(inventory);

        int slot = OpenPlayerNpcEntity.bestSafeFoodSlotForLocalUse(inventory);

        require(slot == 10, "NPC safe food query must choose only normal inventory food");
        require(inventoriesMatch(inventory, snapshot), "NPC safe food query must not mutate inventory");
        require(OpenPlayerNpcEntity.isSafeFoodSlotForLocalUse(inventory, 10),
                "normal inventory safe food slot must be usable after cooldown acquisition");
        require(!OpenPlayerNpcEntity.isSafeFoodSlotForLocalUse(inventory, NpcInventoryTransfer.OFFHAND_SLOT),
                "offhand food must remain outside the local survival food boundary");
        require(!OpenPlayerNpcEntity.isSafeFoodSlotForLocalUse(inventory, NpcInventoryTransfer.ARMOR_FEET_SLOT),
                "equipment food must remain outside the local survival food boundary");
    }

    private static void classifiesImmediateDangers() {
        require(SurvivalDangerPolicy.immediateDanger(false, true, true) == SurvivalDangerKind.LAVA,
                "lava must be highest immediate danger priority");
        require(SurvivalDangerPolicy.immediateDanger(true, false, true) == SurvivalDangerKind.FIRE,
                "fire must precede projectile danger");
        require(SurvivalDangerPolicy.immediateDanger(false, false, true) == SurvivalDangerKind.PROJECTILE,
                "projectile danger must be classified when no fire/lava is present");
        require(SurvivalDangerPolicy.immediateDanger(false, false, false) == SurvivalDangerKind.NONE,
                "no danger inputs must classify as none");
    }

    private static void appliesHealthThresholds() {
        require(SurvivalHealthPolicy.isLowHealth(12.0F, 20.0F),
                "60 percent health must be low health");
        require(!SurvivalHealthPolicy.isLowHealth(13.0F, 20.0F),
                "above 60 percent health must not be low health");
        require(SurvivalHealthPolicy.isDangerouslyLowHealth(5.0F, 20.0F),
                "25 percent health must be dangerously low");
        require(!SurvivalHealthPolicy.isDangerouslyLowHealth(6.0F, 20.0F),
                "above 25 percent health must not be dangerously low");
    }

    private static void appliesCooldownBackoff() {
        SurvivalCooldownPolicy cooldown = new SurvivalCooldownPolicy(3, 2);
        require(cooldown.ready(), "new survival cooldown must be ready");
        cooldown.backoffAfterAction();
        require(cooldown.remainingTicks() == 3, "action backoff must use action cooldown");
        cooldown.tick();
        cooldown.tick();
        cooldown.tick();
        require(cooldown.ready(), "cooldown must become ready after action ticks elapse");
        cooldown.backoffAfterDiagnostic();
        require(cooldown.remainingTicks() == 2, "diagnostic backoff must use diagnostic cooldown");
        cooldown.reset();
        require(cooldown.ready(), "reset must clear survival cooldown");
    }

    private static void gatesAndPrioritizesIdleSurvival() {
        SurvivalIdleAction disabled = SurvivalIdlePolicy.choose(
                false, false, true, true, SurvivalDangerKind.NONE, true, true, true, true, true
        );
        require(disabled == SurvivalIdleAction.NONE,
                "background survival monitor must be disabled when world actions are disabled");

        SurvivalIdleAction active = SurvivalIdlePolicy.choose(
                true, true, true, true, SurvivalDangerKind.NONE, true, true, true, true, true
        );
        require(active == SurvivalIdleAction.NONE,
                "background survival monitor must not run while another command is active");

        SurvivalIdleAction eatBeforeCombat = SurvivalIdlePolicy.choose(
                true, false, true, true, SurvivalDangerKind.NONE, true, true, true, true, false
        );
        require(eatBeforeCombat == SurvivalIdleAction.EAT_SAFE_FOOD,
                "low health safe food must be chosen before combat");

        SurvivalIdleAction defend = SurvivalIdlePolicy.choose(
                true, false, true, true, SurvivalDangerKind.NONE, false, false, false, true, false
        );
        require(defend == SurvivalIdleAction.DEFEND_OWNER,
                "owner danger must queue defense when no higher priority is present");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> inventory) {
        List<ItemStack> copy = new ArrayList<>(inventory.size());
        for (ItemStack itemStack : inventory) {
            copy.add(itemStack.copy());
        }
        return copy;
    }

    private static boolean inventoriesMatch(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int slot = 0; slot < left.size(); slot++) {
            if (!ItemStack.matches(left.get(slot), right.get(slot))) {
                return false;
            }
        }
        return true;
    }
}
