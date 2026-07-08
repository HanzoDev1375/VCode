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
    private final int currentLine = 1;
    private int gutterWidth = 0;
    private CodeEditText editor;
    private int cursorOffset = 0;

    // Perf: cache cursor-line scan result to avoid O(n) scan every draw frame
    private int cachedCursorOffset = -1;
    private int cachedCursorLine = 1;
    // Perf: cache firstLine scan result
    private int cachedFirstLineStart = -1;
    private int cachedFirstLogicalLine = 1;
    // Perf: reuse char buffer to avoid String alloc per line in draw loop
    private final char[] lineNumBuffer = new char[6];
    // Perf: cache color lookups (ContextCompat.getColor is not free)
    private int colorPrimary;
    private int colorSecondary;
    private boolean colorsLoaded = false;


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

        // Pre-load colors once — ContextCompat.getColor() is non-trivial
        colorPrimary = ContextCompat.getColor(getContext(), R.color.vcode_text_primary);
        colorSecondary = ContextCompat.getColor(getContext(), R.color.vcode_line_number_text);
        colorsLoaded = true;
    }

    public void setCursorOffset(int cursorOffset) {
        if (this.cursorOffset != cursorOffset) {
            this.cursorOffset = cursorOffset;
            cachedCursorOffset = -1; // invalidate cursor-line cache
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (editor == null || editor.getLayout() == null || editor.getText() == null) return;

        // Render sidebar background sheet strip bounds
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        // Render the sharp edge vertical separation divider string rule
        canvas.drawLine(getWidth() - DIVIDER_WIDTH_PX, 0,
                getWidth() - DIVIDER_WIDTH_PX, getHeight(), dividerPaint);

        // Use pre-loaded colors — avoids ContextCompat.getColor() on every frame
        int _colorPrimary = colorsLoaded ? colorPrimary : ContextCompat.getColor(getContext(), R.color.vcode_text_primary);
        int _colorSecondary = colorsLoaded ? colorSecondary : ContextCompat.getColor(getContext(), R.color.vcode_line_number_text);

        float textX = getWidth() - DIVIDER_WIDTH_PX - dpToPx(4);

        android.text.Layout layout = editor.getLayout();
        // Perf: use CharSequence directly — avoids a full String copy of the document on every draw frame
        CharSequence textSeq = editor.getText();
        if (textSeq == null) return;
        int textLen = textSeq.length();
        int paddingTop = editor.getPaddingTop();
        int scrollY = editor.getScrollY();

        // Ask the layout engine for the exact row index range currently in view
        int firstVisibleLine = layout.getLineForVertical(scrollY);
        int lastVisibleLine = layout.getLineForVertical(scrollY + getHeight());

        // Perf: cache cursor-line O(n) scan — reuse if cursorOffset hasn't changed
        int activeLogicalLine;
        if (cursorOffset == cachedCursorOffset) {
            activeLogicalLine = cachedCursorLine;
        } else {
            activeLogicalLine = 1;
            for (int j = 0; j < cursorOffset && j < textLen; j++) {
                if (textSeq.charAt(j) == '\n') activeLogicalLine++;
            }
            cachedCursorOffset = cursorOffset;
            cachedCursorLine = activeLogicalLine;
        }

        // Perf: cache firstLine O(n) scan — reuse if firstLineStart hasn't changed
        int firstLineStart = layout.getLineStart(firstVisibleLine);
        int logicalLine;
        if (firstLineStart == cachedFirstLineStart) {
            logicalLine = cachedFirstLogicalLine;
        } else {
            logicalLine = 1;
            for (int j = 0; j < firstLineStart && j < textLen; j++) {
                if (textSeq.charAt(j) == '\n') logicalLine++;
            }
            cachedFirstLineStart = firstLineStart;
            cachedFirstLogicalLine = logicalLine;
        }

        int scanOffset = firstLineStart;
        for (int i = firstVisibleLine; i <= lastVisibleLine; i++) {
            int lineStart = layout.getLineStart(i);

            // Advance scanOffset to lineStart, counting newlines via CharSequence
            while (scanOffset < lineStart && scanOffset < textLen) {
                if (textSeq.charAt(scanOffset) == '\n') logicalLine++;
                scanOffset++;
            }

            boolean isNewLogicalLine = (lineStart == 0) || (lineStart > 0 && lineStart <= textLen && textSeq.charAt(lineStart - 1) == '\n');

            // Extract the baseline Y coordinate to position indices on target with editor code lines
            int baselineY = layout.getLineBaseline(i);
            float y = paddingTop + baselineY - scrollY;

            if (isNewLogicalLine) {
                // Emphasize text color if this is the primary active line
                boolean isActiveLine = (logicalLine == activeLogicalLine);
                numberPaint.setColor(isActiveLine ? _colorPrimary : _colorSecondary);
                // Perf: use char-buffer drawText to avoid String.valueOf() allocation per line
                int _s = fillLineNum(logicalLine, lineNumBuffer);
                canvas.drawText(lineNumBuffer, _s, lineNumBuffer.length - _s, textX, y, numberPaint);
            }
        }
    }

    /**
     * Pairs up an edit view text field instance to synchronize font metrics configurations.
     */
    public void bindEditor(CodeEditText editor) {
        this.editor = editor;
        // Invalidate all caches when editor changes
        cachedCursorOffset = -1;
        cachedCursorLine = 1;
        cachedFirstLineStart = -1;
        cachedFirstLogicalLine = 1;
        if (editor != null && editor.getTypeface() != null) {
            numberPaint.setTypeface(editor.getTypeface());
        }
        invalidate();
    }

    /**
     * Writes an integer into a pre-allocated char[] from the right, returning the start index.
     * Avoids String.valueOf() + allocation per visible line in onDraw.
     */
    private int fillLineNum(int num, char[] buf) {
        int pos = buf.length;
        do {
            buf[--pos] = (char) ('0' + (num % 10));
            num /= 10;
        } while (num > 0);
        return pos;
    }

    public void setLineCount() {
        // invalidate is driven by syncComplete()
    }

    public void setScrollY(int scrollY) {
        invalidate();
    }



    public void setLineHeight() {
        // invalidate is driven by syncComplete()
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