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
        validatesPhaseThirteenControlAndExpressionInstructions();
        validatesPhaseFiveInventoryInstructions();
        validatesContainerTransferInstructions();
        validatesGetItemInstruction();
        validatesSmeltItemInstruction();
        validatesCoordinateInstructions();
        validatesGotoInstructions();
        validatesRadiusInstructions();
        rejectsNonAutomationConversationKinds();
        validatesSurvivalRadiusInstructions();
        validatesWorkLoopInstructions();
        validatesBuildStructureInstructions();
        validatesPhaseFourteenInteractionInstructions();
        validatesPhaseFourteenTargetAttackInstructions();
        validatesAdvancedLoadedReconnaissanceInstructions();
        validatesPortalTravelInstructions();
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
            } else if (kind == IntentKind.CHAT
                    || kind == IntentKind.UNAVAILABLE || kind == IntentKind.OBSERVE) {
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
                IntentKind.PAUSE,
                IntentKind.UNPAUSE,
                IntentKind.RESET_MEMORY,
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

    private static void validatesPhaseThirteenControlAndExpressionInstructions() {
        require(RuntimeIntentValidator.validate(intent(IntentKind.BODY_LANGUAGE, ""), true).isAccepted(),
                "BODY_LANGUAGE should accept blank idle instruction");
        require(RuntimeIntentValidator.validate(intent(IntentKind.BODY_LANGUAGE, "wave"), true).isAccepted(),
                "BODY_LANGUAGE should accept wave");
        require(RuntimeIntentValidator.validate(intent(IntentKind.BODY_LANGUAGE, "look_owner"), true).isAccepted(),
                "BODY_LANGUAGE should accept look_owner");
        require(RuntimeIntentValidator.validate(intent(IntentKind.BODY_LANGUAGE, "CROUCH"), true).isAccepted(),
                "BODY_LANGUAGE should accept case-insensitive crouch");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.BODY_LANGUAGE, "nod"), true),
                "BODY_LANGUAGE requires blank, idle, wave, swing, crouch, uncrouch, or look_owner");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.BODY_LANGUAGE, "shake"), true),
                "BODY_LANGUAGE requires blank, idle, wave, swing, crouch, uncrouch, or look_owner");
        require(RuntimeIntentValidator.validate(intent(IntentKind.BODY_LANGUAGE, "wave"), false).isAccepted(),
                "BODY_LANGUAGE must not be gated as a world or inventory action");
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

    private static void validatesSmeltItemInstruction() {
        require(RuntimeIntentValidator.validate(intent(IntentKind.SMELT_ITEM, "minecraft:iron_ingot"), true).isAccepted(),
                "SMELT_ITEM should accept exact item id with default count");
        require(RuntimeIntentValidator.validate(intent(IntentKind.SMELT_ITEM, "minecraft:iron_ingot 8"), true).isAccepted(),
                "SMELT_ITEM should accept one-stack output count");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.SMELT_ITEM, ""), true),
                "SMELT_ITEM requires instruction: <item_id> [count]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.SMELT_ITEM, "minecraft:iron_ingot 0"), true),
                "SMELT_ITEM requires instruction: <item_id> [count]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.SMELT_ITEM, "iron_ingot 1"), true),
                "SMELT_ITEM requires instruction: <item_id> [count]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.SMELT_ITEM, "minecraft:air"), true),
                "SMELT_ITEM requires instruction: <item_id> [count]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.SMELT_ITEM, "minecraft:iron_ingot 1"), false),
                "World actions are disabled for this OpenPlayer character");
    }

    private static void validatesContainerTransferInstructions() {
        require(RuntimeIntentValidator.validate(intent(IntentKind.DEPOSIT_ITEM, ""), true).isAccepted(),
                "DEPOSIT_ITEM should accept blank deposit-all syntax");
        require(RuntimeIntentValidator.validate(intent(IntentKind.DEPOSIT_ITEM, "minecraft:bread 3"), true).isAccepted(),
                "DEPOSIT_ITEM should accept exact item count syntax");
        require(RuntimeIntentValidator.validate(intent(IntentKind.DEPOSIT_ITEM, "minecraft:bread 3 repeat=2"), true).isAccepted(),
                "DEPOSIT_ITEM should accept bounded repeat suffix");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.DEPOSIT_ITEM, "minecraft:bread 0"), true),
                "DEPOSIT_ITEM requires blank or instruction: <item_id> [count] [repeat=1..5]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.DEPOSIT_ITEM, "minecraft:bread count=2"), true),
                "DEPOSIT_ITEM requires blank or instruction: <item_id> [count] [repeat=1..5]");
        require(RuntimeIntentValidator.validate(intent(IntentKind.STASH_ITEM, ""), true).isAccepted(),
                "STASH_ITEM should accept blank deposit-all syntax");
        require(RuntimeIntentValidator.validate(intent(IntentKind.STASH_ITEM, "minecraft:bread 3"), true).isAccepted(),
                "STASH_ITEM should accept exact item count syntax");
        require(RuntimeIntentValidator.validate(intent(IntentKind.STASH_ITEM, "minecraft:bread 3 repeat=2"), true).isAccepted(),
                "STASH_ITEM should accept bounded repeat suffix");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.STASH_ITEM, "minecraft:bread owner"), true),
                "STASH_ITEM requires blank or instruction: <item_id> [count] [repeat=1..5]");
        require(RuntimeIntentValidator.validate(intent(IntentKind.WITHDRAW_ITEM, "minecraft:bread"), true).isAccepted(),
                "WITHDRAW_ITEM should accept exact item id with default count");
        require(RuntimeIntentValidator.validate(intent(IntentKind.WITHDRAW_ITEM, "minecraft:bread 3"), true).isAccepted(),
                "WITHDRAW_ITEM should accept exact item count syntax");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.WITHDRAW_ITEM, ""), true),
                "WITHDRAW_ITEM requires instruction: <item_id> [count]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.WITHDRAW_ITEM, "minecraft:bread 0"), true),
                "WITHDRAW_ITEM requires instruction: <item_id> [count]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.WITHDRAW_ITEM, "minecraft:bread 1"), false),
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

    private static void validatesGotoInstructions() {
        require(RuntimeIntentValidator.validate(intent(IntentKind.GOTO, "1 64 -2"), true).isAccepted(),
                "GOTO should accept coordinate syntax");
        require(RuntimeIntentValidator.validate(intent(IntentKind.GOTO, "owner"), true).isAccepted(),
                "GOTO should accept owner syntax");
        require(RuntimeIntentValidator.validate(intent(IntentKind.GOTO, "block minecraft:oak_log 24"), true).isAccepted(),
                "GOTO should accept block id and radius syntax");
        require(RuntimeIntentValidator.validate(intent(IntentKind.GOTO, "entity minecraft:zombie"), true).isAccepted(),
                "GOTO should accept entity id syntax");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.GOTO, "home"), true),
                "GOTO requires instruction: x y z, owner, block <block_or_item_id> [radius], or entity <entity_type_id> [radius]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.GOTO, "block oak_log"), true),
                "GOTO requires instruction: x y z, owner, block <block_or_item_id> [radius], or entity <entity_type_id> [radius]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.GOTO, "entity minecraft:zombie 0"), true),
                "GOTO requires instruction: x y z, owner, block <block_or_item_id> [radius], or entity <entity_type_id> [radius]");
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

    private static void validatesSurvivalRadiusInstructions() {
        IntentKind[] survivalKinds = {IntentKind.COLLECT_FOOD, IntentKind.DEFEND_OWNER};
        for (IntentKind kind : survivalKinds) {
            require(RuntimeIntentValidator.validate(intent(kind, ""), true).isAccepted(),
                    kind + " should accept blank radius instruction");
            require(RuntimeIntentValidator.validate(intent(kind, "12.5"), true).isAccepted(),
                    kind + " should accept positive finite radius");
            requireRejected(RuntimeIntentValidator.validate(intent(kind, "0"), true),
                    kind.name() + " instruction must be blank or a positive radius number");
            requireRejected(RuntimeIntentValidator.validate(intent(kind, "wide"), true),
                    kind.name() + " instruction must be blank or a positive radius number");
            requireRejected(RuntimeIntentValidator.validate(intent(kind, "12 extra"), true),
                    kind.name() + " instruction must be blank or a positive radius number");
            requireRejected(RuntimeIntentValidator.validate(intent(kind, "12"), false),
                    "World actions are disabled for this OpenPlayer character");
        }
    }

    private static void validatesWorkLoopInstructions() {
        require(RuntimeIntentValidator.validate(intent(IntentKind.FARM_NEARBY, ""), true).isAccepted(),
                "FARM_NEARBY should accept blank radius instruction");
        require(RuntimeIntentValidator.validate(intent(IntentKind.FARM_NEARBY, "8"), true).isAccepted(),
                "FARM_NEARBY should accept positive radius instruction");
        require(RuntimeIntentValidator.validate(intent(IntentKind.FARM_NEARBY, "radius=8 repeat=3"), true).isAccepted(),
                "FARM_NEARBY should accept bounded repeat instruction");
        require(RuntimeIntentValidator.validate(intent(IntentKind.FARM_NEARBY, "radius=8 count=2"), true).isAccepted(),
                "FARM_NEARBY should accept count as repeat alias");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.FARM_NEARBY, "wide"), true),
                "FARM_NEARBY instruction must be blank, a positive radius number, or radius=<blocks> repeat=1..5");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.FARM_NEARBY, "radius=8 repeat=6"), true),
                "FARM_NEARBY instruction must be blank, a positive radius number, or radius=<blocks> repeat=1..5");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.FARM_NEARBY, "8"), false),
                "World actions are disabled for this OpenPlayer character");

        require(RuntimeIntentValidator.validate(intent(IntentKind.FISH, ""), true).isAccepted(),
                "FISH should accept blank duration instruction");
        require(RuntimeIntentValidator.validate(intent(IntentKind.FISH, "45"), true).isAccepted(),
                "FISH should accept positive seconds instruction");
        require(RuntimeIntentValidator.validate(intent(IntentKind.FISH, "duration=45 repeat=2"), true).isAccepted(),
                "FISH should accept duration repeat syntax for truthful diagnostics");
        require(RuntimeIntentValidator.validate(intent(IntentKind.FISH, "stop"), true).isAccepted(),
                "FISH should accept stop instruction");
        require(RuntimeIntentValidator.validate(intent(IntentKind.FISH, "cancel"), true).isAccepted(),
                "FISH should accept cancel instruction");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.FISH, "0"), true),
                "FISH instruction must be blank, stop, cancel, a positive duration in seconds, or duration=<seconds> repeat=1..5");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.FISH, "soon"), true),
                "FISH instruction must be blank, stop, cancel, a positive duration in seconds, or duration=<seconds> repeat=1..5");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.FISH, "45"), false),
                "World actions are disabled for this OpenPlayer character");
    }

    private static void rejectsNonAutomationConversationKinds() {
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.CHAT, "hello"), true),
                "CHAT cannot be submitted to automation");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.UNAVAILABLE, "no"), true),
                "UNAVAILABLE cannot be submitted to automation");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.OBSERVE, ""), true),
                "OBSERVE cannot be submitted to automation");
    }

    private static void validatesBuildStructureInstructions() {
        require(RuntimeIntentValidator.validate(intent(IntentKind.BUILD_STRUCTURE,
                        "primitive=floor origin=0,64,0 size=3,1,3 material=minecraft:cobblestone"), true).isAccepted(),
                "BUILD_STRUCTURE should accept strict primitive syntax");
        require(RuntimeIntentValidator.validate(intent(IntentKind.BUILD_STRUCTURE,
                        "material=minecraft:cobblestone size=1,4,1 origin=0,64,0 primitive=line"), true).isAccepted(),
                "BUILD_STRUCTURE should accept strict key order independence");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.BUILD_STRUCTURE, "build a small house"), true),
                "BUILD_STRUCTURE requires instruction: primitive=<line|wall|floor|box|stairs> origin=<x,y,z> size=<x,y,z> material=<item_id>");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.BUILD_STRUCTURE,
                        "primitive=floor origin=0,64,0 size=9,1,8 material=minecraft:cobblestone"), true),
                "BUILD_STRUCTURE requires instruction: primitive=<line|wall|floor|box|stairs> origin=<x,y,z> size=<x,y,z> material=<item_id>");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.BUILD_STRUCTURE,
                        "primitive=floor origin=0,64,0 size=3,1,3 material=cobblestone"), true),
                "BUILD_STRUCTURE requires instruction: primitive=<line|wall|floor|box|stairs> origin=<x,y,z> size=<x,y,z> material=<item_id>");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.BUILD_STRUCTURE,
                        "primitive=floor origin=0,64,0 size=3,1,3 material=minecraft:cobblestone"), false),
                "World actions are disabled for this OpenPlayer character");
    }

    private static void validatesAdvancedLoadedReconnaissanceInstructions() {
        require(RuntimeIntentValidator.validate(intent(IntentKind.LOCATE_LOADED_BLOCK, "minecraft:oak_log"), true).isAccepted(),
                "LOCATE_LOADED_BLOCK should accept exact block id");
        require(RuntimeIntentValidator.validate(intent(IntentKind.LOCATE_LOADED_BLOCK, "minecraft:oak_log 32"), true).isAccepted(),
                "LOCATE_LOADED_BLOCK should accept bounded radius");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.LOCATE_LOADED_BLOCK, "oak_log"), true),
                "LOCATE_LOADED_BLOCK requires instruction: <block_or_item_id> [radius]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.LOCATE_LOADED_BLOCK, "minecraft:oak_log"), false),
                "World actions are disabled for this OpenPlayer character");

        require(RuntimeIntentValidator.validate(intent(IntentKind.LOCATE_LOADED_ENTITY, "minecraft:zombie"), true).isAccepted(),
                "LOCATE_LOADED_ENTITY should accept exact entity id");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.LOCATE_LOADED_ENTITY, "minecraft:zombie 0"), true),
                "LOCATE_LOADED_ENTITY requires instruction: <entity_type_id> [radius]");

        require(RuntimeIntentValidator.validate(intent(IntentKind.FIND_LOADED_BIOME, "minecraft:plains 16"), true).isAccepted(),
                "FIND_LOADED_BIOME should accept exact biome id and radius");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.FIND_LOADED_BIOME, "plains 16"), true),
                "FIND_LOADED_BIOME requires instruction: <biome_id> [radius]");

        require(RuntimeIntentValidator.validate(intent(IntentKind.EXPLORE_CHUNKS, ""), true).isAccepted(),
                "EXPLORE_CHUNKS should accept blank loaded-only defaults");
        require(RuntimeIntentValidator.validate(intent(IntentKind.EXPLORE_CHUNKS, "radius=64 steps=8"), true).isAccepted(),
                "EXPLORE_CHUNKS should accept bounded key/value syntax");
        require(RuntimeIntentValidator.validate(intent(IntentKind.EXPLORE_CHUNKS, "clear"), true).isAccepted(),
                "EXPLORE_CHUNKS should accept clear instruction");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.EXPLORE_CHUNKS, "north"), true),
                "EXPLORE_CHUNKS requires blank, reset, clear, or instruction: radius=<blocks> steps=<count>");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.EXPLORE_CHUNKS, ""), false),
                "World actions are disabled for this OpenPlayer character");

        require(RuntimeIntentValidator.validate(intent(IntentKind.LOCATE_STRUCTURE, "minecraft:village 64"), true).isAccepted(),
                "LOCATE_STRUCTURE should accept exact structure id and bounded radius");
        require(RuntimeIntentValidator.validate(intent(IntentKind.LOCATE_STRUCTURE, "minecraft:village source=loaded"), true).isAccepted(),
                "LOCATE_STRUCTURE should accept explicit loaded source");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.LOCATE_STRUCTURE, "village 16"), true),
                "LOCATE_STRUCTURE requires instruction: <structure_id> [radius] [source=loaded]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.LOCATE_STRUCTURE, "minecraft:village source=generated"), true),
                "LOCATE_STRUCTURE requires instruction: <structure_id> [radius] [source=loaded]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.LOCATE_STRUCTURE, "minecraft:village"), false),
                "World actions are disabled for this OpenPlayer character");
    }

    private static void validatesPortalTravelInstructions() {
        require(RuntimeIntentValidator.validate(intent(IntentKind.USE_PORTAL, ""), true).isAccepted(),
                "USE_PORTAL should accept blank instruction");
        require(RuntimeIntentValidator.validate(
                intent(IntentKind.USE_PORTAL, "radius=12 target=minecraft:the_nether build=true"), true
        ).isAccepted(), "USE_PORTAL should accept strict portal key/value syntax");
        require(RuntimeIntentValidator.validate(
                intent(IntentKind.USE_PORTAL, "radius=12 target=example:moon build=false"), true
        ).isAccepted(), "USE_PORTAL should accept arbitrary ResourceLocation target dimensions");
        require(RuntimeIntentValidator.validate(intent(IntentKind.TRAVEL_NETHER, ""), true).isAccepted(),
                "TRAVEL_NETHER should accept blank instruction");
        require(RuntimeIntentValidator.validate(intent(IntentKind.TRAVEL_NETHER, "radius=24 build=false"), true).isAccepted(),
                "TRAVEL_NETHER should accept bounded radius/build syntax");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.USE_PORTAL, "nether"), true),
                "USE_PORTAL requires blank or instruction: radius=<blocks> target=<dimension_id> build=<true|false>");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.USE_PORTAL, "radius=128"), true),
                "USE_PORTAL requires blank or instruction: radius=<blocks> target=<dimension_id> build=<true|false>");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.USE_PORTAL, "target=not_a_resource_location"), true),
                "USE_PORTAL requires blank or instruction: radius=<blocks> target=<dimension_id> build=<true|false>");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.TRAVEL_NETHER, "target=minecraft:overworld"), true),
                "TRAVEL_NETHER requires blank or instruction: radius=<blocks> build=<true|false>");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.TRAVEL_NETHER, ""), false),
                "World actions are disabled for this OpenPlayer character");
    }

    private static void validatesPhaseFourteenInteractionInstructions() {
        require(RuntimeIntentValidator.validate(intent(IntentKind.INTERACT, "block 1 64 -2"), true).isAccepted(),
                "INTERACT should accept safe block coordinate syntax");
        require(RuntimeIntentValidator.validate(intent(IntentKind.INTERACT, "entity minecraft:sheep 4"), true).isAccepted(),
                "INTERACT should accept entity capability syntax");
        require(RuntimeIntentValidator.validate(
                intent(IntentKind.INTERACT, "entity 123e4567-e89b-12d3-a456-426614174000 4"), true
        ).isAccepted(), "INTERACT should accept entity UUID syntax");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.INTERACT, "right_click minecraft:lever"), true),
                "INTERACT requires instruction: block <x> <y> <z> or entity <entity_type_or_uuid> [radius]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.INTERACT, "block 1 64 -2 use_item"), true),
                "INTERACT requires instruction: block <x> <y> <z> or entity <entity_type_or_uuid> [radius]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.INTERACT, "block 1 64 -2"), false),
                "World actions are disabled for this OpenPlayer character");
    }

    private static void validatesPhaseFourteenTargetAttackInstructions() {
        require(RuntimeIntentValidator.validate(intent(IntentKind.ATTACK_TARGET, "minecraft:zombie"), true).isAccepted(),
                "ATTACK_TARGET should accept exact entity type syntax");
        require(RuntimeIntentValidator.validate(intent(IntentKind.ATTACK_TARGET, "entity minecraft:zombie 12"), true).isAccepted(),
                "ATTACK_TARGET should accept entity-prefixed type and radius syntax");
        require(RuntimeIntentValidator.validate(
                intent(IntentKind.ATTACK_TARGET, "entity 123e4567-e89b-12d3-a456-426614174000 8"), true
        ).isAccepted(), "ATTACK_TARGET should accept UUID syntax");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.ATTACK_TARGET, "zombie"), true),
                "ATTACK_TARGET requires instruction: [entity] <entity_type_or_uuid> [radius]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.ATTACK_TARGET, "minecraft:zombie 0"), true),
                "ATTACK_TARGET requires instruction: [entity] <entity_type_or_uuid> [radius]");
        requireRejected(RuntimeIntentValidator.validate(intent(IntentKind.ATTACK_TARGET, "minecraft:zombie"), false),
                "World actions are disabled for this OpenPlayer character");
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
            case INTERACT -> "block 1 64 1";
            case CHAT, UNAVAILABLE -> "details";
            case GOTO -> "owner";
            case INVENTORY_QUERY -> "";
            case EQUIP_ITEM -> "minecraft:iron_sword";
            case GIVE_ITEM -> "minecraft:bread 1 owner";
            case GET_ITEM -> "minecraft:stick 1";
            case SMELT_ITEM -> "minecraft:iron_ingot 1";
            case DEPOSIT_ITEM, STASH_ITEM -> "";
            case WITHDRAW_ITEM -> "minecraft:bread 1";
            case COLLECT_FOOD,
                    FARM_NEARBY,
                    FISH -> "";
            case BUILD_STRUCTURE -> "primitive=line origin=1,64,1 size=2,1,1 material=minecraft:cobblestone";
            case LOCATE_LOADED_BLOCK -> "minecraft:oak_log";
            case LOCATE_LOADED_ENTITY -> "minecraft:zombie";
            case FIND_LOADED_BIOME -> "minecraft:plains";
            case LOCATE_STRUCTURE -> "minecraft:village";
            case EXPLORE_CHUNKS -> "";
            case USE_PORTAL, TRAVEL_NETHER -> "";
            case ATTACK_TARGET -> "minecraft:zombie";
            case DEFEND_OWNER -> "";
            case PAUSE,
                    UNPAUSE,
                    RESET_MEMORY -> "";
            case BODY_LANGUAGE -> "wave";
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
