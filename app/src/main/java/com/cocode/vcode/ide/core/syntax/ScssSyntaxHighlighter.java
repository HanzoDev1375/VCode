package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import android.text.SpannableStringBuilder;

import java.util.regex.Pattern;

public class ScssSyntaxHighlighter extends CssSyntaxHighlighter {

    private static final Pattern PAT_SCSS_VAR = Pattern.compile("\\$[a-zA-Z0-9_-]+");

    public ScssSyntaxHighlighter(Context context) {
        super(context);
    }

    @Override
    public SpannableStringBuilder highlight(String code) {
        if (code == null || code.isEmpty())
            return new SpannableStringBuilder(code != null ? code : "");
        SpannableStringBuilder ssb = new SpannableStringBuilder(code);

        apply(ssb, PAT_SELECTOR, code, colorSelector);
        apply(ssb, PAT_CSS_VALUE, code, colorValue);
        apply(ssb, PAT_PROPERTY, code, colorProperty);
        apply(ssb, PAT_AT_RULE, code, colorAtRule);
        apply(ssb, PAT_SCSS_VAR, code, colorProperty); // Variables
        apply(ssb, PAT_PSEUDO, code, colorValue);
        apply(ssb, PAT_STRING, code, colorString);
        apply(ssb, PAT_NUMBER_UNIT, code, colorNumber);
        applyColors(ssb, code);
        apply(ssb, PAT_HEX_COLOR, code, colorNumber);
        apply(ssb, PAT_IMPORTANT, code, colorWarning);
        apply(ssb, PAT_COMMENT, code, colorComment);

        applyLinks(ssb, code);
        applyBrackets(ssb, code);

        return ssb;
    }
}
