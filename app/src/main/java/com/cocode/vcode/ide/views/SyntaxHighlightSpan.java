package com.cocode.vcode.ide.views;

import android.text.style.ForegroundColorSpan;

/**
 * Custom character text markup formatting style span.
 * Inherits from standard platform ForegroundColorSpan types to tag and paint parsed
 * programming tokens within editable code views safely.
 */
public class SyntaxHighlightSpan extends ForegroundColorSpan {

    /**
     * Constructs a code token foreground colorization style block.
     * @param color The absolute color value hex integer applied across character targets.
     */
    public SyntaxHighlightSpan(int color) {
        super(color);
    }
}