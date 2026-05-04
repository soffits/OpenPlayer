package dev.soffits.openplayer.runtime.validation;

import dev.soffits.openplayer.automation.AutomationInstructionParser;
import dev.soffits.openplayer.automation.BodyLanguageInstructionParser;
import dev.soffits.openplayer.automation.InteractionInstruction;
import dev.soffits.openplayer.automation.InteractionInstructionParser;
import dev.soffits.openplayer.automation.TargetAttackInstructionParser;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskInstructionParser;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskPolicy;
import dev.soffits.openplayer.automation.building.BuildPlanParser;
import dev.soffits.openplayer.automation.work.FishingWorkPolicy;
import dev.soffits.openplayer.automation.work.FarmingWorkPolicy;
import dev.soffits.openplayer.automation.work.WorkRepeatPolicy;
import dev.soffits.openplayer.automation.InventoryActionInstructionParser;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;

public final class RuntimeIntentValidator {
    private RuntimeIntentValidator() {
    }

    public static RuntimeIntentValidationResult validate(CommandIntent intent, boolean allowWorldActions) {
        if (intent == null) {
            return RuntimeIntentValidationResult.rejected("Runtime intent cannot be null");
        }
        IntentKind kind = intent.kind();
        if (RuntimeIntentPolicies.isLocalWorldOrInventoryAction(kind) && !allowWorldActions) {
            return RuntimeIntentValidationResult.rejected("World actions are disabled for this OpenPlayer character");
        }
        return switch (kind) {
            case STOP -> requireBlankInstruction(intent, "STOP");
            case PAUSE -> requireBlankInstruction(intent, "PAUSE");
            case UNPAUSE -> requireBlankInstruction(intent, "UNPAUSE");
            case RESET_MEMORY -> requireBlankInstruction(intent, "RESET_MEMORY");
            case REPORT_STATUS -> requireBlankInstruction(intent, "REPORT_STATUS");
            case MOVE -> requireCoordinateInstruction(intent, "MOVE");
            case GOTO -> requireGotoInstruction(intent);
            case LOOK -> requireCoordinateInstruction(intent, "LOOK");
            case PATROL -> requireCoordinateInstruction(intent, "PATROL");
            case FOLLOW_OWNER -> requireBlankInstruction(intent, "FOLLOW_OWNER");
            case COLLECT_ITEMS -> requireBlankInstruction(intent, "COLLECT_ITEMS");
            case EQUIP_BEST_ITEM -> requireBlankInstruction(intent, "EQUIP_BEST_ITEM");
            case EQUIP_ARMOR -> requireBlankInstruction(intent, "EQUIP_ARMOR");
            case USE_SELECTED_ITEM -> requireBlankInstruction(intent, "USE_SELECTED_ITEM");
            case SWAP_TO_OFFHAND -> requireBlankInstruction(intent, "SWAP_TO_OFFHAND");
            case DROP_ITEM -> requireBlankOrItemCountInstruction(intent, "DROP_ITEM");
            case BREAK_BLOCK -> requireCoordinateInstruction(intent, "BREAK_BLOCK");
            case PLACE_BLOCK -> requireCoordinateInstruction(intent, "PLACE_BLOCK");
            case ATTACK_NEAREST -> requireBlankOrPositiveRadius(intent, "ATTACK_NEAREST");
            case GUARD_OWNER -> requireBlankOrPositiveRadius(intent, "GUARD_OWNER");
            case COLLECT_FOOD -> requireBlankOrPositiveRadius(intent, "COLLECT_FOOD");
            case FARM_NEARBY -> requireRepeatableRadiusInstruction(intent, "FARM_NEARBY");
            case BUILD_STRUCTURE -> requireBuildStructureInstruction(intent);
            case LOCATE_LOADED_BLOCK -> requireLoadedSearchInstruction(
                    intent, AdvancedTaskInstructionParser.LOCATE_LOADED_BLOCK_USAGE
            );
            case LOCATE_LOADED_ENTITY -> requireLoadedSearchInstruction(
                    intent, AdvancedTaskInstructionParser.LOCATE_LOADED_ENTITY_USAGE
            );
            case FIND_LOADED_BIOME -> requireLoadedSearchInstruction(
                    intent, AdvancedTaskInstructionParser.FIND_LOADED_BIOME_USAGE
            );
            case EXPLORE_CHUNKS -> requireExploreChunksInstruction(intent);
            case LOCATE_STRUCTURE -> requireLocateStructureInstruction(intent);
            case USE_PORTAL -> requireUsePortalInstruction(intent);
            case TRAVEL_NETHER -> requireTravelNetherInstruction(intent);
            case LOCATE_STRONGHOLD,
                    END_GAME_TASK -> RuntimeIntentValidationResult.rejected(AdvancedTaskPolicy.unsupportedReason(kind));
            case FISH -> requireFishInstruction(intent);
            case DEFEND_OWNER -> requireBlankOrPositiveRadius(intent, "DEFEND_OWNER");
            case INVENTORY_QUERY -> requireBlankInstruction(intent, "INVENTORY_QUERY");
            case EQUIP_ITEM -> requireItemOnlyInstruction(intent, "EQUIP_ITEM");
            case GIVE_ITEM -> requireGiveItemInstruction(intent);
            case DEPOSIT_ITEM, STASH_ITEM -> requireBlankOrItemCountRepeatInstruction(intent, kind.name());
            case WITHDRAW_ITEM -> requireItemCountInstruction(intent, "WITHDRAW_ITEM");
            case GET_ITEM -> requireItemCountInstruction(intent, "GET_ITEM");
            case SMELT_ITEM -> requireItemCountInstruction(intent, "SMELT_ITEM");
            case INTERACT -> requireInteractInstruction(intent);
            case BODY_LANGUAGE -> requireBodyLanguageInstruction(intent);
            case CHAT -> RuntimeIntentValidationResult.rejected("CHAT cannot be submitted to automation");
            case UNAVAILABLE -> RuntimeIntentValidationResult.rejected("UNAVAILABLE cannot be submitted to automation");
            case OBSERVE -> RuntimeIntentValidationResult.rejected("OBSERVE cannot be submitted to automation");
            case ATTACK_TARGET -> requireAttackTargetInstruction(intent);
        };
    }

    private static RuntimeIntentValidationResult rejectUnimplemented(IntentKind kind) {
        return RuntimeIntentValidationResult.rejected(kind.name() + " is not implemented by the vanilla runtime");
    }

    private static RuntimeIntentValidationResult requireBlankInstruction(CommandIntent intent, String kindName) {
        if (!AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
            return RuntimeIntentValidationResult.rejected(kindName + " requires a blank instruction");
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireCoordinateInstruction(CommandIntent intent, String kindName) {
        if (AutomationInstructionParser.parseCoordinateOrNull(intent.instruction()) == null) {
            return RuntimeIntentValidationResult.rejected(kindName + " requires instruction: x y z");
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireGotoInstruction(CommandIntent intent) {
        if (AutomationInstructionParser.parseGotoInstructionOrNull(intent.instruction(), 16.0D, 32.0D) == null) {
            return RuntimeIntentValidationResult.rejected(
                    "GOTO requires instruction: x y z, owner, block <block_or_item_id> [radius], or entity <entity_type_id> [radius]"
            );
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireItemOnlyInstruction(CommandIntent intent, String kindName) {
        if (InventoryActionInstructionParser.parseItemOnlyOrNull(intent.instruction()) == null) {
            return RuntimeIntentValidationResult.rejected(kindName + " requires instruction: <item_id>");
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireBlankOrItemCountInstruction(CommandIntent intent, String kindName) {
        if (AutomationInstructionParser.isBlankInstruction(intent.instruction())) {
            return RuntimeIntentValidationResult.accepted();
        }
        if (InventoryActionInstructionParser.parseItemCountOrNull(intent.instruction(), false) == null) {
            return RuntimeIntentValidationResult.rejected(kindName + " requires blank or instruction: <item_id> [count]");
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireBlankOrItemCountRepeatInstruction(
            CommandIntent intent,
            String kindName
    ) {
        WorkRepeatPolicy.InventoryRepeatInstruction repeatInstruction = WorkRepeatPolicy
                .parseInventoryRepeatInstructionOrNull(intent.instruction());
        if (repeatInstruction == null) {
            return RuntimeIntentValidationResult.rejected(kindName
                    + " requires blank or instruction: <item_id> [count] [repeat=1.."
                    + WorkRepeatPolicy.MAX_REPEAT_COUNT + "]");
        }
        if (AutomationInstructionParser.isBlankInstruction(repeatInstruction.itemInstruction())) {
            return RuntimeIntentValidationResult.accepted();
        }
        if (InventoryActionInstructionParser.parseItemCountOrNull(repeatInstruction.itemInstruction(), false) == null) {
            return RuntimeIntentValidationResult.rejected(kindName
                    + " requires blank or instruction: <item_id> [count] [repeat=1.."
                    + WorkRepeatPolicy.MAX_REPEAT_COUNT + "]");
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireItemCountInstruction(CommandIntent intent, String kindName) {
        if (InventoryActionInstructionParser.parseItemCountOrNull(intent.instruction(), false) == null) {
            return RuntimeIntentValidationResult.rejected(kindName + " requires instruction: <item_id> [count]");
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireGiveItemInstruction(CommandIntent intent) {
        if (InventoryActionInstructionParser.parseItemCountOrNull(intent.instruction(), true) == null) {
            return RuntimeIntentValidationResult.rejected("GIVE_ITEM requires instruction: <item_id> [count] [owner]");
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireFishInstruction(CommandIntent intent) {
        if (FishingWorkPolicy.isStopInstruction(intent.instruction())
                || WorkRepeatPolicy.parseDurationSecondsInstructionOrNull(
                intent.instruction(),
                FishingWorkPolicy.DEFAULT_DURATION_TICKS / 20.0D,
                FishingWorkPolicy.MAX_DURATION_TICKS / 20.0D
        ) != null) {
            return RuntimeIntentValidationResult.accepted();
        }
        return RuntimeIntentValidationResult.rejected(
                "FISH instruction must be blank, stop, cancel, a positive duration in seconds, or duration=<seconds> repeat=1.."
                        + WorkRepeatPolicy.MAX_REPEAT_COUNT
        );
    }

    private static RuntimeIntentValidationResult requireRepeatableRadiusInstruction(CommandIntent intent, String kindName) {
        if (WorkRepeatPolicy.parseRadiusInstructionOrNull(
                intent.instruction(), FarmingWorkPolicy.DEFAULT_RADIUS, FarmingWorkPolicy.MAX_RADIUS
        ) == null) {
            return RuntimeIntentValidationResult.rejected(kindName
                    + " instruction must be blank, a positive radius number, or radius=<blocks> repeat=1.."
                    + WorkRepeatPolicy.MAX_REPEAT_COUNT);
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireBodyLanguageInstruction(CommandIntent intent) {
        if (BodyLanguageInstructionParser.parseOrNull(intent.instruction()) == null) {
            return RuntimeIntentValidationResult.rejected(BodyLanguageInstructionParser.USAGE);
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireInteractInstruction(CommandIntent intent) {
        InteractionInstruction instruction = InteractionInstructionParser.parseOrNull(intent.instruction());
        if (instruction == null) {
            return RuntimeIntentValidationResult.rejected(InteractionInstructionParser.USAGE);
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireAttackTargetInstruction(CommandIntent intent) {
        if (TargetAttackInstructionParser.parseOrNull(intent.instruction()) == null) {
            return RuntimeIntentValidationResult.rejected(TargetAttackInstructionParser.USAGE);
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireBuildStructureInstruction(CommandIntent intent) {
        if (BuildPlanParser.parseOrNull(intent.instruction()) == null) {
            return RuntimeIntentValidationResult.rejected(BuildPlanParser.USAGE);
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireLoadedSearchInstruction(CommandIntent intent, String usage) {
        if (AdvancedTaskInstructionParser.parseLoadedSearchOrNull(intent.instruction()) == null) {
            return RuntimeIntentValidationResult.rejected(usage);
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireExploreChunksInstruction(CommandIntent intent) {
        if (AdvancedTaskInstructionParser.parseExploreChunksOrNull(intent.instruction()) == null) {
            return RuntimeIntentValidationResult.rejected(AdvancedTaskInstructionParser.EXPLORE_CHUNKS_USAGE);
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireLocateStructureInstruction(CommandIntent intent) {
        if (AdvancedTaskInstructionParser.parseLocateStructureOrNull(intent.instruction()) == null) {
            return RuntimeIntentValidationResult.rejected(AdvancedTaskInstructionParser.LOCATE_STRUCTURE_USAGE);
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireUsePortalInstruction(CommandIntent intent) {
        if (AdvancedTaskInstructionParser.parseUsePortalOrNull(intent.instruction()) == null) {
            return RuntimeIntentValidationResult.rejected(AdvancedTaskInstructionParser.USE_PORTAL_USAGE);
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireTravelNetherInstruction(CommandIntent intent) {
        if (AdvancedTaskInstructionParser.parseTravelNetherOrNull(intent.instruction()) == null) {
            return RuntimeIntentValidationResult.rejected(AdvancedTaskInstructionParser.TRAVEL_NETHER_USAGE);
        }
        return RuntimeIntentValidationResult.accepted();
    }

    private static RuntimeIntentValidationResult requireBlankOrPositiveRadius(CommandIntent intent, String kindName) {
        String instruction = intent.instruction().trim();
        if (instruction.isEmpty()) {
            return RuntimeIntentValidationResult.accepted();
        }
        try {
            double radius = Double.parseDouble(instruction);
            if (!Double.isFinite(radius) || radius <= 0.0D) {
                return RuntimeIntentValidationResult.rejected(
                        kindName + " instruction must be blank or a positive radius number"
                );
            }
            return RuntimeIntentValidationResult.accepted();
        } catch (NumberFormatException exception) {
            return RuntimeIntentValidationResult.rejected(
                    kindName + " instruction must be blank or a positive radius number"
            );
        }
    }
}
