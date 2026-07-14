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

    private static final long HIGHLIGHT_DELAY_MS_SMALL = 150;
    private static final long HIGHLIGHT_DELAY_MS_LARGE = 300;
    private static final int LARGE_FILE_THRESHOLD = 20000; // chars — files under this get full highlighting
    private static final int VIEWPORT_BUFFER_LINES = 30; // extra lines above/below visible area
    private static final long AUTOCOMPLETE_DELAY_MS = 100;

    /**
     * Characters that trigger a new completion context — typed alone (no word before them).
     */
    private static final String TRIGGER_CHARS = ".</:'" + "\"" + "@#!";

    private final HtmlTagParser htmlTagParser = new HtmlTagParser();
    private final BracketMatcher bracketMatcher = new BracketMatcher();
    private final IndentationEngine indentEngine = new IndentationEngine(new AppSettings().tabSize);
    private final AutoCompletePopup autoCompletePopup;

    private final UndoRedoManager undoRedoManager = new UndoRedoManager();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final boolean autoCloseHtmlTags = true;
    private final boolean isFormatting = false;
    private final DirtyRangeTracker dirtyTracker = new DirtyRangeTracker();
    // Edit tracking for intelligent undo grouping
    private String undoOldText;
    private int undoEditStart;
    private int undoDeletedCount;
    private int undoInsertedCount;
    private SyntaxHighlighter syntaxHighlighter;
    private AutoCompleteEngine autoCompleteEngine;
    private FileType fileType = FileType.TEXT;
    private final Runnable autoCompleteRunnable = this::triggerAutoComplete;
    private boolean autoCloseBrackets = true;
    private boolean autoIndent = true;
    private Paint lineHighlightPaint;
    private boolean isAutoClosing = false;
    private boolean isApplyingHighlight = false;
    private boolean isUndoRedoActive = false;
    private boolean isSettingText = false;
    private boolean isTypingText = false;
    private boolean isInsertingCompletion = false;
    private File currentFile;
    private OnScrollChangeListener scrollChangeListener;
    private Paint diagnosticPaint;
    private android.graphics.Path diagnosticPath;
    private int[] cachedLineOffsets = null;
    private boolean lineOffsetsDirty = true;
    private int lastHighlightStart = -1;
    private int lastHighlightEnd = -1;
    private long highlightVersion = 0;
    private final Runnable highlightRunnable = this::triggerHighlight;
    private final Runnable scrollHighlightRunnable = this::triggerHighlight;
    private List<com.cocode.vcode.ide.data.model.Problem> currentProblems = new ArrayList<>();
    // Perf: cached diagnostic draw colors — avoid ContextCompat.getColor() inside onDraw every frame
    private int cachedErrorColor;
    private int cachedWarningColor;
    private int cachedInfoColor;
    private int cachedBracketHighlightColor;
    // Perf: debounced bracket match to avoid span scan on main thread every keystroke
    private final Runnable bracketMatchRunnable = () -> {
        if (getText() != null) updateBracketMatch(getText().toString(), getSelectionStart());
    };

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

        diagnosticPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        diagnosticPaint.setStyle(Paint.Style.STROKE);
        diagnosticPaint.setStrokeWidth(3f);
        diagnosticPath = new android.graphics.Path();
        // Perf: pre-load diagnostic colors once to avoid ContextCompat.getColor() in onDraw
        cachedErrorColor = ContextCompat.getColor(context, R.color.vcode_accent_error);
        cachedWarningColor = ContextCompat.getColor(context, R.color.vcode_accent_warning);
        cachedInfoColor = ContextCompat.getColor(context, R.color.vcode_accent_primary);
        cachedBracketHighlightColor = ContextCompat.getColor(context, R.color.vcode_bracket_match_bg);

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
                lineOffsetsDirty = true;
                if (currentProblems != null && !currentProblems.isEmpty()) {
                    currentProblems.clear();
                    invalidate();
                }

                // Ignore self-induced system operations or text styling passes
                if (isApplyingHighlight || isUndoRedoActive || isSettingText || isAutoClosing)
                    return;

                dirtyTracker.addEdit(start, before, count);


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
                // Only trigger autocomplete on insertions — not on deletions or pure selections
                if (undoInsertedCount > 0) {
                    scheduleAutoComplete();
                } else if (autoCompletePopup != null && autoCompletePopup.isShowing()) {
                    // On deletion, update the popup (word fragment got shorter) or dismiss
                    scheduleAutoComplete();
                }
                // Intelligent undo recording with edit metadata
                if (undoOldText != null) {
                    undoRedoManager.onEdit(s.toString(), getSelectionStart(),
                            undoEditStart, undoDeletedCount, undoInsertedCount, undoOldText);
                    undoOldText = null;
                }
                // Perf: debounce bracket-match span scan — avoids full-document getSpans() on every keystroke
                mainHandler.removeCallbacks(bracketMatchRunnable);
                mainHandler.postDelayed(bracketMatchRunnable, 150);
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
        if (getLayout() != null && isFocused()) {
            int cursor = getSelectionStart();
            if (cursor >= 0 && cursor <= (getText() != null ? getText().length() : 0)) {
                int line = getLayout().getLineForOffset(cursor);
                float lineTop = getLayout().getLineTop(line) + getTotalPaddingTop();
                float lineBot = getLayout().getLineBottom(line) + getTotalPaddingTop();
                canvas.drawRect(0, lineTop, getWidth(), lineBot, lineHighlightPaint);
            }
        }

        super.onDraw(canvas);

        // ── 5. Diagnostic squiggles ────────────────────────────────────────────
        if (getLayout() != null && currentProblems != null && !currentProblems.isEmpty() && getText() != null) {
            rebuildLineOffsets();
            if (cachedLineOffsets == null) return;
            int[] lineOffsets = cachedLineOffsets;
            int lineCount = lineOffsets.length;

            // Visible scroll bounds — skip problems outside viewport
            int scrollY = getScrollY();
            int visibleTop = scrollY - getTotalPaddingTop();
            int visibleBot = scrollY + getHeight();

            int errorColor = cachedErrorColor;
            int warningColor = cachedWarningColor;
            int infoColor = cachedInfoColor;

            int textLen = getText().length();

            for (com.cocode.vcode.ide.data.model.Problem problem : currentProblems) {
                int lineIdx = problem.getLine() - 1;
                if (lineIdx < 0 || lineIdx >= lineCount) continue;

                int lineStart = lineOffsets[lineIdx];
                int lineEnd = (lineIdx + 1 < lineCount) ? lineOffsets[lineIdx + 1] - 1 : textLen;
                int colIdx = Math.max(0, problem.getColumn() - 1);

                int startOff = Math.min(lineStart + colIdx, lineEnd);
                int endOff = Math.min(startOff + Math.max(1, problem.getLength()), textLen);
                if (startOff < 0 || startOff >= textLen) continue;

                if (problem.getSeverity() == com.cocode.vcode.ide.data.model.Problem.Severity.INFO)
                    continue;

                int color = infoColor;
                if (problem.getSeverity() == com.cocode.vcode.ide.data.model.Problem.Severity.ERROR)
                    color = errorColor;
                else if (problem.getSeverity() == com.cocode.vcode.ide.data.model.Problem.Severity.WARNING)
                    color = warningColor;
                diagnosticPaint.setColor(color);

                int startLayout = getLayout().getLineForOffset(startOff);
                int endLayout = getLayout().getLineForOffset(Math.min(endOff - 1, textLen - 1));

                for (int l = startLayout; l <= endLayout; l++) {
                    float lineTop = getLayout().getLineTop(l) + getTotalPaddingTop();
                    float lineBot = getLayout().getLineBottom(l) + getTotalPaddingTop();
                    // Skip lines outside the visible viewport
                    if (lineBot < visibleTop || lineTop > visibleBot) continue;

                    int drawStart = Math.max(startOff, getLayout().getLineStart(l));
                    int drawEnd = Math.min(endOff, getLayout().getLineEnd(l));
                    if (drawStart >= drawEnd) continue;

                    float x0 = getLayout().getPrimaryHorizontal(drawStart) + getTotalPaddingLeft();
                    float x1 = getLayout().getPrimaryHorizontal(drawEnd) + getTotalPaddingLeft();
                    if (x0 > x1) {
                        float t = x0;
                        x0 = x1;
                        x1 = t;
                    }

                    float baseline = getLayout().getLineBaseline(l) + getTotalPaddingTop();
                    float waveY = baseline + 3f;
                    float amp = 2.5f;   // wave amplitude
                    float period = 8f;     // full wave width in px

                    diagnosticPath.reset();
                    diagnosticPath.moveTo(x0, waveY);
                    // Smooth quadratic bézier wave: each segment is half-period wide
                    float half = period / 2f;
                    boolean up = true;
                    for (float cx = x0; cx < x1; cx += half) {
                        float ex = Math.min(cx + half, x1);
                        float mid = (cx + ex) / 2f;
                        float ctlY = up ? waveY - amp : waveY + amp;
                        diagnosticPath.quadTo(mid, ctlY, ex, waveY);
                        up = !up;
                    }
                    canvas.drawPath(diagnosticPath, diagnosticPaint);
                }
            }
        }
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
                while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;

                StringBuilder baseIndent = new StringBuilder();
                for (int i = lineStart; i < newlineIndex; i++) {
                    char c = text.charAt(i);
                    if (c == ' ' || c == '\t') baseIndent.append(c);
                    else break;
                }
                outerIndent = baseIndent.toString();
            }
        }

        // HTML tag expansion logic removed as per user request

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
        long delay = (getText() != null && getText().length() > LARGE_FILE_THRESHOLD)
                ? HIGHLIGHT_DELAY_MS_LARGE : HIGHLIGHT_DELAY_MS_SMALL;
        mainHandler.postDelayed(highlightRunnable, delay);
    }

    /**
     * Computes the visible character range in the EditText including a buffer zone.
     * Returns int[2] = {start, end} or null if layout not ready.
     */
    private int[] getVisibleCharRange() {
        if (getLayout() == null || getText() == null) return null;
        int scrollY = getScrollY();
        int viewHeight = getHeight();
        if (viewHeight <= 0) return null;

        android.text.Layout layout = getLayout();
        int firstLine = layout.getLineForVertical(scrollY);
        int lastLine = layout.getLineForVertical(scrollY + viewHeight);

        // Add buffer lines
        firstLine = Math.max(0, firstLine - VIEWPORT_BUFFER_LINES);
        lastLine = Math.min(layout.getLineCount() - 1, lastLine + VIEWPORT_BUFFER_LINES);

        int start = layout.getLineStart(firstLine);
        int end = layout.getLineEnd(lastLine);
        return new int[]{start, end};
    }

    /**
     * Triggers viewport-aware syntax highlighting on a background thread.
     * For small files, highlights the entire document. For large files, highlights
     * only the visible range plus a buffer zone.
     */
    private void triggerHighlight() {
        if (syntaxHighlighter == null || getText() == null) return;

        final String code = getText().toString();
        final long version = ++highlightVersion;

        int finalRangeStart;
        int finalRangeEnd;

        if (code.length() <= LARGE_FILE_THRESHOLD && !dirtyTracker.isDirty()) {
            finalRangeStart = 0;
            finalRangeEnd = code.length();
        } else {
            android.text.Layout layout = getLayout();
            if (layout == null) return;

            if (dirtyTracker.isDirty()) {
                int ds = dirtyTracker.start;
                int de = dirtyTracker.end;
                ds = Math.max(0, ds);
                de = Math.min(code.length(), Math.max(ds, de));

                int firstLine = Math.max(0, layout.getLineForOffset(ds) - VIEWPORT_BUFFER_LINES);
                int lastLine = Math.min(layout.getLineCount() - 1, layout.getLineForOffset(de) + VIEWPORT_BUFFER_LINES);

                finalRangeStart = layout.getLineStart(firstLine);
                finalRangeEnd = layout.getLineEnd(lastLine);
            } else {
                int[] vp = getVisibleCharRange();
                if (vp == null) return;
                finalRangeStart = vp[0];
                finalRangeEnd = vp[1];
            }
        }

        dirtyTracker.reset();

        ExecutorProvider.getInstance().runOnCpu(() -> {
            SpannableStringBuilder ssb = syntaxHighlighter.highlightRange(code, finalRangeStart, finalRangeEnd);
            mainHandler.post(() -> {
                if (version != highlightVersion) return;
                applyHighlightSpans(ssb, finalRangeStart, finalRangeEnd);
            });
        });
    }

    public void applyDiagnostics(List<com.cocode.vcode.ide.data.model.Problem> problems) {
        if (getText() == null) return;
        this.currentProblems = problems != null ? problems : new ArrayList<>();
        // Rebuild line offset cache now (on main thread, text is stable)
        rebuildLineOffsets();
        invalidate();
    }

    /**
     * Returns true if the cursor is inside a // line comment or a /* block comment.
     */
    private boolean isCursorInComment(String text, int cursor) {
        // Scan backwards for // on the same line
        int lineStart = cursor - 1;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        String lineUpToCursor = text.substring(lineStart, cursor);

        // Check for // line comment (not inside a string — simple heuristic)
        boolean inStr = false;
        char strCh = 0;
        for (int i = 0; i < lineUpToCursor.length() - 1; i++) {
            char c = lineUpToCursor.charAt(i);
            if (inStr) {
                if (c == strCh && (i == 0 || lineUpToCursor.charAt(i - 1) != '\\')) inStr = false;
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') {
                inStr = true;
                strCh = c;
                continue;
            }
            if (c == '/' && lineUpToCursor.charAt(i + 1) == '/') return true;
        }

        // Check for /* block comment: scan backwards from cursor for /* without a preceding */
        for (int i = cursor - 2; i >= 0; i--) {
            if (text.charAt(i) == '/' && text.charAt(i + 1) == '*') return true;
            if (i + 1 < text.length() && text.charAt(i) == '*' && text.charAt(i + 1) == '/')
                return false;
        }
        return false;
    }

    private void rebuildLineOffsets() {
        if (!lineOffsetsDirty && cachedLineOffsets != null) return;
        if (getText() == null) {
            cachedLineOffsets = null;
            return;
        }
        // Perf: use CharSequence directly — avoids full String copy every time diagnostics need line offsets
        CharSequence seq = getText();
        int len = seq.length();
        int count = 1;
        for (int i = 0; i < len; i++) if (seq.charAt(i) == '\n') count++;
        // Perf: reuse existing array if size matches — avoids GC allocation on every keystroke
        if (cachedLineOffsets == null || cachedLineOffsets.length != count) {
            cachedLineOffsets = new int[count];
        }
        cachedLineOffsets[0] = 0;
        int idx = 1;
        for (int i = 0; i < len && idx < count; i++) {
            if (seq.charAt(i) == '\n') cachedLineOffsets[idx++] = i + 1;
        }
        lineOffsetsDirty = false;
    }

    /**
     * Applies highlight spans only within the specified range, avoiding full-document span operations.
     */
    private void applyHighlightSpans(SpannableStringBuilder ssb, int rangeStart, int rangeEnd) {
        if (getText() == null || ssb == null) return;
        int textLen = getText().length();
        int safeEnd = Math.min(rangeEnd, textLen);
        if (rangeStart >= safeEnd) return;

        isApplyingHighlight = true;
        int savedStart = getSelectionStart();
        int savedEnd = getSelectionEnd();

        // Remove spans only in the affected range
        SyntaxHighlightSpan[] old = getText().getSpans(rangeStart, safeEnd, SyntaxHighlightSpan.class);
        for (SyntaxHighlightSpan s : old) getText().removeSpan(s);

        ColorPreviewSpan[] oldColors = getText().getSpans(rangeStart, safeEnd, ColorPreviewSpan.class);
        for (ColorPreviewSpan s : oldColors) getText().removeSpan(s);

        // Apply new spans offset by rangeStart
        SyntaxHighlightSpan[] newSpans = ssb.getSpans(0, ssb.length(), SyntaxHighlightSpan.class);
        for (SyntaxHighlightSpan span : newSpans) {
            int start = ssb.getSpanStart(span) + rangeStart;
            int end = ssb.getSpanEnd(span) + rangeStart;
            if (start >= 0 && end <= textLen && start < end) {
                getText().setSpan(new SyntaxHighlightSpan(span.getForegroundColor(), span.isUnderline()),
                        start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        ColorPreviewSpan[] newColorSpans = ssb.getSpans(0, ssb.length(), ColorPreviewSpan.class);
        for (ColorPreviewSpan span : newColorSpans) {
            int start = ssb.getSpanStart(span) + rangeStart;
            int end = ssb.getSpanEnd(span) + rangeStart;
            if (start >= 0 && end <= textLen && start < end) {
                getText().setSpan(new ColorPreviewSpan(span.getPreviewColor(), span.getTextColor()),
                        start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        lastHighlightStart = rangeStart;
        lastHighlightEnd = safeEnd;

        int len = getText().length();
        setSelection(Math.min(savedStart, len), Math.min(savedEnd, len));
        isApplyingHighlight = false;

        // Text changed — force lineOffsets cache to rebuild on next onDraw
        lineOffsetsDirty = true;
    }

    private void scheduleAutoComplete() {
        mainHandler.removeCallbacks(autoCompleteRunnable);
        mainHandler.postDelayed(autoCompleteRunnable, AUTOCOMPLETE_DELAY_MS);
    }

    /**
     * Professional IDE autocomplete trigger logic:
     * — Trigger chars (. < / : ' " @ # !)  → show immediately (0 word chars needed).
     * — Identifier chars (letters, digits, _ $ -)  → require ≥ 2 chars to avoid flooding.
     * — Space, newline, operator, closing bracket → dismiss.
     * — Never trigger inside a string literal or comment (left to engine filtering).
     * — Never trigger when the popup was just dismissed by the user (handled by delay reset).
     */
    private void triggerAutoComplete() {
        if (autoCompleteEngine == null || getText() == null) return;

        String text = getText().toString();
        int cursor = getSelectionStart();

        if (cursor <= 0 || cursor > text.length()) {
            autoCompletePopup.dismiss();
            return;
        }

        // Never trigger inside a comment
        if (isCursorInComment(text, cursor)) {
            autoCompletePopup.dismiss();
            return;
        }

        char lastChar = text.charAt(cursor - 1);

        // Whitespace and newlines always dismiss
        if (Character.isWhitespace(lastChar)) {
            autoCompletePopup.dismiss();
            return;
        }

        boolean isTriggerChar = TRIGGER_CHARS.indexOf(lastChar) >= 0;
        boolean isIdentifier = Character.isLetterOrDigit(lastChar)
                || lastChar == '_' || lastChar == '$'
                || (lastChar == '-' && (fileType == FileType.CSS || fileType == FileType.HTML));

        if (!isIdentifier && !isTriggerChar) {
            // Operator, closing bracket, punctuation etc. — dismiss
            autoCompletePopup.dismiss();
            return;
        }

        if (isIdentifier && !isTriggerChar) {
            // Allow autocomplete on a single character
        }

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
     * Skipped for very large documents to avoid frame drops.
     */
    private void updateBracketMatch(String text, int cursor) {
        if (getText() == null) return;
        BracketMatchSpan[] old = getText().getSpans(0, getText().length(), BracketMatchSpan.class);
        for (BracketMatchSpan s : old) getText().removeSpan(s);

        if (text.length() > 60000) return;

        BracketMatcher.MatchResult match = null;

        if (cursor < text.length()) {
            match = bracketMatcher.findMatch(text, cursor);
        }

        if ((match == null || !match.found) && cursor > 0) {
            match = bracketMatcher.findMatch(text, cursor - 1);
        }

        if (match == null || !match.found) {
            match = bracketMatcher.findEnclosing(text, cursor);
        }

        if (match != null && match.found) {
            applyBracketSpans(match);
        }
    }

    private void applyBracketSpans(BracketMatcher.MatchResult match) {
        int len = getText().length();
        int color = cachedBracketHighlightColor;
        if (match.openPos >= 0 && match.openPos + 1 <= len) {
            getText().setSpan(new BracketMatchSpan(color), match.openPos, match.openPos + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (match.closePos >= 0 && match.closePos + 1 <= len) {
            getText().setSpan(new BracketMatchSpan(color), match.closePos, match.closePos + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

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

    // ─── Undo/Redo ─────────────────────────────────────────────────────────────

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
                    CssAutoCompleteEngine cssEngine = new CssAutoCompleteEngine(ctx);
                    if (currentFile != null) cssEngine.setCurrentFile(currentFile);
                    this.autoCompleteEngine = cssEngine;
                    break;
                case SCSS:
                    this.syntaxHighlighter = new com.cocode.vcode.ide.core.syntax.ScssSyntaxHighlighter(ctx);
                    CssAutoCompleteEngine scssEngine = new CssAutoCompleteEngine(ctx);
                    if (currentFile != null) scssEngine.setCurrentFile(currentFile);
                    this.autoCompleteEngine = scssEngine;
                    break;
                case JAVASCRIPT:
                    this.syntaxHighlighter = new JsSyntaxHighlighter(ctx);
                    JsAutoCompleteEngine jsEngine = new JsAutoCompleteEngine(ctx);
                    if (currentFile != null) jsEngine.setCurrentFile(currentFile);
                    this.autoCompleteEngine = jsEngine;
                    break;
                case TYPESCRIPT:
                    this.syntaxHighlighter = new com.cocode.vcode.ide.core.syntax.TsSyntaxHighlighter(ctx);
                    com.cocode.vcode.ide.core.autocomplete.TsAutoCompleteEngine tsEngine =
                            new com.cocode.vcode.ide.core.autocomplete.TsAutoCompleteEngine(ctx);
                    if (currentFile != null) tsEngine.setCurrentFile(currentFile);
                    this.autoCompleteEngine = tsEngine;
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

        // Suppress keyboard word suggestions for code file types.
        // HTML, JSON, Markdown may contain prose so we allow suggestions there.
        boolean suppressSuggestions = fileType == FileType.CSS
                || fileType == FileType.SCSS
                || fileType == FileType.JAVASCRIPT
                || fileType == FileType.TYPESCRIPT
                || fileType == FileType.SVG
                || fileType == FileType.GITIGNORE
                || fileType == FileType.ENV
                || fileType == FileType.LOG;

        int baseFlags = android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        setInputType(suppressSuggestions
                ? baseFlags | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : baseFlags | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        scheduleHighlight();
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
        if (autoCompleteEngine instanceof HtmlAutoCompleteEngine) {
            ((HtmlAutoCompleteEngine) autoCompleteEngine).setCurrentFile(file);
        } else if (autoCompleteEngine instanceof JsAutoCompleteEngine) {
            ((JsAutoCompleteEngine) autoCompleteEngine).setCurrentFile(file);
        } else if (autoCompleteEngine instanceof JsonAutoCompleteEngine) {
            ((JsonAutoCompleteEngine) autoCompleteEngine).setCurrentFile(file);
        } else if (autoCompleteEngine instanceof CssAutoCompleteEngine) {
            ((CssAutoCompleteEngine) autoCompleteEngine).setCurrentFile(file);
        }
    }

    public void setOnScrollChangeListener(OnScrollChangeListener listener) {
        this.scrollChangeListener = listener;
    }

    @Override
    protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert);

        // Notify scroll listener (drives LineNumberView.invalidate)
        if (scrollChangeListener != null) {
            scrollChangeListener.onScrollChanged(horiz, vert);
        }

        // Re-highlight viewport after scroll settles — debounced to avoid hammering CPU during fast fling
        if (getText() != null && getText().length() > LARGE_FILE_THRESHOLD) {
            mainHandler.removeCallbacks(scrollHighlightRunnable);
            mainHandler.postDelayed(scrollHighlightRunnable, 200);
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
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                && event.getAction() == android.view.KeyEvent.ACTION_UP) {
            if (autoCompletePopup != null && autoCompletePopup.isShowing()) {
                autoCompletePopup.dismiss();
                return true;
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
                autoCompletePopup.dismiss();
            }
            mainHandler.removeCallbacks(bracketMatchRunnable);
            mainHandler.postDelayed(bracketMatchRunnable, 80);
        }
    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mainHandler.removeCallbacks(highlightRunnable);
        mainHandler.removeCallbacks(autoCompleteRunnable);
        mainHandler.removeCallbacks(scrollHighlightRunnable);
        mainHandler.removeCallbacksAndMessages(null);
        autoCompletePopup.dismiss();
    }

    public interface OnScrollChangeListener {
        void onScrollChanged(int scrollX, int scrollY);
    }


    private static class BracketMatchSpan extends StrokeHighlightSpan {
        BracketMatchSpan(int borderColor) {
            super(borderColor);
        }
    }
}