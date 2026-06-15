package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import android.text.SpannableStringBuilder;

import java.util.regex.Pattern;

public class TsSyntaxHighlighter extends JsSyntaxHighlighter {

    private static final Pattern PAT_TS_KEYWORDS = Pattern.compile(
            "\\b(interface|type|enum|as|readonly|implements|declare|namespace|module|any|string|number|boolean|symbol|unknown|never|public|private|protected|abstract|override)\\b");

    public TsSyntaxHighlighter(Context context) {
        super(context);
    }

    @Override
    public SpannableStringBuilder highlight(String code) {
        if (code == null || code.isEmpty())
            return new SpannableStringBuilder(code != null ? code : "");
        SpannableStringBuilder ssb = new SpannableStringBuilder(code);

        apply(ssb, PAT_OPERATORS, code, colorOperator);
        apply(ssb, PAT_NUMBER, code, colorNumber);
        apply(ssb, PAT_FUNC_NAME, code, colorFunc);
        apply(ssb, PAT_BUILTINS, code, colorBuiltin);
        apply(ssb, PAT_BOOL_NULL, code, colorBoolean);
        apply(ssb, PAT_KEYWORDS, code, colorKeyword);
        apply(ssb, PAT_TS_KEYWORDS, code, colorKeyword); // TS specific keywords
        apply(ssb, PAT_STRING_DQ, code, colorString);
        apply(ssb, PAT_STRING_SQ, code, colorString);
        apply(ssb, PAT_TEMPLATE_LIT, code, colorString);
        apply(ssb, PAT_COMMENT_SL, code, colorComment);
        apply(ssb, PAT_COMMENT_ML, code, colorComment);

        applyLinks(ssb, code);
        applyBrackets(ssb, code);

        return ssb;
    }
}
