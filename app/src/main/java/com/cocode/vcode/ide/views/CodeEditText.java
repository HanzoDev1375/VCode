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
    private static final long UNDO_RECORD_DELAY_MS = 400;

    private final HtmlTagParser htmlTagParser = new HtmlTagParser();
    private final BracketMatcher bracketMatcher = new BracketMatcher();
    private final IndentationEngine indentEngine = new IndentationEngine(new AppSettings().tabSize);
    private final AutoCompletePopup autoCompletePopup;

    private final UndoRedoManager undoRedoManager = new UndoRedoManager();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable undoRecordRunnable = () -> {
        if (getText() != null) {
            undoRedoManager.record(getText().toString(), getSelectionStart());
        }
    };

    private final boolean autoCloseHtmlTags = true;
    private SyntaxHighlighter syntaxHighlighter;
    private AutoCompleteEngine autoCompleteEngine;
    private FileType fileType = FileType.TEXT;
    private boolean autoCloseBrackets = true;
    private boolean autoIndent = true;
    private Paint lineHighlightPaint;
    private boolean isAutoClosing = false;
    private boolean isApplyingHighlight = false;
    private final Runnable highlightRunnable = this::triggerHighlight;
    private boolean isUndoRedoActive = false;
    private boolean isSettingText = false;
    private boolean isTypingText = false;
    private File currentFile;
    private final boolean isFormatting = false;

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
                scheduleUndoRecord();
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

        // Map tab physical key key codes to inject custom spaced layouts instead of standard spacing skips
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
        mainHandler.removeCallbacksAndMessages("ac");
        mainHandler.postAtTime(this::triggerAutoComplete,
                "ac", android.os.SystemClock.uptimeMillis() + AUTOCOMPLETE_DELAY_MS);
    }

    /**
     * Evaluates cursor location bounds to prompt contextual vocabulary proposals.
     */
    private void triggerAutoComplete() {
        if (autoCompleteEngine == null || getText() == null) return;

        String text = getText().toString();
        int cursor = getSelectionStart();

        if (cursor <= 0 || cursor > text.length()) {
            mainHandler.post(autoCompletePopup::dismiss);
            return;
        }

        char lastChar = text.charAt(cursor - 1);

        if (Character.isWhitespace(lastChar)) {
            mainHandler.post(autoCompletePopup::dismiss);
            return;
        }

        ExecutorProvider.getInstance().runOnCpu(() -> {
            List<CompletionItem> items = autoCompleteEngine.getSuggestions(text, cursor);
            mainHandler.post(() -> {
                if (items != null && !items.isEmpty()) {
                    autoCompletePopup.show(items, this, cursor);
                } else {
                    autoCompletePopup.dismiss();
                }
            });
        });
    }

    /**
     * Injects selected autocomplete text choices, adjusting line padding values and cursor offsets.
     */
    private void insertCompletion(CompletionItem item) {
        if (item == null || getText() == null) return;
        String insertText = item.getEffectiveInsertText();
        if (insertText == null) return;

        int cursor = getSelectionStart();
        String text = getText().toString();
        int wordStart = cursor;
        while (wordStart > 0 && Character.isLetterOrDigit(text.charAt(wordStart - 1))) {
            wordStart--;
        }

        if (wordStart > 0 && text.charAt(wordStart - 1) == '<' && insertText.startsWith("<")) {
            wordStart--;
        }

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

        int pipeIdx = insertText.indexOf('|');
        String cleanInsert;
        int finalCursor;
        if (pipeIdx >= 0) {
            cleanInsert = insertText.replace("|", "");
            finalCursor = wordStart + pipeIdx;
        } else {
            cleanInsert = insertText;
            finalCursor = wordStart + cleanInsert.length() + item.getCursorOffset();
        }

        isApplyingHighlight = true;
        getText().replace(wordStart, cursor, cleanInsert);
        setSelection(Math.min(finalCursor, getText().length()));
        isApplyingHighlight = false;

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
            isApplyingHighlight = false;

            if (mainHandler != null) {
                scheduleHighlight();
            }
        } catch (Exception ignored) {
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

    private void scheduleUndoRecord() {
        mainHandler.removeCallbacks(undoRecordRunnable);
        mainHandler.postDelayed(undoRecordRunnable, UNDO_RECORD_DELAY_MS);
    }

    /**
     * Pops previous document states off history stacks to roll modifications backwards safely.
     */
    public void undo() {
        mainHandler.removeCallbacks(undoRecordRunnable);
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
        mainHandler.removeCallbacks(undoRecordRunnable);
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
                    this.autoCompleteEngine = new JsAutoCompleteEngine(ctx);
                    break;
                case JSON:
                    this.syntaxHighlighter = new JsonSyntaxHighlighter(ctx);
                    this.autoCompleteEngine = new JsonAutoCompleteEngine(ctx);
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
        if (!isTypingText && !isSettingText && !isAutoClosing && !isUndoRedoActive) {
            if (autoCompletePopup != null && autoCompletePopup.isShowing()) {
                autoCompletePopup.dismiss();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
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
     * Managed history track stack implementation bounded to 50 operations entries max.
     */
    private static class UndoRedoManager {
        private static final int MAX = 50;
        private final List<EditorState> history = new ArrayList<>();
        private int index = -1;

        void record(String text, int cursor) {
            if (index >= 0 && history.get(index).text.equals(text)) return;
            while (index < history.size() - 1) history.remove(history.size() - 1);

            history.add(new EditorState(text, cursor));

            if (history.size() > MAX) {
                history.remove(0);
            } else {
                index++;
            }
        }

        void reset(String text) {
            history.clear();
            index = -1;
            record(text, 0);
        }

        boolean canUndo() {
            return index > 0;
        }

        boolean canRedo() {
            return index < history.size() - 1;
        }

        EditorState undo() {
            if (!canUndo()) return null;
            return history.get(--index);
        }

        EditorState redo() {
            if (!canRedo()) return null;
            return history.get(++index);
        }
    }

    private static class BracketMatchSpan extends BackgroundColorSpan {
        BracketMatchSpan(int color) {
            super(color);
        }
    }
}