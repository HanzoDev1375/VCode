package com.cocode.vcode.ide.core.formatter;

import java.util.regex.Pattern;

/**
 * Fast streaming token state beautifier for JSON payload text.
 * Parses character streams directly to apply indentation without allocating complex in-memory tree nodes,
 * supporting processing of large payloads without memory constraint issues.
 */
public class JsonFormatter extends BaseFormatter {

    private static final Pattern EMPTY_OBJECT = Pattern.compile("\\{\\s+\\}");
    private static final Pattern EMPTY_ARRAY = Pattern.compile("\\[\\s+]");

    @Override
    public String format(String code) {
        if (code == null || code.isEmpty()) return "";
        StringBuilder out = new StringBuilder(code.length() + code.length() / 10);
        int indent = 0;
        boolean inString = false;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);

            // Protect internal literal string formats from being altered
            if (inString) {
                out.append(c);
                if (c == '"' && code.charAt(i - 1) != '\\') {
                    inString = false;
                }
                continue;
            }

            // Start of a string literal
            if (c == '"') {
                out.append(c);
                inString = true;
                continue;
            }

            // Skip structural external whitespaces
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                continue;
            }

            // Layout scoping rules
            if (c == '{' || c == '[') {
                out.append(c).append('\n');
                indent++;
                out.append(getIndentString(indent));
            } else if (c == '}' || c == ']') {
                out.append('\n');
                indent = Math.max(0, indent - 1);
                out.append(getIndentString(indent)).append(c);
            } else if (c == ',') {
                out.append(c).append('\n').append(getIndentString(indent));
            } else if (c == ':') {
                out.append(c).append(' ');
            } else {
                out.append(c);
            }
        }

        // Post-processing cleanup for empty brackets
        String result = out.toString();
        result = EMPTY_OBJECT.matcher(result).replaceAll("{}");
        result = EMPTY_ARRAY.matcher(result).replaceAll("[]");
        return result.trim();
    }
}