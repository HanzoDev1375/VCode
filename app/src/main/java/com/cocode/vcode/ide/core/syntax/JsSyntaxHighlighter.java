package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import android.text.SpannableStringBuilder;
import com.cocode.vcode.ide.R;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core grammar tokenizer engine optimized for JavaScript (ECMAScript syntax specifications).
 * Applies layered execution passes across text blocks to differentiate operations, definitions,
 * object structures, text variables, and structural statements.
 */
public class JsSyntaxHighlighter extends SyntaxHighlighter {

    // RegEx definitions classifying core JS lexical tokens
    private static final Pattern PAT_COMMENT_ML = Pattern.compile("/\\*[\\s\\S]*?\\*/", Pattern.DOTALL);
    private static final Pattern PAT_COMMENT_SL = Pattern.compile("//[^\n]*");
    private static final Pattern PAT_TEMPLATE_LIT = Pattern.compile("`(?:[^`\\\\]|\\\\.|\n)*`", Pattern.DOTALL);
    private static final Pattern PAT_STRING_DQ = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern PAT_STRING_SQ = Pattern.compile("'(?:[^'\\\\]|\\\\.)*'");
    private static final Pattern PAT_KEYWORDS = Pattern.compile(
            "\\b(var|let|const|function|return|if|else|for|while|do|switch|case|break|" +
                    "continue|new|delete|typeof|instanceof|in|of|class|extends|import|export|" +
                    "default|async|await|try|catch|finally|throw|void|yield|this|super)\\b");
    private static final Pattern PAT_BOOL_NULL = Pattern.compile(
            "\\b(true|false|null|undefined|NaN|Infinity)\\b");
    private static final Pattern PAT_BUILTINS = Pattern.compile(
            "\\b(console|document|window|Math|JSON|Array|Object|String|Number|Boolean|" +
                    "Promise|fetch|setTimeout|setInterval|clearTimeout|clearInterval|" +
                    "localStorage|sessionStorage|navigator|location|history|alert|confirm|" +
                    "prompt|Symbol|Map|Set|WeakMap|WeakSet|Proxy|Reflect|Error|TypeError|" +
                    "RangeError|parseInt|parseFloat|isNaN|isFinite|encodeURIComponent|" +
                    "decodeURIComponent|requestAnimationFrame|cancelAnimationFrame)\\b");
    private static final Pattern PAT_NUMBER = Pattern.compile(
            "\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?([eE][+-]?\\d+)?)\\b");
    private static final Pattern PAT_OPERATORS = Pattern.compile(
            "[+\\-*/%=!<>&|^~?:]+");
    private static final Pattern PAT_FUNC_NAME = Pattern.compile(
            "\\b([a-zA-Z_$][\\w$]*)(?=\\s*\\()"); // Identifies labels followed directly by call parentheses

    private final int colorComment;
    private final int colorString;
    private final int colorKeyword;
    private final int colorBoolean;
    private final int colorBuiltin;
    private final int colorNumber;
    private final int colorOperator;
    private final int colorFunc;

    public JsSyntaxHighlighter(Context context) {
        super(context);
        colorComment = getColor(R.color.vcode_color_js_comment);
        colorString = getColor(R.color.vcode_color_js_string);
        colorKeyword = getColor(R.color.vcode_color_js_keyword);
        colorBoolean = getColor(R.color.vcode_color_js_boolean);
        colorBuiltin = getColor(R.color.vcode_color_js_function);
        colorNumber = getColor(R.color.vcode_color_js_number);
        colorOperator = getColor(R.color.vcode_color_js_operator);
        colorFunc = getColor(R.color.vcode_color_js_function);
    }

    @Override
    public SpannableStringBuilder highlight(String code) {
        if (code == null || code.isEmpty())
            return new SpannableStringBuilder(code != null ? code : "");
        SpannableStringBuilder ssb = new SpannableStringBuilder(code);

        // CRITICAL SEQUENCE ORDERING: Highlights run out from lowest to highest layout priority.
        // Broad elements are drawn early, allowing precise tokens (like text literals and comments)
        // to overlay and paint over any matching internal operators or identifiers cleanly.
        apply(ssb, PAT_OPERATORS, code, colorOperator);
        apply(ssb, PAT_NUMBER, code, colorNumber);
        apply(ssb, PAT_FUNC_NAME, code, colorFunc);
        apply(ssb, PAT_BUILTINS, code, colorBuiltin);
        apply(ssb, PAT_BOOL_NULL, code, colorBoolean);
        apply(ssb, PAT_KEYWORDS, code, colorKeyword);
        apply(ssb, PAT_STRING_DQ, code, colorString);
        apply(ssb, PAT_STRING_SQ, code, colorString);
        apply(ssb, PAT_TEMPLATE_LIT, code, colorString);
        apply(ssb, PAT_COMMENT_SL, code, colorComment); // Line comments paint over text strings safely
        apply(ssb, PAT_COMMENT_ML, code, colorComment); // Block comments retain maximum overlay layout priority

        return ssb;
    }

    private void apply(SpannableStringBuilder ssb, Pattern pattern, String code, int color) {
        Matcher m = pattern.matcher(code);
        while (m.find()) {
            applySpan(ssb, m.start(), m.end(), color);
        }
    }
}