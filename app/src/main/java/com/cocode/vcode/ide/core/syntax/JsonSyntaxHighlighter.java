package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import android.text.SpannableStringBuilder;
import com.cocode.vcode.ide.R;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-fidelity syntax parsing tokenizer optimized for JSON structural documents.
 * Utilizes multi-pass analysis mapping to safely paint key strings, structural colons,
 * and sequence separators without incorrectly formatting matching character literals nested inside string values.
 */
public class JsonSyntaxHighlighter extends SyntaxHighlighter {

    // Token verification patterns checking for text string fields, digit layouts, and structural punctuation elements
    private static final Pattern PAT_STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern PAT_NUMBER = Pattern.compile("-?\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b");
    private static final Pattern PAT_BOOLEAN = Pattern.compile("\\b(true|false)\\b");
    private static final Pattern PAT_NULL = Pattern.compile("\\bnull\\b");
    private static final Pattern PAT_BRACKET = Pattern.compile("[\\[\\]{}]");
    private static final Pattern PAT_KEY = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"(?=\\s*:)"); // Positive lookahead identifies key definitions
    private static final Pattern PAT_COLON = Pattern.compile(":");
    private static final Pattern PAT_COMMA = Pattern.compile(",");

    private final int colorKey;
    private final int colorString;
    private final int colorNumber;
    private final int colorBoolean;
    private final int colorNull;
    private final int colorBracket;
    private final int colorColon;
    private final int colorComma;

    public JsonSyntaxHighlighter(Context context) {
        super(context);
        colorKey = getColor(R.color.vcode_color_json_key);
        colorString = getColor(R.color.vcode_color_json_string);
        colorNumber = getColor(R.color.vcode_color_json_number);
        colorBoolean = getColor(R.color.vcode_color_json_boolean);
        colorNull = getColor(R.color.vcode_color_json_null);
        colorBracket = getColor(R.color.vcode_color_json_bracket);
        colorColon = getColor(R.color.vcode_color_json_colon);
        colorComma = getColor(R.color.vcode_color_json_comma);
    }

    @Override
    public SpannableStringBuilder highlight(String code) {
        if (code == null || code.isEmpty())
            return new SpannableStringBuilder(code != null ? code : "");
        SpannableStringBuilder ssb = new SpannableStringBuilder(code);

        // Pass 1: Trace out and log boundaries of all string ranges.
        // This index profile protects characters like ':' or ',' wrapped inside data values from being colored as punctuation delimiters.
        List<int[]> stringRanges = new ArrayList<>();
        Matcher strMatcher = PAT_STRING.matcher(code);
        while (strMatcher.find()) {
            stringRanges.add(new int[]{strMatcher.start(), strMatcher.end()});
        }

        // Pass 2: Color primitive components (safe to handle globally since primitives cannot contain interior text strings)
        apply(ssb, PAT_BRACKET, code, colorBracket);
        apply(ssb, PAT_NUMBER, code, colorNumber);
        apply(ssb, PAT_BOOLEAN, code, colorBoolean);
        apply(ssb, PAT_NULL, code, colorNull);

        // Pass 3: Process colons and commas while verifying their positions exclude locked text areas
        applyOutsideStrings(ssb, PAT_COLON, code, colorColon, stringRanges);
        applyOutsideStrings(ssb, PAT_COMMA, code, colorComma, stringRanges);

        // Pass 4: Apply general color theme layouts across strings first, then overlay specialized key styles onto descriptive keys
        apply(ssb, PAT_STRING, code, colorString);
        apply(ssb, PAT_KEY, code, colorKey);

        return ssb;
    }

    private void apply(SpannableStringBuilder ssb, Pattern pattern, String code, int color) {
        Matcher m = pattern.matcher(code);
        while (m.find()) {
            applySpan(ssb, m.start(), m.end(), color);
        }
    }

    /**
     * Inspects target punctuation layout coordinates to prevent applying style changes inside active string fields.
     */
    private void applyOutsideStrings(SpannableStringBuilder ssb, Pattern pattern,
                                     String code, int color, List<int[]> stringRanges) {
        Matcher m = pattern.matcher(code);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (!isInsideString(start, end, stringRanges)) {
                applySpan(ssb, start, end, color);
            }
        }
    }

    /**
     * Cross-checks coordinates against the index array list to evaluate if a match sits inside literal string quotes.
     */
    private boolean isInsideString(int start, int end, List<int[]> ranges) {
        for (int[] range : ranges) {
            if (start >= range[0] && end <= range[1]) return true;
        }
        return false;
    }
}