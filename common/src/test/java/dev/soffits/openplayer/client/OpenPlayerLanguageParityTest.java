package dev.soffits.openplayer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpenPlayerLanguageParityTest {
    private static final Pattern ENTRY = Pattern.compile("\\s*\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:[^\\\\\\\"]|\\\\.)*)\\\"[,}]?");
    private static final Pattern PLACEHOLDER = Pattern.compile("%s");

    private OpenPlayerLanguageParityTest() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> english = load("en_us.json");
        Map<String, String> japanese = load("ja_jp.json");
        Map<String, String> french = load("fr_fr.json");

        require(english.keySet().equals(japanese.keySet()), "ja_jp language keys must match en_us");
        require(english.keySet().equals(french.keySet()), "fr_fr language keys must match en_us");
        for (String key : english.keySet()) {
            int expectedPlaceholders = placeholderCount(english.get(key));
            require(placeholderCount(japanese.get(key)) == expectedPlaceholders,
                    "ja_jp placeholder count must match en_us for " + key);
            require(placeholderCount(french.get(key)) == expectedPlaceholders,
                    "fr_fr placeholder count must match en_us for " + key);
        }
        require(english.containsKey("screen.openplayer.controls.capability_status"),
                "capability status UI key must be localized");
        require(english.containsKey("screen.openplayer.controls.no_capability_status"),
                "empty capability status UI key must be localized");
        String capabilityStatus = english.get("screen.openplayer.controls.capability_status").toLowerCase(java.util.Locale.ROOT);
        require(capabilityStatus.contains("viewer") && capabilityStatus.contains("world"),
                "status UI must label ServerPlayer-derived lines as viewer/world diagnostics");
        require(!capabilityStatus.contains("npc"),
                "status UI must not label ServerPlayer-derived lines as NPC diagnostics");
    }

    private static Map<String, String> load(String fileName) throws Exception {
        String content = Files.readString(Path.of("src/main/resources/assets/openplayer/lang", fileName));
        Matcher matcher = ENTRY.matcher(content);
        Map<String, String> values = new LinkedHashMap<>();
        while (matcher.find()) {
            values.put(matcher.group(1), matcher.group(2));
        }
        return values;
    }

    private static int placeholderCount(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
