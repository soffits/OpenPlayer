package dev.soffits.openplayer.runtime.planner;

import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import java.util.Locale;

public final class PlannerPrimitiveProgress {
    private PlannerPrimitiveProgress() {
    }

    public static String format(CommandIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("intent cannot be null");
        }
        return format(intent.kind(), intent.instruction());
    }

    public static String format(IntentKind kind, String instruction) {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        String boundedInstruction = bound(instruction, 96);
        return switch (kind) {
            case COLLECT_ITEMS -> collectItemsProgress(boundedInstruction);
            case BREAK_BLOCK -> "I'll break that block and then check what dropped.";
            case LOCATE_LOADED_BLOCK -> "I'll look nearby for usable blocks.";
            case LOCATE_LOADED_ENTITY -> "I'll look nearby for useful entities.";
            case GOTO, MOVE, FOLLOW_OWNER -> "I'll move into position first.";
            case LOOK -> "I'll look at the target first.";
            case CRAFT -> "I'll try that craft from my current inventory.";
            case DROP_ITEM -> "I'll drop the item once I have it selected.";
            case PLACE_BLOCK -> "I'll place that block and then check the result.";
            case INVENTORY_QUERY -> "I'll check my inventory first.";
            case EQUIP_ITEM -> "I'll select the item I need first.";
            case INTERACT -> "I'll interact with the target and then check what happened.";
            case ATTACK_NEAREST, ATTACK_TARGET -> "I'll handle the nearby threat first.";
            case REPORT_STATUS -> "I'll check what I'm doing right now.";
            case STOP -> "I'll stop the current action first.";
            case PAUSE -> "I'll pause the current action first.";
            case UNPAUSE -> "I'll resume the queued action first.";
            default -> "I'll try the next safe step first.";
        };
    }

    private static String collectItemsProgress(String instruction) {
        String itemName = firstItemName(instruction);
        if (itemName.isBlank()) {
            return "I'll pick up nearby items first.";
        }
        return "I'll pick up nearby " + itemName + " first.";
    }

    private static String firstItemName(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return "";
        }
        String firstToken = instruction.trim().split("\\s+")[0];
        if (firstToken.contains("=") || firstToken.contains("{") || firstToken.contains("}")) {
            return "";
        }
        int namespaceSeparator = firstToken.indexOf(':');
        String path = namespaceSeparator >= 0 ? firstToken.substring(namespaceSeparator + 1) : firstToken;
        String clean = path.replace('_', ' ').replace('-', ' ').toLowerCase(Locale.ROOT).trim();
        if (clean.isBlank() || !clean.matches("[a-z0-9 ]+")) {
            return "";
        }
        if (clean.endsWith("s")) {
            return clean;
        }
        return clean + "s";
    }

    private static String bound(String value, int maxLength) {
        String source = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        if (source.length() <= maxLength) {
            return source;
        }
        return source.substring(0, maxLength);
    }
}
