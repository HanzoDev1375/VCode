package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import android.text.SpannableStringBuilder;
import com.cocode.vcode.ide.R;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntax highlighter for SVG source code.
 * Similar to HTML highlighter, parses general XML/SVG markup tokens.
 */
public class SvgSyntaxHighlighter extends SyntaxHighlighter {

    private static final Pattern PAT_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern PAT_DOCTYPE = Pattern.compile("<!DOCTYPE[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_XML_DECL = Pattern.compile("<\\?[^>]*\\?>");
    private static final Pattern PAT_TAG_NAME = Pattern.compile("(?<=</?)[\\w-:]+");
    private static final Pattern PAT_BRACKET = Pattern.compile("</?|/?>|>");
    private static final Pattern PAT_ATTR_NAME = Pattern.compile("\\s([\\w:-]+)(?=\\s*=)");
    private static final Pattern PAT_ATTR_VAL = Pattern.compile("=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')");
    private static final Pattern PAT_ENTITY = Pattern.compile("&[#\\w]+;");

    private final int colorComment;
    private final int colorBracket;
    private final int colorTag;
    private final int colorAttrName;
    private final int colorAttrVal;
    private final int colorEntity;

    public SvgSyntaxHighlighter(Context context) {
        super(context);
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

        // Pass 1: Comments (highest priority)
        apply(ssb, PAT_COMMENT, code, colorComment);

        // Pass 2: XML Declarations
        apply(ssb, PAT_XML_DECL, code, colorTag);

        // Pass 3: Global Document Directives
        apply(ssb, PAT_DOCTYPE, code, colorTag);

        // Pass 4: Element block keys
        apply(ssb, PAT_TAG_NAME, code, colorTag);

        // Pass 5: Markup brackets
        apply(ssb, PAT_BRACKET, code, colorBracket);

        // Pass 6: Element attributes
        Matcher attrMatcher = PAT_ATTR_NAME.matcher(code);
        while (attrMatcher.find()) {
            applySpan(ssb, attrMatcher.start(1), attrMatcher.end(1), colorAttrName);
        }

        // Pass 7: Inline attribute field values
        Matcher valMatcher = PAT_ATTR_VAL.matcher(code);
        while (valMatcher.find()) {
            if (valMatcher.group(1) != null) {
                applySpan(ssb, valMatcher.start(1), valMatcher.end(1), colorAttrVal);
            }
        }

        // Pass 8: Character reference entities
        apply(ssb, PAT_ENTITY, code, colorEntity);

        return ssb;
    }

    private void apply(SpannableStringBuilder ssb, Pattern pattern, String code, int color) {
        Matcher m = pattern.matcher(code);
        while (m.find()) {
            applySpan(ssb, m.start(), m.end(), color);
        }
    }
}
