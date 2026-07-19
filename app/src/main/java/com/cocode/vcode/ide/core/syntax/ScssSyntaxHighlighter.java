package com.cocode.vcode.ide.core.syntax;

import android.content.Context;

public class ScssSyntaxHighlighter extends CssSyntaxHighlighter {

    public ScssSyntaxHighlighter(Context context) {
        super(context);
    }

    @Override
    protected boolean isWordStart(char c) {
        return super.isWordStart(c) || c == '$';
    }

    @Override
    protected boolean isWordPart(char c) {
        return super.isWordPart(c) || c == '$';
    }
}
