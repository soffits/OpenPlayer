package dev.soffits.openplayer.automation.work;

public final class WorkRepeatPolicy {
    public static final int DEFAULT_REPEAT_COUNT = 1;
    public static final int MAX_REPEAT_COUNT = 5;

    private WorkRepeatPolicy() {
    }

    public static WorkInstruction parseRadiusInstructionOrNull(
            String instruction,
            double defaultRadius,
            double maxRadius
    ) {
        return parseValuedInstructionOrNull(instruction, "radius", defaultRadius, maxRadius, true);
    }

    public static WorkInstruction parseDurationSecondsInstructionOrNull(
            String instruction,
            double defaultSeconds,
            double maxSeconds
    ) {
        return parseValuedInstructionOrNull(instruction, "duration", defaultSeconds, maxSeconds, true);
    }

    public static InventoryRepeatInstruction parseInventoryRepeatInstructionOrNull(String instruction) {
        if (instruction == null) {
            return null;
        }
        String trimmed = instruction.trim();
        if (trimmed.isEmpty()) {
            return new InventoryRepeatInstruction("", DEFAULT_REPEAT_COUNT);
        }
        String[] tokens = trimmed.split("\\s+");
        StringBuilder itemInstruction = new StringBuilder();
        int repeatCount = DEFAULT_REPEAT_COUNT;
        boolean repeatSeen = false;
        for (String token : tokens) {
            if (token.startsWith("repeat=")) {
                if (repeatSeen) {
                    return null;
                }
                repeatCount = parseRepeatOrNegative(token.substring("repeat=".length()));
                if (repeatCount < 1) {
                    return null;
                }
                repeatSeen = true;
                continue;
            }
            if (token.indexOf('=') >= 0) {
                return null;
            }
            if (repeatSeen) {
                return null;
            }
            if (itemInstruction.length() > 0) {
                itemInstruction.append(' ');
            }
            itemInstruction.append(token);
        }
        return new InventoryRepeatInstruction(itemInstruction.toString(), repeatCount);
    }

    public static int parseRepeatOrNegative(String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return -1;
            }
        }
        try {
            int repeatCount = Integer.parseInt(value);
            if (repeatCount < 1 || repeatCount > MAX_REPEAT_COUNT) {
                return -1;
            }
            return repeatCount;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    public static boolean shouldQueueNextRepeat(boolean allowRepeat, int repeatRemaining, boolean allowWorldActions) {
        return allowRepeat && repeatRemaining > 1 && allowWorldActions;
    }

    private static WorkInstruction parseValuedInstructionOrNull(
            String instruction,
            String valueKey,
            double defaultValue,
            double maxValue,
            boolean allowCountAlias
    ) {
        if (instruction == null || !Double.isFinite(defaultValue) || !Double.isFinite(maxValue)
                || defaultValue <= 0.0D || maxValue <= 0.0D) {
            return null;
        }
        String trimmed = instruction.trim();
        if (trimmed.isEmpty()) {
            return new WorkInstruction(defaultValue, DEFAULT_REPEAT_COUNT);
        }
        String[] tokens = trimmed.split("\\s+");
        Double value = null;
        int repeatCount = DEFAULT_REPEAT_COUNT;
        boolean valueSeen = false;
        boolean repeatSeen = false;
        for (String token : tokens) {
            int separator = token.indexOf('=');
            if (separator < 0) {
                if (valueSeen || tokens.length > 1) {
                    return null;
                }
                value = parsePositiveFiniteDoubleOrNull(token);
                if (value == null) {
                    return null;
                }
                valueSeen = true;
                continue;
            }
            if (separator == 0 || separator == token.length() - 1) {
                return null;
            }
            String key = token.substring(0, separator);
            String rawValue = token.substring(separator + 1);
            if (key.equals(valueKey)) {
                if (valueSeen) {
                    return null;
                }
                value = parsePositiveFiniteDoubleOrNull(rawValue);
                if (value == null) {
                    return null;
                }
                valueSeen = true;
                continue;
            }
            if (key.equals("repeat") || allowCountAlias && key.equals("count")) {
                if (repeatSeen) {
                    return null;
                }
                repeatCount = parseRepeatOrNegative(rawValue);
                if (repeatCount < 1) {
                    return null;
                }
                repeatSeen = true;
                continue;
            }
            return null;
        }
        return new WorkInstruction(Math.min(value == null ? defaultValue : value, maxValue), repeatCount);
    }

    private static Double parsePositiveFiniteDoubleOrNull(String value) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed <= 0.0D) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record InventoryRepeatInstruction(String itemInstruction, int repeatCount) {
        public InventoryRepeatInstruction {
            if (itemInstruction == null) {
                throw new IllegalArgumentException("itemInstruction cannot be null");
            }
            if (repeatCount < 1 || repeatCount > MAX_REPEAT_COUNT) {
                throw new IllegalArgumentException("repeatCount must be within the bounded repeat cap");
            }
        }
    }
}
