package com.cocode.vcode.ide.core.parser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * String parser designed to compute open or unclosed markup nodes within HTML documents.
 * Aids context-aware actions like closing-tag generation and tracking attribute autocomplete scopes.
 */
public class HtmlTagParser {

    // RegEx patterns tailored for capturing markup components while skipping attributes or trailing whitespace
    private static final Pattern PAT_OPEN_TAG = Pattern.compile(
            "<([\\w-]+)(?:\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_CLOSE_TAG = Pattern.compile(
            "</([\\w-]+)\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_INSIDE_TAG = Pattern.compile(
            "<([\\w-]+)[^>]*$");

    public static boolean isVoidElement(String tagName) {
        return HtmlTagCache.isVoidElement(tagName);
    }

    /**
     * Evaluates text balance up to the cursor to isolate the nearest unmatched open tag.
     * Useful for automatically injecting proper closing elements when typing '</'.
     */
    public String getUnclosedTagAt(String text, int cursorPos) {
        if (text == null || cursorPos <= 0) return null;

        // Isolate document context preceding the cursor position
        String before = text.substring(0, Math.min(cursorPos, text.length()));
        Deque<String> stack = new ArrayDeque<>(); // LIFO collection tracks structural tag nests

        int pos = 0;
        while (pos < before.length()) {
            int closeIdx = before.indexOf("</", pos);
            int openIdx = before.indexOf('<', pos);
            if (openIdx < 0) break; // No further element fragments left to evaluate

            // Evaluate Close Elements encountered ahead of standard opens
            if (closeIdx >= 0 && closeIdx <= openIdx) {
                Matcher m = PAT_CLOSE_TAG.matcher(before.substring(closeIdx));
                if (m.find() && m.start() == 0) {
                    String tag = Objects.requireNonNull(m.group(1)).toLowerCase();
                    // Match found; pop parent structural tracking off our virtual branch stack
                    if (!stack.isEmpty() && Objects.equals(stack.peek(), tag)) stack.pop();
                    pos = closeIdx + m.end();
                } else {
                    pos = closeIdx + 2; // Jump forward past broken characters
                }
                continue;
            }

            // Evaluate standard open tag nodes
            Matcher m = PAT_OPEN_TAG.matcher(before.substring(openIdx));
            if (m.find() && m.start() == 0) {
                String tag = Objects.requireNonNull(m.group(1)).toLowerCase();

                // Only record container elements; ignore standard inline void variants (e.g. <img/>, <br>)
                if (!HtmlTagCache.isVoidElement(tag)) stack.push(tag);

                pos = openIdx + m.end();
            } else {
                pos = openIdx + 1; // Incremental forward slide past unparsed single characters
            }
        }

        // The element tree peak holds the absolute closest unclosed ancestor tag reference
        return stack.isEmpty() ? null : stack.peek();
    }

    /**
     * Evaluates if the current cursor points directly inside an unclosed HTML opening tag.
     * Used to verify whether the autocomplete engine should transition into attribute suggestion mode.
     */
    public String getCurrentOpenTagName(String text, int cursorPos) {
        if (text == null || cursorPos <= 0) return null;
        String before = text.substring(0, Math.min(cursorPos, text.length()));
        Matcher m = PAT_INSIDE_TAG.matcher(before);
        if (m.find()) {
            return Objects.requireNonNull(m.group(1)).toLowerCase();
        }
        return null;
    }
}