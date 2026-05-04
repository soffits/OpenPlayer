package dev.soffits.openplayer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpenPlayerLanguageParityTest {
    private static final Pattern ENTRY = Pattern.compile("\\s*\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:[^\\\\\\\"]|\\\\.)*)\\\"[,}]?");
    private static final Pattern PLACEHOLDER = Pattern.compile("%s");
    private static final Pattern CJK_TEXT = Pattern.compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]");
    private static final List<String> LOCALIZED_LANGUAGE_FILES = List.of(
            "common/src/main/resources/assets/openplayer/lang/ja_jp.json",
            "common/src/main/resources/assets/openplayer/lang/zh_cn.json",
            "common/src/main/resources/assets/openplayer/lang/zh_tw.json"
    );

    private OpenPlayerLanguageParityTest() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> english = load("en_us.json");
        Map<String, String> japanese = load("ja_jp.json");
        Map<String, String> french = load("fr_fr.json");
        Map<String, String> simplifiedChinese = load("zh_cn.json");
        Map<String, String> traditionalChinese = load("zh_tw.json");

        require(english.keySet().equals(japanese.keySet()), "ja_jp language keys must match en_us");
        require(english.keySet().equals(french.keySet()), "fr_fr language keys must match en_us");
        require(english.keySet().equals(simplifiedChinese.keySet()), "zh_cn language keys must match en_us");
        require(english.keySet().equals(traditionalChinese.keySet()), "zh_tw language keys must match en_us");
        for (String key : english.keySet()) {
            int expectedPlaceholders = placeholderCount(english.get(key));
            require(placeholderCount(japanese.get(key)) == expectedPlaceholders,
                    "ja_jp placeholder count must match en_us for " + key);
            require(placeholderCount(french.get(key)) == expectedPlaceholders,
                    "fr_fr placeholder count must match en_us for " + key);
            require(placeholderCount(simplifiedChinese.get(key)) == expectedPlaceholders,
                    "zh_cn placeholder count must match en_us for " + key);
            require(placeholderCount(traditionalChinese.get(key)) == expectedPlaceholders,
                    "zh_tw placeholder count must match en_us for " + key);
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
        requireNoCjkOutsideLocalizedResources();
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

    private static void requireNoCjkOutsideLocalizedResources() throws Exception {
        Path repositoryRoot = Path.of("..").toAbsolutePath().normalize();
        try (java.util.stream.Stream<Path> paths = Files.walk(repositoryRoot)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relativePath = repositoryRoot.relativize(path).toString().replace('\\', '/');
                if (!shouldScan(relativePath)) {
                    continue;
                }
                Matcher matcher = CJK_TEXT.matcher(Files.readString(path));
                require(!matcher.find(), "CJK text is only allowed in localized resource values: " + relativePath);
            }
        }
    }

    private static boolean shouldScan(String relativePath) {
        if (relativePath.startsWith(".git/") || relativePath.startsWith(".gradle/") || relativePath.contains("/build/")) {
            return false;
        }
        if (LOCALIZED_LANGUAGE_FILES.contains(relativePath)) {
            return false;
        }
        return relativePath.endsWith(".java") || relativePath.endsWith(".json") || relativePath.endsWith(".md")
                || relativePath.endsWith(".gradle") || relativePath.endsWith(".properties")
                || relativePath.endsWith(".toml") || relativePath.endsWith(".yml") || relativePath.endsWith(".yaml");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
