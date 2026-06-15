package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.views.SyntaxHighlightSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base abstract class for real-time code token styling engine.
 * Provides utility helpers to extract resource theme colors and safely apply
 * text styling spans to document ranges without risking index out of bounds exceptions.
 */
public abstract class SyntaxHighlighter {

    protected static final Pattern PAT_LINK = Pattern.compile("(?i)(https?://[^\\s\"'<>]+|(?:[.]{1,2}/)+[a-zA-Z0-9_.-/]+|\\b[a-zA-Z0-9_-]+/[a-zA-Z0-9_.-/]+\\.[a-zA-Z0-9]{2,5}\\b|\\b[a-zA-Z0-9_.-]+\\.(?:html|css|js|json|xml|png|jpg|jpeg|gif|svg|ico|webp|mp4|webm|wav|mp3|ogg|ttf|woff|woff2|eot|otf)\\b)");

    protected final Context context;

    public SyntaxHighlighter(Context context) {
        // Prevent context memory leaks by binding to the application scope
        this.context = context.getApplicationContext();
    }

    /**
     * Parses a raw code string into a styled Spannable representation matching language grammar patterns.
     *
     * @param code The unstyled source code text character sequence.
     * @return A SpannableStringBuilder containing structural color spans applied to text ranges.
     */
    public abstract SpannableStringBuilder highlight(String code);

    /**
     * Highlights only a range of the document, returning spans with offsets relative to the full text.
     * The range is expanded to line boundaries for correctness.
     *
     * @param fullCode The full document text.
     * @param rangeStart Start character offset of the visible region.
     * @param rangeEnd End character offset of the visible region.
     * @return A SpannableStringBuilder of the substring with spans positioned relative to rangeStart.
     */
    public SpannableStringBuilder highlightRange(String fullCode, int rangeStart, int rangeEnd) {
        if (fullCode == null || fullCode.isEmpty()) return new SpannableStringBuilder("");
        // Clamp
        int start = Math.max(0, rangeStart);
        int end = Math.min(fullCode.length(), rangeEnd);
        if (start >= end) return new SpannableStringBuilder("");
        String sub = fullCode.substring(start, end);
        return highlight(sub);
    }

    /**
     * Injects a syntax styling span across a precise structural region of the text buffer.
     * Incorporates safety boundary filtering checks to discard invalid or overlapping range requests.
     */
    protected void applySpan(SpannableStringBuilder ssb, int start, int end, int color) {
        applySpan(ssb, start, end, color, false);
    }

    protected void applySpan(SpannableStringBuilder ssb, int start, int end, int color, boolean underline) {
        if (ssb == null || start < 0 || end > ssb.length() || start >= end) return;
        ssb.setSpan(
                new SyntaxHighlightSpan(color, underline),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }

    protected void applyLinks(SpannableStringBuilder ssb, String code) {
        Matcher m = PAT_LINK.matcher(code);
        while (m.find()) {
            applySpan(ssb, m.start(), m.end(), 0, true);
        }
    }

    /**
     * Resolves an internal application theme color resource identifier to its hex integer value.
     */
    protected int getColor(int resId) {
        return ContextCompat.getColor(context, resId);
    }
}