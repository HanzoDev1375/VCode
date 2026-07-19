package com.cocode.vcode.ide.core.editor.text;

/**
 * Observer interface for structural changes to a {@link Content} document.
 *
 * <p>This replaces Android's {@code TextWatcher} as the change-notification contract for the
 * new line-based text model. Subsystems that need to react to edits — the syntax-highlight
 * analyzer, bracket matcher, autocomplete engine, and the {@code TextWatcher} compatibility shim
 * in {@code CodeEditText} — register here rather than on an {@code Editable}.
 *
 * <p>Callbacks are <strong>always delivered on the thread that performed the mutation</strong>.
 * In practice that is the main thread (the UI thread is the only writer). Listeners must
 * not perform I/O or heavy computation inline; dispatch to a background executor instead.
 *
 * <p>A future snippet system that needs to hook into the mutation stream would register here —
 * the interface is intentionally general and requires no breaking changes to support that.
 */
public interface ContentChangeListener {

    /**
     * Called after text has been inserted into the document.
     *
     * @param line     Zero-indexed line at which the insertion begins.
     * @param column   Zero-indexed column at which the insertion begins.
     * @param inserted The exact character sequence that was inserted. May contain {@code '\n'}.
     */
    void onInsert(int line, int column, CharSequence inserted);

    /**
     * Called after a range of text has been deleted from the document.
     *
     * @param startLine   Zero-indexed line where the deleted range began.
     * @param startColumn Zero-indexed column where the deleted range began.
     * @param endLine     Zero-indexed line where the deleted range ended (inclusive of that line's
     *                    characters up to {@code endColumn}).
     * @param endColumn   Zero-indexed column just past the last deleted character on {@code endLine}.
     */
    void onDelete(int startLine, int startColumn, int endLine, int endColumn);
}
