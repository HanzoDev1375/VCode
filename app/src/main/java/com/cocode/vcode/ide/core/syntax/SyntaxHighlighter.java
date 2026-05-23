package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import androidx.core.content.ContextCompat;
import com.cocode.vcode.ide.views.SyntaxHighlightSpan;

/**
 * Base abstract class for real-time code token styling engine.
 * Provides utility helpers to extract resource theme colors and safely apply
 * text styling spans to document ranges without risking index out of bounds exceptions.
 */
public abstract class SyntaxHighlighter {

    protected final Context context;

    public SyntaxHighlighter(Context context) {
        // Prevent context memory leaks by binding to the application scope
        this.context = context.getApplicationContext();
    }

    /**
     * Parses a raw code string into a styled Spannable representation matching language grammar patterns.
     * @param code The unstyled source code text character sequence.
     * @return A SpannableStringBuilder containing structural color spans applied to text ranges.
     */
    public abstract SpannableStringBuilder highlight(String code);

    /**
     * Injects a syntax styling span across a precise structural region of the text buffer.
     * Incorporates safety boundary filtering checks to discard invalid or overlapping range requests.
     */
    protected void applySpan(SpannableStringBuilder ssb, int start, int end, int color) {
        if (ssb == null || start < 0 || end > ssb.length() || start >= end) return;
        ssb.setSpan(
                new SyntaxHighlightSpan(color),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }

    /**
     * Resolves an internal application theme color resource identifier to its hex integer value.
     */
    protected int getColor(int resId) {
        return ContextCompat.getColor(context, resId);
    }
}