package dev.soffits.openplayer.automation.survival;

import dev.soffits.openplayer.entity.OpenPlayerNpcEntity;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;

public final class SurvivalPolicyTest {
    private SurvivalPolicyTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        classifiesHostileDangerTargets();
        classifiesImmediateDangers();
        appliesHealthThresholds();
        appliesCooldownBackoff();
        gatesAndPrioritizesIdleSurvival();
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

        SurvivalIdleAction combat = SurvivalIdlePolicy.choose(
                true, false, true, true, SurvivalDangerKind.NONE, true, true, true, true, false
        );
        require(combat == SurvivalIdleAction.SELF_DEFENSE,
                "idle survival must not choose hidden food or armor chains before combat");

        SurvivalIdleAction ownerDanger = SurvivalIdlePolicy.choose(
                true, false, true, true, SurvivalDangerKind.NONE, false, false, false, true, false
        );
        require(ownerDanger == SurvivalIdleAction.NONE,
                "owner danger must not queue a macro defense action");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

}
