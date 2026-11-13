package mas.mail.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * small JSON reader and writer, so the framework needs no external library
 * supports objects (LinkedHashMap), arrays (ArrayList), strings, numbers (Long/Double), booleans and null
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (!p.atEnd()) {
            throw new IllegalArgumentException("Trailing characters at position " + p.pos);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object o = parse(text);
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new IllegalArgumentException("JSON object expected");
    }

    /**
     * finds and reads the first valid JSON object inside free text
     * the LLM often puts text around the JSON
     */
    public static Optional<Map<String, Object>> extractObject(String text) {
        if (text == null) {
            return Optional.empty();
        }
        int start = text.indexOf('{');
        while (start >= 0) {
            int depth = 0;
            boolean inString = false;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (inString) {
                    if (c == '\\') {
                        i++;
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        try {
                            return Optional.of(parseObject(text.substring(start, i + 1)));
                        } catch (RuntimeException e) {
                            break;
                        }
                    }
                }
            }
            start = text.indexOf('{', start + 1);
        }
        return Optional.empty();
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, false, 0);
        return sb.toString();
    }

    public static String pretty(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, true, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, boolean pretty, int level) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            quote(sb, s);
        } else if (v instanceof Double d && (d.isNaN() || d.isInfinite())) {
            sb.append("null");
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                newline(sb, pretty, level + 1);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(pretty ? ": " : ":");
                write(sb, e.getValue(), pretty, level + 1);
            }
            if (!map.isEmpty()) {
                newline(sb, pretty, level);
            }
            sb.append('}');
        } else if (v instanceof Collection<?> col) {
            sb.append('[');
            boolean first = true;
            for (Object o : col) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                newline(sb, pretty, level + 1);
                write(sb, o, pretty, level + 1);
            }
            if (!col.isEmpty()) {
                newline(sb, pretty, level);
            }
            sb.append(']');
        } else {
            quote(sb, v.toString());
        }
    }

    private static void newline(StringBuilder sb, boolean pretty, int level) {
        if (pretty) {
            sb.append('\n').append("  ".repeat(level));
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object value() {
            if (atEnd()) {
                throw error("Unexpected end of input");
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWs();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                String key = string();
                skipWs();
                expect(':');
                skipWs();
                map.put(key, value());
                skipWs();
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw error("',' or '}' expected");
                }
            }
        }

        List<Object> array() {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWs();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWs();
                list.add(value());
                skipWs();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw error("',' or ']' expected");
                }
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Number number() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            String n = s.substring(start, pos);
            if (n.isEmpty()) {
                throw error("Unexpected character '" + s.charAt(start) + "'");
            }
            if (n.contains(".") || n.contains("e") || n.contains("E")) {
                return Double.parseDouble(n);
            }
            return Long.parseLong(n);
        }

        Object literal(String word, Object value) {
            if (!s.startsWith(word, pos)) {
                throw error("'" + word + "' expected");
            }
            pos += word.length();
            return value;
        }

        char peek() {
            if (atEnd()) {
                throw error("Unexpected end of input");
            }
            return s.charAt(pos);
        }

        char next() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char c) {
            if (next() != c) {
                throw error("'" + c + "' expected");
            }
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " (position " + pos + ")");
        }
    }
}
