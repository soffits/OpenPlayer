package dev.soffits.openplayer.automation.policy;

import dev.soffits.openplayer.OpenPlayerConstants;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class MovementPolicyLoader {
    public static final ResourceLocation DEFAULT_POLICY_ID = OpenPlayerConstants.id("companion_safe");
    private static final MovementProfile SERVER_CAPS = new MovementProfile(
            DEFAULT_POLICY_ID,
            true,
            false,
            3,
            true,
            true,
            BlockSafetyPolicy.boundedDefault(),
            EntitySafetyPolicy.boundedDefault()
    );
    private static final MovementProfile BUILT_IN_DEFAULT = new MovementProfile(
            DEFAULT_POLICY_ID,
            true,
            false,
            3,
            true,
            true,
            BlockSafetyPolicy.boundedDefault(),
            EntitySafetyPolicy.boundedDefault()
    ).boundBy(SERVER_CAPS);

    private MovementPolicyLoader() {
    }

    public static MovementProfile defaultPolicy() {
        return BUILT_IN_DEFAULT;
    }

    public static MovementProfile effectivePolicy(String selectedPolicyId) {
        ResourceLocation parsed = parsePolicyId(selectedPolicyId).orElse(DEFAULT_POLICY_ID);
        return loadBundledPolicy(parsed).orElse(BUILT_IN_DEFAULT).boundBy(SERVER_CAPS);
    }

    public static Optional<ResourceLocation> parsePolicyId(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ResourceLocation(value.trim()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<MovementProfile> loadBundledPolicy(ResourceLocation id) {
        if (!DEFAULT_POLICY_ID.equals(id)) {
            return Optional.empty();
        }
        String path = "data/" + id.getNamespace() + "/movement_policies/" + id.getPath() + ".json";
        try (InputStream stream = MovementPolicyLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return Optional.of(BUILT_IN_DEFAULT);
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(parseCompanionSafe(json));
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.of(BUILT_IN_DEFAULT);
        }
    }

    private static MovementProfile parseCompanionSafe(String json) {
        String movement = readObject(json, "movement");
        String blocks = readObject(json, "blocks");
        String entities = readObject(json, "entities");
        return new MovementProfile(
                DEFAULT_POLICY_ID,
                readBoolean(movement, "canBreakObstacles", true),
                readBoolean(movement, "canPlaceScaffold", false),
                readInt(movement, "maxFallDistance", 3),
                readBoolean(movement, "avoidLiquids", true),
                readBoolean(movement, "avoidHostiles", true),
                new BlockSafetyPolicy(
                        readStringArray(blocks, "neverBreak"),
                        readStringArray(blocks, "avoid"),
                        readStringArray(blocks, "lowRiskBreakable")
                ),
                new EntitySafetyPolicy(
                        readStringArray(entities, "avoid"),
                        readStringArray(entities, "defendAgainst"),
                        readStringArray(entities, "neverAttack")
                )
        );
    }

    private static String readObject(String json, String field) {
        int index = fieldIndex(json, field);
        if (index < 0) {
            return "";
        }
        int open = json.indexOf('{', index);
        if (open < 0) {
            return "";
        }
        int depth = 0;
        for (int cursor = open; cursor < json.length(); cursor++) {
            char value = json.charAt(cursor);
            if (value == '{') {
                depth++;
            } else if (value == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(open + 1, cursor);
                }
            }
        }
        return "";
    }

    private static boolean readBoolean(String json, String field, boolean fallback) {
        int index = fieldIndex(json, field);
        if (index < 0) {
            return fallback;
        }
        int colon = json.indexOf(':', index);
        if (colon < 0) {
            return fallback;
        }
        String tail = json.substring(colon + 1).stripLeading();
        if (tail.startsWith("true")) {
            return true;
        }
        if (tail.startsWith("false")) {
            return false;
        }
        return fallback;
    }

    private static int readInt(String json, String field, int fallback) {
        int index = fieldIndex(json, field);
        if (index < 0) {
            return fallback;
        }
        int colon = json.indexOf(':', index);
        if (colon < 0) {
            return fallback;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == start) {
            return fallback;
        }
        return Integer.parseInt(json.substring(start, end));
    }

    private static Set<String> readStringArray(String json, String field) {
        int index = fieldIndex(json, field);
        if (index < 0) {
            return Set.of();
        }
        int open = json.indexOf('[', index);
        int close = json.indexOf(']', open + 1);
        if (open < 0 || close < 0) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        int cursor = open + 1;
        while (cursor < close) {
            int quote = json.indexOf('"', cursor);
            if (quote < 0 || quote >= close) {
                break;
            }
            int endQuote = json.indexOf('"', quote + 1);
            if (endQuote < 0 || endQuote > close) {
                break;
            }
            String value = json.substring(quote + 1, endQuote).trim();
            if (!value.isBlank()) {
                values.add(value);
            }
            cursor = endQuote + 1;
        }
        return Set.copyOf(values);
    }

    private static int fieldIndex(String json, String field) {
        return json == null ? -1 : json.indexOf('"' + field + '"');
    }
}
