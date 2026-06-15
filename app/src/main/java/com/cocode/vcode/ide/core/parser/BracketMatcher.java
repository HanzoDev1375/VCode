package com.cocode.vcode.ide.core.parser;

/**
 * Utility class responsible for pair-matching bracket tokens under the cursor.
 * Supports structural highlighting and scope navigation for parentheses (), brackets [], and braces {}.
 */
public class BracketMatcher {

    private static final String OPEN_BRACKETS = "({[";
    private static final String CLOSE_BRACKETS = ")}]";

    /**
     * Inspects the character directly under the cursor and tracks its matching sibling balance
     * by scanning either forward or backward through the document text.
     *
     * @param text      The complete text buffer of the document.
     * @param cursorPos The current 0-based index position of the cursor caret.
     * @return A MatchResult object detailing the pair coordinates and discovery status.
     */
    public MatchResult findMatch(String text, int cursorPos) {
        // Guard check against empty inputs or indices targeting outer space bounds
        if (text == null || cursorPos < 0 || cursorPos >= text.length()) {
            return new MatchResult(-1, -1, false);
        }

        char c = text.charAt(cursorPos);
        int openIdx = OPEN_BRACKETS.indexOf(c);
        int closeIdx = CLOSE_BRACKETS.indexOf(c);

        if (openIdx >= 0) {
            // Case 1: Cursor is resting on an open bracket token -> Scan forward to locate closure
            char closeChar = CLOSE_BRACKETS.charAt(openIdx);
            int depth = 1; // Track nesting level changes

            for (int i = cursorPos + 1; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == c)
                    depth++;          // Found another identical open token; increment depth level
                if (ch == closeChar)
                    depth--;  // Found matching target closer; decrement depth level

                // Nesting balanced out to zero; we found the exact sibling token match!
                if (depth == 0) return new MatchResult(cursorPos, i, true);
            }
        } else if (closeIdx >= 0) {
            // Case 2: Cursor is resting on a close bracket token -> Scan backward to locate opening
            char openChar = OPEN_BRACKETS.charAt(closeIdx);
            int depth = 1; // Track nesting level changes backwards

            for (int i = cursorPos - 1; i >= 0; i--) {
                char ch = text.charAt(i);
                if (ch == c)
                    depth++;         // Found another identical close token; increment depth level
                if (ch == openChar) depth--;  // Found matching target opener; decrement depth level

                // Nesting balanced out to zero; pair tracking successfully satisfied
                if (depth == 0) return new MatchResult(i, cursorPos, true);
            }
        }

        // No matching partner token was discovered within document limits
        return new MatchResult(-1, -1, false);
    }

    public static void applyRainbowBrackets(android.text.SpannableStringBuilder ssb, String text, int[] colors) {
        if (text == null || colors == null || colors.length == 0) return;
        
        int[] depthArray = new int[text.length()];
        int currentDepthParenthesis = 0;
        int currentDepthBracket = 0;
        int currentDepthBrace = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (c == '(') {
                depthArray[i] = currentDepthParenthesis % colors.length;
                currentDepthParenthesis++;
            } else if (c == ')') {
                currentDepthParenthesis = Math.max(0, currentDepthParenthesis - 1);
                depthArray[i] = currentDepthParenthesis % colors.length;
            } else if (c == '[') {
                depthArray[i] = currentDepthBracket % colors.length;
                currentDepthBracket++;
            } else if (c == ']') {
                currentDepthBracket = Math.max(0, currentDepthBracket - 1);
                depthArray[i] = currentDepthBracket % colors.length;
            } else if (c == '{') {
                depthArray[i] = currentDepthBrace % colors.length;
                currentDepthBrace++;
            } else if (c == '}') {
                currentDepthBrace = Math.max(0, currentDepthBrace - 1);
                depthArray[i] = currentDepthBrace % colors.length;
            } else {
                depthArray[i] = -1;
            }
        }
        
        for (int i = 0; i < text.length(); i++) {
            if (depthArray[i] != -1) {
                ssb.setSpan(
                        new com.cocode.vcode.ide.views.SyntaxHighlightSpan(colors[depthArray[i]]),
                        i,
                        i + 1,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
    }

    /**
     * Immutable container capturing the absolute coordinates of matched structural pairs.
     */
    public static class MatchResult {
        public final int openPos;   // Index coordinate of the opening element
        public final int closePos;  // Index coordinate of the closing element
        public final boolean found; // True if the partner token was successfully located

        public MatchResult(int openPos, int closePos, boolean found) {
            this.openPos = openPos;
            this.closePos = closePos;
            this.found = found;
        }
    }
}