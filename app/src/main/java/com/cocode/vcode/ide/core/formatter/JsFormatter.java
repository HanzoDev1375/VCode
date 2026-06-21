package com.cocode.vcode.ide.core.formatter;

import java.util.regex.Pattern;

/**
 * Comprehensive formatting token state engine tailored for ECMAScript/JavaScript formatting.
 * Handles bracket tracking, conditional nesting structures, argument definitions, string structures,
 * and controls block chaining (e.g., managing spacing layouts for keyword chains like else, catch, finally).
 */
public class JsFormatter extends BaseFormatter {

    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern SPACE_NEWLINE = Pattern.compile(" \\n");
    private static final Pattern NEWLINE_SPACE = Pattern.compile("\\n ");
    private static final Pattern BLANK_LINES = Pattern.compile("(?m)^\\s+$");
    private static final Pattern MULTI_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern EMPTY_BRACES = Pattern.compile("\\{\\s+\\}");

    @Override
    public String format(String code) {
        if (code == null || code.isEmpty()) return "";
        StringBuilder out = new StringBuilder(code.length() + code.length() / 10);
        int indent = 0;
        boolean inString = false;
        char stringChar = 0;
        int inParens = 0; // Counts paren layers to properly adjust formatting style inside loop parameters or arguments

        // Clean out erratic tabs and double whitespaces, keeping baseline structural spaces intact
        String cleanCode = SPACES.matcher(code).replaceAll(" ");
        cleanCode = SPACE_NEWLINE.matcher(cleanCode).replaceAll("\n");
        cleanCode = NEWLINE_SPACE.matcher(cleanCode).replaceAll("\n");
        cleanCode = cleanCode.trim();

        boolean isNewLine = true;

        for (int i = 0; i < cleanCode.length(); i++) {
            char c = cleanCode.charAt(i);

            // String block protection: Pass internal contents forward unmodified
            if (inString) {
                out.append(c);
                if (c == stringChar && (i == 0 || cleanCode.charAt(i - 1) != '\\'))
                    inString = false;
                continue;
            }

            // Check for modern JS template literals, double quotes, and single quotes alike
            if (c == '"' || c == '\'' || c == '`') {
                if (isNewLine) {
                    out.append(getIndentString(indent));
                    isNewLine = false;
                }
                inString = true;
                stringChar = c;
                out.append(c);
                continue;
            }

            // Monitor start parameters to control statement splitting behavior inside parameters
            if (c == '(') {
                if (isNewLine) {
                    out.append(getIndentString(indent));
                    isNewLine = false;
                }
                inParens++;
                out.append(c);
                continue;
            }
            // Monitor close parameters
            if (c == ')') {
                if (isNewLine) {
                    out.append(getIndentString(indent));
                    isNewLine = false;
                }
                inParens = Math.max(0, inParens - 1);
                out.append(c);
                continue;
            }

            boolean haveNewLine = i + 1 >= cleanCode.length() || cleanCode.charAt(i + 1) != '\n';
            if (c == '{') {
                if (!isNewLine && out.length() > 0 && out.charAt(out.length() - 1) != ' ')
                    out.append(" ");
                out.append("{");
                if (haveNewLine) {
                    out.append("\n");
                }
                indent++;
                isNewLine = true;
            }
            // Handle close braces: Decrease indentation level safely and align syntax properly
            else if (c == '}') {
                indent = Math.max(0, indent - 1);
                if (!isNewLine) out.append("\n");

                out.append(getIndentString(indent)).append("}");
                isNewLine = false;

                // Look ahead to check if the upcoming token needs to combine inline with this closing brace
                int j = i + 1;
                while (j < cleanCode.length() && (cleanCode.charAt(j) == ' ' || cleanCode.charAt(j) == '\n')) {
                    j++;
                }

                if (j < cleanCode.length()) {
                    char nextC = cleanCode.charAt(j);
                    // If block is followed by standard termination signs, do not break line
                    if (nextC != ',' && nextC != ';' && nextC != ')' && nextC != ']' && nextC != '}') {
                        String rem = cleanCode.substring(j);
                        // Check for inline keyword chains to append cleanly on the same row (e.g. "} else {")
                        if (rem.startsWith("else") || rem.startsWith("catch") || rem.startsWith("finally")) {
                            out.append(" ");
                        } else {
                            if (haveNewLine) {
                                out.append("\n");
                            }
                            isNewLine = true;
                        }
                    }
                }
            } else if (c == ';') {
                out.append(";");
                // Only create line break for semicolons outside function or control loops parameters
                if (inParens == 0) {
                    if (haveNewLine) {
                        out.append("\n");
                    }
                    isNewLine = true;
                }
            } else if (c == ',') {
                out.append(",");
                if (inParens == 0) {
                    if (haveNewLine) {
                        out.append("\n");
                    }
                    isNewLine = true;
                } else {
                    out.append(" "); // Maintain a tidy readable space inside multiple argument definitions
                }
            }
            // Guard trailing structural carriage line breaks from building redundant layers
            else if (c == '\n') {
                out.append("\n");
                isNewLine = true;
            } else {
                if (c == ' ' && isNewLine) continue;

                if (isNewLine) {
                    out.append(getIndentString(indent));
                    isNewLine = false;
                }
                out.append(c);
            }
        }

        // Post-processing: Empty out accidental blank string margins and ensure empty structures display tightly as '{}'
        String result = out.toString();
        result = BLANK_LINES.matcher(result).replaceAll("");
        result = MULTI_NEWLINES.matcher(result).replaceAll("\n\n");
        result = EMPTY_BRACES.matcher(result).replaceAll("{}");
        return result.trim();
    }
}