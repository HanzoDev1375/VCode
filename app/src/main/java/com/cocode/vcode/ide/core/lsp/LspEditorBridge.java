package com.cocode.vcode.ide.core.lsp;

import android.os.Handler;
import android.os.Looper;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.Problem;
import com.cocode.vcode.ide.views.CodeEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bridges {@link CodeEditText} to the in-process {@link LspClientManager}.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Listens to text changes via {@link CodeEditText.OnContentChangeListener}.</li>
 *   <li>Debounces rapid keystrokes (300 ms) before dispatching diagnostic requests.</li>
 *   <li>Builds an {@link LspDocument} snapshot from the editor's current text on every change.</li>
 *   <li>Converts {@link LspDiagnostic} results back into {@link Problem} objects and feeds
 *       them to {@link CodeEditText#applyDiagnostics(List)}.</li>
 *   <li>Exposes async methods for completion, definition, references, and signature help
 *       that callers can invoke directly (e.g., from a long-press context menu).</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Construct once and call {@link #attach(CodeEditText)} when a file is loaded.</li>
 *   <li>Call {@link #detach()} when the file is closed or the editor is destroyed.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * All methods must be called on the Android main thread. The bridge posts debounced
 * work via {@link Handler} and receives all LSP callbacks on the main thread via
 * {@link LspCallback}.
 */
public final class LspEditorBridge {

    private static final long DIAGNOSTIC_DEBOUNCE_MS = 300L;
    private static final long COMPLETION_DEBOUNCE_MS = 100L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Monotonically increasing document version counter — invalidates stale responses. */
    private final AtomicInteger docVersion = new AtomicInteger(0);

    private CodeEditText editor;
    private File currentFile;
    private FileType fileType;

    /** Whether the bridge is actively connected to an editor instance. */
    private boolean attached = false;

    // -------------------------------------------------------------------------
    // Debounce runnables — cancelled and rescheduled on every keystroke
    // -------------------------------------------------------------------------

    private final Runnable diagnosticRunnable = this::performDiagnostics;
    private final Runnable completionRunnable  = this::performCompletion;

    // -------------------------------------------------------------------------
    // ContentChangeListener wired to the editor
    // -------------------------------------------------------------------------

    private final CodeEditText.OnContentChangeListener contentListener = () -> {
        if (!attached || editor == null) return;
        docVersion.incrementAndGet();
        // Reschedule debounced diagnostics
        mainHandler.removeCallbacks(diagnosticRunnable);
        mainHandler.postDelayed(diagnosticRunnable, DIAGNOSTIC_DEBOUNCE_MS);
        // Reschedule debounced completion
        mainHandler.removeCallbacks(completionRunnable);
        mainHandler.postDelayed(completionRunnable, COMPLETION_DEBOUNCE_MS);
        // Notify ProjectIndex of the in-memory change (no IO, just updates the snapshot)
        updateProjectIndex();
    };

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Attaches this bridge to the given editor instance and file.
     * Safe to call multiple times — detaches from the previous editor first.
     *
     * @param codeEditor the editor view to observe
     */
    public void attach(CodeEditText codeEditor) {
        detach();
        this.editor   = codeEditor;
        this.fileType = codeEditor.getFileType();
        this.attached = true;
        codeEditor.addContentChangeListener(contentListener);
    }

    /**
     * Sets the file currently open in the editor.
     * Must be called after {@link #attach(CodeEditText)} whenever a new file is loaded.
     *
     * @param file the open file (used as the document URI)
     */
    public void setFile(File file) {
        this.currentFile = file;
        this.fileType = editor != null ? editor.getFileType() : fileType;
        docVersion.incrementAndGet();
        updateProjectIndex();
        // Trigger an immediate diagnostic pass for the newly opened file
        mainHandler.removeCallbacks(diagnosticRunnable);
        mainHandler.post(diagnosticRunnable);
    }

    /**
     * Requests completion items at the current caret position.
     * The result is delivered to the editor's AutoCompletePopup on the main thread.
     *
     * @param callback optional external callback; may be null
     */
    public void requestCompletion(LspCallback<List<LspCompletionItem>> callback) {
        LspDocument doc = buildSnapshot();
        if (doc == null) {
            if (callback != null) callback.onResult(Collections.emptyList());
            return;
        }
        LspPosition pos = cursorPosition();
        LspClientManager.getInstance().requestCompletion(doc, pos, new LspCallback<List<LspCompletionItem>>() {
            @Override
            public void onResult(List<LspCompletionItem> result) {
                if (callback != null) callback.onResult(result);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    /**
     * Requests the definition location for the symbol under the caret.
     *
     * @param callback delivers the result on the main thread
     */
    public void requestDefinition(LspCallback<LspLocation> callback) {
        LspDocument doc = buildSnapshot();
        if (doc == null) {
            if (callback != null) callback.onError("No document");
            return;
        }
        LspClientManager.getInstance().requestDefinition(doc, cursorPosition(), callback);
    }

    /**
     * Requests all references to the symbol under the caret.
     *
     * @param callback delivers the result list on the main thread
     */
    public void requestReferences(LspCallback<List<LspLocation>> callback) {
        LspDocument doc = buildSnapshot();
        if (doc == null) {
            if (callback != null) callback.onError("No document");
            return;
        }
        LspClientManager.getInstance().requestReferences(doc, cursorPosition(), callback);
    }

    /**
     * Requests signature help at the current caret position.
     * Typically called when the user types {@code (} or {@code ,}.
     *
     * @param callback delivers the result on the main thread
     */
    public void requestSignatureHelp(LspCallback<LspSignatureHelp> callback) {
        LspDocument doc = buildSnapshot();
        if (doc == null) {
            if (callback != null) callback.onResult(null);
            return;
        }
        LspClientManager.getInstance().requestSignatureHelp(doc, cursorPosition(), callback);
    }

    /**
     * Detaches this bridge from the editor, cancelling all pending callbacks.
     * Safe to call even if not attached.
     */
    public void detach() {
        attached = false;
        mainHandler.removeCallbacks(diagnosticRunnable);
        mainHandler.removeCallbacks(completionRunnable);
        if (editor != null) {
            editor.removeContentChangeListener(contentListener);
            editor = null;
        }
        currentFile = null;
    }

    // -------------------------------------------------------------------------
    // Private — debounced operations
    // -------------------------------------------------------------------------

    private void performDiagnostics() {
        if (!attached || editor == null) return;
        LspDocument doc = buildSnapshot();
        if (doc == null) return;
        final int capturedVersion = doc.version;

        LspClientManager.getInstance().requestDiagnostics(doc, new LspCallback<List<LspDiagnostic>>() {
            @Override
            public void onResult(List<LspDiagnostic> result) {
                // Discard stale result if the document has changed since the request
                if (capturedVersion != docVersion.get()) return;
                if (!attached || editor == null) return;
                editor.applyDiagnostics(convertDiagnostics(result));
            }

            @Override
            public void onError(String errorMessage) {
                // Server not ready yet — clear any stale squiggles
                if (attached && editor != null && capturedVersion == docVersion.get()) {
                    editor.applyDiagnostics(new ArrayList<>());
                }
            }
        });
    }

    private void performCompletion() {
        // Completion is handled by the editor's own triggerAutoComplete() during transition.
        // Once LSP servers are fully wired, this will replace that call.
        // For now this is a no-op placeholder.
    }

    // -------------------------------------------------------------------------
    // Private — helpers
    // -------------------------------------------------------------------------

    /**
     * Builds an immutable {@link LspDocument} snapshot from the editor's current content.
     * Returns null if the editor or file is not available.
     */
    private LspDocument buildSnapshot() {
        if (editor == null || currentFile == null) return null;
        String text = editor.getText() != null ? editor.getText().toString() : "";
        String languageId = fileType != null ? fileType.getLspLanguageId() : "plaintext";
        return new LspDocument(currentFile.getAbsolutePath(), text, languageId, docVersion.get());
    }

    /**
     * Returns the current caret position as an {@link LspPosition}.
     * Falls back to (0, 0) if the position cannot be determined.
     */
    private LspPosition cursorPosition() {
        // CodeEditText exposes the cursor via its content model.
        // We read the full text and compute line/char from the flat offset.
        if (editor == null) return new LspPosition(0, 0);
        try {
            // getText() returns the full content; find newlines to determine line/char
            CharSequence text = editor.getText();
            if (text == null) return new LspPosition(0, 0);
            // Use getCursorOffset if it's available (added in Phase 2 wiring);
            // otherwise fall back to a content scan.
            return offsetToLspPosition(text.toString(), getCursorFlatOffset());
        } catch (Exception e) {
            return new LspPosition(0, 0);
        }
    }

    /**
     * Attempts to read the flat cursor offset from the editor.
     * Returns 0 if the editor does not expose a direct accessor.
     */
    private int getCursorFlatOffset() {
        // CodeEditText currently stores the cursor internally; the public API exposes
        // getCursorScreenCoords() but not a raw flat offset.
        // We conservatively return 0 here; a direct accessor will be added in
        // a follow-up when CodeEditText is wired more deeply.
        return 0;
    }

    /** Converts a flat character offset to a zero-based LSP Position. */
    static LspPosition offsetToLspPosition(String text, int offset) {
        int line = 0;
        int lastNl = -1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lastNl = i;
            }
        }
        return new LspPosition(line, Math.max(0, offset - lastNl - 1));
    }

    /** Updates the {@link ProjectIndex} with the latest in-memory snapshot of the current file. */
    private void updateProjectIndex() {
        LspDocument doc = buildSnapshot();
        if (doc != null) {
            ProjectIndex.getInstance().updateDocument(doc);
        }
    }

    /**
     * Converts a list of {@link LspDiagnostic} objects into the editor's native
     * {@link Problem} list format so they can be rendered as squiggly underlines.
     */
    private List<Problem> convertDiagnostics(List<LspDiagnostic> lspDiagnostics) {
        if (lspDiagnostics == null || lspDiagnostics.isEmpty()) return new ArrayList<>();
        List<Problem> problems = new ArrayList<>(lspDiagnostics.size());
        for (LspDiagnostic d : lspDiagnostics) {
            if (d == null || d.range == null) continue;
            int line   = d.range.start.line;
            int col    = d.range.start.character;
            int endCol = d.range.end.character;
            int length = Math.max(1, endCol - col);
            Problem.Severity severity = d.severity == LspDiagnostic.SEVERITY_ERROR
                    ? Problem.Severity.ERROR
                    : d.severity == LspDiagnostic.SEVERITY_WARNING
                    ? Problem.Severity.WARNING
                    : Problem.Severity.INFO;
            // Problem constructor is 1-indexed for line; LSP is 0-indexed
            problems.add(new Problem(currentFile, line + 1, col, length, d.message, severity));
        }
        return problems;
    }
}
