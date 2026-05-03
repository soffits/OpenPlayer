package dev.soffits.openplayer.runtime.validation;

import dev.soffits.openplayer.automation.AutomationInstructionParser;
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
            case LOOK -> requireCoordinateInstruction(intent, "LOOK");
            case PATROL -> requireCoordinateInstruction(intent, "PATROL");
            case FOLLOW_OWNER -> requireBlankInstruction(intent, "FOLLOW_OWNER");
            case COLLECT_ITEMS -> requireBlankInstruction(intent, "COLLECT_ITEMS");
            case EQUIP_BEST_ITEM -> requireBlankInstruction(intent, "EQUIP_BEST_ITEM");
            case EQUIP_ARMOR -> requireBlankInstruction(intent, "EQUIP_ARMOR");
            case USE_SELECTED_ITEM -> requireBlankInstruction(intent, "USE_SELECTED_ITEM");
            case SWAP_TO_OFFHAND -> requireBlankInstruction(intent, "SWAP_TO_OFFHAND");
            case DROP_ITEM -> requireBlankInstruction(intent, "DROP_ITEM");
            case BREAK_BLOCK -> requireCoordinateInstruction(intent, "BREAK_BLOCK");
            case PLACE_BLOCK -> requireCoordinateInstruction(intent, "PLACE_BLOCK");
            case ATTACK_NEAREST -> requireBlankOrPositiveRadius(intent, "ATTACK_NEAREST");
            case GUARD_OWNER -> requireBlankOrPositiveRadius(intent, "GUARD_OWNER");
            case INTERACT -> RuntimeIntentValidationResult.rejected("INTERACT is not implemented by the vanilla runtime");
            case CHAT -> RuntimeIntentValidationResult.rejected("CHAT cannot be submitted to automation");
            case UNAVAILABLE -> RuntimeIntentValidationResult.rejected("UNAVAILABLE cannot be submitted to automation");
            case OBSERVE -> RuntimeIntentValidationResult.rejected("OBSERVE cannot be submitted to automation");
            case GOTO,
                    INVENTORY_QUERY,
                    EQUIP_ITEM,
                    GIVE_ITEM,
                    DEPOSIT_ITEM,
                    STASH_ITEM,
                    GET_ITEM,
                    COLLECT_FOOD,
                    FARM_NEARBY,
                    FISH,
                    ATTACK_TARGET,
                    DEFEND_OWNER,
                    PAUSE,
                    UNPAUSE,
                    RESET_MEMORY,
                    BODY_LANGUAGE,
                    BUILD_STRUCTURE -> rejectUnimplemented(kind);
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
