package com.cocode.vcode.ide.views;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.cocode.vcode.ide.databinding.ViewSelectionToolbarBinding;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * Bottom-docked selection action bar shown whenever the editor has a non-empty selection.
 *
 * <p>Built as part of the Sora-editor-grade rewrite (Phase 4). Since {@link CodeEditText} is now
 * a plain {@code View} (not a {@code TextView}), there is no {@code ActionMode} to suppress —
 * this toolbar is simply shown/hidden directly based on selection state.
 *
 * <p>Shows: Cancel (left) | Cut / Copy / Paste / Select All (right).
 * Actions wire directly into {@link CodeEditText}'s mutation API and {@link ClipboardManager}.
 */
public class SelectionToolbar {

    private final ViewSelectionToolbarBinding binding;
    private final Context context;
    private CodeEditText editor;

    public SelectionToolbar(Context context) {
        this.context = context;
        binding = ViewSelectionToolbarBinding.inflate(LayoutInflater.from(context));
        setupTypefaces();
        setupListeners();
    }

    /**
     * Returns the root view of this toolbar (add it to whatever host ViewGroup you like).
     */
    public View getView() {
        return binding.getRoot();
    }

    /**
     * Binds this toolbar to an editor. Must be called before the toolbar is shown.
     * The editor reference is held weakly in spirit — don't hold this toolbar longer than the editor.
     */
    public void bindEditor(CodeEditText editor) {
        this.editor = editor;
    }

    /** Shows the toolbar if it is not already visible. */
    public void show() {
        if (binding.getRoot().getVisibility() != View.VISIBLE) {
            binding.getRoot().setVisibility(View.VISIBLE);
        }
    }

    /** Hides the toolbar. */
    public void hide() {
        binding.getRoot().setVisibility(View.GONE);
    }

    /** Returns true if the toolbar is currently visible. */
    public boolean isVisible() {
        return binding.getRoot().getVisibility() == View.VISIBLE;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void setupTypefaces() {
        FontManager fm = FontManager.getInstance();
        binding.btnCut.setTypeface(fm.getUiMedium(context));
        binding.btnCopy.setTypeface(fm.getUiMedium(context));
        binding.btnPaste.setTypeface(fm.getUiMedium(context));
        binding.btnSelectAll.setTypeface(fm.getUiMedium(context));
        binding.btnCancel.setTypeface(fm.getUiMedium(context));
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
        binding.btnCancel.setOnClickListener(v -> {
            if (editor != null) editor.collapseSelection();
            hide();
        });
    }
}
