package com.cocode.vcode.ide.views;

import java.util.ArrayList;
import java.util.List;

/**
 * Intelligent undo/redo manager that groups edits like professional IDEs (VS Code, IntelliJ).
 */
public class UndoRedoManager {
    private static final int MAX_SMALL = 100;
    private static final int MAX_LARGE = 30; // Fewer snapshots for large files
    private static final int LARGE_TEXT_THRESHOLD = 10000;
    private static final long TIME_GAP_MS = 1000;

    private final List<EditorState> history = new ArrayList<>();
    private int index = -1;

    // Pending group tracking
    private String pendingBaseText;    // Text before the current group started
    private int pendingBaseCursor;     // Cursor before the current group started
    private int pendingLastCursor;     // Cursor after the last edit in current group
    private long lastEditTime;         // Timestamp of last edit in current group
    private int lastEditType;          // 0=none, 1=insert, 2=backspace, 3=forward-delete

    private static boolean isWordBoundary(char c) {
        return c == ' ' || c == '\n' || c == '\t' || c == '\r'
                || c == '.' || c == ',' || c == ';' || c == ':'
                || c == '(' || c == ')' || c == '{' || c == '}'
                || c == '[' || c == ']' || c == '<' || c == '>'
                || c == '"' || c == '\'' || c == '`'
                || c == '=' || c == '+' || c == '-' || c == '/'
                || c == '!' || c == '?' || c == '&' || c == '|';
    }

    public void reset(String text) {
        history.clear();
        index = -1;
        pendingBaseText = null;
        lastEditType = 0;
        record(text, 0);
    }

    /**
     * Records a full snapshot unconditionally (used for initial state).
     */
    public void record(String text, int cursor) {
        if (index >= 0 && history.get(index).text.equals(text)) return;
        commitPending();
        pushState(text, cursor);
    }

    /**
     * Called on every text change with edit metadata for intelligent grouping.
     */
    public void onEdit(String newText, int cursor, int start, int deletedCount, int insertedCount, String oldText) {
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
        boolean shouldBreak = lastEditType != 0 && (now - lastEditTime) > TIME_GAP_MS;

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
    public void commitPendingFromCurrent(String currentText, int currentCursor) {
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
        int max = (text.length() > LARGE_TEXT_THRESHOLD) ? MAX_LARGE : MAX_SMALL;
        if (history.size() > max) {
            history.remove(0);
        } else {
            index++;
        }
    }

    public boolean canUndo() {
        return index > 0 || pendingBaseText != null;
    }

    public boolean canRedo() {
        return index < history.size() - 1;
    }

    public EditorState undo() {
        if (!canUndo()) return null;
        if (index > 0) {
            return history.get(--index);
        }
        return null;
    }

    public EditorState redo() {
        if (!canRedo()) return null;
        return history.get(++index);
    }
}
