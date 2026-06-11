package com.cocode.vcode.ide.views;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

public class ColorPreviewSpan extends ReplacementSpan {

    private final int previewColor;
    private final int textColor;
    private int width;

    public ColorPreviewSpan(int previewColor, int textColor) {
        this.previewColor = previewColor;
        this.textColor = textColor;
    }

    public int getPreviewColor() {
        return previewColor;
    }

    public int getTextColor() {
        return textColor;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        float charWidth = paint.measureText(text, start, end);
        float textSize = paint.getTextSize();
        int circleRadius = (int) (textSize * 0.35f);
        int padding = (int) (textSize * 0.25f);
        
        this.width = (int) (circleRadius * 2 + padding + charWidth);
        return this.width;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        float textSize = paint.getTextSize();
        float circleRadius = textSize * 0.35f;
        float padding = textSize * 0.25f;
        
        float circleX = x + circleRadius;
        // Vertically center the circle based on the text baseline and ascent/descent
        float circleY = y + (paint.ascent() + paint.descent()) / 2f;
        
        int oldColor = paint.getColor();
        Paint.Style oldStyle = paint.getStyle();
        
        // Draw the color circle
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(previewColor);
        canvas.drawCircle(circleX, circleY, circleRadius, paint);
        
        // Draw a subtle border for contrast (e.g. if color is white)
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb(50, 128, 128, 128)); // Light gray border
        paint.setStrokeWidth(textSize * 0.05f);
        canvas.drawCircle(circleX, circleY, circleRadius, paint);
        
        // Draw the text (the character we replaced, e.g. '#')
        paint.setStyle(oldStyle);
        paint.setColor(textColor);
        canvas.drawText(text, start, end, x + circleRadius * 2 + padding, y, paint);
        
        // Restore old color just in case
        paint.setColor(oldColor);
    }
}
