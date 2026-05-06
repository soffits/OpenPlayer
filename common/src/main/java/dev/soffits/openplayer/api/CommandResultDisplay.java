package dev.soffits.openplayer.api;

public record CommandResultDisplay(String translationKey, String[] args, boolean companionChat) {
    private static final String STATUS_KEY_PREFIX = "commands.openplayer.result.status.";

    public CommandResultDisplay {
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

    public static CommandResultDisplay status(CommandSubmissionStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        return translatable(STATUS_KEY_PREFIX + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static CommandResultDisplay translatable(String translationKey, String... args) {
        return new CommandResultDisplay(translationKey, args, false);
    }

    public static CommandResultDisplay companionChat(String translationKey, String... args) {
        return new CommandResultDisplay(translationKey, args, true);
    }

    @Override
    public String[] args() {
        return args.clone();
    }
}
