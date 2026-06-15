package com.cocode.vcode.ide.core.parser;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FoldSpan extends ReplacementSpan {

    private final String placeholder = " \u2026 "; // " ... "

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        return (int) paint.measureText(placeholder);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        int oldColor = paint.getColor();
        paint.setColor(0xFF888888); // Gray placeholder
        canvas.drawText(placeholder, x, y, paint);
        paint.setColor(oldColor);
    }
}
