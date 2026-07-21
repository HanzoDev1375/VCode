package com.cocode.vcode.ide.views;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.ViewSelectionToolbarBinding;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * Floating selection action bar shown whenever the editor has a non-empty selection.
 *
 * <p>Displays as a native-style floating PopupWindow above/below the selection.
 * It does NOT dismiss on outside touch — only when the selection is cleared.
 * It follows the selection as the user scrolls.
 */
public class SelectionToolbar {

    private final ViewSelectionToolbarBinding binding;
    private final Context context;
    private CodeEditText editor;
    private final PopupWindow popupWindow;
    private final ClipboardManager clipboardManager;

    // Shadow padding in px — must match what the CardView elevation renders into.
    // We pad the PopupWindow contents so the drop-shadow is not clipped.
    private final int shadowPadding;

    public SelectionToolbar(Context context) {
        this.context = context;
        binding = ViewSelectionToolbarBinding.inflate(LayoutInflater.from(context));

        float density = context.getResources().getDisplayMetrics().density;
        // cardElevation = 8dp → shadow can bleed ~12dp; give 14dp safety margin
        shadowPadding = (int) (14 * density);

        setupTypefaces();
        setupListeners();

        popupWindow = new PopupWindow(
                binding.getRoot(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false /* not focusable — editor keeps keyboard */);

        // Transparent background required for the card's own shadow to show.
        popupWindow.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

        // Do NOT dismiss on outside touch — scroll is an "outside touch" and we
        // want the toolbar to follow the scroll, not disappear.
        popupWindow.setOutsideTouchable(false);

        // Do not clip to screen bounds — the shadow bleed needs room at the edges.
        popupWindow.setClippingEnabled(false);

        // No extra system elevation — the MaterialCardView handles it.
        popupWindow.setElevation(0f);
        popupWindow.setAnimationStyle(R.style.VCodePopupMenuAnimation);

        clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /** Binds this toolbar to an editor. Must be called before show(). */
    public void bindEditor(CodeEditText editor) {
        this.editor = editor;
    }

    /** Shows or repositions the floating toolbar. Safe to call repeatedly. */
    public void show() {
        if (editor == null) return;

        boolean hasPasteData = clipboardManager != null && clipboardManager.hasPrimaryClip();
        binding.btnPaste.setVisibility(hasPasteData ? View.VISIBLE : View.GONE);

        if (!popupWindow.isShowing()) {
            // Show off-screen first so the View can measure itself.
            popupWindow.showAtLocation(editor, Gravity.NO_GRAVITY, -10000, -10000);
        }
        updatePosition();
    }

    /** Hides the toolbar. */
    public void hide() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    /** Returns true if the toolbar is currently showing. */
    public boolean isVisible() {
        return popupWindow.isShowing();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void updatePosition() {
        if (editor == null || !popupWindow.isShowing()) return;

        int selStart = editor.getSelectionStart();
        int selEnd   = editor.getSelectionEnd();
        if (selStart == -1 || selEnd == -1) {
            hide();
            return;
        }

        // Measure the popup content (includes the shadow-padding wrapper).
        binding.getRoot().measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupWidth  = binding.getRoot().getMeasuredWidth();
        int popupHeight = binding.getRoot().getMeasuredHeight();

        float density   = context.getResources().getDisplayMetrics().density;
        int   screenW   = context.getResources().getDisplayMetrics().widthPixels;
        int   screenH   = context.getResources().getDisplayMetrics().heightPixels;
        int   margin    = (int) (8 * density); // visual gap between toolbar and selection

        int firstOffset = Math.min(selStart, selEnd);
        int[] coords    = editor.getCursorScreenCoords(firstOffset);
        int anchorX     = coords[0];
        int anchorYTop  = coords[1];
        int anchorYBot  = coords[2];

        // ── Horizontal ────────────────────────────────────────────────────────
        // Center over the selection start; account for shadow padding on the left.
        int x = anchorX - (popupWidth / 2) + shadowPadding;
        // Clamp so the visible card never leaves the screen (shadow can overflow).
        int minX = -shadowPadding;
        int maxX = screenW - popupWidth + shadowPadding;
        if (x < minX) x = minX;
        if (x > maxX) x = maxX;

        // ── Vertical ──────────────────────────────────────────────────────────
        // Prefer above the selection; fall back to below if not enough room.
        int y = anchorYTop - popupHeight - margin + shadowPadding;
        if (y < 0) {
            // Not enough room above — show below.
            y = anchorYBot + margin - shadowPadding;
        }
        // Clamp to screen bottom.
        int maxY = screenH - popupHeight + shadowPadding;
        if (y > maxY) y = maxY;

        popupWindow.update(x, y, popupWidth, popupHeight);
    }

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
