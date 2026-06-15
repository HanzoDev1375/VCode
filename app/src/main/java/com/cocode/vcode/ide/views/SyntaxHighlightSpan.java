package com.cocode.vcode.ide.views;

import android.text.style.ForegroundColorSpan;

/**
 * Custom character text markup formatting style span.
 * Inherits from standard platform ForegroundColorSpan types to tag and paint parsed
 * programming tokens within editable code views safely.
 */
public class SyntaxHighlightSpan extends ForegroundColorSpan {

    private final boolean underline;

    /**
     * Constructs a code token foreground colorization style block.
     *
     * @param color The absolute color value hex integer applied across character targets.
     */
    public SyntaxHighlightSpan(int color) {
        this(color, false);
    }

    public SyntaxHighlightSpan(int color, boolean underline) {
        super(color);
        this.underline = underline;
    }

    public boolean isUnderline() {
        return underline;
    }

    @Override
    public void updateDrawState(android.text.TextPaint ds) {
        if (getForegroundColor() != 0) {
            super.updateDrawState(ds);
        }
        if (underline) {
            ds.setUnderlineText(true);
        }
    }
}