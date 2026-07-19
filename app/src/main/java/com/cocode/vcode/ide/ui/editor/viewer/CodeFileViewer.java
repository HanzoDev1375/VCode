package com.cocode.vcode.ide.ui.editor.viewer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.views.CodeEditText;
import com.cocode.vcode.ide.views.CodeEditorLayout;

public class CodeFileViewer implements IFileViewer {

    private final Handler jsonValidationHandler = new Handler(Looper.getMainLooper());
    private FrameLayout viewContainer;
    private CodeEditorLayout editorLayout;
    private CodeEditText codeEditText;
    private EditorFile currentFile;
    private EditorViewModel viewModel;
    private IEditorCallback editorCallback;
    public void flushContentToViewModel() {
        if (currentFile != null && codeEditText != null) {
            currentFile.setContent(codeEditText.getTextAsString());
            currentFile.setCursorPosition(codeEditText.getSelectionStart());
            currentFile.setScrollY(codeEditText.getScrollY());
        }
    }

    @Override
    public View getView(Context context, ViewGroup parent) {
        if (editorLayout == null) {
            // Outer FrameLayout — holds the editor + the floating selection toolbar
            FrameLayout container = new FrameLayout(context);
            container.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            editorLayout = new CodeEditorLayout(context);
            editorLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            container.addView(editorLayout);

            // Pin the SelectionToolbar to the bottom of the container (Phase 4)
            View toolbarView = editorLayout.getSelectionToolbar().getView();
            FrameLayout.LayoutParams tbParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.BOTTOM);
            toolbarView.setLayoutParams(tbParams);
            container.addView(toolbarView);

            this.viewContainer = container;
            codeEditText = editorLayout.getCodeEditText();
            codeEditText.addContentChangeListener(() -> {
                if (currentFile != null && viewModel != null) {
                    if (!currentFile.isDirty()) {
                        currentFile.setDirty(true);
                        viewModel.notifyFileDirtyStatusChanged();
                    }
                    validateCodeIfRequired();
                }
            });

            if (context instanceof IEditorCallback) {
                editorCallback = (IEditorCallback) context;
            }
        }
        return viewContainer;
    }


    @Override
    public void bindFile(EditorFile file, EditorViewModel viewModel) {
        if (codeEditText != null) {
            // We must flush the PREVIOUS file's state to the model before switching!
            // Do this BEFORE updating this.currentFile, otherwise we overwrite the NEW file with the old (or empty) editor content!
            flushContentToViewModel();
        }

        this.currentFile = file;
        this.viewModel = viewModel;

        if (codeEditText == null) return;

        AppSettings settings = viewModel.getSettingsLiveData().getValue();
        if (settings != null) {
            codeEditText.setTextSize(settings.getFontSize());
            codeEditText.setAutoCloseBrackets(settings.isAutoCloseBrackets());
            codeEditText.setAutoIndent(settings.autoIndent);
            editorLayout.setShowLineNumbers(settings.isShowLineNumbers());
        }

        // Horizontal scrolling is managed internally by CodeEditText (View-based OverScroller)

        codeEditText.setTag(file.getId());
        codeEditText.setCurrentFile(file.getFile());
        codeEditText.setFileType(file.getFileType());

        // Only set text if it's different to prevent resetting cursor
        String currentText = codeEditText.getText() != null ? codeEditText.getText().toString() : "";
        if (!currentText.equals(file.getContent())) {
            codeEditText.setText(file.getContent());
            int cursor = file.getCursorPosition();
            if (cursor >= 0 && cursor <= codeEditText.length()) {
                codeEditText.setSelection(cursor);
            }
            codeEditText.scrollTo(0, file.getScrollY());
        }

        validateCodeIfRequired();
    }

    @Override
    public void onResume() {
        if (currentFile != null && codeEditText != null) {
            validateCodeIfRequired();
        }
    }

    @Override
    public void onPause() {
        jsonValidationHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void destroy() {
        onPause();
        if (codeEditText != null) {
            // Nothing to remove for lambdas since we just clear the reference
        }
        editorLayout = null;
        codeEditText = null;
        currentFile = null;
        viewModel = null;
        editorCallback = null;
    }

    @Override
    public CodeEditText getCodeEditor() {
        return codeEditText;
    }

    private void validateCodeIfRequired() {
        if (editorCallback == null || currentFile == null || viewModel == null) return;

        AppSettings settings = viewModel.getSettingsLiveData().getValue();
        if (settings != null) {
            jsonValidationHandler.removeCallbacksAndMessages(null);

            final EditorFile capturedFile = currentFile;
            final IEditorCallback capturedCallback = editorCallback;

            // Capture UI state on main thread
            final int cursor = codeEditText.getSelectionStart();
            final int scrollY = codeEditText.getScrollY();

            Runnable validationRunnable = () -> {
                ExecutorProvider.getInstance().runOnIo(() -> {
                    if (capturedFile == null || capturedFile.getFile() == null) {
                        ExecutorProvider.getInstance().runOnMain(() -> {
                            if (codeEditText != null)
                                codeEditText.applyDiagnostics(new java.util.ArrayList<>());
                        });
                        return;
                    }

                    // 1. Safely allocate the large document string on the background thread!
                    String text = "";
                    if (codeEditText != null) {
                        text = codeEditText.getTextAsString();
                    }

                    // 2. Update EditorFile with latest state so AutoSave will pick it up
                    capturedFile.setContent(text);
                    capturedFile.setCursorPosition(cursor);
                    capturedFile.setScrollY(scrollY);

                    // 3. Trigger AutoSave on Main Thread
                    if (settings.autoSave) {
                        ExecutorProvider.getInstance().runOnMain(() -> viewModel.triggerAutoSave());
                    }

                    // 4. Run language diagnostics
                    java.util.List<com.cocode.vcode.ide.data.model.Problem> problems =
                            com.cocode.vcode.ide.core.diagnostic.DiagnosticEngine.analyze(capturedFile.getFile(), text, capturedFile.getFileType());

                    ExecutorProvider.getInstance().runOnMain(() -> {
                        if (editorLayout == null || editorLayout.getParent() == null || ((View) editorLayout.getParent()).getVisibility() != View.VISIBLE) {
                            return;
                        }
                        if (codeEditText != null) {
                            codeEditText.applyDiagnostics(problems);
                        }
                        if (capturedCallback != null) {
                            capturedCallback.reportProblems(capturedFile.getFile(), problems);
                        }
                    });
                });
            };

            // Set loading state immediately so the UI shows "Analyzing..." while waiting for debounce
            viewModel.setDiagnosticLoading();

            // Perf: adaptive delay — large files get more debounce time so diagnostics don't compete with typing
            int contentLen = codeEditText != null ? codeEditText.length() : 0;
            long diagDelay = contentLen > 20000 ? 1500L : 800L;
            jsonValidationHandler.postDelayed(validationRunnable, diagDelay);
        }
    }
}
