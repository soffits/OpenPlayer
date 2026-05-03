package dev.soffits.openplayer.runtime.validation;

import dev.soffits.openplayer.automation.AutomationInstructionParser;
import dev.soffits.openplayer.automation.building.BuildPlanParser;
import dev.soffits.openplayer.automation.work.FishingWorkPolicy;
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
            case FARM_NEARBY -> requireBlankOrPositiveRadius(intent, "FARM_NEARBY");
            case BUILD_STRUCTURE -> requireBuildStructureInstruction(intent);
            case FISH -> requireFishInstruction(intent);
            case DEFEND_OWNER -> requireBlankOrPositiveRadius(intent, "DEFEND_OWNER");
            case INVENTORY_QUERY -> requireBlankInstruction(intent, "INVENTORY_QUERY");
            case EQUIP_ITEM -> requireItemOnlyInstruction(intent, "EQUIP_ITEM");
            case GIVE_ITEM -> requireGiveItemInstruction(intent);
            case DEPOSIT_ITEM, STASH_ITEM -> requireBlankOrItemCountInstruction(intent, kind.name());
            case WITHDRAW_ITEM -> requireItemCountInstruction(intent, "WITHDRAW_ITEM");
            case GET_ITEM -> requireItemCountInstruction(intent, "GET_ITEM");
            case SMELT_ITEM -> requireItemCountInstruction(intent, "SMELT_ITEM");
            case INTERACT -> RuntimeIntentValidationResult.rejected("INTERACT is not implemented by the vanilla runtime");
            case CHAT -> RuntimeIntentValidationResult.rejected("CHAT cannot be submitted to automation");
            case UNAVAILABLE -> RuntimeIntentValidationResult.rejected("UNAVAILABLE cannot be submitted to automation");
            case OBSERVE -> RuntimeIntentValidationResult.rejected("OBSERVE cannot be submitted to automation");
            case ATTACK_TARGET,
                    PAUSE,
                    UNPAUSE,
                    RESET_MEMORY,
                    BODY_LANGUAGE -> rejectUnimplemented(kind);
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
                || FishingWorkPolicy.parseDurationTicksOrNegative(intent.instruction()) >= 0) {
            return RuntimeIntentValidationResult.accepted();
        }
        return RuntimeIntentValidationResult.rejected(
                "FISH instruction must be blank, stop, cancel, or a positive duration in seconds"
        );
    }

    private static RuntimeIntentValidationResult requireBuildStructureInstruction(CommandIntent intent) {
        if (BuildPlanParser.parseOrNull(intent.instruction()) == null) {
            return RuntimeIntentValidationResult.rejected(BuildPlanParser.USAGE);
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
