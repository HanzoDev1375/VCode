package com.cocode.vcode.ide.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.search.SearchEngine;
import com.cocode.vcode.ide.core.search.SearchResult;
import com.cocode.vcode.ide.databinding.ViewFindReplaceBinding;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Embedded search and text substitution dashboard component.
 * Coordinates user query updates through regex/token filters on thread pools,
 * rendering color highlights above matching entries with look-ahead viewport centering.
 */
public class FindReplaceBar extends LinearLayout {

    private static final long DEBOUNCE_MS = 300;

    private final SearchEngine searchEngine = new SearchEngine();
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());

    private final ViewFindReplaceBinding binding;
    private final int highlightColor;
    private final int activeHighlightColor;
    private CodeEditText editor;
    private List<SearchResult> results = new ArrayList<>();
    private int currentIndex = -1;
    private int activeSearchId = 0;
    private boolean caseSensitive = false;
    private boolean wholeWord = false;
    private boolean useRegex = false;

    public FindReplaceBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        binding = ViewFindReplaceBinding.inflate(LayoutInflater.from(context), this, true);

        highlightColor = ContextCompat.getColor(context, R.color.vcode_selection_color);
        activeHighlightColor = ContextCompat.getColor(context, R.color.vcode_accent_warning);

        setupTypefaces(context);
        setupListeners();
    }

    private void setupTypefaces(Context context) {
        binding.etSearch.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.etReplace.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.btnToggleCase.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.btnToggleRegex.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.btnToggleWord.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.tvMatchCount.setTypeface(FontManager.getInstance().getUiFont(context));
        binding.btnReplace.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.btnReplaceAll.setTypeface(FontManager.getInstance().getUiSemiBold(context));
    }

    private void setupListeners() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleSearch();
            }
        });

        binding.btnPrev.setOnClickListener(v -> navigatePrev());
        binding.btnNext.setOnClickListener(v -> navigateNext());
        binding.btnClose.setOnClickListener(v -> slideUp());
        binding.btnReplace.setOnClickListener(v -> replace());
        binding.btnReplaceAll.setOnClickListener(v -> replaceAll());

        binding.btnToggleCase.setOnClickListener(v -> {
            caseSensitive = !caseSensitive;
            updateToggle(binding.btnToggleCase, caseSensitive);
            scheduleSearch();
        });

        binding.btnToggleWord.setOnClickListener(v -> {
            wholeWord = !wholeWord;
            updateToggle(binding.btnToggleWord, wholeWord);
            scheduleSearch();
        });

        binding.btnToggleRegex.setOnClickListener(v -> {
            useRegex = !useRegex;
            updateToggle(binding.btnToggleRegex, useRegex);
            scheduleSearch();
        });
    }

    public void setEditor(CodeEditText editor) {
        this.editor = editor;
    }

    public void navigateNext() {
        if (results.isEmpty()) return;
        currentIndex = (currentIndex + 1) % results.size();
        scrollToCurrentResult();
    }

    public void navigatePrev() {
        if (results.isEmpty()) return;
        currentIndex = (currentIndex - 1 + results.size()) % results.size();
        scrollToCurrentResult();
    }

    /**
     * Swaps out the currently selected text match snippet with the replacement configuration data.
     */
    public void replace() {
        if (editor == null || results.isEmpty() || currentIndex < 0) return;
        SearchResult cur = results.get(currentIndex);
        String replacement = binding.etReplace.getText().toString();

        Objects.requireNonNull(editor.getText()).replace(cur.absoluteStart, cur.absoluteEnd, replacement);
        scheduleSearch();
    }

    /**
     * Replaces every single identified match down the document text buffer.
     * Evaluates files working backwards to keep modifications from invalidating upcoming indices.
     */
    public void replaceAll() {
        if (editor == null || results.isEmpty()) return;
        String replacement = binding.etReplace.getText().toString();

        // Note: Looping backwards prevents shifting index parameters from invalidating text boundaries downstream
        for (int i = results.size() - 1; i >= 0; i--) {
            SearchResult r = results.get(i);
            Objects.requireNonNull(editor.getText()).replace(r.absoluteStart, r.absoluteEnd, replacement);
        }
        scheduleSearch();
    }

    public void slideDown() {
        if (getVisibility() == VISIBLE) return;

        if (getParent() instanceof ViewGroup) {
            android.transition.AutoTransition transition = new android.transition.AutoTransition();
            transition.setDuration(200);
            transition.setInterpolator(new android.view.animation.DecelerateInterpolator());
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) getParent(), transition);
        }

        setVisibility(VISIBLE);
        binding.etSearch.requestFocus();
    }

    public void slideUp() {
        if (getVisibility() == GONE) return;

        if (getParent() instanceof ViewGroup) {
            android.transition.AutoTransition transition = new android.transition.AutoTransition();
            transition.setDuration(200);
            transition.setInterpolator(new android.view.animation.AccelerateInterpolator());
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) getParent(), transition);
        }

        setVisibility(GONE);
        clearHighlights();
    }

    private void scheduleSearch() {
        debounceHandler.removeCallbacksAndMessages(null);
        debounceHandler.postDelayed(this::runSearch, DEBOUNCE_MS);
    }

    /**
     * Executes queries parsing systems on async workers to preserve smooth ui presentation flows.
     */
    private void runSearch() {
        if (editor == null) return;
        String query = binding.etSearch.getText().toString();
        String text = Objects.requireNonNull(editor.getText()).toString();

        if (query.isEmpty()) {
            clearHighlights();
            results.clear();
            currentIndex = -1;
            binding.tvMatchCount.setText("");
            return;
        }

        final int searchId = ++activeSearchId;

        ExecutorProvider.getInstance().runOnCpu(() -> {
            List<SearchResult> found = searchEngine.find(query, text, caseSensitive, useRegex, wholeWord);

            ExecutorProvider.getInstance().runOnMain(() -> {
                // Safeguard against background thread racing anomalies
                if (searchId != activeSearchId) return;

                results = found;
                currentIndex = found.isEmpty() ? -1 : 0;
                applyHighlights();
                updateMatchCountLabel();
                if (!found.isEmpty()) scrollToCurrentResult();
            });
        });
    }

    private void applyHighlights() {
        if (editor == null || editor.getText() == null) return;
        Editable editable = editor.getText();
        clearHighlights();
        
        int textColor = editor.getCurrentTextColor();

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            if (r.absoluteStart >= 0 && r.absoluteEnd <= editable.length()) {
                if (i == currentIndex) {
                    applySpanSafely(editable, r.absoluteStart, r.absoluteEnd, activeHighlightColor, textColor, true);
                } else {
                    applySpanSafely(editable, r.absoluteStart, r.absoluteEnd, highlightColor, textColor, false);
                }
            }
        }
    }
    
    private void applySpanSafely(Editable editable, int start, int end, int color, int textColor, boolean isActive) {
        String text = editable.subSequence(start, end).toString();
        int currentStart = start;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                if (currentStart < start + i) {
                    editable.setSpan(isActive ? new ActiveHighlightSpan(color) : new SearchHighlightSpan(color), currentStart, start + i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                currentStart = start + i + 1;
            }
        }
        if (currentStart < end) {
            editable.setSpan(isActive ? new ActiveHighlightSpan(color) : new SearchHighlightSpan(color), currentStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void clearHighlights() {
        if (editor == null || editor.getText() == null) return;
        Editable editable = editor.getText();
        SearchHighlightSpan[] spans = editable.getSpans(0, editable.length(), SearchHighlightSpan.class);
        for (SearchHighlightSpan s : spans) editable.removeSpan(s);
        ActiveHighlightSpan[] active = editable.getSpans(0, editable.length(), ActiveHighlightSpan.class);
        for (ActiveHighlightSpan s : active) editable.removeSpan(s);
    }

    /**
     * Shifts editor focus coordinates to position current search items cleanly on screen.
     */
    private void scrollToCurrentResult() {
        if (editor == null || currentIndex < 0 || currentIndex >= results.size()) return;
        SearchResult r = results.get(currentIndex);
        editor.setSelection(r.absoluteEnd);

        if (editor.getLayout() != null) {
            int line = editor.getLayout().getLineForOffset(r.absoluteStart);
            int y = editor.getLayout().getLineTop(line);
            editor.scrollTo(0, Math.max(0, y - editor.getHeight() / 3));
        }
        applyHighlights();
        updateMatchCountLabel();
    }

    private void updateMatchCountLabel() {
        binding.tvMatchCount.setText(results.isEmpty()
                ? (binding.etSearch.getText().length() > 0 ? "0/0" : "")
                : ((currentIndex + 1) + "/" + results.size()));
    }

    private void updateToggle(MaterialButton btn, boolean active) {
        int color = ContextCompat.getColor(getContext(), active ? R.color.vcode_accent_primary : R.color.vcode_text_secondary);
        btn.setTextColor(color);
        btn.setStrokeColor(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(getContext(), active ? R.color.vcode_accent_primary : R.color.vcode_divider)));
    }

    private static class SearchHighlightSpan extends SolidHighlightSpan {
        SearchHighlightSpan(int color) {
            super(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 90));
        }
    }

    private static class ActiveHighlightSpan extends SolidHighlightSpan {
        ActiveHighlightSpan(int color) {
            super(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 140));
        }
    }
}