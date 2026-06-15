package com.cocode.vcode.ide.ui.editor.viewer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.core.parser.json.JsonError;
import com.cocode.vcode.ide.core.parser.json.JsonValidator;
import com.cocode.vcode.ide.core.parser.json.ValidationReport;
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
                validateJsonIfRequired(content);
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
        
        codeEditText.setHorizontallyScrolling(!file.isWordWrapEnabled());

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
        validateJsonIfRequired(file.getContent());
    }

    @Override
    public void onResume() {
        if (currentFile != null && codeEditText != null) {
            validateJsonIfRequired(codeEditText.getText().toString());
        }
    }

    @Override
    public void onPause() {
        jsonValidationHandler.removeCallbacksAndMessages(null);
        if (editorCallback != null) {
            editorCallback.hideJsonStatus();
        }
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

    private void validateJsonIfRequired(String text) {
        if (editorCallback == null || currentFile == null || viewModel == null) return;

        AppSettings settings = viewModel.getSettingsLiveData().getValue();
        if (settings != null && settings.jsonValidateRealtime && currentFile.getFileType() == FileType.JSON) {
            editorCallback.showJsonValidating();
            jsonValidationHandler.removeCallbacksAndMessages(null);

            Runnable jsonValidationRunnable = () -> ExecutorProvider.getInstance().runOnIo(() -> {
                JsonValidator validator = new JsonValidator();
                ValidationReport report = validator.validate(text);

                ExecutorProvider.getInstance().runOnMain(() -> {
                    // Check if this viewer is still active/alive before updating UI
                    if (editorLayout == null || editorLayout.getParent() == null || ((View) editorLayout.getParent()).getVisibility() != View.VISIBLE) {
                        return;
                    }
                    if (report.isValid()) {
                        editorCallback.showJsonValid();
                    } else {
                        JsonError firstError = report.getErrors().get(0);
                        String formattedError = firstError.message + " (Line " + firstError.line + ", Col " + firstError.column + ")";
                        editorCallback.showJsonInvalid(formattedError);
                    }
                });
            });
            jsonValidationHandler.postDelayed(jsonValidationRunnable, 500);
        } else {
            editorCallback.hideJsonStatus();
        }
    }
}
