package com.cocode.vcode.ide.core.formatter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance line-by-line tree layout formatter for HTML markup documents.
 * Normalizes uneven tags, calculates open vs close element depths, and prevents
 * nesting padding shifts on self-closing void elements.
 */
public class HtmlFormatter extends BaseFormatter {

    private static final Pattern TAG_SPLIT = Pattern.compile(">[ \\t\\x0B\\f\\r]*<");
    private static final Pattern VOID_TAGS = Pattern.compile("(?i)^<(img|br|hr|input|link|meta|area|base|col|param|source).*?>$");
    private static final Pattern INLINE_TAG = Pattern.compile("^<[^>]+>.*</[^>]+>$");
    private static final Pattern MULTI_NEWLINES = Pattern.compile("\\n{3,}");

    @Override
    public String format(String code) {
        if (code == null || code.isEmpty()) return "";

        String uuid = UUID.randomUUID().toString().replace("-", "");
        List<String> styleContents = new ArrayList<>();
        List<String> scriptContents = new ArrayList<>();

        CssFormatter cssFormatter = new CssFormatter();
        JsFormatter jsFormatter = new JsFormatter();

        Matcher styleMatcher = Pattern.compile("(?is)(<style[^>]*>)(.*?)(</style>)").matcher(code);
        StringBuffer sb1 = new StringBuffer();
        while (styleMatcher.find()) {
            String content = styleMatcher.group(2);
            if (content.trim().isEmpty()) {
                styleMatcher.appendReplacement(sb1, Matcher.quoteReplacement(styleMatcher.group(0)));
            } else {
                styleContents.add(cssFormatter.format(content));
                styleMatcher.appendReplacement(sb1, Matcher.quoteReplacement(styleMatcher.group(1) + "\n___STYLE_" + uuid + "_" + (styleContents.size() - 1) + "___\n" + styleMatcher.group(3)));
            }
        }
        styleMatcher.appendTail(sb1);
        code = sb1.toString();

        Matcher scriptMatcher = Pattern.compile("(?is)(<script[^>]*>)(.*?)(</script>)").matcher(code);
        StringBuffer sb2 = new StringBuffer();
        while (scriptMatcher.find()) {
            String content = scriptMatcher.group(2);
            if (content.trim().isEmpty()) {
                scriptMatcher.appendReplacement(sb2, Matcher.quoteReplacement(scriptMatcher.group(0)));
            } else {
                scriptContents.add(jsFormatter.format(content));
                scriptMatcher.appendReplacement(sb2, Matcher.quoteReplacement(scriptMatcher.group(1) + "\n___SCRIPT_" + uuid + "_" + (scriptContents.size() - 1) + "___\n" + scriptMatcher.group(3)));
            }
        }
        scriptMatcher.appendTail(sb2);
        code = sb2.toString();

        // Strip trailing horizontal whitespaces between tags and insert newlines
        String clean = TAG_SPLIT.matcher(code).replaceAll(">\n<");
        String[] lines = clean.split("\n", -1);
        StringBuilder out = new StringBuilder(clean.length() + lines.length * 2);
        int indent = 0;
        int emptyLines = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (emptyLines < 1 && out.length() > 0) {
                    out.append("\n");
                    emptyLines++;
                }
                continue;
            }
            emptyLines = 0;

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

        String result = out.toString();

        for (int i = 0; i < styleContents.size(); i++) {
            String placeholder = "___STYLE_" + uuid + "_" + i + "___";
            String formattedCss = styleContents.get(i);
            Pattern p = Pattern.compile("(?m)^([ \\t]*)" + placeholder + "$");
            Matcher m = p.matcher(result);
            if (m.find()) {
                String indentStr = m.group(1);
                String indentedCss = indentLines(formattedCss, indentStr);
                result = result.replace(m.group(0), indentedCss);
            }
        }

        for (int i = 0; i < scriptContents.size(); i++) {
            String placeholder = "___SCRIPT_" + uuid + "_" + i + "___";
            String formattedJs = scriptContents.get(i);
            Pattern p = Pattern.compile("(?m)^([ \\t]*)" + placeholder + "$");
            Matcher m = p.matcher(result);
            if (m.find()) {
                String indentStr = m.group(1);
                String indentedJs = indentLines(formattedJs, indentStr);
                result = result.replace(m.group(0), indentedJs);
            }
        }

        result = MULTI_NEWLINES.matcher(result).replaceAll("\n\n");
        return result.trim();
    }

    private String indentLines(String text, String indentStr) {
        if (text == null || text.trim().isEmpty()) return "";
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                if (i < lines.length - 1) sb.append("\n");
            } else {
                sb.append(indentStr).append(lines[i]);
                if (i < lines.length - 1) sb.append("\n");
            }
        }
        return sb.toString();
    }
}