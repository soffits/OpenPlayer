package dev.soffits.openplayer.automation;

import java.util.Locale;

public final class BodyLanguageInstructionParser {
    public static final String USAGE = "BODY_LANGUAGE requires blank, idle, wave, swing, crouch, uncrouch, or look_owner";

    private BodyLanguageInstructionParser() {
    }

    public static BodyLanguageInstruction parseOrNull(String instruction) {
        if (instruction == null || instruction.trim().isEmpty()) {
            return BodyLanguageInstruction.IDLE;
        }
        String normalized = instruction.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "idle" -> BodyLanguageInstruction.IDLE;
            case "wave" -> BodyLanguageInstruction.WAVE;
            case "swing" -> BodyLanguageInstruction.SWING;
            case "crouch" -> BodyLanguageInstruction.CROUCH;
            case "uncrouch" -> BodyLanguageInstruction.UNCROUCH;
            case "look_owner" -> BodyLanguageInstruction.LOOK_OWNER;
            default -> null;
        };
    }
}
