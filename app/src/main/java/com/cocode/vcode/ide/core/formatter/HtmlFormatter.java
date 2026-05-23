package com.cocode.vcode.ide.core.formatter;

/**
 * High-performance line-by-line tree layout formatter for HTML markup documents.
 * Normalizes uneven tags, calculates open vs close element depths, and prevents
 * nesting padding shifts on self-closing void elements.
 */
public class HtmlFormatter extends BaseFormatter {
    @Override
    public String format(String code) {
        // Strip trailing whitespaces between tags and insert newlines to split markup safely onto independent lines
        String clean = code.replaceAll(">\\s*<", "><").replace("><", ">\n<");
        String[] lines = clean.split("\n");
        StringBuilder out = new StringBuilder();
        int indent = 0;

        // Comprehensive regex map containing elements that must never enforce indentation nesting increases
        String voidTags = "(?i)^<(img|br|hr|input|link|meta|area|base|col|param|source).*?>$";

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
                    && !trimmed.matches(voidTags) && !trimmed.endsWith("/>")) {
                // Do not increment indent value if the entire element block opens and completes inside this exact same line boundary
                if (!trimmed.matches("^<[^>]+>.*</[^>]+>$")) indent++;
            }
        }
        return out.toString().trim();
    }
}