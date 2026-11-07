package com.apex.engine.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal JSON parser. Handles objects, arrays, strings, numbers,
 * booleans, null. Integral numbers parse as {@link Long}; everything
 * else as {@link Double}. Not a security boundary — validation happens
 * after parsing. Throws {@link JsonException} on malformed input.
 */
public final class Json {

    private final String input;
    private int pos;

    private Json(String input) {
        this.input = input;
        this.pos = 0;
    }

    /**
     * Parses a JSON object string into a {@code Map}. Throws
     * {@link JsonException} on malformed input.
     */
    public static Map<String, Object> parseObject(String input) {
        Objects.requireNonNull(input, "input");
        Json p = new Json(input);
        p.skipWhitespace();
        Map<String, Object> result = p.parseObjectValue();
        p.skipWhitespace();
        if (p.pos != input.length()) {
            throw new JsonException("trailing content at " + p.pos);
        }
        return result;
    }

    private Map<String, Object> parseObjectValue() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, parseValue());
            skipWhitespace();
            char c = next();
            if (c == '}') return map;
            if (c != ',') throw new JsonException("expected ',' or '}' at " + (pos - 1));
        }
    }

    private List<Object> parseArrayValue() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            skipWhitespace();
            list.add(parseValue());
            skipWhitespace();
            char c = next();
            if (c == ']') return list;
            if (c != ',') throw new JsonException("expected ',' or ']' at " + (pos - 1));
        }
    }

    private Object parseValue() {
        skipWhitespace();
        char c = peek();
        switch (c) {
            case '{': return parseObjectValue();
            case '[': return parseArrayValue();
            case '"': return parseString();
            case 't': return parseLiteral("true", true);
            case 'f': return parseLiteral("false", false);
            case 'n': return parseLiteral("null", null);
            default: return parseNumber();
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char esc = next();
                switch (esc) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > input.length()) {
                            throw new JsonException("unterminated unicode escape at " + (pos - 1));
                        }
                        String hex = input.substring(pos, pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                        break;
                    default:
                        throw new JsonException("bad escape '\\" + esc + "' at " + (pos - 1));
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object parseNumber() {
        int start = pos;
        boolean isFloat = false;
        if (peek() == '-') pos++;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        if (pos < input.length() && input.charAt(pos) == '.') {
            isFloat = true; pos++;
        }
        if (pos < input.length()
                && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;

        String num = input.substring(start, pos);
        if (num.isEmpty() || num.equals("-")) {
            throw new JsonException("bad number at " + start);
        }
        try {
            if (isFloat) return Double.parseDouble(num);
            return Long.parseLong(num);
        } catch (NumberFormatException e) {
            try {
                return Double.parseDouble(num);
            } catch (NumberFormatException e2) {
                throw new JsonException("bad number: " + num, e2);
            }
        }
    }

    private Object parseLiteral(String literal, Object value) {
        if (!input.startsWith(literal, pos)) {
            throw new JsonException("expected " + literal + " at " + pos);
        }
        pos += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }

    private char peek() {
        if (pos >= input.length()) throw new JsonException("unexpected end");
        return input.charAt(pos);
    }

    private char next() {
        if (pos >= input.length()) throw new JsonException("unexpected end");
        return input.charAt(pos++);
    }

    private void expect(char expected) {
        char c = next();
        if (c != expected) {
            throw new JsonException("expected '" + expected + "' at " + (pos - 1));
        }
    }
}
