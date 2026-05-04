package dev.soffits.openplayer.runtime.validation;

import dev.soffits.openplayer.automation.AutomationInstructionParser;
import dev.soffits.openplayer.automation.BodyLanguageInstructionParser;
import dev.soffits.openplayer.automation.InteractionInstruction;
import dev.soffits.openplayer.automation.InteractionInstructionParser;
import dev.soffits.openplayer.automation.TargetAttackInstructionParser;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskInstructionParser;
import dev.soffits.openplayer.automation.work.WorkRepeatPolicy;
import dev.soffits.openplayer.automation.InventoryActionInstructionParser;
import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import dev.soffits.openplayer.intent.ProviderPlanIntentCodec;
import java.util.List;

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
            case GOTO -> requireCoordinateInstruction(intent, "GOTO");
            case LOOK -> requireCoordinateInstruction(intent, "LOOK");
            case PATROL -> requireCoordinateInstruction(intent, "PATROL");
            case FOLLOW_OWNER -> requireBlankInstruction(intent, "FOLLOW_OWNER");
            case COLLECT_ITEMS -> requireBlankInstruction(intent, "COLLECT_ITEMS");
            case SWAP_TO_OFFHAND -> requireBlankInstruction(intent, "SWAP_TO_OFFHAND");
            case DROP_ITEM -> requireBlankOrItemCountInstruction(intent, "DROP_ITEM");
            case BREAK_BLOCK -> requireCoordinateInstruction(intent, "BREAK_BLOCK");
            case PLACE_BLOCK -> requireCoordinateInstruction(intent, "PLACE_BLOCK");
            case ATTACK_NEAREST -> requireBlankOrPositiveRadius(intent, "ATTACK_NEAREST");
            case GUARD_OWNER -> requireBlankOrPositiveRadius(intent, "GUARD_OWNER");
            case LOCATE_LOADED_BLOCK -> requireLoadedSearchInstruction(
                    intent, AdvancedTaskInstructionParser.LOCATE_LOADED_BLOCK_USAGE
            );
            case LOCATE_LOADED_ENTITY -> requireLoadedSearchInstruction(
                    intent, AdvancedTaskInstructionParser.LOCATE_LOADED_ENTITY_USAGE
            );
            case FIND_LOADED_BIOME -> requireLoadedSearchInstruction(
                    intent, AdvancedTaskInstructionParser.FIND_LOADED_BIOME_USAGE
            );
            case INVENTORY_QUERY -> requireBlankInstruction(intent, "INVENTORY_QUERY");
            case EQUIP_ITEM -> requireItemOnlyInstruction(intent, "EQUIP_ITEM");
            case GIVE_ITEM -> requireGiveItemInstruction(intent);
            case DEPOSIT_ITEM, STASH_ITEM -> requireBlankOrItemCountRepeatInstruction(intent, kind.name());
            case WITHDRAW_ITEM -> requireItemCountInstruction(intent, "WITHDRAW_ITEM");
            case INTERACT -> requireInteractInstruction(intent);
            case BODY_LANGUAGE -> requireBodyLanguageInstruction(intent);
            case CHAT -> RuntimeIntentValidationResult.rejected("CHAT cannot be submitted to automation");
            case UNAVAILABLE -> RuntimeIntentValidationResult.rejected("UNAVAILABLE cannot be submitted to automation");
            case OBSERVE -> RuntimeIntentValidationResult.rejected("OBSERVE cannot be submitted to automation");
            case ATTACK_TARGET -> requireAttackTargetInstruction(intent);
            case PROVIDER_PLAN -> requireProviderPlanInstruction(intent, allowWorldActions);
        };
    }

    private static RuntimeIntentValidationResult requireProviderPlanInstruction(
            CommandIntent intent,
            boolean allowWorldActions
    ) {
        List<CommandIntent> steps;
        try {
            steps = ProviderPlanIntentCodec.decode(intent.instruction());
        } catch (IllegalArgumentException exception) {
            return RuntimeIntentValidationResult.rejected(exception.getMessage());
        }
        for (CommandIntent step : steps) {
            RuntimeIntentValidationResult validation = validate(step, allowWorldActions);
            if (!validation.isAccepted()) {
                return RuntimeIntentValidationResult.rejected("PROVIDER_PLAN step rejected: " + validation.message());
            }
        }
        return RuntimeIntentValidationResult.accepted();
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

    private static RuntimeIntentValidationResult requireLoadedSearchInstruction(CommandIntent intent, String usage) {
        if (AdvancedTaskInstructionParser.parseLoadedSearchOrNull(intent.instruction()) == null) {
            return RuntimeIntentValidationResult.rejected(usage);
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
