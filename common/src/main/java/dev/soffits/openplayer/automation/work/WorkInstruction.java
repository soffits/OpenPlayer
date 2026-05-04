package dev.soffits.openplayer.automation.work;

public record WorkInstruction(double value, int repeatCount) {
    public WorkInstruction {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("value must be finite and non-negative");
        }
        if (repeatCount < 1 || repeatCount > WorkRepeatPolicy.MAX_REPEAT_COUNT) {
            throw new IllegalArgumentException("repeatCount must be within the bounded repeat cap");
        }
    }
}
