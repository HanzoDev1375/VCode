package com.cocode.vcode.ide.ui.editor.viewer;

/**
 * Callback interface to allow viewers to communicate with the hosting EditorActivity
 * for global UI updates like JSON status bar or find/replace bar.
 */
public interface IEditorCallback {

    void reportProblems(java.io.File file, java.util.List<com.cocode.vcode.ide.data.model.Problem> problems);
}
