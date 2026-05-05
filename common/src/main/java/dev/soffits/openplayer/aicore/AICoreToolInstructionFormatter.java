package dev.soffits.openplayer.aicore;

import dev.soffits.openplayer.automation.CollectItemsInstructionParser;
import dev.soffits.openplayer.automation.advanced.AdvancedTaskInstructionParser;
import java.util.Map;

final class AICoreToolInstructionFormatter {
    private AICoreToolInstructionFormatter() {
    }

    static String runtimeInstruction(ToolCall call) {
        if (!call.arguments().instruction().isBlank()) {
            return call.arguments().instruction();
        }
        Map<String, String> values = call.arguments().values();
        if (hasCoordinates(values)) {
            if (call.name().value().equals("activate_block")) {
                return "block " + values.get("x") + " " + values.get("y") + " " + values.get("z");
            }
            return values.get("x") + " " + values.get("y") + " " + values.get("z");
        }
        if (values.containsKey("goal")) {
            return goalInstruction(values.get("goal"));
        }
        if (call.name().equals(MinecraftPrimitiveTools.PICKUP_ITEMS_NEARBY)) {
            return CollectItemsInstructionParser.canonicalInstruction(values.get("matching"), values.get("maxDistance"));
        }
        if (values.containsKey("matching")) {
            return loadedSearchInstruction(values);
        }
        if (call.name().equals(MinecraftPrimitiveTools.DROP_ITEM)) {
            return itemCountInstruction(values.get("itemType"), values.get("count"));
        }
        if (values.containsKey("item")) {
            return values.get("item");
        }
        if (values.containsKey("itemType")) {
            return itemCountInstruction(values.get("itemType"), values.get("count"));
        }
        if (values.containsKey("recipe")) {
            return craftInstruction(values);
        }
        if (values.containsKey("entityId")) {
            if (call.name().value().equals("activate_entity") || call.name().value().equals("activate_entity_at")
                    || call.name().value().equals("use_on_entity")) {
                return "entity " + values.get("entityId") + " " + values.getOrDefault("maxDistance", "4");
            }
            if (call.name().equals(MinecraftPrimitiveTools.ATTACK_TARGET)) {
                String maxDistance = values.getOrDefault("maxDistance", "").trim();
                return (values.get("entityId") + " " + maxDistance).trim();
            }
            return values.get("entityId");
        }
        if (values.containsKey("maxDistance")) {
            return values.get("maxDistance");
        }
        return "";
    }

    private static String loadedSearchInstruction(Map<String, String> values) {
        String maxDistance = values.getOrDefault("maxDistance", "").trim();
        if (maxDistance.isEmpty()) {
            maxDistance = String.valueOf((int) AdvancedTaskInstructionParser.DEFAULT_RADIUS);
        }
        return namespacedMinecraftId(values.get("matching")) + " " + maxDistance;
    }

    private static String itemCountInstruction(String itemType, String count) {
        return ((itemType == null ? "" : itemType) + " " + (count == null ? "" : count)).trim();
    }

    private static String namespacedMinecraftId(String value) {
        if (value == null) {
            return "";
        }
        String trimmedValue = value.trim();
        return trimmedValue.indexOf(':') >= 0 ? trimmedValue : "minecraft:" + trimmedValue;
    }

    private static boolean hasCoordinates(Map<String, String> values) {
        return values.containsKey("x") && values.containsKey("y") && values.containsKey("z");
    }

    private static String goalInstruction(String goalJson) {
        if (goalJson == null || goalJson.isBlank()) {
            return "";
        }
        String type = AICoreJsonFields.stringField(goalJson, "type");
        String x = AICoreJsonFields.numberField(goalJson, "x");
        String y = AICoreJsonFields.numberField(goalJson, "y");
        String z = AICoreJsonFields.numberField(goalJson, "z");
        if ((type.equals("goal_block") || type.equals("goal_near") || type.equals("goal_get_to_block")
                || type.equals("goal_look_at_block") || type.equals("goal_place_block"))
                && !x.isBlank() && !y.isBlank() && !z.isBlank()) {
            return x + " " + y + " " + z;
        }
        if ((type.equals("goal_xz") || type.equals("goal_near_xz")) && !x.isBlank() && !z.isBlank()) {
            return x + " 0 " + z;
        }
        return "";
    }

    private static String craftInstruction(Map<String, String> values) {
        String instruction = (values.get("recipe") + " " + values.getOrDefault("count", "1")).trim();
        String craftingTable = values.get("craftingTable");
        if (craftingTable == null || craftingTable.isBlank()) {
            return instruction;
        }
        String x = AICoreJsonFields.integerField(craftingTable, "x");
        String y = AICoreJsonFields.integerField(craftingTable, "y");
        String z = AICoreJsonFields.integerField(craftingTable, "z");
        return (instruction + " table " + x + " " + y + " " + z).trim();
    }
}
