package com.apex.engine.tools.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal JSON parser for tool arguments. Handles objects, arrays,
 * strings, numbers, booleans, null. Integral numbers parse as
 * {@link Long}; everything else as {@link Double}. Not a security
 * boundary — validation happens after parsing.
 */
public final class Json {

    private final String input;
    private int pos;

    private Json(String input) {
        this.input = input;
        this.pos = 0;
    }

    public static Map<String, Object> parseObject(String input) {
        Objects.requireNonNull(input, "input");
        Json p = new Json(input);
        p.skipWhitespace();
        Map<String, Object> result = p.parseObjectValue();
        p.skipWhitespace();
        if (p.pos != input.length()) {
            throw new ToolException("json", "trailing content at " + p.pos);
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
            if (c != ',') throw new ToolException("json", "expected , or } at " + pos);
        }
    }

    private Object parseValue() {
        skipWhitespace();
        char c = peek();
        return switch (c) {
            case '{' -> parseObjectValue();
            case '[' -> parseArrayValue();
            case '"' -> parseString();
            case 't' -> parseLiteral("true", Boolean.TRUE);
            case 'f' -> parseLiteral("false", Boolean.FALSE);
            case 'n' -> parseLiteral("null", null);
            default -> parseNumber();
        };
    }

    private List<Object> parseArrayValue() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            list.add(parseValue());
            skipWhitespace();
            char c = next();
            if (c == ']') return list;
            if (c != ',') throw new ToolException("json", "expected , or ] at " + pos);
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
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        String hex = input.substring(pos, pos + 4);
                        pos += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> throw new ToolException("json", "bad escape: \\" + esc);
                }
            } else if (c < 0x20) {
                throw new ToolException("json", "control char at " + pos);
            } else {
                sb.append(c);
            }
        }
    }

    private Object parseNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;

        boolean isFloat = false;
        if (pos < input.length() && input.charAt(pos) == '.') {
            isFloat = true; pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        }
        if (pos < input.length()
                && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            isFloat = true; pos++;
            if (pos < input.length()
                    && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        }

        String num = input.substring(start, pos);
        if (num.isEmpty() || num.equals("-")) {
            throw new ToolException("json", "bad number at " + start);
        }
        try {
            if (isFloat) return Double.parseDouble(num);
            return Long.parseLong(num);
        } catch (NumberFormatException e) {
            try {
                return Double.parseDouble(num);
            } catch (NumberFormatException e2) {
                throw new ToolException("json", "bad number: " + num);
            }
        }
    }

    private Object parseLiteral(String literal, Object value) {
        if (!input.startsWith(literal, pos)) {
            throw new ToolException("json", "expected " + literal + " at " + pos);
        }
        pos += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }

    private char peek() {
        if (pos >= input.length()) throw new ToolException("json", "unexpected end");
        return input.charAt(pos);
    }

    private char next() {
        if (pos >= input.length()) throw new ToolException("json", "unexpected end");
        return input.charAt(pos++);
    }

    private void expect(char expected) {
        char c = next();
        if (c != expected) {
            throw new ToolException("json", "expected '" + expected + "' at " + (pos - 1));
        }
    }
}
