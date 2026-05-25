package com.cocode.vcode.ide.core.parser;

import com.cocode.vcode.ide.core.language.Language;

/**
 * Computing manager coordinating intelligent newline auto-indentation layouts.
 * Matches structural syntax cues like brackets, block elements, or open states to adjust tab indentation.
 */
public class IndentationEngine {
    private final String tabString;
    private final int tabSize;

    public IndentationEngine(int tabSize) {
        this.tabSize = Math.max(1, tabSize);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.tabSize; i++) sb.append(" ");
        this.tabString = sb.toString();
    }

    /**
     * Determines the exact workspace indentation whitespace block necessary when a carriage return is pressed.
     */
    public String getIndentForNewLine(String text, int cursorPos, Language lang) {
        if (text == null || cursorPos <= 0) return "";

        // Scan backwards to extract the current line up to the cursor safely without redundant allocations
        int minPos = Math.min(cursorPos, text.length());
        int lineStart = text.lastIndexOf('\n', minPos - 1);
        String currentLine = text.substring(lineStart + 1, minPos);

        String baseIndent = getLeadingWhitespace(currentLine); // Read ancestral indentation level
        String trimmedLine = currentLine.trim();

        // Increment spacing offset if structural keywords or block markers are met
        if (shouldIncreaseIndent(trimmedLine, lang)) {
            return baseIndent + getTabString();
        }

        return baseIndent; // Maintain original layout balance level
    }

    public String getTabString() {
        return this.tabString;
    }

    /**
     * Decides whether the current code statement implies a nested structural block follows next.
     */
    private boolean shouldIncreaseIndent(String trimmedLine, Language lang) {
        if (trimmedLine.isEmpty()) return false;
        char last = trimmedLine.charAt(trimmedLine.length() - 1);

        // Core Trigger: Line terminates with typical programming block delimiters
        if (last == '{' || last == '(' || last == '[') return true;

        // Custom XML/Markup evaluation blocks
        if (lang == Language.HTML || lang == Language.TEXT) {
            if (trimmedLine.endsWith(">") && !trimmedLine.contains("</")) {
                // Highly efficient tag extraction without regex
                int openAngle = trimmedLine.lastIndexOf('<');
                if (openAngle >= 0) {
                    int spaceIdx = trimmedLine.indexOf(' ', openAngle);
                    int closeAngle = trimmedLine.indexOf('>', openAngle);
                    int endIdx = spaceIdx > -1 && spaceIdx < closeAngle ? spaceIdx : closeAngle;

                    if (endIdx > openAngle + 1) {
                        String tag = trimmedLine.substring(openAngle + 1, endIdx);
                        // Confirm if the structural tag behaves as a block container requiring a sub-indent layout
                        return HtmlTagCache.isBlockElement(tag);
                    }
                }
            }
        }

        return false;
    }

    /**
     * Captures leading tabs or space patterns running down the front edge of a code statement line.
     */
    private String getLeadingWhitespace(String line) {
        if (line == null) return "";
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return i == 0 ? "" : line.substring(0, i);
    }
}