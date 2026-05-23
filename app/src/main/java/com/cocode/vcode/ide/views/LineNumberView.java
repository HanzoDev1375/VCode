package com.cocode.vcode.ide.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;

/**
 * Vertical line layout margin gutter designed for the editor hierarchy view.
 * Utilizes low-level canvas drawing pipelines to display row counts index identifiers,
 * tracking baseline offsets to follow edit rows seamlessly.
 */
public class LineNumberView extends View {

    private static final int DIVIDER_WIDTH_PX = 1;
    private Paint numberPaint;
    private Paint bgPaint;
    private Paint dividerPaint;
    private int currentLine = 1;
    private int gutterWidth = 0;
    private CodeEditText editor;

    public LineNumberView(Context context) {
        super(context);
        init();
    }

    public LineNumberView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineNumberView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(ContextCompat.getColor(getContext(), R.color.vcode_line_number_bg));

        numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        numberPaint.setTextSize(spToPx(13));
        numberPaint.setTextAlign(Paint.Align.RIGHT);

        dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setColor(ContextCompat.getColor(getContext(), R.color.vcode_divider));
        dividerPaint.setStrokeWidth(DIVIDER_WIDTH_PX);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (editor == null || editor.getLayout() == null) return;

        // Render sidebar background sheet strip bounds
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        // Render the sharp edge vertical separation divider string rule
        canvas.drawLine(getWidth() - DIVIDER_WIDTH_PX, 0,
                getWidth() - DIVIDER_WIDTH_PX, getHeight(), dividerPaint);

        int colorPrimary = ContextCompat.getColor(getContext(), R.color.vcode_text_primary);
        int colorSecondary = ContextCompat.getColor(getContext(), R.color.vcode_line_number_text);

        float textX = getWidth() - DIVIDER_WIDTH_PX - dpToPx(4);

        android.text.Layout layout = editor.getLayout();
        int paddingTop = editor.getPaddingTop();
        int scrollY = editor.getScrollY();

        // Ask the layout engine for the exact row index range currently in view
        int firstVisibleLine = layout.getLineForVertical(scrollY);
        int lastVisibleLine = layout.getLineForVertical(scrollY + getHeight());

        for (int i = firstVisibleLine; i <= lastVisibleLine; i++) {
            int lineNumber = i + 1;

            // Extract the baseline Y coordinate to position indices on target with editor code lines
            int baselineY = layout.getLineBaseline(i);
            float y = paddingTop + baselineY - scrollY;

            // Emphasize text color parameters if the index matches the active editing row
            numberPaint.setColor(lineNumber == currentLine ? colorPrimary : colorSecondary);

            canvas.drawText(String.valueOf(lineNumber), textX, y, numberPaint);
        }
    }

    /**
     * Pairs up an edit view text field instance to synchronize font metrics configurations.
     */
    public void bindEditor(CodeEditText editor) {
        this.editor = editor;
        if (editor != null && editor.getTypeface() != null) {
            numberPaint.setTypeface(editor.getTypeface());
        }
        invalidate();
    }

    public void setLineCount() {
        invalidate();
    }

    public void setCurrentLine(int currentLine) {
        this.currentLine = currentLine;
        invalidate();
    }

    public void setScrollY(int scrollY) {
        invalidate();
    }

    public void setLineHeight() {
        invalidate();
    }

    /**
     * Monitors changes in overall digit lengths thresholds to expand gutter widths safely.
     * Prevents infinite measuring update cycles by gating execution behind distinct state changes.
     */
    public void updateGutterWidth(int maxLines) {
        int digits = String.valueOf(maxLines).length();
        digits = Math.max(digits, 2); // Enforce a minimum 2-digit column layout base width

        int newGutterWidth = (int) (numberPaint.measureText("0") * digits + dpToPx(8) * 2 + DIVIDER_WIDTH_PX);

        // Layout calls trigger only when changes are confirmed, eliminating jitter loops
        if (gutterWidth != newGutterWidth) {
            gutterWidth = newGutterWidth;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = gutterWidth > 0 ? gutterWidth : (int) dpToPx(40);
        setMeasuredDimension(w, MeasureSpec.getSize(heightMeasureSpec));
    }

    private float dpToPx(float dp) {
        return dp * getContext().getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getContext().getResources().getDisplayMetrics().scaledDensity;
    }
}