package dev.soffits.openplayer.automation.work;

import dev.soffits.openplayer.automation.AutomationInstructionParser;

public final class FishingWorkPolicy {
    public static final int DEFAULT_DURATION_TICKS = 20 * 30;
    public static final int MAX_DURATION_TICKS = 20 * 120;

    private FishingWorkPolicy() {
    }

    public static int parseDurationTicksOrNegative(String instruction) {
        if (instruction == null) {
            return -1;
        }
        String trimmedInstruction = instruction.trim();
        if (trimmedInstruction.isEmpty()) {
            return DEFAULT_DURATION_TICKS;
        }
        try {
            double seconds = Double.parseDouble(trimmedInstruction);
            if (!Double.isFinite(seconds) || seconds <= 0.0D) {
                return -1;
            }
            return Math.min((int) Math.ceil(seconds * 20.0D), MAX_DURATION_TICKS);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    public static boolean isStopInstruction(String instruction) {
        if (AutomationInstructionParser.isBlankInstruction(instruction)) {
            return false;
        }
        String trimmedInstruction = instruction.trim();
        return trimmedInstruction.equals("stop") || trimmedInstruction.equals("cancel");
    }
}
