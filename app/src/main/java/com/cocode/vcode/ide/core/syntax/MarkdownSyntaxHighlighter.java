package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.views.ColorPreviewSpan;
import com.cocode.vcode.ide.views.SyntaxHighlightSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownSyntaxHighlighter extends SyntaxHighlighter {

    private static final Pattern PAT_HEADER = Pattern.compile("^#{1,6}\\s+.*$", Pattern.MULTILINE);
    private static final Pattern PAT_BOLD = Pattern.compile("(\\*\\*|__)(.*?)\\1");
    private static final Pattern PAT_ITALIC = Pattern.compile("(?<!\\w)(\\*|_)(?![\\s*_*])(.*?)(?<!\\s)\\1(?!\\w)");
    private static final Pattern PAT_STRIKETHROUGH = Pattern.compile("~~(.*?)~~");
    private static final Pattern PAT_CODE_BLOCK = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern PAT_CODE_BLOCK_LANG = Pattern.compile("```([a-z]+)[ \\t]*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_INLINE_CODE = Pattern.compile("`[^`]+`");
    private static final Pattern PAT_QUOTE = Pattern.compile("^>.*$", Pattern.MULTILINE);
    private static final Pattern PAT_LIST = Pattern.compile("^[ \\t]*([*+-]|\\d+\\.)\\s", Pattern.MULTILINE);
    private static final Pattern PAT_LINK = Pattern.compile("!?\\[(.*?)\\]\\((.*?)\\)");

    private final int colorHeader;
    private final int colorBold;
    private final int colorItalic;
    private final int colorStrikethrough;
    private final int colorCode;
    private final int colorQuote;
    private final int colorList;
    private final int colorLink;

    private final HtmlSyntaxHighlighter htmlHighlighter;
    private final CssSyntaxHighlighter cssHighlighter;

    public MarkdownSyntaxHighlighter(Context context) {
        super(context);
        colorHeader = getColor(R.color.vcode_color_md_header);
        colorBold = getColor(R.color.vcode_color_md_bold);
        colorItalic = getColor(R.color.vcode_color_md_italic);
        colorStrikethrough = getColor(R.color.vcode_color_md_strikethrough);
        colorCode = getColor(R.color.vcode_color_md_code);
        colorQuote = getColor(R.color.vcode_color_md_quote);
        colorList = getColor(R.color.vcode_color_md_list);
        colorLink = getColor(R.color.vcode_color_md_link);

        htmlHighlighter = new HtmlSyntaxHighlighter(context);
        cssHighlighter = new CssSyntaxHighlighter(context);
    }

    @Override
    public SpannableStringBuilder highlight(String code) {
        if (code == null || code.isEmpty())
            return new SpannableStringBuilder(code != null ? code : "");
        return highlightRange(code, 0, code.length());
    }

    @Override
    public SpannableStringBuilder highlightRange(String fullCode, int rangeStart, int rangeEnd) {
        if (fullCode == null || fullCode.isEmpty()) return new SpannableStringBuilder("");
        int start = Math.max(0, rangeStart);
        int end = Math.min(fullCode.length(), rangeEnd);
        if (start >= end) return new SpannableStringBuilder("");

        String sub = fullCode.substring(start, end);
        SpannableStringBuilder ssb = new SpannableStringBuilder(sub);

        apply(ssb, PAT_CODE_BLOCK, sub, colorCode);
        apply(ssb, PAT_INLINE_CODE, sub, colorCode);
        apply(ssb, PAT_HEADER, sub, colorHeader);
        apply(ssb, PAT_QUOTE, sub, colorQuote);
        apply(ssb, PAT_LIST, sub, colorList);
        apply(ssb, PAT_LINK, sub, colorLink);
        apply(ssb, PAT_BOLD, sub, colorBold);
        apply(ssb, PAT_ITALIC, sub, colorItalic);
        apply(ssb, PAT_STRIKETHROUGH, sub, colorStrikethrough);

        // Apply HTML highlighter for raw HTML/CSS mixed in markdown
        SpannableStringBuilder htmlSsb = htmlHighlighter.highlightRange(fullCode, start, end);
        mergeSpans(ssb, htmlSsb, 0);

        // Additionally highlight specific code blocks for CSS explicitly
        Matcher m = PAT_CODE_BLOCK_LANG.matcher(fullCode);
        while (m.find()) {
            String lang = m.group(1).toLowerCase();
            int blockStart = m.start(2);
            int blockEnd = m.end(2);

            if (blockEnd > start && blockStart < end) {
                if (lang.equals("css")) {
                    int hlStart = Math.max(blockStart, start) - blockStart;
                    int hlEnd = Math.min(blockEnd, end) - blockStart;
                    if (hlStart < hlEnd) {
                        String visibleInner = m.group(2).substring(hlStart, hlEnd);
                        SpannableStringBuilder innerSsb = cssHighlighter.highlight(visibleInner);
                        int offset = Math.max(blockStart, start) - start;
                        mergeSpans(ssb, innerSsb, offset);
                    }
                }
            }
        }

        return ssb;
    }

    private void apply(SpannableStringBuilder ssb, Pattern pattern, String code, int color) {
        Matcher m = pattern.matcher(code);
        while (m.find()) {
            applySpan(ssb, m.start(), m.end(), color);
        }
    }

    private void mergeSpans(SpannableStringBuilder target, SpannableStringBuilder source, int offset) {
        if (source == null || offset < 0) return;

        SyntaxHighlightSpan[] spans = source.getSpans(0, source.length(), SyntaxHighlightSpan.class);
        for (SyntaxHighlightSpan span : spans) {
            int s = source.getSpanStart(span) + offset;
            int e = source.getSpanEnd(span) + offset;
            applySpan(target, s, e, span.getForegroundColor(), span.isUnderline());
        }

        ColorPreviewSpan[] colorSpans = source.getSpans(0, source.length(), ColorPreviewSpan.class);
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
