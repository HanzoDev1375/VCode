package com.cocode.vcode.ide.core.formatter;

/**
 * Format beautifier for CSS source sheets.
 * Runs a token state scan across raw style text to apply consistent indentation levels,
 * break property declarations onto dedicated rows, and manage inline property-value spacing rules.
 */
public class CssFormatter extends BaseFormatter {
    @Override
    public String format(String code) {
        StringBuilder out = new StringBuilder();
        int indent = 0;
        boolean inString = false; // State flag to avoid scrambling characters inside literal text blocks
        char stringChar = 0;      // Tracks quote boundaries matching double vs single bounds

        // Collapse all messy pre-existing whitespaces down to a single space token to reset baseline positioning
        String cleanCode = code.replaceAll("\\s+", " ").trim();
        boolean isNewLine = true;

        for (int i = 0; i < cleanCode.length(); i++) {
            char c = cleanCode.charAt(i);

            // Safety check: Skip layout formatting mutations if we're scanning characters inside text strings
            if (inString) {
                out.append(c);
                // Exit string mode if we see the matching closing quote, unless it's escaped via backslash
                if (c == stringChar && cleanCode.charAt(i - 1) != '\\') inString = false;
                continue;
            }

            // Toggle text string mode upon discovering valid literal notation quote ticks
            if (c == '"' || c == '\'') {
                if (isNewLine) {
                    out.append(getIndentString(indent));
                    isNewLine = false;
                }
                inString = true;
                stringChar = c;
                out.append(c);
                continue;
            }

            // Handle block start layout updates
            if (c == '{') {
                // Ensure a nice clear space precedes an open bracket block if missing
                if (!isNewLine && out.length() > 0 && out.charAt(out.length() - 1) != ' ')
                    out.append(" ");
                out.append("{\n");
                indent++;
                isNewLine = true;
            }
            // Handle block wrap updates
            else if (c == '}') {
                indent = Math.max(0, indent - 1); // Step back an indent depth level safely
                if (!isNewLine) out.append("\n");
                out.append(getIndentString(indent)).append("}\n\n");
                isNewLine = true;
            }
            // Separate single rule declarations safely onto independent lines
            else if (c == ';') {
                out.append(";\n");
                isNewLine = true;
            }
            else {
                boolean b = i + 1 < cleanCode.length() && cleanCode.charAt(i + 1) != ' ';

                // Keep look and feel legible by placing single clean spaces behind structural property colons
                if (c == ':') {
                    out.append(":");
                    if (b) {
                        out.append(" ");
                    }
                }
                // Put clear spaces following selector separator commas
                else if (c == ',') {
                    out.append(",");
                    if (b) {
                        out.append(" ");
                    }
                }
                else {
                    // Prevent piling up unnecessary trailing/leading indentation text spacers
                    if (c == ' ' && isNewLine) continue;

                    if (isNewLine) {
                        out.append(getIndentString(indent));
                        isNewLine = false;
                    }
                    out.append(c);
                }
            }
        }

        // Final sanitation sweep: clear out completely blank whitespace rows and smooth over double breaks
        return out.toString().replaceAll("(?m)^\\s+$", "").replaceAll("\\n{3,}", "\n\n").trim();
    }
}