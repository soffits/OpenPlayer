package dev.soffits.openplayer.intent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class ProviderPlanIntentCodec {
    public static final int MAX_STEPS = 5;
    public static final int MAX_ENCODED_LENGTH = 4096;

    private ProviderPlanIntentCodec() {
    }

    public static CommandIntent encode(List<CommandIntent> steps, IntentPriority priority) throws IntentParseException {
        if (steps == null) {
            throw new IntentParseException("provider plan steps cannot be null");
        }
        if (priority == null) {
            throw new IntentParseException("provider plan priority cannot be null");
        }
        if (steps.isEmpty()) {
            throw new IntentParseException("provider plan JSON must contain at least one executable tool");
        }
        if (steps.size() > MAX_STEPS) {
            throw new IntentParseException("provider plan step count is out of bounds");
        }
        StringBuilder builder = new StringBuilder();
        for (CommandIntent step : steps) {
            if (step == null) {
                throw new IntentParseException("provider plan contains a null step");
            }
            if (step.kind() == IntentKind.PROVIDER_PLAN || step.kind() == IntentKind.CHAT
                    || step.kind() == IntentKind.UNAVAILABLE || step.kind() == IntentKind.OBSERVE) {
                throw new IntentParseException("provider plan contains a non-executable primitive tool");
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(step.kind().name())
                    .append('\t')
                    .append(step.priority().name())
                    .append('\t')
                    .append(Base64.getEncoder().encodeToString(step.instruction().getBytes(StandardCharsets.UTF_8)));
        }
        String instruction = builder.toString();
        if (instruction.length() > MAX_ENCODED_LENGTH) {
            throw new IntentParseException("provider plan encoded instruction is too large");
        }
        return new CommandIntent(IntentKind.PROVIDER_PLAN, priority, instruction);
    }

    public static List<CommandIntent> decode(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("provider plan instruction cannot be blank");
        }
        if (instruction.length() > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException("provider plan encoded instruction is too large");
        }
        String[] lines = instruction.split("\\n", -1);
        if (lines.length == 0 || lines.length > MAX_STEPS) {
            throw new IllegalArgumentException("provider plan step count is out of bounds");
        }
        ArrayList<CommandIntent> steps = new ArrayList<>(lines.length);
        for (String line : lines) {
            if (line.isBlank()) {
                throw new IllegalArgumentException("provider plan step cannot be blank");
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length != 3) {
                throw new IllegalArgumentException("provider plan step encoding is invalid");
            }
            IntentKind kind = parseKind(fields[0]);
            if (kind == IntentKind.PROVIDER_PLAN || kind == IntentKind.CHAT
                    || kind == IntentKind.UNAVAILABLE || kind == IntentKind.OBSERVE) {
                throw new IllegalArgumentException("provider plan contains a non-executable primitive tool");
            }
            IntentPriority priority = parsePriority(fields[1]);
            String stepInstruction = new String(Base64.getDecoder().decode(fields[2]), StandardCharsets.UTF_8);
            steps.add(new CommandIntent(kind, priority, stepInstruction));
        }
        return List.copyOf(steps);
    }

    private static IntentKind parseKind(String value) {
        try {
            return IntentKind.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("provider plan step kind is invalid", exception);
        }
    }

    private static IntentPriority parsePriority(String value) {
        try {
            return IntentPriority.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("provider plan step priority is invalid", exception);
        }
    }
}
