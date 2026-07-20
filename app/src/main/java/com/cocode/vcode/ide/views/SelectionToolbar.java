package com.cocode.vcode.ide.views;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import com.cocode.vcode.ide.databinding.ViewSelectionToolbarBinding;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * Floating selection action bar shown whenever the editor has a non-empty selection.
 *
 * <p>Built as part of the Sora-editor-grade rewrite. Displays as a native-style floating
 * PopupWindow above the selection with horizontal scrolling for actions.
 */
public class SelectionToolbar {

    private final ViewSelectionToolbarBinding binding;
    private final Context context;
    private CodeEditText editor;
    private PopupWindow popupWindow;
    private ClipboardManager clipboardManager;

    public SelectionToolbar(Context context) {
        this.context = context;
        binding = ViewSelectionToolbarBinding.inflate(LayoutInflater.from(context));
        
        setupTypefaces();
        setupListeners();

        popupWindow = new PopupWindow(binding.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false); // not focusable so editor keeps keyboard
        popupWindow.setOutsideTouchable(true);
        // Required for touch events and elevation to work on some API levels
        popupWindow.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        // Remove standard elevation as we handle it in our layout
        popupWindow.setElevation(0);

        clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /**
     * Binds this toolbar to an editor. Must be called before the toolbar is shown.
     */
    public void bindEditor(CodeEditText editor) {
        this.editor = editor;
    }

    /** Shows or updates the floating toolbar. */
    public void show() {
        if (editor == null) return;

        boolean hasPasteData = clipboardManager != null && clipboardManager.hasPrimaryClip();
        binding.btnPaste.setVisibility(hasPasteData ? View.VISIBLE : View.GONE);

        if (!popupWindow.isShowing()) {
            // Show off-screen first to measure, then immediately update
            popupWindow.showAtLocation(editor, android.view.Gravity.NO_GRAVITY, -10000, -10000);
        }
        updatePosition();
    }

    private void updatePosition() {
        if (editor == null || !popupWindow.isShowing()) return;

        // Force layout measurement to get accurate width/height
        binding.getRoot().measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupWidth = binding.getRoot().getMeasuredWidth();
        int popupHeight = binding.getRoot().getMeasuredHeight();

        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();
        if (selStart == -1 || selEnd == -1) {
            hide();
            return;
        }

        // Get the visual bounding box of the selection
        int firstOffset = Math.min(selStart, selEnd);
        int[] coords = editor.getCursorScreenCoords(firstOffset);
        
        int x = coords[0];
        int yTop = coords[1];
        
        // Center horizontally above the start of the selection, clamped to screen bounds
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;

        int finalX = x - (popupWidth / 2);
        if (finalX < 0) finalX = 0;
        if (finalX + popupWidth > screenWidth) finalX = screenWidth - popupWidth;

        int finalY = yTop - popupHeight - (int)(8 * context.getResources().getDisplayMetrics().density); // 8dp margin
        
        // If it goes above the screen, show it below the selection instead
        if (finalY < 0) {
            // Need the bottom of the selection. Approximation:
            int[] endCoords = editor.getCursorScreenCoords(Math.max(selStart, selEnd));
            finalY = endCoords[2] + (int)(8 * context.getResources().getDisplayMetrics().density);
        }

        popupWindow.update(finalX, finalY, popupWidth, popupHeight);
    }

    /** Hides the toolbar. */
    public void hide() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    /** Returns true if the toolbar is currently visible. */
    public boolean isVisible() {
        return popupWindow.isShowing();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void setupTypefaces() {
        FontManager fm = FontManager.getInstance();
        binding.btnCut.setTypeface(fm.getUiMedium(context));
        binding.btnCopy.setTypeface(fm.getUiMedium(context));
        binding.btnPaste.setTypeface(fm.getUiMedium(context));
        binding.btnSelectAll.setTypeface(fm.getUiMedium(context));
    }

    private void setupListeners() {
        binding.btnCut.setOnClickListener(v -> {
            if (editor != null) editor.cutSelection();
        });
        binding.btnCopy.setOnClickListener(v -> {
            if (editor != null) editor.copySelection();
        });
        binding.btnPaste.setOnClickListener(v -> {
            if (editor != null) {
                editor.paste();
                hide();
            }
        });
        binding.btnSelectAll.setOnClickListener(v -> {
            if (editor != null) editor.selectAll();
        });
    }
}
