package dev.soffits.openplayer.aicore;

final class AICoreJsonFields {
    private AICoreJsonFields() {
    }

    static String stringField(String json, String fieldName) {
        String quoted = "\"" + fieldName + "\"";
        int fieldIndex = json == null ? -1 : json.indexOf(quoted);
        if (fieldIndex < 0) {
            return "";
        }
        int colonIndex = json.indexOf(':', fieldIndex + quoted.length());
        int quoteStart = colonIndex < 0 ? -1 : json.indexOf('"', colonIndex + 1);
        int quoteEnd = quoteStart < 0 ? -1 : json.indexOf('"', quoteStart + 1);
        return quoteEnd > quoteStart ? json.substring(quoteStart + 1, quoteEnd) : "";
    }

    static String numberField(String json, String fieldName) {
        String quoted = "\"" + fieldName + "\"";
        int fieldIndex = json == null ? -1 : json.indexOf(quoted);
        if (fieldIndex < 0) {
            return "";
        }
        int colonIndex = json.indexOf(':', fieldIndex + quoted.length());
        if (colonIndex < 0) {
            return "";
        }
        int index = colonIndex + 1;
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        if (index < json.length() && json.charAt(index) == '"') {
            int quoteEnd = json.indexOf('"', index + 1);
            return quoteEnd > index ? json.substring(index + 1, quoteEnd) : "";
        }
        int start = index;
        while (index < json.length()) {
            char character = json.charAt(index);
            if ((character >= '0' && character <= '9') || character == '-' || character == '+') {
                index++;
            } else {
                break;
            }
        }
        return index > start ? json.substring(start, index) : "";
    }

    static String integerField(String json, String fieldName) {
        String quoted = "\"" + fieldName + "\"";
        int fieldIndex = json == null ? -1 : json.indexOf(quoted);
        if (fieldIndex < 0) {
            return "";
        }
        int colonIndex = json.indexOf(':', fieldIndex + quoted.length());
        if (colonIndex < 0) {
            return "";
        }
        int index = colonIndex + 1;
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        if (index < json.length() && json.charAt(index) == '"') {
            int quoteEnd = json.indexOf('"', index + 1);
            return quoteEnd > index ? json.substring(index + 1, quoteEnd) : "";
        }
        int start = index;
        if (index < json.length() && (json.charAt(index) == '-' || json.charAt(index) == '+')) {
            index++;
        }
        while (index < json.length() && Character.isDigit(json.charAt(index))) {
            index++;
        }
        if (index == start || (index == start + 1 && (json.charAt(start) == '-' || json.charAt(start) == '+'))) {
            return "";
        }
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        if (index < json.length() && json.charAt(index) != ',' && json.charAt(index) != '}') {
            return "invalid";
        }
        return json.substring(start, index).trim();
    }
}
