package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import android.text.SpannableStringBuilder;

import com.cocode.vcode.ide.R;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hybrid syntax highlighter coordinator for HTML source code.
 * Parses general markup tokens while embedding dedicated sub-highlighters to handle
 * inline JavaScript script scopes and CSS design block sections seamlessly.
 */
public class HtmlSyntaxHighlighter extends SyntaxHighlighter {

    // Regular Expression patterns isolating explicit markup syntax boundaries
    private static final Pattern PAT_COMMENT = Pattern.compile("", Pattern.DOTALL);
    private static final Pattern PAT_DOCTYPE = Pattern.compile("<!DOCTYPE[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_TAG_NAME = Pattern.compile("(?<=</?)[\\w-]+"); // Positive lookbehind isolates target tag keyword
    private static final Pattern PAT_BRACKET = Pattern.compile("</?|/?>|>");
    private static final Pattern PAT_ATTR_NAME = Pattern.compile("\\s([\\w:-]+)(?=\\s*=)"); // Positive lookahead catches key names
    private static final Pattern PAT_ATTR_VAL = Pattern.compile("=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')");
    private static final Pattern PAT_ENTITY = Pattern.compile("&[#\\w]+;");

    // Scopes extracted for nested multi-language engine delegation processing
    private static final Pattern PAT_SCRIPT = Pattern.compile(
            "<script[^>]*>([\\s\\S]*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PAT_STYLE = Pattern.compile(
            "<style[^>]*>([\\s\\S]*?)</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final JsSyntaxHighlighter jsHighlighter;
    private final CssSyntaxHighlighter cssHighlighter;

    private final int colorComment;
    private final int colorBracket;
    private final int colorTag;
    private final int colorAttrName;
    private final int colorAttrVal;
    private final int colorEntity;

    public HtmlSyntaxHighlighter(Context context) {
        super(context);
        // Instantiate specialized engine blocks to handle interior script/style text regions
        jsHighlighter = new JsSyntaxHighlighter(context);
        cssHighlighter = new CssSyntaxHighlighter(context);

        colorComment = getColor(R.color.vcode_color_comment);
        colorBracket = getColor(R.color.vcode_color_html_bracket);
        colorTag = getColor(R.color.vcode_color_html_tag);
        colorAttrName = getColor(R.color.vcode_color_html_attribute);
        colorAttrVal = getColor(R.color.vcode_color_html_value);
        colorEntity = getColor(R.color.vcode_color_html_attribute);
    }

    @Override
    public SpannableStringBuilder highlight(String code) {
        if (code == null || code.isEmpty())
            return new SpannableStringBuilder(code != null ? code : "");
        SpannableStringBuilder ssb = new SpannableStringBuilder(code);

        // Pass 1: Delegate internal <script> content blocks to the specialized JavaScript highlighter
        Matcher scriptMatcher = PAT_SCRIPT.matcher(code);
        while (scriptMatcher.find()) {
            int groupStart = scriptMatcher.start(1); // Identify text entry index inside the tags
            String inner = scriptMatcher.group(1);
            if (inner != null && !inner.isEmpty()) {
                SpannableStringBuilder innerSsb = jsHighlighter.highlight(inner);
                mergeSpans(ssb, innerSsb, groupStart); // Map sub-tokens back into primary buffer coordinates
            }
        }

        // Pass 2: Delegate internal <style> design blocks to the specialized CSS highlighter
        Matcher styleMatcher = PAT_STYLE.matcher(code);
        while (styleMatcher.find()) {
            int groupStart = styleMatcher.start(1); // Identify text entry index inside the tags
            String inner = styleMatcher.group(1);
            if (inner != null && !inner.isEmpty()) {
                SpannableStringBuilder innerSsb = cssHighlighter.highlight(inner);
                mergeSpans(ssb, innerSsb, groupStart); // Map sub-tokens back into primary buffer coordinates
            }
        }

        // Pass 3: Comments (highest priority — intentionally overwrites any underlying nested tags or tokens)
        apply(ssb, PAT_COMMENT, code, colorComment);

        // Pass 4: Global Document Directives
        apply(ssb, PAT_DOCTYPE, code, colorTag);

        // Pass 5: Element block keys
        apply(ssb, PAT_TAG_NAME, code, colorTag);

        // Pass 6: Markup brackets
        apply(ssb, PAT_BRACKET, code, colorBracket);

        // Pass 7: Element attributes
        Matcher attrMatcher = PAT_ATTR_NAME.matcher(code);
        while (attrMatcher.find()) {
            int s = attrMatcher.start(1); // Focus strictly on capturing group 1 index bounds
            int e = attrMatcher.end(1);
            applySpan(ssb, s, e, colorAttrName);
        }

        // Pass 8: Inline attribute field values
        Matcher valMatcher = PAT_ATTR_VAL.matcher(code);
        while (valMatcher.find()) {
            if (valMatcher.group(1) != null) {
                applySpan(ssb, valMatcher.start(1), valMatcher.end(1), colorAttrVal);
            }
        }

        // Pass 9: Character reference entities
        apply(ssb, PAT_ENTITY, code, colorEntity);

        return ssb;
    }

    /**
     * Loops through matching regular expression structures to lay down specific color highlights.
     */
    private void apply(SpannableStringBuilder ssb, Pattern pattern, String code, int color) {
        Matcher m = pattern.matcher(code);
        while (m.find()) {
            applySpan(ssb, m.start(), m.end(), color);
        }
    }

    /**
     * Helper to offset and inject syntax styling elements parsed from external nested
     * sub-language engines into the main structural document stream.
     */
    private void mergeSpans(SpannableStringBuilder target,
                            SpannableStringBuilder source, int offset) {
        if (source == null || offset < 0) return;
        com.cocode.vcode.ide.views.SyntaxHighlightSpan[] spans =
                source.getSpans(0, source.length(), com.cocode.vcode.ide.views.SyntaxHighlightSpan.class);
        for (com.cocode.vcode.ide.views.SyntaxHighlightSpan span : spans) {
            int s = source.getSpanStart(span) + offset;
            int e = source.getSpanEnd(span) + offset;
            applySpan(target, s, e, span.getForegroundColor());
        }
    }
}