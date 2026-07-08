package com.cocode.vcode.ide.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

/**
 * Composite container linking the editor workspace elements.
 * Coordinates sizing constraints and horizontal alignments between the vertical line number gutter
 * and the primary editable source code canvas sheet.
 */
public class CodeEditorLayout extends LinearLayout {

    private static final long SYNC_DEBOUNCE_MS = 16; // ~1 frame — enough to batch rapid text changes
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LineNumberView lineNumberView;
    private CodeEditText codeEditText;
    private final Runnable syncRunnable = this::syncLineNumberView;

    public CodeEditorLayout(Context context) {
        super(context);
        init(context);
    }

    public CodeEditorLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CodeEditorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);

        lineNumberView = new LineNumberView(context);
        codeEditText = new CodeEditText(context);

        LayoutParams lineParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        lineNumberView.setLayoutParams(lineParams);

        // Grant expanding weight structures across the primary text field sheet component
        LayoutParams editorParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        codeEditText.setLayoutParams(editorParams);

        int dp12 = (int) (4 * context.getResources().getDisplayMetrics().density);
        codeEditText.setPadding(dp12, dp12, dp12, 0);

        addView(lineNumberView);
        addView(codeEditText);

        lineNumberView.bindEditor(codeEditText);
        // Synchronize scroll shifts from the editor to the line numbers gutter
        // Synchronize scroll shifts from the editor to the line numbers gutter.
        // Only update scrollY — NOT cursorOffset — during scroll to avoid O(n) scan mid-fling.
        codeEditText.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            lineNumberView.setScrollY(scrollY);
        });

        codeEditText.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                syncLineNumberView());

        codeEditText.setOnClickListener(v -> syncLineNumberView());

        // Update gutter measurements in response to typing additions (debounced)
        codeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleSyncLineNumberView();
            }
        });
    }

    private void scheduleSyncLineNumberView() {
        handler.removeCallbacks(syncRunnable);
        handler.postDelayed(syncRunnable, SYNC_DEBOUNCE_MS);
    }

    /**
     * Pumps positioning coordinates and line metrics state values from the editor canvas into the side gutter view.
     */
    private void syncLineNumberView() {
        int lineCount = codeEditText.getLineCount();

        lineNumberView.setLineCount();
        lineNumberView.setLineHeight();
        lineNumberView.setCursorOffset(codeEditText.getSelectionStart());
        lineNumberView.setScrollY(codeEditText.getScrollY());
        lineNumberView.updateGutterWidth(lineCount);
    }

    /**
     * Controls the visibility state configuration mapping for the gutter panel view layer.
     */
    public void setShowLineNumbers(boolean show) {
        if (lineNumberView != null) {
            lineNumberView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public CodeEditText getCodeEditText() {
        return codeEditText;
    }

    public LineNumberView getLineNumberView() {
        return lineNumberView;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Defer synchronization logic until view hierarchy cycles have resolved calculations fully
        post(this::syncLineNumberView);
    }
}