package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import android.text.SpannableStringBuilder;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.utils.ColorParser;
import com.cocode.vcode.ide.views.ColorPreviewSpan;
import android.text.Spanned;

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
    private static final Pattern PAT_COLOR = Pattern.compile("(#(?:[0-9a-fA-F]{3,4}){1,2}\\b|\\b(?:rgb|hsl)a?\\([^)]+\\)|\\b(?i)(?:aliceblue|antiquewhite|aqua|aquamarine|azure|beige|bisque|black|blanchedalmond|blue|blueviolet|brown|burlywood|cadetblue|chartreuse|chocolate|coral|cornflowerblue|cornsilk|crimson|cyan|darkblue|darkcyan|darkgoldenrod|darkgray|darkgreen|darkgrey|darkkhaki|darkmagenta|darkolivegreen|darkorange|darkorchid|darkred|darksalmon|darkseagreen|darkslateblue|darkslategray|darkslategrey|darkturquoise|darkviolet|deeppink|deepskyblue|dimgray|dimgrey|dodgerblue|firebrick|floralwhite|forestgreen|fuchsia|gainsboro|ghostwhite|gold|goldenrod|gray|green|greenyellow|grey|honeydew|hotpink|indianred|indigo|ivory|khaki|lavender|lavenderblush|lawngreen|lemonchiffon|lightblue|lightcoral|lightcyan|lightgoldenrodyellow|lightgray|lightgreen|lightgrey|lightpink|lightsalmon|lightseagreen|lightskyblue|lightslategray|lightslategrey|lightsteelblue|lightyellow|lime|limegreen|linen|magenta|maroon|mediumaquamarine|mediumblue|mediumorchid|mediumpurple|mediumseagreen|mediumslateblue|mediumspringgreen|mediumturquoise|mediumvioletred|midnightblue|mintcream|mistyrose|moccasin|navajowhite|navy|oldlace|olive|olivedrab|orange|orangered|orchid|palegoldenrod|palegreen|paleturquoise|palevioletred|papayawhip|peachpuff|peru|pink|plum|powderblue|purple|rebeccapurple|red|rosybrown|royalblue|saddlebrown|salmon|sandybrown|seagreen|seashell|sienna|silver|skyblue|slateblue|slategray|slategrey|snow|springgreen|steelblue|tan|teal|thistle|tomato|transparent|turquoise|violet|wheat|white|whitesmoke|yellow|yellowgreen)\\b)");
    private static final Pattern PAT_NUMBER_UNIT = Pattern.compile("\\b\\d+(\\.\\d+)?(px|em|rem|%|vh|vw|dvh|dvw|s|ms|deg|rad|fr|ch|ex|vmin|vmax)\\b");
    private static final Pattern PAT_STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'");
    private static final Pattern PAT_IMPORTANT = Pattern.compile("!important");
    private static final Pattern PAT_CSS_VALUE = Pattern.compile("(?<=:)[^;{}]+(?=[;{}])"); // Catches values mapped behind structural colons

    private final int colorComment;
    private final int colorAtRule;
    private final int colorSelector;
    private final int colorProperty;
    private final int colorValue;
    private final int colorNumber;
    private final int colorString;
    private final int colorWarning;

    public CssSyntaxHighlighter(Context context) {
        super(context);
        colorComment = getColor(R.color.vcode_color_comment);
        colorAtRule = getColor(R.color.vcode_color_css_at_rule);
        colorSelector = getColor(R.color.vcode_color_css_selector);
        colorProperty = getColor(R.color.vcode_color_css_property);
        colorValue = getColor(R.color.vcode_color_css_value);
        colorNumber = getColor(R.color.vcode_color_js_number);
        colorString = getColor(R.color.vcode_color_js_string);
        colorWarning = getColor(R.color.vcode_accent_warning);
    }

    @Override
    public SpannableStringBuilder highlight(String code) {
        if (code == null || code.isEmpty())
            return new SpannableStringBuilder(code != null ? code : "");
        SpannableStringBuilder ssb = new SpannableStringBuilder(code);

        // Cascading processing hierarchy: Layer lower priority structural boundaries first,
        // and allow distinct localized elements (strings, colors, comments) to paint over them.
        apply(ssb, PAT_SELECTOR, code, colorSelector);
        apply(ssb, PAT_CSS_VALUE, code, colorValue);
        apply(ssb, PAT_PROPERTY, code, colorProperty);
        apply(ssb, PAT_AT_RULE, code, colorAtRule);
        apply(ssb, PAT_PSEUDO, code, colorValue);
        apply(ssb, PAT_STRING, code, colorString);
        apply(ssb, PAT_NUMBER_UNIT, code, colorNumber);
        applyColors(ssb, code);
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

    private void applyColors(SpannableStringBuilder ssb, String code) {
        Matcher m = PAT_COLOR.matcher(code);
        while (m.find()) {
            applySpan(ssb, m.start(), m.end(), colorNumber);
            Integer colorVal = ColorParser.parse(m.group());
            if (colorVal != null && m.start() < m.end()) {
                ssb.setSpan(
                        new ColorPreviewSpan(colorVal, colorNumber),
                        m.start(),
                        m.start() + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
    }
}