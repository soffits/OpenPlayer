package dev.soffits.openplayer.aicore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AICoreProviderJsonToolParser {
    private final ToolRegistry registry;
    private final int maxPlanSteps;

    public AICoreProviderJsonToolParser(ToolRegistry registry, int maxPlanSteps) {
        if (registry == null) {
            throw new IllegalArgumentException("tool registry cannot be null");
        }
        if (maxPlanSteps < 1) {
            throw new IllegalArgumentException("max plan steps must be positive");
        }
        this.registry = registry;
        this.maxPlanSteps = maxPlanSteps;
    }

    public JsonToolParseResult parse(String json) {
        if (json == null || json.isBlank()) {
            return JsonToolParseResult.rejected("tool JSON cannot be blank");
        }
        try {
            Object root = new Parser(json).parseValue();
            if (!(root instanceof Map<?, ?> rootObject)) {
                return JsonToolParseResult.rejected("tool JSON root must be an object");
            }
            if (rootObject.containsKey("tool")) {
                return parseSingle(rootObject);
            }
            Object plan = rootObject.get("plan");
            if (!(plan instanceof List<?> steps)) {
                return JsonToolParseResult.rejected("tool JSON must contain tool or plan");
            }
            if (steps.isEmpty() || steps.size() > maxPlanSteps) {
                return JsonToolParseResult.rejected("plan step count is out of bounds");
            }
            List<ToolCall> calls = new ArrayList<>();
            for (Object step : steps) {
                if (!(step instanceof Map<?, ?> stepObject)) {
                    return JsonToolParseResult.rejected("plan steps must be objects");
                }
                JsonToolParseResult result = parseSingle(stepObject);
                if (!result.isAccepted()) {
                    return result;
                }
                calls.add(result.calls().get(0));
            }
            return JsonToolParseResult.accepted(calls);
        } catch (IllegalArgumentException exception) {
            return JsonToolParseResult.rejected(exception.getMessage());
        }
    }

    private JsonToolParseResult parseSingle(Map<?, ?> object) {
        Object tool = object.get("tool");
        if (!(tool instanceof String toolNameValue)) {
            return JsonToolParseResult.rejected("tool must be a string");
        }
        ToolName toolName;
        try {
            toolName = ToolName.of(toolNameValue);
        } catch (IllegalArgumentException exception) {
            return JsonToolParseResult.rejected(exception.getMessage());
        }
        if (!registry.contains(toolName)) {
            return JsonToolParseResult.rejected("unknown tool: " + toolName.value());
        }
        Object args = object.get("args");
        if (args == null) {
            args = Map.of();
        }
        if (!(args instanceof Map<?, ?> argsObject)) {
            return JsonToolParseResult.rejected("args must be an object");
        }
        return JsonToolParseResult.accepted(List.of(new ToolCall(toolName, new ToolArguments(flatten(argsObject)))));
    }

    private static Map<String, String> flatten(Map<?, ?> object) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : object.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new IllegalArgumentException("argument keys must be non-blank strings");
            }
            Object value = entry.getValue();
            if (value == null) {
                values.put(key, "null");
            } else if (value instanceof Map<?, ?> || value instanceof List<?>) {
                values.put(key, Serializer.write(value));
            } else {
                values.put(key, String.valueOf(value));
            }
        }
        return values;
    }

    private record Parser(String source) {
        Object parseValue() {
            Cursor cursor = new Cursor(source);
            Object value = cursor.readValue();
            cursor.skipWhitespace();
            if (!cursor.isDone()) {
                throw new IllegalArgumentException("unexpected trailing JSON content");
            }
            return value;
        }
    }

    private static final class Cursor {
        private final String source;
        private int index;

        private Cursor(String source) {
            this.source = source;
        }

        private Object readValue() {
            skipWhitespace();
            if (isDone()) {
                throw new IllegalArgumentException("unexpected end of JSON");
            }
            char current = source.charAt(index);
            if (current == '{') {
                return readObject();
            }
            if (current == '[') {
                return readArray();
            }
            if (current == '"') {
                return readString();
            }
            if (current == '-' || Character.isDigit(current)) {
                return readNumber();
            }
            if (source.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            if (source.startsWith("false", index)) {
                index += 5;
                return Boolean.FALSE;
            }
            if (source.startsWith("null", index)) {
                index += 4;
                return null;
            }
            throw new IllegalArgumentException("invalid JSON value");
        }

        private Map<String, Object> readObject() {
            index++;
            LinkedHashMap<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) {
                return object;
            }
            while (true) {
                skipWhitespace();
                if (isDone() || source.charAt(index) != '"') {
                    throw new IllegalArgumentException("object keys must be strings");
                }
                String key = readString();
                skipWhitespace();
                require(':');
                object.put(key, readValue());
                skipWhitespace();
                if (consume('}')) {
                    return object;
                }
                require(',');
            }
        }

        private List<Object> readArray() {
            index++;
            ArrayList<Object> array = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) {
                return array;
            }
            while (true) {
                array.add(readValue());
                skipWhitespace();
                if (consume(']')) {
                    return array;
                }
                require(',');
            }
        }

        private String readString() {
            require('"');
            StringBuilder builder = new StringBuilder();
            while (!isDone()) {
                char current = source.charAt(index++);
                if (current == '"') {
                    return builder.toString();
                }
                if (current == '\\') {
                    if (isDone()) {
                        throw new IllegalArgumentException("unterminated JSON escape");
                    }
                    char escaped = source.charAt(index++);
                    builder.append(switch (escaped) {
                        case '"' -> '"';
                        case '\\' -> '\\';
                        case '/' -> '/';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> throw new IllegalArgumentException("unsupported JSON escape");
                    });
                } else {
                    builder.append(current);
                }
            }
            throw new IllegalArgumentException("unterminated JSON string");
        }

        private String readNumber() {
            int start = index;
            if (source.charAt(index) == '-') {
                index++;
            }
            while (!isDone() && Character.isDigit(source.charAt(index))) {
                index++;
            }
            if (!isDone() && source.charAt(index) == '.') {
                index++;
                while (!isDone() && Character.isDigit(source.charAt(index))) {
                    index++;
                }
            }
            return source.substring(start, index);
        }

        private void skipWhitespace() {
            while (!isDone() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private boolean consume(char expected) {
            if (!isDone() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void require(char expected) {
            if (!consume(expected)) {
                throw new IllegalArgumentException("expected JSON character: " + expected);
            }
        }

        private boolean isDone() {
            return index >= source.length();
        }
    }

    private static final class Serializer {
        private static String write(Object value) {
            if (value == null) {
                return "null";
            }
            if (value instanceof Map<?, ?> map) {
                StringBuilder builder = new StringBuilder("{");
                int index = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (index++ > 0) {
                        builder.append(',');
                    }
                    builder.append('"').append(entry.getKey()).append("\":").append(write(entry.getValue()));
                }
                return builder.append('}').toString();
            }
            if (value instanceof List<?> list) {
                StringBuilder builder = new StringBuilder("[");
                for (int index = 0; index < list.size(); index++) {
                    if (index > 0) {
                        builder.append(',');
                    }
                    builder.append(write(list.get(index)));
                }
                return builder.append(']').toString();
            }
            if (value instanceof Boolean || value instanceof Number) {
                return String.valueOf(value);
            }
            return '"' + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
    }
}
