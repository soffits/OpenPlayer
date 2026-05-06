package dev.soffits.openplayer.runtime.planner;

import dev.soffits.openplayer.intent.CommandIntent;
import dev.soffits.openplayer.intent.IntentKind;
import java.util.Locale;

public final class PlannerPrimitiveProgress {
    private static final String KEY_PREFIX = "commands.openplayer.progress.primitive.started.";

    private PlannerPrimitiveProgress() {
    }

    public static Display fallback(CommandIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("intent cannot be null");
        }
        return fallback(intent.kind(), intent.instruction());
    }

    public static Display fallback(IntentKind kind, String instruction) {
        if (kind == null) {
            throw new IllegalArgumentException("kind cannot be null");
        }
        if (kind == IntentKind.COLLECT_ITEMS) {
            String itemName = firstItemName(bound(instruction, 96));
            if (!itemName.isBlank()) {
                return new Display(KEY_PREFIX + "collect_items.item", new String[] { itemName });
            }
        }
        return new Display(KEY_PREFIX + category(kind), new String[0]);
    }

    private static String category(IntentKind kind) {
        return switch (kind) {
            case MOVE, GOTO, FOLLOW_OWNER, GUARD_OWNER, PATROL -> "move";
            case LOOK -> "look";
            case COLLECT_ITEMS -> "collect_items";
            case SWAP_TO_OFFHAND, DROP_ITEM, INVENTORY_QUERY, EQUIP_ITEM, GIVE_ITEM, DEPOSIT_ITEM, STASH_ITEM,
                    WITHDRAW_ITEM, CRAFT -> "inventory";
            case PLACE_BLOCK -> "place_block";
            case BREAK_BLOCK -> "break_block";
            case INTERACT -> "interact";
            case ATTACK_NEAREST, ATTACK_TARGET -> "combat";
            case REPORT_STATUS, OBSERVE, UNAVAILABLE, RESET_MEMORY, BODY_LANGUAGE, LOCATE_LOADED_BLOCK,
                    LOCATE_LOADED_ENTITY, FIND_LOADED_BIOME -> "status";
            case PAUSE, UNPAUSE -> "pause";
            case STOP -> "stop";
            case CHAT, PROVIDER_PLAN -> "default";
        };
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

    public record Display(String translationKey, String[] args) {
        public Display {
            if (translationKey == null || translationKey.isBlank()) {
                throw new IllegalArgumentException("translationKey cannot be blank");
            }
            args = args == null ? new String[0] : args.clone();
            for (String arg : args) {
                if (arg == null) {
                    throw new IllegalArgumentException("args cannot contain null");
                }
            }
        }

        @Override
        public String[] args() {
            return args.clone();
        }
    }
}
