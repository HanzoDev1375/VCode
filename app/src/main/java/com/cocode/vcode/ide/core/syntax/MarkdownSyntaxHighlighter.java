package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import android.text.SpannableStringBuilder;
import com.cocode.vcode.ide.R;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownSyntaxHighlighter extends SyntaxHighlighter {

    private static final Pattern PAT_HEADER = Pattern.compile("^#{1,6}\\s+.*$", Pattern.MULTILINE);
    private static final Pattern PAT_BOLD = Pattern.compile("(\\*\\*|__)(.*?)\\1");
    private static final Pattern PAT_ITALIC = Pattern.compile("(?<!\\w)(\\*|_)(?![\\s*_*])(.*?)(?<!\\s)\\1(?!\\w)");
    private static final Pattern PAT_STRIKETHROUGH = Pattern.compile("~~(.*?)~~");
    private static final Pattern PAT_CODE_BLOCK = Pattern.compile("```[\\s\\S]*?```");
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
    }

    @Override
    public SpannableStringBuilder highlight(String code) {
        if (code == null || code.isEmpty())
            return new SpannableStringBuilder(code != null ? code : "");
        SpannableStringBuilder ssb = new SpannableStringBuilder(code);

        apply(ssb, PAT_CODE_BLOCK, code, colorCode);
        apply(ssb, PAT_INLINE_CODE, code, colorCode);
        apply(ssb, PAT_HEADER, code, colorHeader);
        apply(ssb, PAT_QUOTE, code, colorQuote);
        apply(ssb, PAT_LIST, code, colorList);
        apply(ssb, PAT_LINK, code, colorLink);
        apply(ssb, PAT_BOLD, code, colorBold);
        apply(ssb, PAT_ITALIC, code, colorItalic);
        apply(ssb, PAT_STRIKETHROUGH, code, colorStrikethrough);

        return ssb;
    }

    private void apply(SpannableStringBuilder ssb, Pattern pattern, String code, int color) {
        Matcher m = pattern.matcher(code);
        while (m.find()) {
            applySpan(ssb, m.start(), m.end(), color);
        }
    }
}
