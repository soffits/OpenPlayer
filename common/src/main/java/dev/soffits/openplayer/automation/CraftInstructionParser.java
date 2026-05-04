package dev.soffits.openplayer.automation;

import net.minecraft.resources.ResourceLocation;

public final class CraftInstructionParser {
    public static final String USAGE = "CRAFT requires instruction: <recipe_id> [count] [table <x> <y> <z>]";

    private CraftInstructionParser() {
    }

    public static CraftInstruction parseOrNull(String instruction) {
        if (instruction == null) {
            return null;
        }
        String trimmed = instruction.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length != 1 && parts.length != 2 && parts.length != 6) {
            return null;
        }
        ResourceLocation recipeId = ResourceLocation.tryParse(parts[0]);
        if (recipeId == null || !parts[0].contains(":")) {
            return null;
        }
        int count;
        if (parts.length == 1) {
            count = 1;
        } else if (isUnsignedInteger(parts[1])) {
            try {
                count = Integer.parseInt(parts[1]);
            } catch (NumberFormatException exception) {
                return null;
            }
        } else {
            return null;
        }
        if (count < 1 || count > 256) {
            return null;
        }
        if (parts.length != 6) {
            return new CraftInstruction(recipeId, count, null);
        }
        if (!parts[2].equals("table")) {
            return null;
        }
        if (!isSignedInteger(parts[3]) || !isSignedInteger(parts[4]) || !isSignedInteger(parts[5])) {
            return null;
        }
        try {
            return new CraftInstruction(recipeId, count, new CraftingTablePosition(
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]),
                    Integer.parseInt(parts[5])
            ));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean isUnsignedInteger(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSignedInteger(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) {
            return false;
        }
        for (int index = start; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    public record CraftInstruction(ResourceLocation recipeId, int count, CraftingTablePosition craftingTablePos) {
    }

    public record CraftingTablePosition(int x, int y, int z) {
    }
}
