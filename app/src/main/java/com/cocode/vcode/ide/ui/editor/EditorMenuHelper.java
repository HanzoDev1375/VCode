package com.cocode.vcode.ide.ui.editor;

import android.content.Intent;
import android.widget.Toast;

import androidx.fragment.app.FragmentManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.ui.git.GitActivity;
import com.cocode.vcode.ide.ui.sheets.EditorOptionsBottomSheet;
import com.cocode.vcode.ide.utils.CodeFormatter;

import java.util.ArrayList;
import java.util.List;

public class EditorMenuHelper {

    public interface MenuCallbacks {
        void onShowFindReplace();
        void onToggleReadOnly();
        void onFormatCode();
        void onGoToLine();
        void onShowSnippetManager();
        void onNavigateWithUnsavedCheck(Runnable action);
        boolean isReadOnly();
    }

    public static void showOverflowMenu(EditorActivity activity, EditorViewModel viewModel, FragmentManager fragmentManager, String projectName, MenuCallbacks callbacks) {
        int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        boolean hasOpenFile = files != null && activeIndex >= 0 && activeIndex < files.size();
        boolean showTextEditingOptions = false;
        EditorFile activeFile = hasOpenFile ? files.get(activeIndex) : null;

        if (hasOpenFile && activeFile != null) {
            FileType type = activeFile.getFileType();
            boolean isBinary = activeFile.isBinaryAsset();

            boolean supportsPreview = type == FileType.CSV || type == FileType.SVG || type == FileType.MARKDOWN;
            String relPath = activeFile.getRelativePath(viewModel.getProjectRoot());
            boolean isPreviewMode = supportsPreview && viewModel.getPreviewState(relPath);

            if (!isBinary && !isPreviewMode) {
                showTextEditingOptions = true;
            }
        }

        List<EditorOptionsBottomSheet.Option> options = new ArrayList<>();

        if (showTextEditingOptions) {
            boolean isVirtual = activeFile.isVirtual();
            if (!isVirtual) {
                options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_magnifying_glass, "Find/Replace", callbacks::onShowFindReplace));
                options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_lock, "Read-only", true, callbacks.isReadOnly(), callbacks::onToggleReadOnly));
            }
            if (CodeFormatter.isFormatSupported(files.get(activeIndex).getFileType())) {
                options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_wand_magic, "Format Code", callbacks::onFormatCode));
            }
            if (!isVirtual) {
                options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_arrow_right, "Go to Line", callbacks::onGoToLine));
            }
        }

        options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_star, "Snippet Manager", callbacks::onShowSnippetManager));

        options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_globe, "API Tester", () -> viewModel.openApiTester()));

        options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_git, "Git", () -> callbacks.onNavigateWithUnsavedCheck(() -> {
            Intent navToGit = new Intent(activity, GitActivity.class);
            if (viewModel.getProjectRoot() != null) {
                navToGit.putExtra("project_path", viewModel.getProjectRoot().getAbsolutePath());
                navToGit.putExtra("project_name", projectName);
                AppSettings settings = viewModel.getSettingsLiveData().getValue();
                if (settings != null && settings.gitDefaultBranch != null) {
                    navToGit.putExtra("default_branch", settings.gitDefaultBranch);
                }
                activity.startActivity(navToGit);
            } else {
                Toast.makeText(activity, R.string.vcode_error_project_directory_not_loaded, Toast.LENGTH_SHORT).show();
            }
        })));

        AppSettings settingsForMenu = viewModel.getSettingsLiveData().getValue();
        boolean autoSave = settingsForMenu != null && settingsForMenu.autoSave;
        if (hasOpenFile && !autoSave) {
            options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_floppy_disk, "Save All", () -> {
                viewModel.saveAll();
                Toast.makeText(activity, R.string.vcode_saving_all_files, Toast.LENGTH_SHORT).show();
            }));
        }

        EditorOptionsBottomSheet bottomSheet = new EditorOptionsBottomSheet();
        bottomSheet.setOptions(options);
        bottomSheet.show(fragmentManager, "EditorOptions");
    }
}
