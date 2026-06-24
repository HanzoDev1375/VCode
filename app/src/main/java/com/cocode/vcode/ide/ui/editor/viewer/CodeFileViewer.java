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
    private CodeEditorLayout editorLayout;
    private CodeEditText codeEditText;
    private EditorFile currentFile;
    private EditorViewModel viewModel;
    private IEditorCallback editorCallback;
    private final TextWatcher editorTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (codeEditText != null && currentFile != null && viewModel != null) {
                String content = s.toString();
                viewModel.updateActiveFileContent(content, codeEditText.getSelectionStart(), codeEditText.getScrollY());
                validateCodeIfRequired(content);
            }
        }
    };

    @Override
    public View getView(Context context, ViewGroup parent) {
        if (editorLayout == null) {
            editorLayout = new CodeEditorLayout(context);
            editorLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            codeEditText = editorLayout.getCodeEditText();
            codeEditText.addTextChangedListener(editorTextWatcher);

            if (context instanceof IEditorCallback) {
                editorCallback = (IEditorCallback) context;
            }
        }
        return editorLayout;
    }

    @Override
    public void bindFile(EditorFile file, EditorViewModel viewModel) {
        this.currentFile = file;
        this.viewModel = viewModel;

        if (codeEditText == null) return;

        // Remove listener temporarily to avoid triggering changes during load
        codeEditText.removeTextChangedListener(editorTextWatcher);

        AppSettings settings = viewModel.getSettingsLiveData().getValue();
        if (settings != null) {
            codeEditText.setTextSize(settings.getFontSize());
            codeEditText.setAutoCloseBrackets(settings.isAutoCloseBrackets());
            codeEditText.setAutoIndent(settings.autoIndent);
            editorLayout.setShowLineNumbers(settings.isShowLineNumbers());
        }

        codeEditText.setHorizontallyScrolling(false);

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

        codeEditText.addTextChangedListener(editorTextWatcher);
        validateCodeIfRequired(file.getContent());
    }

    @Override
    public void onResume() {
        if (currentFile != null && codeEditText != null) {
            validateCodeIfRequired(codeEditText.getText().toString());
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
            codeEditText.removeTextChangedListener(editorTextWatcher);
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

    private void validateCodeIfRequired(String text) {
        if (editorCallback == null || currentFile == null || viewModel == null) return;

        AppSettings settings = viewModel.getSettingsLiveData().getValue();
        if (settings != null) {
            jsonValidationHandler.removeCallbacksAndMessages(null);

            final EditorFile capturedFile = currentFile;
            final IEditorCallback capturedCallback = editorCallback;
            Runnable validationRunnable = () -> ExecutorProvider.getInstance().runOnIo(() -> {
                if (capturedFile == null || capturedFile.getFile() == null) {
                    ExecutorProvider.getInstance().runOnMain(() -> {
                        if (codeEditText != null) codeEditText.applyDiagnostics(new java.util.ArrayList<>());
                    });
                    return;
                }

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
            viewModel.setDiagnosticLoading();
            jsonValidationHandler.postDelayed(validationRunnable, 500);
        }
    }
}
