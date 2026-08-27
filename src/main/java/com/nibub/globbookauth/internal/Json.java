package com.nibub.globbookauth.internal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A minimal, dependency-free JSON reader sufficient for this SDK's needs:
 * decoding a flat top-level object of strings/booleans/numbers/nulls, as
 * returned by Globbook's token and userinfo endpoints. Not a general-
 * purpose JSON library — intentionally scoped to avoid a third-party
 * runtime dependency (Jackson/Gson) for a handful of simple response
 * shapes.
 *
 * <p>This is an internal implementation detail, not part of the public
 * API — it may change or be replaced without notice.
 */
public final class Json {
    private Json() {
    }

    /**
     * Parses a JSON object into a {@code Map<String, Object>} where each
     * value is a {@code String}, {@code Boolean}, {@code Double}, or
     * {@code null}. Throws {@link JsonException} for anything else
     * (nested objects/arrays) or malformed input — this SDK's response
     * shapes are always flat.
     */
    public static Map<String, Object> parseObject(String input) {
        Parser p = new Parser(input);
        p.skipWhitespace();
        Map<String, Object> result = p.parseObjectValue();
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw new JsonException("trailing content after JSON object");
        }
        return result;
    }

    public static final class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
            this.i = 0;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        char peek() {
            if (atEnd()) {
                throw new JsonException("unexpected end of input");
            }
            return s.charAt(i);
        }

        void expect(char c) {
            if (atEnd() || s.charAt(i) != c) {
                throw new JsonException("expected '" + c + "' at position " + i);
            }
            i++;
        }

        Map<String, Object> parseObjectValue() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                i++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == '}') {
                    i++;
                    break;
                }
                throw new JsonException("expected ',' or '}' at position " + i);
            }
            return map;
        }

        Object parseValue() {
            char c = peek();
            if (c == '"') {
                return parseString();
            }
            if (c == 't' || c == 'f') {
                return parseBoolean();
            }
            if (c == 'n') {
                parseNull();
                return null;
            }
            if (c == '-' || Character.isDigit(c)) {
                return parseNumber();
            }
            if (c == '{' || c == '[') {
                throw new JsonException("nested objects/arrays are not supported by this minimal parser");
            }
            throw new JsonException("unexpected character '" + c + "' at position " + i);
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonException("unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw new JsonException("unterminated escape sequence");
                    }
                    char esc = s.charAt(i++);
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (i + 4 > s.length()) {
                                throw new JsonException("truncated unicode escape");
                            }
                            String hex = s.substring(i, i + 4);
                            i += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default:
                            throw new JsonException("invalid escape '\\" + esc + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            throw new JsonException("invalid literal at position " + i);
        }

        void parseNull() {
            if (s.startsWith("null", i)) {
                i += 4;
                return;
            }
            throw new JsonException("invalid literal at position " + i);
        }

        Double parseNumber() {
            int start = i;
            if (peek() == '-') {
                i++;
            }
            while (!atEnd() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            if (!atEnd() && s.charAt(i) == '.') {
                i++;
                while (!atEnd() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            if (!atEnd() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                i++;
                if (!atEnd() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    i++;
                }
                while (!atEnd() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            return Double.parseDouble(s.substring(start, i));
        }
    }
}
