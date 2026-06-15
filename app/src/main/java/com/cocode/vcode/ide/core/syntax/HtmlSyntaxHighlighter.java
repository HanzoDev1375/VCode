package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.views.ColorPreviewSpan;
import com.cocode.vcode.ide.views.SyntaxHighlightSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hybrid syntax highlighter coordinator for HTML source code.
 * Parses general markup tokens while embedding dedicated sub-highlighters to handle
 * inline JavaScript script scopes and CSS design block sections seamlessly.
 */
public class HtmlSyntaxHighlighter extends SyntaxHighlighter {

    // Regular Expression patterns isolating explicit markup syntax boundaries
    private static final Pattern PAT_COMMENT = Pattern.compile("<!--[\\s\\S]*?-->", Pattern.DOTALL);
    private static final Pattern PAT_DOCTYPE = Pattern.compile("<!DOCTYPE[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_TAG_NAME = Pattern.compile("(?<=</?)[\\w-]+");
    private static final Pattern PAT_BRACKET = Pattern.compile("</?|/?>|>");
    private static final Pattern PAT_ATTR_NAME = Pattern.compile("\\s([\\w:-]+)(?=\\s*=)");
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

        // Collect embedded block ranges so HTML patterns can skip them
        List<int[]> embeddedRanges = new ArrayList<>();

        // Pass 1: Delegate internal <script> content blocks to JavaScript highlighter
        Matcher scriptMatcher = PAT_SCRIPT.matcher(code);
        while (scriptMatcher.find()) {
            int groupStart = scriptMatcher.start(1);
            int groupEnd = scriptMatcher.end(1);
            String inner = scriptMatcher.group(1);
            if (inner != null && !inner.isEmpty()) {
                embeddedRanges.add(new int[]{groupStart, groupEnd});
                SpannableStringBuilder innerSsb = jsHighlighter.highlight(inner);
                mergeSpans(ssb, innerSsb, groupStart);
            }
        }

        // Pass 2: Delegate internal <style> design blocks to CSS highlighter
        Matcher styleMatcher = PAT_STYLE.matcher(code);
        while (styleMatcher.find()) {
            int groupStart = styleMatcher.start(1);
            int groupEnd = styleMatcher.end(1);
            String inner = styleMatcher.group(1);
            if (inner != null && !inner.isEmpty()) {
                embeddedRanges.add(new int[]{groupStart, groupEnd});
                SpannableStringBuilder innerSsb = cssHighlighter.highlight(inner);
                mergeSpans(ssb, innerSsb, groupStart);
            }
        }

        // Pass 3: Comments (highest priority)
        applySkippingRanges(ssb, PAT_COMMENT, code, colorComment, embeddedRanges);

        // Pass 4: DOCTYPE
        applySkippingRanges(ssb, PAT_DOCTYPE, code, colorTag, embeddedRanges);

        // Pass 5: Tag names
        applySkippingRanges(ssb, PAT_TAG_NAME, code, colorTag, embeddedRanges);

        // Pass 6: Brackets
        applySkippingRanges(ssb, PAT_BRACKET, code, colorBracket, embeddedRanges);

        // Pass 7: Attribute names
        Matcher attrMatcher = PAT_ATTR_NAME.matcher(code);
        while (attrMatcher.find()) {
            int s = attrMatcher.start(1);
            int e = attrMatcher.end(1);
            if (!isInsideEmbeddedRange(s, e, embeddedRanges)) {
                applySpan(ssb, s, e, colorAttrName);
            }
        }

        // Pass 8: Attribute values
        Matcher valMatcher = PAT_ATTR_VAL.matcher(code);
        while (valMatcher.find()) {
            if (valMatcher.group(1) != null) {
                int s = valMatcher.start(1);
                int e = valMatcher.end(1);
                if (!isInsideEmbeddedRange(s, e, embeddedRanges)) {
                    applySpan(ssb, s, e, colorAttrVal);
                }
            }
        }

        // Pass 9: Entities
        applySkippingRanges(ssb, PAT_ENTITY, code, colorEntity, embeddedRanges);

        // Pass 10: Inline CSS Colors
        applyInlineColors(ssb, code, embeddedRanges, 0);

        applyLinks(ssb, code);

        return ssb;
    }

    /**
     * Highlights a visible range while still correctly detecting embedded style/script blocks
     * in the full document.
     */
    @Override
    public SpannableStringBuilder highlightRange(String fullCode, int rangeStart, int rangeEnd) {
        if (fullCode == null || fullCode.isEmpty()) return new SpannableStringBuilder("");
        int start = Math.max(0, rangeStart);
        int end = Math.min(fullCode.length(), rangeEnd);
        if (start >= end) return new SpannableStringBuilder("");

        String sub = fullCode.substring(start, end);
        SpannableStringBuilder ssb = new SpannableStringBuilder(sub);

        // Collect embedded block ranges from the FULL document
        List<int[]> embeddedRanges = new ArrayList<>();

        // Find all <script> blocks in full code and highlight those overlapping visible range
        Matcher scriptMatcher = PAT_SCRIPT.matcher(fullCode);
        while (scriptMatcher.find()) {
            int groupStart = scriptMatcher.start(1);
            int groupEnd = scriptMatcher.end(1);
            String inner = scriptMatcher.group(1);
            if (inner == null || inner.isEmpty()) continue;
            embeddedRanges.add(new int[]{groupStart, groupEnd});
            if (groupEnd > start && groupStart < end) {
                int hlStart = Math.max(groupStart, start) - groupStart;
                int hlEnd = Math.min(groupEnd, end) - groupStart;
                if (hlStart < hlEnd) {
                    String visibleInner = inner.substring(hlStart, hlEnd);
                    SpannableStringBuilder innerSsb = jsHighlighter.highlight(visibleInner);
                    int offset = Math.max(groupStart, start) - start;
                    mergeSpans(ssb, innerSsb, offset);
                }
            }
        }

        // Find all <style> blocks in full code and highlight those overlapping visible range
        Matcher styleMatcher = PAT_STYLE.matcher(fullCode);
        while (styleMatcher.find()) {
            int groupStart = styleMatcher.start(1);
            int groupEnd = styleMatcher.end(1);
            String inner = styleMatcher.group(1);
            if (inner == null || inner.isEmpty()) continue;
            embeddedRanges.add(new int[]{groupStart, groupEnd});
            if (groupEnd > start && groupStart < end) {
                int hlStart = Math.max(groupStart, start) - groupStart;
                int hlEnd = Math.min(groupEnd, end) - groupStart;
                if (hlStart < hlEnd) {
                    String visibleInner = inner.substring(hlStart, hlEnd);
                    SpannableStringBuilder innerSsb = cssHighlighter.highlight(visibleInner);
                    int offset = Math.max(groupStart, start) - start;
                    mergeSpans(ssb, innerSsb, offset);
                }
            }
        }

        // Apply HTML patterns only outside embedded ranges (adjusted to substring coordinates)
        applySkippingRangesForRange(ssb, PAT_COMMENT, sub, colorComment, embeddedRanges, start);
        applySkippingRangesForRange(ssb, PAT_DOCTYPE, sub, colorTag, embeddedRanges, start);
        applySkippingRangesForRange(ssb, PAT_TAG_NAME, sub, colorTag, embeddedRanges, start);
        applySkippingRangesForRange(ssb, PAT_BRACKET, sub, colorBracket, embeddedRanges, start);

        Matcher attrMatcher = PAT_ATTR_NAME.matcher(sub);
        while (attrMatcher.find()) {
            int s = attrMatcher.start(1);
            int e = attrMatcher.end(1);
            if (!isInsideEmbeddedRange(s + start, e + start, embeddedRanges)) {
                applySpan(ssb, s, e, colorAttrName);
            }
        }

        Matcher valMatcher = PAT_ATTR_VAL.matcher(sub);
        while (valMatcher.find()) {
            if (valMatcher.group(1) != null) {
                int s = valMatcher.start(1);
                int e = valMatcher.end(1);
                if (!isInsideEmbeddedRange(s + start, e + start, embeddedRanges)) {
                    applySpan(ssb, s, e, colorAttrVal);
                }
            }
        }

        applySkippingRangesForRange(ssb, PAT_ENTITY, sub, colorEntity, embeddedRanges, start);

        applyInlineColors(ssb, sub, embeddedRanges, start);

        applyLinks(ssb, sub);

        return ssb;
    }

    /**
     * Applies pattern matches but skips any match that falls inside an embedded range.
     */
    private void applySkippingRanges(SpannableStringBuilder ssb, Pattern pattern,
                                     String code, int color, List<int[]> embeddedRanges) {
        Matcher m = pattern.matcher(code);
        while (m.find()) {
            if (!isInsideEmbeddedRange(m.start(), m.end(), embeddedRanges)) {
                applySpan(ssb, m.start(), m.end(), color);
            }
        }
    }

    /**
     * Applies pattern matches on a substring, skipping matches inside embedded ranges.
     * The embeddedRanges are in full-document coordinates; rangeOffset converts.
     */
    private void applySkippingRangesForRange(SpannableStringBuilder ssb, Pattern pattern,
                                             String sub, int color, List<int[]> embeddedRanges,
                                             int rangeOffset) {
        Matcher m = pattern.matcher(sub);
        while (m.find()) {
            int absStart = m.start() + rangeOffset;
            int absEnd = m.end() + rangeOffset;
            if (!isInsideEmbeddedRange(absStart, absEnd, embeddedRanges)) {
                applySpan(ssb, m.start(), m.end(), color);
            }
        }
    }

    /**
     * Checks if a match region overlaps with any embedded script/style content range.
     */
    private boolean isInsideEmbeddedRange(int start, int end, List<int[]> ranges) {
        for (int[] range : ranges) {
            if (start >= range[0] && end <= range[1]) return true;
        }
        return false;
    }

    private void applyInlineColors(SpannableStringBuilder ssb, String sub, List<int[]> embeddedRanges, int rangeOffset) {
        Matcher styleAttrMatcher = Pattern.compile("(?i)style\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')").matcher(sub);
        while (styleAttrMatcher.find()) {
            if (styleAttrMatcher.group(1) != null) {
                int valStart = styleAttrMatcher.start(1);
                String valStr = styleAttrMatcher.group(1);
                
                Matcher m = Pattern.compile("(#(?:[0-9a-fA-F]{3,4}){1,2}\\b|\\b(?:rgb|hsl)a?\\([^)]+\\)|\\b(?i)(?:aliceblue|antiquewhite|aqua|aquamarine|azure|beige|bisque|black|blanchedalmond|blue|blueviolet|brown|burlywood|cadetblue|chartreuse|chocolate|coral|cornflowerblue|cornsilk|crimson|cyan|darkblue|darkcyan|darkgoldenrod|darkgray|darkgreen|darkgrey|darkkhaki|darkmagenta|darkolivegreen|darkorange|darkorchid|darkred|darksalmon|darkseagreen|darkslateblue|darkslategray|darkslategrey|darkturquoise|darkviolet|deeppink|deepskyblue|dimgray|dimgrey|dodgerblue|firebrick|floralwhite|forestgreen|fuchsia|gainsboro|ghostwhite|gold|goldenrod|gray|green|greenyellow|grey|honeydew|hotpink|indianred|indigo|ivory|khaki|lavender|lavenderblush|lawngreen|lemonchiffon|lightblue|lightcoral|lightcyan|lightgoldenrodyellow|lightgray|lightgreen|lightgrey|lightpink|lightsalmon|lightseagreen|lightskyblue|lightslategray|lightslategrey|lightsteelblue|lightyellow|lime|limegreen|linen|magenta|maroon|mediumaquamarine|mediumblue|mediumorchid|mediumpurple|mediumseagreen|mediumslateblue|mediumspringgreen|mediumturquoise|mediumvioletred|midnightblue|mintcream|mistyrose|moccasin|navajowhite|navy|oldlace|olive|olivedrab|orange|orangered|orchid|palegoldenrod|palegreen|paleturquoise|palevioletred|papayawhip|peachpuff|peru|pink|plum|powderblue|purple|rebeccapurple|red|rosybrown|royalblue|saddlebrown|salmon|sandybrown|seagreen|seashell|sienna|silver|skyblue|slateblue|slategray|slategrey|snow|springgreen|steelblue|tan|teal|thistle|tomato|transparent|turquoise|violet|wheat|white|whitesmoke|yellow|yellowgreen)\\b)").matcher(valStr);
                while (m.find()) {
                    int absStart = valStart + m.start();
                    int absEnd = valStart + m.end();
                    if (!isInsideEmbeddedRange(absStart + rangeOffset, absEnd + rangeOffset, embeddedRanges)) {
                        Integer colorVal = com.cocode.vcode.ide.utils.ColorParser.parse(m.group());
                        if (colorVal != null && absStart < absEnd) {
                            ssb.setSpan(
                                    new ColorPreviewSpan(colorVal, colorAttrVal),
                                    absStart,
                                    absStart + 1,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            );
                        }
                    }
                }
            }
        }
    }

    /**
     * Merges syntax spans from a sub-highlighter into the target buffer at the given offset.
     */
    private void mergeSpans(SpannableStringBuilder target,
                            SpannableStringBuilder source, int offset) {
        if (source == null || offset < 0) return;
        
        SyntaxHighlightSpan[] spans =
                source.getSpans(0, source.length(), SyntaxHighlightSpan.class);
        for (SyntaxHighlightSpan span : spans) {
            int s = source.getSpanStart(span) + offset;
            int e = source.getSpanEnd(span) + offset;
            applySpan(target, s, e, span.getForegroundColor(), span.isUnderline());
        }
        
        ColorPreviewSpan[] colorSpans =
                source.getSpans(0, source.length(), ColorPreviewSpan.class);
        for (ColorPreviewSpan span : colorSpans) {
            int s = source.getSpanStart(span) + offset;
            int e = source.getSpanEnd(span) + offset;
            if (s >= 0 && e <= target.length()) {
                target.setSpan(
                        new ColorPreviewSpan(span.getPreviewColor(), span.getTextColor()),
                        s,
                        e,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
    }
}