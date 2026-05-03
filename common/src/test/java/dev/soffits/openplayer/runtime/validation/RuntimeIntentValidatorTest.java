package dev.soffits.openplayer.runtime.validation;

import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.IntentPriority;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class RuntimeIntentValidatorTest {
    private RuntimeIntentValidatorTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        rejectsNullIntent();
        validatesWorldActionGate();
        validatesBlankOnlyInstructions();
        validatesPhaseFiveInventoryInstructions();
        validatesGetItemInstruction();
        validatesCoordinateInstructions();
        validatesRadiusInstructions();
        rejectsNonAutomationConversationKinds();
        rejectsPlannedIntentKinds();
        gatesPlannedWorldActionKindsBeforeUnimplementedRejection();
        rejectsPlannedNonGatedKindsAsUnimplemented();
        policyClassifiesEveryIntentKind();
    }

    private static void rejectsNullIntent() {
        RuntimeIntentValidationResult result = RuntimeIntentValidator.validate(null, true);
        requireRejected(result, "Runtime intent cannot be null");
    }

    private static void validatesWorldActionGate() {
        for (IntentKind kind : IntentKind.values()) {
            RuntimeIntentValidationResult result = RuntimeIntentValidator.validate(validIntent(kind), false);
            if (RuntimeIntentPolicies.isLocalWorldOrInventoryAction(kind)) {
                requireRejected(result, "World actions are disabled for this OpenPlayer character");
            } else if (kind == IntentKind.INTERACT || kind == IntentKind.CHAT
                    || kind == IntentKind.UNAVAILABLE || kind == IntentKind.OBSERVE || plannedKinds().contains(kind)) {
                require(!result.isAccepted(), kind + " must not be accepted by automation");
            } else {
                require(result.isAccepted(), kind + " should pass validation when world actions are disabled");
            }
        }
    }

    private static void validatesBlankOnlyInstructions() {
        IntentKind[] blankOnlyKinds = {
                IntentKind.STOP,
                IntentKind.REPORT_STATUS,
                IntentKind.FOLLOW_OWNER,
                IntentKind.COLLECT_ITEMS,
                IntentKind.EQUIP_BEST_ITEM,
                IntentKind.EQUIP_ARMOR,
                IntentKind.USE_SELECTED_ITEM,
                IntentKind.SWAP_TO_OFFHAND,
                IntentKind.INVENTORY_QUERY
        };
        for (IntentKind kind : blankOnlyKinds) {
            require(RuntimeIntentValidator.validate(intent(kind, ""), true).isAccepted(),
                    kind + " should accept blank instruction");
            require(RuntimeIntentValidator.validate(intent(kind, "  \t  "), true).isAccepted(),
                    kind + " should accept whitespace instruction");
            requireRejected(RuntimeIntentValidator.validate(intent(kind, "extra"), true),
                    kind.name() + " requires a blank instruction");
        }
    }

    private static void validatesPhaseFiveInventoryInstructions() {
        require(RuntimeIntentValidator.validate(intent(IntentKind.INVENTORY_QUERY, ""), true).isAccepted(),
                "INVENTORY_QUERY should accept blank instruction");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.INVENTORY_QUERY, "minecraft:bread"), true),
                "INVENTORY_QUERY requires a blank instruction");

        require(RuntimeIntentValidator.validate(intent(IntentKind.EQUIP_ITEM, "minecraft:iron_sword"), true).isAccepted(),
                "EQUIP_ITEM should accept exact item id");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.EQUIP_ITEM, "iron sword"), true),
                "EQUIP_ITEM requires instruction: <item_id>");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.EQUIP_ITEM, "minecraft:air"), true),
                "EQUIP_ITEM requires instruction: <item_id>");

        require(RuntimeIntentValidator.validate(intent(IntentKind.GIVE_ITEM, "minecraft:bread"), true).isAccepted(),
                "GIVE_ITEM should accept default owner syntax");
        require(RuntimeIntentValidator.validate(intent(IntentKind.GIVE_ITEM, "minecraft:bread 3 owner"), true).isAccepted(),
                "GIVE_ITEM should accept count and owner syntax");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.GIVE_ITEM, "minecraft:bread 0"), true),
                "GIVE_ITEM requires instruction: <item_id> [count] [owner]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.GIVE_ITEM, "minecraft:bread 1 Steve"), true),
                "GIVE_ITEM requires instruction: <item_id> [count] [owner]");

        require(RuntimeIntentValidator.validate(intent(IntentKind.DROP_ITEM, ""), true).isAccepted(),
                "DROP_ITEM should preserve blank selected-hotbar syntax");
        require(RuntimeIntentValidator.validate(intent(IntentKind.DROP_ITEM, "minecraft:cobblestone 16"), true).isAccepted(),
                "DROP_ITEM should accept exact item count syntax");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.DROP_ITEM, "minecraft:cobblestone 0"), true),
                "DROP_ITEM requires blank or instruction: <item_id> [count]");
    }

    private static void validatesGetItemInstruction() {
        require(RuntimeIntentValidator.validate(intent(IntentKind.GET_ITEM, "minecraft:stick"), true).isAccepted(),
                "GET_ITEM should accept exact item id with default count");
        require(RuntimeIntentValidator.validate(intent(IntentKind.GET_ITEM, "minecraft:stick 64"), true).isAccepted(),
                "GET_ITEM should accept one stack count for stackable items");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.GET_ITEM, "stick 1"), true),
                "GET_ITEM requires instruction: <item_id> [count]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.GET_ITEM, "minecraft:air 1"), true),
                "GET_ITEM requires instruction: <item_id> [count]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.GET_ITEM, "minecraft:stick 65"), true),
                "GET_ITEM requires instruction: <item_id> [count]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.GET_ITEM, "minecraft:stick 0"), true),
                "GET_ITEM requires instruction: <item_id> [count]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.GET_ITEM, "minecraft:stick 1 owner"), true),
                "GET_ITEM requires instruction: <item_id> [count]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.GET_ITEM, "minecraft:stick 1"), false),
                "World actions are disabled for this OpenPlayer character");
    }

    private static void validatesCoordinateInstructions() {
        IntentKind[] coordinateKinds = {
                IntentKind.MOVE,
                IntentKind.LOOK,
                IntentKind.PATROL,
                IntentKind.BREAK_BLOCK,
                IntentKind.PLACE_BLOCK
        };
        for (IntentKind kind : coordinateKinds) {
            require(RuntimeIntentValidator.validate(intent(kind, "1 64 -2.5"), true).isAccepted(),
                    kind + " should accept finite x y z coordinates");
            requireRejected(RuntimeIntentValidator.validate(intent(kind, "1 64"), true),
                    kind.name() + " requires instruction: x y z");
            requireRejected(RuntimeIntentValidator.validate(intent(kind, "1 NaN 3"), true),
                    kind.name() + " requires instruction: x y z");
            requireRejected(RuntimeIntentValidator.validate(intent(kind, "1 two 3"), true),
                    kind.name() + " requires instruction: x y z");
        }
    }

    private static void validatesRadiusInstructions() {
        IntentKind[] radiusKinds = {IntentKind.ATTACK_NEAREST, IntentKind.GUARD_OWNER};
        for (IntentKind kind : radiusKinds) {
            require(RuntimeIntentValidator.validate(intent(kind, ""), true).isAccepted(),
                    kind + " should accept blank radius instruction");
            require(RuntimeIntentValidator.validate(intent(kind, "12.5"), true).isAccepted(),
                    kind + " should accept positive finite radius");
            requireRejected(RuntimeIntentValidator.validate(intent(kind, "0"), true),
                    kind.name() + " instruction must be blank or a positive radius number");
            requireRejected(RuntimeIntentValidator.validate(intent(kind, "-1"), true),
                    kind.name() + " instruction must be blank or a positive radius number");
            requireRejected(RuntimeIntentValidator.validate(intent(kind, "NaN"), true),
                    kind.name() + " instruction must be blank or a positive radius number");
            requireRejected(RuntimeIntentValidator.validate(intent(kind, "wide"), true),
                    kind.name() + " instruction must be blank or a positive radius number");
        }
    }

    private static void rejectsNonAutomationConversationKinds() {
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.CHAT, "hello"), true),
                "CHAT cannot be submitted to automation");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.UNAVAILABLE, "no"), true),
                "UNAVAILABLE cannot be submitted to automation");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.OBSERVE, ""), true),
                "OBSERVE cannot be submitted to automation");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.INTERACT, "target"), true),
                "INTERACT is not implemented by the vanilla runtime");
    }

    private static void rejectsPlannedIntentKinds() {
        for (IntentKind kind : plannedKinds()) {
            requireRejected(RuntimeIntentValidator.validate(validIntent(kind), true),
                    kind.name() + " is not implemented by the vanilla runtime");
        }
    }

    private static void gatesPlannedWorldActionKindsBeforeUnimplementedRejection() {
        for (IntentKind kind : plannedGatedKinds()) {
            requireRejected(RuntimeIntentValidator.validate(validIntent(kind), false),
                    "World actions are disabled for this OpenPlayer character");
        }
    }

    private static void rejectsPlannedNonGatedKindsAsUnimplemented() {
        for (IntentKind kind : plannedNonGatedKinds()) {
            require(!RuntimeIntentPolicies.isLocalWorldOrInventoryAction(kind),
                    kind + " must not use the world-action gate");
            requireRejected(RuntimeIntentValidator.validate(validIntent(kind), false),
                    kind.name() + " is not implemented by the vanilla runtime");
        }
    }

    private static void policyClassifiesEveryIntentKind() {
        Set<IntentKind> classifiedKinds = EnumSet.noneOf(IntentKind.class);
        for (IntentKind kind : IntentKind.values()) {
            RuntimeIntentPolicies.isLocalWorldOrInventoryAction(kind);
            classifiedKinds.add(kind);
        }
        require(classifiedKinds.equals(EnumSet.allOf(IntentKind.class)),
                "Runtime world-action policy must classify every intent kind");
    }

    private static CommandIntent validIntent(IntentKind kind) {
        String instruction = switch (kind) {
            case MOVE, LOOK, PATROL, BREAK_BLOCK, PLACE_BLOCK -> "1 64 1";
            case ATTACK_NEAREST, GUARD_OWNER -> "12";
            case INTERACT, CHAT, UNAVAILABLE -> "details";
            case GOTO -> "home";
            case INVENTORY_QUERY -> "";
            case EQUIP_ITEM -> "minecraft:iron_sword";
            case GIVE_ITEM -> "minecraft:bread 1 owner";
            case GET_ITEM -> "minecraft:stick 1";
            case DEPOSIT_ITEM,
                    STASH_ITEM,
                    COLLECT_FOOD,
                    FARM_NEARBY,
                    FISH,
                    ATTACK_TARGET,
                    DEFEND_OWNER,
                    PAUSE,
                    UNPAUSE,
                    RESET_MEMORY,
                    BODY_LANGUAGE,
                    BUILD_STRUCTURE -> "";
            case OBSERVE,
                    STOP,
                    FOLLOW_OWNER,
                    COLLECT_ITEMS,
                    EQUIP_BEST_ITEM,
                    EQUIP_ARMOR,
                    USE_SELECTED_ITEM,
                    SWAP_TO_OFFHAND,
                    DROP_ITEM,
                    REPORT_STATUS -> "";
        };
        return intent(kind, instruction);
    }

    private static EnumSet<IntentKind> plannedKinds() {
        EnumSet<IntentKind> kinds = plannedGatedKinds();
        kinds.addAll(plannedNonGatedKinds());
        return kinds;
    }

    private static EnumSet<IntentKind> plannedGatedKinds() {
        return EnumSet.of(
                IntentKind.DEPOSIT_ITEM,
                IntentKind.STASH_ITEM,
                IntentKind.COLLECT_FOOD,
                IntentKind.FARM_NEARBY,
                IntentKind.FISH,
                IntentKind.ATTACK_TARGET,
                IntentKind.DEFEND_OWNER,
                IntentKind.BUILD_STRUCTURE
        );
    }

    private static EnumSet<IntentKind> plannedNonGatedKinds() {
        return EnumSet.of(
                IntentKind.GOTO,
                IntentKind.PAUSE,
                IntentKind.UNPAUSE,
                IntentKind.RESET_MEMORY,
                IntentKind.BODY_LANGUAGE
        );
    }

    private static CommandIntent intent(IntentKind kind, String instruction) {
        return new CommandIntent(kind, IntentPriority.NORMAL, instruction);
    }

    private static void requireRejected(RuntimeIntentValidationResult result, String expectedMessage) {
        require(!result.isAccepted(), "Expected rejection for message: " + expectedMessage);
        require(result.message().equals(expectedMessage),
                "Expected message " + expectedMessage + " but got " + result.message());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
