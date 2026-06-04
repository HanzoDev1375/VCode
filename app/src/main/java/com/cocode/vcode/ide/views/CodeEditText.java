package com.cocode.vcode.ide.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.autocomplete.AutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.CompletionItem;
import com.cocode.vcode.ide.core.autocomplete.CssAutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.HtmlAutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.JsAutoCompleteEngine;
import com.cocode.vcode.ide.core.autocomplete.JsonAutoCompleteEngine;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.parser.BracketMatcher;
import com.cocode.vcode.ide.core.parser.HtmlTagParser;
import com.cocode.vcode.ide.core.parser.IndentationEngine;
import com.cocode.vcode.ide.core.syntax.CssSyntaxHighlighter;
import com.cocode.vcode.ide.core.syntax.HtmlSyntaxHighlighter;
import com.cocode.vcode.ide.core.syntax.JsSyntaxHighlighter;
import com.cocode.vcode.ide.core.syntax.JsonSyntaxHighlighter;
import com.cocode.vcode.ide.core.syntax.MarkdownSyntaxHighlighter;
import com.cocode.vcode.ide.core.syntax.SvgSyntaxHighlighter;
import com.cocode.vcode.ide.core.syntax.SyntaxHighlighter;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Advanced text orchestration workspace component serving as the IDE code canvas.
 * Implements token state change observation channels executing text highlighters,
 * asynchronous autocomplete queries, pair brackets generation closures, undo-redo queues,
 * and custom layout styling rules.
 */
public class CodeEditText extends AppCompatEditText {

    private static final long HIGHLIGHT_DELAY_MS = 150;
    private static final long AUTOCOMPLETE_DELAY_MS = 100;

    /** Characters that trigger a new autocomplete session — never dismiss on these. */
    private static final String TRIGGER_CHARS = ".<>/:'\"@#";

    private final HtmlTagParser htmlTagParser = new HtmlTagParser();
    private final BracketMatcher bracketMatcher = new BracketMatcher();
    private final IndentationEngine indentEngine = new IndentationEngine(new AppSettings().tabSize);
    private final AutoCompletePopup autoCompletePopup;

    private final UndoRedoManager undoRedoManager = new UndoRedoManager();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Edit tracking for intelligent undo grouping
    private String undoOldText;
    private int undoEditStart;
    private int undoDeletedCount;
    private int undoInsertedCount;

    private final boolean autoCloseHtmlTags = true;
    private final boolean isFormatting = false;
    private SyntaxHighlighter syntaxHighlighter;
    private AutoCompleteEngine autoCompleteEngine;
    private FileType fileType = FileType.TEXT;
    private boolean autoCloseBrackets = true;
    private boolean autoIndent = true;
    private Paint lineHighlightPaint;
    private boolean isAutoClosing = false;
    private boolean isApplyingHighlight = false;
    private final Runnable highlightRunnable = this::triggerHighlight;
    private final Runnable autoCompleteRunnable = this::triggerAutoComplete;
    private boolean isUndoRedoActive = false;
    private boolean isSettingText = false;
    private boolean isTypingText = false;
    private boolean isInsertingCompletion = false;
    private File currentFile;
    private OnScrollChangeListener scrollChangeListener;

    public CodeEditText(Context context) {
        super(context);
        autoCompletePopup = new AutoCompletePopup(context);
        init(context);
    }

    public CodeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        autoCompletePopup = new AutoCompletePopup(context);
        init(context);
    }

    public CodeEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        autoCompletePopup = new AutoCompletePopup(context);
        init(context);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init(Context context) {
        Typeface codeFont = FontManager.getInstance().getCodeFont(context);
        setTypeface(codeFont);
        setTextSize(14);

        setTextColor(ContextCompat.getColor(context, R.color.vcode_text_primary));
        setHighlightColor(ContextCompat.getColor(context, R.color.vcode_selection_color));
        setBackgroundColor(ContextCompat.getColor(context, R.color.vcode_bg_surface));

        setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI); // Prevents Android from capturing fullscreen keyboard editors
        setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setHorizontallyScrolling(false);

        setVerticalScrollBarEnabled(true);
        setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);

        setScrollContainer(true);
        setOverScrollMode(View.OVER_SCROLL_ALWAYS);

        // Intercept Touch parameters to support scrolling inside nested ScrollView containers
        // Optimized: Only manipulate parent interception flags on DOWN/UP states to prevent 
        // severe frame-rate drops caused by repeatedly thrashing the hierarchy during ACTION_MOVE.
        setOnTouchListener((v, event) -> {
            int action = event.getAction() & android.view.MotionEvent.ACTION_MASK;
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                if (v.getParent() != null) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                }
            } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                if (v.getParent() != null) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
            }
            return false;
        });

        lineHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lineHighlightPaint.setColor(ContextCompat.getColor(context, R.color.vcode_active_line_highlight));
        lineHighlightPaint.setStyle(Paint.Style.FILL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setFallbackLineSpacing(false);
        }
        setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);

        setShadowLayer(0, 0, 0, 0);
        setIncludeFontPadding(false);

        // Setup real-time text analysis conduits
        addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                isTypingText = true;
                // Capture state before edit for intelligent undo grouping
                if (!isApplyingHighlight && !isUndoRedoActive && !isSettingText && !isAutoClosing) {
                    undoOldText = s.toString();
                    undoEditStart = start;
                    undoDeletedCount = count;
                    undoInsertedCount = after;
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Ignore self-induced system operations or text styling passes
                if (isApplyingHighlight || isUndoRedoActive || isSettingText || isAutoClosing)
                    return;

                if (count == 1) {
                    char typed = s.charAt(start);

                    if (autoCloseBrackets) {
                        handleAutoClose(s, start, typed);
                    }
                    if (autoCloseHtmlTags && fileType == FileType.HTML && typed == '>') {
                        handleAutoCloseHtmlTag(start + 1);
                    }
                    if (typed == '\n') {
                        handleAutoIndent(s.toString(), start);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                isTypingText = false;
                if (isApplyingHighlight || isUndoRedoActive || isSettingText || isAutoClosing)
                    return;
                scheduleHighlight();
                scheduleAutoComplete();
                // Intelligent undo recording with edit metadata
                if (undoOldText != null) {
                    undoRedoManager.onEdit(s.toString(), getSelectionStart(),
                            undoEditStart, undoDeletedCount, undoInsertedCount, undoOldText);
                    undoOldText = null;
                }
                updateBracketMatch(s.toString(), getSelectionStart());
            }
        });

        autoCompletePopup.setOnItemSelectedListener(this::insertCompletion);
        undoRedoManager.record("", 0);

        int padding = (int) (4 * context.getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (autoCompletePopup != null) {
            autoCompletePopup.dismiss();
        }

        isSettingText = true;
        super.setText(text, type);

        if (undoRedoManager != null && text != null && !isUndoRedoActive) {
            undoRedoManager.reset(text.toString());
        }

        isSettingText = false;

        if (mainHandler != null) {
            scheduleHighlight();
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (!focused && autoCompletePopup != null) {
            autoCompletePopup.dismiss();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Render horizontal strip highlighting behind the row holding the active text cursor pointer
        if (getLayout() != null && isFocused()) {
            int cursor = getSelectionStart();
            if (cursor >= 0 && cursor <= (getText() != null ? getText().length() : 0)) {
                int line = getLayout().getLineForOffset(cursor);
                float lineTop = getLayout().getLineTop(line) + getPaddingTop();
                float lineBot = getLayout().getLineBottom(line) + getPaddingTop();
                canvas.drawRect(0, lineTop, getWidth(), lineBot, lineHighlightPaint);
            }
        }
        super.onDraw(canvas);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isApplyingHighlight || isUndoRedoActive || isSettingText)
            return super.onKeyDown(keyCode, event);

        // ── Autocomplete keyboard navigation ─────────────────────────────────
        if (autoCompletePopup != null && autoCompletePopup.isShowing()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    autoCompletePopup.moveSelection(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    autoCompletePopup.moveSelection(-1);
                    return true;
                case KeyEvent.KEYCODE_TAB:
                case KeyEvent.KEYCODE_ENTER:
                    CompletionItem selected = autoCompletePopup.getSelectedItem();
                    if (selected != null) {
                        insertCompletion(selected);
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_ESCAPE:
                    autoCompletePopup.dismiss();
                    return true;
            }
        }

        // Map tab physical key to inject custom spaced layouts
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            int start = Math.max(0, getSelectionStart());
            int end = Math.max(start, getSelectionEnd());
            String spaces = buildTabSpaces();
            Objects.requireNonNull(getText()).replace(start, end, spaces);
            setSelection(start + spaces.length());
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Intercepts newline insertions to replicate pre-existing structural indentation margins.
     */
    private void handleAutoIndent(String text, int newlineIndex) {
        if (!autoIndent || indentEngine == null) return;

        String innerIndent = indentEngine.getIndentForNewLine(text, newlineIndex, fileType);
        if (innerIndent == null) innerIndent = "";

        boolean isBracketSplit = false;
        String outerIndent = "";

        if (newlineIndex > 0 && newlineIndex + 1 < text.length()) {
            char before = text.charAt(newlineIndex - 1);
            char after = text.charAt(newlineIndex + 1);

            if ((before == '{' && after == '}') ||
                    (before == '[' && after == ']') ||
                    (before == '(' && after == ')') ||
                    (before == '>' && after == '<')) {

                isBracketSplit = true;

                int lineStart = newlineIndex - 1;
                while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
                    lineStart--;
                }

                StringBuilder baseIndent = new StringBuilder();
                for (int i = lineStart; i < newlineIndex; i++) {
                    char c = text.charAt(i);
                    if (c == ' ' || c == '\t') {
                        baseIndent.append(c);
                    } else {
                        break;
                    }
                }
                outerIndent = baseIndent.toString();
            }
        }

        final String finalInnerIndent = innerIndent;
        final boolean finalSplit = isBracketSplit;
        final String finalOuterIndent = outerIndent;
        final int insertPos = newlineIndex + 1;

        if (!finalInnerIndent.isEmpty() || finalSplit) {
            mainHandler.post(() -> {
                if (getText() != null && insertPos <= getText().length()) {
                    isApplyingHighlight = true;

                    if (finalSplit) {
                        String injection = finalInnerIndent + "\n" + finalOuterIndent;
                        getText().insert(insertPos, injection);
                        setSelection(insertPos + finalInnerIndent.length());
                    } else {
                        getText().insert(insertPos, finalInnerIndent);
                        setSelection(insertPos + finalInnerIndent.length());
                    }

                    isApplyingHighlight = false;
                }
            });
        }
    }

    /**
     * Automatically completes corresponding notation structural closures (e.g. matching quotes, parens).
     */
    private void handleAutoClose(CharSequence s, int insertPos, char typed) {
        if (!autoCloseBrackets) return;
        String closing = getClosingPair(typed);
        if (closing == null) return;

        if (insertPos + 1 < s.length() && s.charAt(insertPos + 1) == closing.charAt(0)) return;

        final String toInsert = closing;
        final int pos = insertPos + 1;
        mainHandler.post(() -> {
            if (getText() != null && pos <= getText().length()) {
                isAutoClosing = true;
                getText().insert(pos, toInsert);
                setSelection(pos);
                isAutoClosing = false;
                scheduleHighlight();
            }
        });
    }

    private String getClosingPair(char open) {
        switch (open) {
            case '(':
                return ")";
            case '[':
                return "]";
            case '{':
                return "}";
            case '"':
                return "\"";
            case '\'':
                return "'";
            default:
                return null;
        }
    }

    /**
     * Resolves currently active element types to generate matching closing labels in HTML scripts.
     */
    private void handleAutoCloseHtmlTag(int cursorAfterGt) {
        mainHandler.post(() -> {
            if (getText() == null) return;
            String currentText = getText().toString();
            String tagName = htmlTagParser.getCurrentOpenTagName(currentText, cursorAfterGt - 1);
            if (tagName == null || tagName.isEmpty() || HtmlTagParser.isVoidElement(tagName))
                return;

            String closing = "</" + tagName + ">";

            isAutoClosing = true;
            getText().insert(cursorAfterGt, closing);
            setSelection(cursorAfterGt);
            isAutoClosing = false;
            scheduleHighlight();
        });
    }

    private void scheduleHighlight() {
        if (mainHandler == null) return;
        mainHandler.removeCallbacks(highlightRunnable);
        mainHandler.postDelayed(highlightRunnable, HIGHLIGHT_DELAY_MS);
    }

    /**
     * Triggers the computation of vocabulary text colorizations on background CPU pools,
     * shielding user interaction streams from frame execution lag.
     */
    private void triggerHighlight() {
        if (syntaxHighlighter == null || getText() == null) return;

        final String code = getText().toString();
        ExecutorProvider.getInstance().runOnCpu(() -> {
            SpannableStringBuilder ssb = syntaxHighlighter.highlight(code);
            mainHandler.post(() -> applyHighlightSpans(ssb));
        });
    }

    /**
     * Wipes historical color spans within text scopes to apply newly rendered configuration metrics.
     */
    private void applyHighlightSpans(SpannableStringBuilder ssb) {
        if (getText() == null || ssb == null || ssb.length() != getText().length()) return;

        isApplyingHighlight = true;
        int savedStart = getSelectionStart();
        int savedEnd = getSelectionEnd();

        SyntaxHighlightSpan[] old = getText().getSpans(0, getText().length(), SyntaxHighlightSpan.class);
        for (SyntaxHighlightSpan s : old) getText().removeSpan(s);

        SyntaxHighlightSpan[] newSpans = ssb.getSpans(0, ssb.length(), SyntaxHighlightSpan.class);
        for (SyntaxHighlightSpan span : newSpans) {
            int start = ssb.getSpanStart(span);
            int end = ssb.getSpanEnd(span);
            if (start >= 0 && end <= getText().length() && start < end) {
                getText().setSpan(new SyntaxHighlightSpan(span.getForegroundColor()),
                        start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        int len = getText().length();
        setSelection(Math.min(savedStart, len), Math.min(savedEnd, len));
        isApplyingHighlight = false;
    }

    private void scheduleAutoComplete() {
        mainHandler.removeCallbacks(autoCompleteRunnable);
        mainHandler.postDelayed(autoCompleteRunnable, AUTOCOMPLETE_DELAY_MS);
    }

    /**
     * Evaluates cursor location to prompt contextual vocabulary proposals.
     * Mirrors VS Code's trigger logic:
     * — Always trigger on identifier characters (letters, digits, _, $, -).
     * — Also trigger on designated trigger characters (., <, /, :, @, #, quotes).
     * — Dismiss on whitespace, newlines, or non-trigger punctuation.
     * — Dismiss when cursor is at position 0.
     */
    private void triggerAutoComplete() {
        if (autoCompleteEngine == null || getText() == null) return;

        String text   = getText().toString();
        int    cursor = getSelectionStart();

        // Must have at least one char before cursor and valid position
        if (cursor <= 0 || cursor > text.length()) {
            autoCompletePopup.dismiss();
            return;
        }

        char lastChar = text.charAt(cursor - 1);

        // Newlines never trigger — dismiss immediately
        if (lastChar == '\n' || lastChar == '\r') {
            autoCompletePopup.dismiss();
            return;
        }

        // Trigger on identifier chars or explicit trigger characters
        boolean isIdentifier = Character.isLetterOrDigit(lastChar)
                || lastChar == '_' || lastChar == '-' || lastChar == '$';
        boolean isTriggerChar = TRIGGER_CHARS.indexOf(lastChar) >= 0;

        if (!isIdentifier && !isTriggerChar) {
            autoCompletePopup.dismiss();
            return;
        }

        // Capture cursor for the lambda — prevents stale reference
        final int capturedCursor = cursor;
        final String capturedText = text;

        ExecutorProvider.getInstance().runOnCpu(() -> {
            List<CompletionItem> items = autoCompleteEngine.getSuggestions(capturedText, capturedCursor);
            mainHandler.post(() -> {
                // Verify cursor hasn't moved since we started computing
                if (getSelectionStart() != capturedCursor) return;

                if (items != null && !items.isEmpty()) {
                    autoCompletePopup.show(items, CodeEditText.this, capturedCursor);
                } else {
                    autoCompletePopup.dismiss();
                }
            });
        });
    }

    /**
     * Injects selected autocomplete text, properly computing replace range.
     *
     * <p>Key behaviors matching professional IDEs:
     * <ul>
     *   <li>If replaceLength is explicitly set (e.g., Emmet), uses that exact range.</li>
     *   <li>For dot-member completions (e.g., Math.floor), only replaces the word AFTER the dot.</li>
     *   <li>For tag completions starting with '<', also replaces the '<' character.</li>
     *   <li>Sets isInsertingCompletion + isAutoClosing to prevent auto-close brackets from
     *       firing on inserted parentheses like "floor()".</li>
     * </ul>
     */
    private void insertCompletion(CompletionItem item) {
        if (item == null || getText() == null) return;
        String insertText = item.getEffectiveInsertText();
        if (insertText == null) return;

        int cursor = getSelectionStart();
        String text = getText().toString();

        // ── Compute wordStart (the position from which to replace) ───────────
        int wordStart;
        if (item.getReplaceLength() >= 0) {
            // Explicit replace length set by the engine (e.g., Emmet abbreviations)
            wordStart = Math.max(0, cursor - item.getReplaceLength());
        } else {
            // Default: walk back over word characters to find the start of the typed prefix
            wordStart = cursor;
            while (wordStart > 0) {
                char c = text.charAt(wordStart - 1);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '$' || c == '@') {
                    wordStart--;
                } else {
                    break;
                }
            }
            // If the insert text starts with '<' and the char before wordStart is '<',
            // extend the replace range to include it (tag completion like typing "di" after "<")
            if (wordStart > 0 && text.charAt(wordStart - 1) == '<' && insertText.startsWith("<")) {
                wordStart--;
            }
        }

        // ── Handle indentation for multi-line insertions ─────────────────────
        int lineStart = wordStart;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        StringBuilder baseIndent = new StringBuilder();
        for (int i = lineStart; i < wordStart; i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                baseIndent.append(c);
            } else {
                break;
            }
        }
        if (insertText.contains("\n")) {
            insertText = insertText.replace("\n", "\n" + baseIndent);
        }

        // ── Handle pipe cursor marker ────────────────────────────────────────
        int pipeIdx = insertText.indexOf('|');
        String cleanInsert;
        int finalCursor;
        if (pipeIdx >= 0) {
            cleanInsert = insertText.substring(0, pipeIdx) + insertText.substring(pipeIdx + 1);
            finalCursor = wordStart + pipeIdx;
        } else {
            cleanInsert = insertText;
            finalCursor = wordStart + cleanInsert.length() + item.getCursorOffset();
        }

        // ── Deduplicate: if the text after cursor already matches the suffix of cleanInsert,
        //    avoid double-inserting (e.g., inserting ">" when ">" already exists) ─────────
        // Only do this for single-char suffixes like closing brackets/quotes that might be
        // auto-closed already
        if (cleanInsert.length() > 0 && cursor < text.length()) {
            char lastInserted = cleanInsert.charAt(cleanInsert.length() - 1);
            char nextInDoc = text.charAt(cursor);
            // If the insertion ends with a closing bracket/quote and the next char matches,
            // skip consuming it only if no cursor offset is pushing us back
            if (item.getCursorOffset() == 0 && pipeIdx < 0
                    && (lastInserted == ')' || lastInserted == ']' || lastInserted == '}' || lastInserted == '"' || lastInserted == '\'')
                    && lastInserted == nextInDoc) {
                // Remove the trailing char from insertion since it's already there
                cleanInsert = cleanInsert.substring(0, cleanInsert.length() - 1);
                finalCursor = wordStart + cleanInsert.length();
            }
        }

        // ── Apply the text replacement ───────────────────────────────────────
        // Set flags to prevent auto-close and popup dismissal during insertion
        isInsertingCompletion = true;
        isAutoClosing = true;
        isApplyingHighlight = true;
        getText().replace(wordStart, cursor, cleanInsert);
        setSelection(Math.min(finalCursor, getText().length()));
        isApplyingHighlight = false;
        isAutoClosing = false;
        isInsertingCompletion = false;

        autoCompletePopup.dismiss();
        scheduleHighlight();
    }

    /**
     * Spawns structured macro boilerplate configurations blocks, centering carets on internal target indicators.
     */
    public void insertSnippet(String snippetTemplate) {
        if (snippetTemplate == null || snippetTemplate.isEmpty()) return;
        if (getText() == null) return;

        setFocusableInTouchMode(true);
        requestFocus();

        int cursor = getSelectionStart();

        if (cursor < 0) {
            cursor = getText().length();
        }

        String currentText = getText().toString();
        String formattedSnippet = getFormattedSnippet(snippetTemplate, cursor, currentText);

        int pipeIndex = formattedSnippet.indexOf('|');
        if (pipeIndex != -1) {
            formattedSnippet = formattedSnippet.substring(0, pipeIndex) + formattedSnippet.substring(pipeIndex + 1);
        }

        try {
            isApplyingHighlight = true;
            getText().insert(cursor, formattedSnippet);

            if (pipeIndex != -1) {
                setSelection(cursor + pipeIndex);
            } else {
                setSelection(cursor + formattedSnippet.length());
            }

            if (mainHandler != null) {
                scheduleHighlight();
            }
        } catch (Exception ignored) {
        } finally {
            isApplyingHighlight = false;
        }
    }

    @NonNull
    private String getFormattedSnippet(String snippetTemplate, int cursor, String currentText) {
        int lineStart = cursor - 1;
        while (lineStart >= 0 && currentText.charAt(lineStart) != '\n') {
            lineStart--;
        }
        lineStart++;

        StringBuilder baseIndent = new StringBuilder();
        for (int i = lineStart; i < cursor; i++) {
            char c = currentText.charAt(i);
            if (c == ' ' || c == '\t') {
                baseIndent.append(c);
            } else {
                break;
            }
        }
        String indent = baseIndent.toString();
        return snippetTemplate.replace("\n", "\n" + indent);
    }

    /**
     * Queries the bracket matching system to highlight paired enclosure delimiters beneath the caret.
     */
    private void updateBracketMatch(String text, int cursor) {
        if (getText() == null) return;
        BracketMatchSpan[] old = getText().getSpans(0, getText().length(), BracketMatchSpan.class);
        for (BracketMatchSpan s : old) getText().removeSpan(s);

        BracketMatcher.MatchResult match = bracketMatcher.findMatch(text, cursor);
        if (match != null && match.found) {
            int color = ContextCompat.getColor(getContext(), R.color.vcode_accent_primary);
            int alphaColor = android.graphics.Color.argb(60,
                    android.graphics.Color.red(color),
                    android.graphics.Color.green(color),
                    android.graphics.Color.blue(color));
            int len = getText().length();
            if (match.openPos >= 0 && match.openPos + 1 <= len) {
                getText().setSpan(new BracketMatchSpan(alphaColor), match.openPos, match.openPos + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (match.closePos >= 0 && match.closePos + 1 <= len) {
                getText().setSpan(new BracketMatchSpan(alphaColor), match.closePos, match.closePos + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    // ─── Undo/Redo ─────────────────────────────────────────────────────────────

    /**
     * Pops previous document states off history stacks to roll modifications backwards safely.
     */
    public void undo() {
        // Commit any pending grouped edits first
        if (getText() != null) {
            undoRedoManager.commitPendingFromCurrent(getText().toString(), getSelectionStart());
        }
        EditorState state = undoRedoManager.undo();
        if (state == null) return;

        isUndoRedoActive = true;
        setText(state.text);

        int safeCursor = Math.min(Math.max(0, state.cursor), state.text.length());
        setSelection(safeCursor);

        isUndoRedoActive = false;
        scheduleHighlight();
    }

    /**
     * Advances document history tracking forward to re-apply rolled-back entries.
     */
    public void redo() {
        EditorState state = undoRedoManager.redo();
        if (state == null) return;

        isUndoRedoActive = true;
        setText(state.text);

        int safeCursor = Math.min(Math.max(0, state.cursor), state.text.length());
        setSelection(safeCursor);

        isUndoRedoActive = false;
        scheduleHighlight();
    }

    public boolean canUndo() {
        return undoRedoManager.canUndo();
    }

    public boolean canRedo() {
        return undoRedoManager.canRedo();
    }

    public void setAutoCloseBrackets(boolean autoClose) {
        this.autoCloseBrackets = autoClose;
    }

    public void setAutoIndent(boolean autoIndent) {
        this.autoIndent = autoIndent;
    }

    public FileType getFileType() {
        return fileType;
    }

    /**
     * Updates target highlights and maps applicable sub-completions engines matching source extensions.
     */
    public void setFileType(FileType fileType) {
        this.fileType = fileType;

        if (fileType != null) {
            Context ctx = getContext();
            switch (fileType) {
                case HTML:
                    this.syntaxHighlighter = new HtmlSyntaxHighlighter(ctx);
                    this.autoCompleteEngine = new HtmlAutoCompleteEngine(ctx);
                    if (currentFile != null) {
                        ((HtmlAutoCompleteEngine) this.autoCompleteEngine).setCurrentFile(currentFile);
                    }
                    break;
                case CSS:
                    this.syntaxHighlighter = new CssSyntaxHighlighter(ctx);
                    this.autoCompleteEngine = new CssAutoCompleteEngine(ctx);
                    break;
                case JAVASCRIPT:
                    this.syntaxHighlighter = new JsSyntaxHighlighter(ctx);
                    JsAutoCompleteEngine jsEngine = new JsAutoCompleteEngine(ctx);
                    if (currentFile != null) jsEngine.setCurrentFile(currentFile);
                    this.autoCompleteEngine = jsEngine;
                    break;
                case JSON:
                    this.syntaxHighlighter = new JsonSyntaxHighlighter(ctx);
                    JsonAutoCompleteEngine jsonEngine = new JsonAutoCompleteEngine(ctx);
                    if (currentFile != null) jsonEngine.setCurrentFile(currentFile);
                    this.autoCompleteEngine = jsonEngine;
                    break;
                case MARKDOWN:
                    this.syntaxHighlighter = new MarkdownSyntaxHighlighter(ctx);
                    this.autoCompleteEngine = null;
                    break;
                case SVG:
                    this.syntaxHighlighter = new SvgSyntaxHighlighter(ctx);
                    this.autoCompleteEngine = null;
                    break;
                default:
                    this.syntaxHighlighter = null;
                    this.autoCompleteEngine = null;
                    break;
            }
        }
        scheduleHighlight();
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
        if (autoCompleteEngine instanceof HtmlAutoCompleteEngine) {
            ((HtmlAutoCompleteEngine) autoCompleteEngine).setCurrentFile(file);
        }
        if (autoCompleteEngine instanceof JsAutoCompleteEngine) {
            ((JsAutoCompleteEngine) autoCompleteEngine).setCurrentFile(file);
        }
        if (autoCompleteEngine instanceof JsonAutoCompleteEngine) {
            ((JsonAutoCompleteEngine) autoCompleteEngine).setCurrentFile(file);
        }
    }

    public void setOnScrollChangeListener(OnScrollChangeListener listener) {
        this.scrollChangeListener = listener;
    }

    @Override
    protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
        if (scrollChangeListener != null) {
            scrollChangeListener.onScrollChanged(horiz, vert);
        }
    }

    public int getLineCount() {
        return getLayout() != null ? getLayout().getLineCount() : 1;
    }

    public int getCurrentLine() {
        return getLayout() != null ? getLayout().getLineForOffset(getSelectionStart()) + 1 : 1;
    }

    private String buildTabSpaces() {
        StringBuilder sb = new StringBuilder();
        int tabSize = new AppSettings().tabSize;
        for (int i = 0; i < tabSize; i++) sb.append(' ');
        return sb.toString();
    }

    @Override
    public boolean onKeyPreIme(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.getAction() == android.view.KeyEvent.ACTION_UP) {
            if (autoCompletePopup != null && autoCompletePopup.isShowing()) {
                autoCompletePopup.dismiss();
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        // Only dismiss on explicit cursor placement (tap/selection drag) — NOT during
        // typing, undo/redo, auto-close, or completion insertion. This prevents the bug
        // where tapping an autocomplete item triggers onSelectionChanged before the click
        // handler fires, causing the popup to dismiss before the item can be selected.
        if (!isTypingText && !isSettingText && !isAutoClosing && !isUndoRedoActive
                && !isApplyingHighlight && !isInsertingCompletion) {
            if (autoCompletePopup != null && autoCompletePopup.isShowing()) {
                // If the selection is a range (dragging), dismiss immediately.
                // If it's a cursor move (single position), dismiss immediately.
                autoCompletePopup.dismiss();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mainHandler.removeCallbacks(highlightRunnable);
        mainHandler.removeCallbacks(autoCompleteRunnable);
        mainHandler.removeCallbacksAndMessages(null);
        autoCompletePopup.dismiss();
    }

    public interface OnScrollChangeListener {
        void onScrollChanged(int scrollX, int scrollY);
    }

    private static class EditorState {
        final String text;
        final int cursor;

        EditorState(String text, int cursor) {
            this.text = text;
            this.cursor = cursor;
        }
    }

    /**
     * Intelligent undo/redo manager that groups edits like professional IDEs (VS Code, IntelliJ).
     *
     * <p>Grouping rules:
     * <ul>
     *   <li>Consecutive single-char insertions are grouped until a word boundary (space/punctuation/newline)</li>
     *   <li>Consecutive single-char deletions are grouped while direction remains the same (backspace vs delete)</li>
     *   <li>Multi-char operations (paste/cut/autocomplete) are committed immediately as a single undo unit</li>
     *   <li>A time gap &gt; 1 second between edits forces a new undo group</li>
     *   <li>Direction changes (insert→delete or backspace→forward-delete) force a commit</li>
     *   <li>Newline insertion always commits the preceding group first</li>
     * </ul>
     */
    private static class UndoRedoManager {
        private static final int MAX = 100;
        private static final long TIME_GAP_MS = 1000;

        private final List<EditorState> history = new ArrayList<>();
        private int index = -1;

        // Pending group tracking
        private String pendingBaseText;    // Text before the current group started
        private int pendingBaseCursor;     // Cursor before the current group started
        private int pendingLastCursor;     // Cursor after the last edit in current group
        private long lastEditTime;         // Timestamp of last edit in current group
        private int lastEditType;          // 0=none, 1=insert, 2=backspace, 3=forward-delete

        void reset(String text) {
            history.clear();
            index = -1;
            pendingBaseText = null;
            lastEditType = 0;
            record(text, 0);
        }

        /**
         * Records a full snapshot unconditionally (used for initial state).
         */
        void record(String text, int cursor) {
            if (index >= 0 && history.get(index).text.equals(text)) return;
            commitPending();
            pushState(text, cursor);
        }

        /**
         * Called on every text change with edit metadata for intelligent grouping.
         *
         * @param newText    Full document text after the edit
         * @param cursor     Cursor position after the edit
         * @param start      Start position of the edit
         * @param deletedCount Number of characters deleted (0 for pure inserts)
         * @param insertedCount Number of characters inserted (0 for pure deletes)
         * @param oldText    Full document text before the edit
         */
        void onEdit(String newText, int cursor, int start, int deletedCount, int insertedCount, String oldText) {
            long now = System.currentTimeMillis();

            // Determine edit type
            int editType;
            if (insertedCount > 0 && deletedCount == 0) {
                editType = 1; // insert
            } else if (deletedCount > 0 && insertedCount == 0) {
                // backspace = cursor was after deleted chars; forward-delete = cursor was at start
                editType = (cursor == start) ? 2 : 3; // 2=backspace, 3=forward-delete
            } else {
                // Replace operation (e.g. autocomplete, find-replace) — commit immediately
                commitPendingWithText(oldText);
                pushState(newText, cursor);
                lastEditType = 0;
                lastEditTime = now;
                return;
            }

            // Check if we should break the group
            boolean shouldBreak = false;

            // Time gap — force new group
            if (lastEditType != 0 && (now - lastEditTime) > TIME_GAP_MS) {
                shouldBreak = true;
            }

            // Direction change (insert→delete or backspace↔forward-delete)
            if (lastEditType != 0 && editType != lastEditType) {
                shouldBreak = true;
            }

            // Multi-char paste/cut — always separate unit
            if (insertedCount > 1 || deletedCount > 1) {
                shouldBreak = true;
            }

            // Word boundary on single-char insert
            if (editType == 1 && insertedCount == 1) {
                char inserted = newText.charAt(start);
                if (isWordBoundary(inserted)) {
                    shouldBreak = true;
                }
            }

            if (shouldBreak) {
                commitPendingWithText(oldText);
            }

            // Start new group if none pending
            if (pendingBaseText == null) {
                // Use the state before this edit as the base
                if (index >= 0) {
                    pendingBaseText = history.get(index).text;
                    pendingBaseCursor = history.get(index).cursor;
                } else {
                    pendingBaseText = oldText;
                    pendingBaseCursor = start;
                }
            }

            // Always track the latest cursor in the group
            pendingLastCursor = cursor;

            // For multi-char operations, commit immediately
            if (insertedCount > 1 || deletedCount > 1) {
                pushState(newText, cursor);
                pendingBaseText = null;
                lastEditType = 0;
            } else {
                lastEditType = editType;
            }

            lastEditTime = now;
        }

        /**
         * Commits the pending group using the CURRENT text state.
         * Called before undo/redo or when explicitly flushing.
         */
        void commitPendingFromCurrent(String currentText, int currentCursor) {
            if (pendingBaseText != null) {
                // Only push if text actually differs from the last recorded state
                if (index < 0 || !history.get(index).text.equals(currentText)) {
                    pushState(currentText, currentCursor);
                }
                pendingBaseText = null;
                lastEditType = 0;
            }
        }

        private void commitPendingWithText(String textBeforeThisEdit) {
            if (pendingBaseText != null) {
                // The pending group's "after" state is whatever text was before the new edit
                if (index < 0 || !history.get(index).text.equals(textBeforeThisEdit)) {
                    pushState(textBeforeThisEdit, pendingLastCursor);
                }
                pendingBaseText = null;
                lastEditType = 0;
            }
        }

        private void commitPending() {
            // Simple commit — only clears pending state (used in record/reset)
            pendingBaseText = null;
            lastEditType = 0;
        }

        private void pushState(String text, int cursor) {
            // Truncate redo history
            while (index < history.size() - 1) history.remove(history.size() - 1);
            history.add(new EditorState(text, cursor));
            if (history.size() > MAX) {
                history.remove(0);
            } else {
                index++;
            }
        }

        boolean canUndo() {
            return index > 0 || pendingBaseText != null;
        }

        boolean canRedo() {
            return index < history.size() - 1;
        }

        EditorState undo() {
            if (!canUndo()) return null;
            if (index > 0) {
                return history.get(--index);
            }
            return null;
        }

        EditorState redo() {
            if (!canRedo()) return null;
            return history.get(++index);
        }

        private static boolean isWordBoundary(char c) {
            return c == ' ' || c == '\n' || c == '\t' || c == '\r'
                    || c == '.' || c == ',' || c == ';' || c == ':'
                    || c == '(' || c == ')' || c == '{' || c == '}'
                    || c == '[' || c == ']' || c == '<' || c == '>'
                    || c == '"' || c == '\'' || c == '`'
                    || c == '=' || c == '+' || c == '-' || c == '/'
                    || c == '!' || c == '?' || c == '&' || c == '|';
        }
    }

    private static class BracketMatchSpan extends BackgroundColorSpan {
        BracketMatchSpan(int color) {
            super(color);
        }
    }
}