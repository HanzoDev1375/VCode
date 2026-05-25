package com.cocode.vcode.ide.core.formatter;

import java.util.regex.Pattern;

/**
 * High-performance line-by-line tree layout formatter for HTML markup documents.
 * Normalizes uneven tags, calculates open vs close element depths, and prevents
 * nesting padding shifts on self-closing void elements.
 */
public class HtmlFormatter extends BaseFormatter {
    
    private static final Pattern TAG_SPLIT = Pattern.compile(">\\s*<");
    private static final Pattern VOID_TAGS = Pattern.compile("(?i)^<(img|br|hr|input|link|meta|area|base|col|param|source).*?>$");
    private static final Pattern INLINE_TAG = Pattern.compile("^<[^>]+>.*</[^>]+>$");

    @Override
    public String format(String code) {
        if (code == null || code.isEmpty()) return "";
        // Strip trailing whitespaces between tags and insert newlines to split markup safely onto independent lines
        String clean = TAG_SPLIT.matcher(code).replaceAll(">\n<");
        String[] lines = clean.split("\n");
        StringBuilder out = new StringBuilder(clean.length() + lines.length * 2);
        int indent = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue; // Ignore empty line noise

            // Pull back indentation depth immediately if the line contains a closing tag
            if (trimmed.startsWith("</")) indent = Math.max(0, indent - 1);

            // Build the current row string complete with its matching calculated left margin spacing
            out.append(getIndentString(indent)).append(trimmed).append("\n");

            // Increase depth tracking if this is a valid open tag, unless it matches standard script headings or void blocks
            if (trimmed.startsWith("<") && !trimmed.startsWith("</")
                    && !trimmed.startsWith("<!") && !trimmed.startsWith("<?")
                    && !VOID_TAGS.matcher(trimmed).matches() && !trimmed.endsWith("/>")) {
                // Do not increment indent value if the entire element block opens and completes inside this exact same line boundary
                if (!INLINE_TAG.matcher(trimmed).matches()) indent++;
            }
        }
        return out.toString().trim();
    }
}