package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import android.text.SpannableStringBuilder;
import com.cocode.vcode.ide.R;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Token syntax highlighter tailored for Cascading Style Sheets (.css syntax configurations).
 * Implements regex processing filters to classify variables, style parameters, values, rulesets, and units.
 */
public class CssSyntaxHighlighter extends SyntaxHighlighter {

    // RegEx rules capturing core CSS syntactic patterns
    private static final Pattern PAT_COMMENT = Pattern.compile("/\\*[\\s\\S]*?\\*/", Pattern.DOTALL);
    private static final Pattern PAT_AT_RULE = Pattern.compile("@[\\w-]+");
    private static final Pattern PAT_PSEUDO = Pattern.compile("::?[\\w-]+(?:\\([^)]*\\))?");
    private static final Pattern PAT_SELECTOR = Pattern.compile("[^{}]+(?=\\s*\\{)"); // Matches everything leading to an opening bracket
    private static final Pattern PAT_PROPERTY = Pattern.compile("(?<=[{;]\\s{0,20})[\\w-]+(?=\\s*:)"); // Isolates property name parameters
    private static final Pattern PAT_HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{3,8}\\b");
    private static final Pattern PAT_NUMBER_UNIT = Pattern.compile("\\b\\d+(\\.\\d+)?(px|em|rem|%|vh|vw|dvh|dvw|s|ms|deg|rad|fr|ch|ex|vmin|vmax)\\b");
    private static final Pattern PAT_STRING = Pattern.compile("\"[^\"]*\"|'[^']*'");
    private static final Pattern PAT_IMPORTANT = Pattern.compile("!important");
    private static final Pattern PAT_CSS_VALUE = Pattern.compile("(?<=:)[^;{}]+(?=[;{}])"); // Catches values mapped behind structural colons

    public CssSyntaxHighlighter(Context context) {
        super(context);
    }

    @Override
    public SpannableStringBuilder highlight(String code) {
        if (code == null || code.isEmpty())
            return new SpannableStringBuilder(code != null ? code : "");
        SpannableStringBuilder ssb = new SpannableStringBuilder(code);

        int colorComment = getColor(R.color.vcode_color_comment);
        int colorAtRule = getColor(R.color.vcode_color_css_at_rule);
        int colorSelector = getColor(R.color.vcode_color_css_selector);
        int colorProperty = getColor(R.color.vcode_color_css_property);
        int colorValue = getColor(R.color.vcode_color_css_value);
        int colorNumber = getColor(R.color.vcode_color_js_number);
        int colorString = getColor(R.color.vcode_color_js_string);
        int colorWarning = getColor(R.color.vcode_accent_warning);

        // Cascading processing hierarchy: Layer lower priority structural boundaries first,
        // and allow distinct localized elements (strings, colors, comments) to paint over them.
        apply(ssb, PAT_SELECTOR, code, colorSelector);
        apply(ssb, PAT_CSS_VALUE, code, colorValue);
        apply(ssb, PAT_PROPERTY, code, colorProperty);
        apply(ssb, PAT_AT_RULE, code, colorAtRule);
        apply(ssb, PAT_PSEUDO, code, colorValue);
        apply(ssb, PAT_STRING, code, colorString);
        apply(ssb, PAT_NUMBER_UNIT, code, colorNumber);
        apply(ssb, PAT_HEX_COLOR, code, colorNumber);
        apply(ssb, PAT_IMPORTANT, code, colorWarning);
        apply(ssb, PAT_COMMENT, code, colorComment); // Comments retain maximum layout priority hierarchy

        return ssb;
    }

    private void apply(SpannableStringBuilder ssb, Pattern pattern, String code, int color) {
        Matcher m = pattern.matcher(code);
        while (m.find()) {
            applySpan(ssb, m.start(), m.end(), color);
        }
    }
}