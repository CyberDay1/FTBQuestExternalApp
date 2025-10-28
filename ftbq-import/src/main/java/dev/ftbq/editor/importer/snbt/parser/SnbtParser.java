package dev.ftbq.editor.importer.snbt.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Basic SNBT parser that produces Java collections.
 */
public final class SnbtParser {

    private final String input;
    private int index;

    public SnbtParser(String input) {
        this.input = input;
        this.index = 0;
    }

    public static Map<String, Object> parseRootCompound(String snbt) {
        SnbtParser parser = new SnbtParser(snbt);
        Object value = parser.parseValue();
        if (!(value instanceof Map<?, ?> map)) {
            throw new SnbtParseException("Root of SNBT must be a compound");
        }
        parser.skipWhitespace();
        if (parser.hasNext()) {
            throw new SnbtParseException("Trailing content after root compound at index " + parser.index);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> compound = (Map<String, Object>) map;
        return compound;
    }

    private Object parseValue() {
        skipWhitespace();
        if (!hasNext()) {
            throw new SnbtParseException("Unexpected end of SNBT input");
        }
        char ch = peek();
        return switch (ch) {
            case '{' -> parseCompound();
            case '[' -> parseList();
            case '"' -> parseString();
            case '\'' -> parseQuotedString('\'');
            default -> parseNumberOrBareWord();
        };
    }

    private Map<String, Object> parseCompound() {
        expect('{');
        Map<String, Object> compound = new LinkedHashMap<>();
        skipWhitespace();
        if (consume('}')) {
            return compound;
        }
        do {
            skipWhitespace();
            String key = parseKey();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            compound.put(key, value);
            skipWhitespace();
        } while (consume(','));
        skipWhitespace();
        expect('}');
        return compound;
    }

    private List<Object> parseList() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (consume(']')) {
            return list;
        }
        do {
            Object value = parseValue();
            list.add(value);
            skipWhitespace();
        } while (consume(','));
        skipWhitespace();
        expect(']');
        return list;
    }

    private String parseKey() {
        skipWhitespace();
        if (!hasNext()) {
            throw new SnbtParseException("Unexpected end of SNBT while reading key");
        }
        char ch = peek();
        if (ch == '"') {
            return parseString();
        }
        if (ch == '\'') {
            return parseQuotedString('\'');
        }
        int start = index;
        while (hasNext()) {
            char c = peek();
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '/') {
                index++;
            } else {
                break;
            }
        }
        if (start == index) {
            throw new SnbtParseException("Invalid key at index " + index);
        }
        return input.substring(start, index);
    }

    private Object parseNumberOrBareWord() {
        int start = index;
        boolean hasColon = false;
        while (hasNext()) {
            char c = peek();
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '+' || c == '.' || c == ':' || c == '/' || c == '#') {
                if (c == ':') {
                    hasColon = true;
                }
                index++;
            } else if (c == 'e' || c == 'E') {
                // part of scientific notation; advance and include following +/- if present
                index++;
                if (hasNext()) {
                    char next = peek();
                    if (next == '+' || next == '-') {
                        index++;
                    }
                }
            } else {
                break;
            }
        }
        if (start == index) {
            throw new SnbtParseException("Expected value at index " + index);
        }
        String token = input.substring(start, index);
        String lower = token.toLowerCase(Locale.ROOT);
        if ("true".equals(lower)) {
            return Boolean.TRUE;
        }
        if ("false".equals(lower)) {
            return Boolean.FALSE;
        }
        if (hasColon || token.indexOf('/') >= 0) {
            return token;
        }
        // Determine suffix for numeric tokens
        char lastChar = token.charAt(token.length() - 1);
        char lastLower = Character.toLowerCase(lastChar);
        String numberPart = token;
        if (Character.isAlphabetic(lastChar)) {
            numberPart = token.substring(0, token.length() - 1);
        }
        try {
            return switch (lastLower) {
                case 'b' -> Byte.valueOf(numberPart);
                case 's' -> Short.valueOf(numberPart);
                case 'l' -> Long.valueOf(numberPart);
                case 'f' -> Float.valueOf(numberPart);
                case 'd' -> Double.valueOf(numberPart);
                default -> parseDefaultNumber(token);
            };
        } catch (NumberFormatException ex) {
            return token;
        }
    }

    private Number parseDefaultNumber(String token) {
        if (token.contains(".") || token.contains("e") || token.contains("E")) {
            return Double.valueOf(token);
        }
        return Long.valueOf(token);
    }

    private String parseString() {
        return parseQuotedString('"');
    }

    private String parseQuotedString(char delimiter) {
        expect(delimiter);
        StringBuilder builder = new StringBuilder();
        while (hasNext()) {
            char c = next();
            if (c == delimiter) {
                return builder.toString();
            }
            if (c == '\\') {
                if (!hasNext()) {
                    throw new SnbtParseException("Unterminated escape sequence");
                }
                char escaped = next();
                builder.append(switch (escaped) {
                    case '"', '\\', '\'', '/' -> escaped;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'u' -> parseUnicodeEscape();
                    default -> throw new SnbtParseException("Unsupported escape character '" + escaped + "'");
                });
            } else {
                builder.append(c);
            }
        }
        throw new SnbtParseException("Unterminated string literal");
    }

    private char parseUnicodeEscape() {
        if (index + 4 > input.length()) {
            throw new SnbtParseException("Incomplete unicode escape");
        }
        String hex = input.substring(index, index + 4);
        index += 4;
        return (char) Integer.parseInt(hex, 16);
    }

    private void skipWhitespace() {
        while (hasNext()) {
            char c = peek();
            if (Character.isWhitespace(c)) {
                index++;
                continue;
            }
            if (c == '/' && index + 1 < input.length()) {
                char next = input.charAt(index + 1);
                if (next == '/') {
                    index += 2;
                    while (hasNext() && peek() != '\n') {
                        index++;
                    }
                    continue;
                }
                if (next == '*') {
                    index += 2;
                    while (index + 1 < input.length() && !(input.charAt(index) == '*' && input.charAt(index + 1) == '/')) {
                        index++;
                    }
                    if (index + 1 >= input.length()) {
                        throw new SnbtParseException("Unterminated block comment");
                    }
                    index += 2;
                    continue;
                }
            }
            if (c == '#') {
                index++;
                while (hasNext() && peek() != '\n') {
                    index++;
                }
                continue;
            }
            break;
        }
    }

    private boolean hasNext() {
        return index < input.length();
    }

    private char peek() {
        return input.charAt(index);
    }

    private char next() {
        return input.charAt(index++);
    }

    private boolean consume(char expected) {
        if (hasNext() && peek() == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!consume(expected)) {
            String message = hasNext() ? "Expected '" + expected + "' at index " + index + " but found '" + peek() + "'"
                    : "Expected '" + expected + "' at end of input";
            throw new SnbtParseException(message);
        }
    }
}
