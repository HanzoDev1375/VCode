package com.cocode.vcode.ide.ui.editor;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;

import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.data.model.FileNode;
import com.cocode.vcode.ide.databinding.ActivityEditorBinding;
import com.cocode.vcode.ide.ui.base.BaseActivity;
import com.cocode.vcode.ide.ui.editor.viewer.IEditorCallback;
import com.cocode.vcode.ide.ui.editor.viewer.IFileViewer;
import com.cocode.vcode.ide.ui.editor.viewer.ViewerManager;
import com.cocode.vcode.ide.ui.filetree.FileTreeFragment;
import com.cocode.vcode.ide.ui.git.GitActivity;
import com.cocode.vcode.ide.ui.preview.PreviewActivity;
import com.cocode.vcode.ide.ui.sheets.EditorOptionsBottomSheet;
import com.cocode.vcode.ide.ui.sheets.GoToLineBottomSheet;
import com.cocode.vcode.ide.ui.sheets.SnippetsBottomSheet;
import com.cocode.vcode.ide.utils.CodeFormatter;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.LocalWebServer;
import com.cocode.vcode.ide.utils.ProjectFileRecovery;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.views.CodeEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EditorActivity extends BaseActivity implements FileTreeFragment.FileSelectionListener, IEditorCallback {

    public static final String EXTRA_PROJECT_PATH = "extra_project_path";
    public static final String EXTRA_PROJECT_ID = "extra_project_id";
    public static final String EXTRA_PROJECT_NAME = "extra_project_name";
    public static final String EXTRA_OPEN_FILE_PATH = "extra_open_file_path";

    private ActivityEditorBinding binding;
    private LocalWebServer localWebServer;
    private EditorViewModel viewModel;
    private ViewerManager viewerManager;
    private IFileViewer activeViewer;
    private boolean isReadOnly = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UiUtils.applySystemBarInsets(binding.drawerLayout, binding.mainContent, binding.drawerContainer);

        String projectPath = getIntent().getStringExtra(EXTRA_PROJECT_PATH);
        String projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        String projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);

        if (projectPath == null) {
            Toast.makeText(this, "No project path provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (projectId == null) projectId = projectPath.substring(projectPath.lastIndexOf("/") + 1);
        if (projectName == null) projectName = "Project";

        EditorViewModelFactory factory = new EditorViewModelFactory(this);
        viewModel = new ViewModelProvider(this, factory).get(EditorViewModel.class);
        viewerManager = new ViewerManager();

        binding.tvProjectName.setText(projectName);
        binding.tvProjectName.setTypeface(FontManager.getInstance().getUiSemiBold(this));
        binding.tvOpenFileFromTree.setTypeface(FontManager.getInstance().getUiMedium(this));

        File projectDirectory = new File(projectPath);
        ProjectFileRecovery.ensureProjectFilesExist(projectDirectory);
        viewModel.initProject(projectDirectory, projectId, projectName);

        setupFragments();
        setupFloatingPreviewStyles();
        setupListeners();
        setupObservers();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                if (binding.findReplaceBar.getVisibility() == View.VISIBLE) {
                    binding.findReplaceBar.slideUp();
                    return;
                }
                navigateWithUnsavedCheck(EditorActivity.this::finish);
            }
        });

        handleOpenFileIntent(getIntent());
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOpenFileIntent(intent);
    }

    private void handleOpenFileIntent(Intent intent) {
        if (intent != null && intent.hasExtra(EXTRA_OPEN_FILE_PATH)) {
            String path = intent.getStringExtra(EXTRA_OPEN_FILE_PATH);
            if (path != null) {
                File file = new File(path);
                if (file.exists() && file.isFile()) {
                    onFileSelected(new FileNode(file, 0));
                }
            }
        }
    }

    private void setupFragments() {
        if (getSupportFragmentManager().findFragmentById(binding.drawerContainer.getId()) == null) {
            FileTreeFragment fileTreeFragment = new FileTreeFragment();
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(binding.drawerContainer.getId(), fileTreeFragment);
            ft.commit();
        }
    }

    private void setupFloatingPreviewStyles() {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true);
        int baseColor = value.data;
        int glassAccentColor = (baseColor & 0x00FFFFFF) | 0xD9000000;

        GradientDrawable ovalDrawable = new GradientDrawable();
        ovalDrawable.setShape(GradientDrawable.OVAL);
        ovalDrawable.setColor(glassAccentColor);
        binding.ivViewPreview.setBackground(ovalDrawable);
        binding.ivTogglePreview.setBackground(ovalDrawable);
    }

    private void setupListeners() {
        binding.btnMenu.setOnClickListener(v -> {
            UiUtils.hideKeyboard(this);
            CodeEditText codeEditText = getActiveCodeEditor();
            if (codeEditText != null) {
                codeEditText.clearFocus();
            }
            binding.drawerLayout.openDrawer(GravityCompat.START);
        });

        binding.btnUndo.setOnClickListener(v -> {
            CodeEditText codeEditText = getActiveCodeEditor();
            if (codeEditText != null && codeEditText.canUndo()) codeEditText.undo();
        });

        binding.btnRedo.setOnClickListener(v -> {
            CodeEditText codeEditText = getActiveCodeEditor();
            if (codeEditText != null && codeEditText.canRedo()) codeEditText.redo();
        });

        binding.btnRun.setOnClickListener(v -> handleRunAction());

        binding.ivViewPreview.setOnClickListener(v -> executeActiveFilePreviewIntent());

        binding.ivTogglePreview.setOnClickListener(v -> toggleInlinePreview());

        binding.btnSaveCurrent.setOnClickListener(v -> {
            if (activeViewer instanceof com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) {
                ((com.cocode.vcode.ide.ui.editor.viewer.CodeFileViewer) activeViewer).flushContentToViewModel();
            }
            Integer activeIndex = viewModel.getActiveTabIndex().getValue();
            if (activeIndex != null && activeIndex >= 0) {
                viewModel.saveActiveFile();
            }
        });


        binding.diagnosticBar.setOnClickListener(v -> {
            com.cocode.vcode.ide.ui.sheets.ProblemsBottomSheet sheet = new com.cocode.vcode.ide.ui.sheets.ProblemsBottomSheet();
            sheet.setListener(this::jumpToLine);
            Integer activeIndex = viewModel.getActiveTabIndex().getValue();
            if (activeIndex != null && activeIndex >= 0) {
                List<EditorFile> openFiles = viewModel.getOpenFiles().getValue();
                if (openFiles != null && activeIndex < openFiles.size()) {
                    sheet.setFilterFile(openFiles.get(activeIndex).getFile());
                }
            }
            sheet.show(getSupportFragmentManager(), "ProblemsSheet");
        });


        binding.btnOverflow.setOnClickListener(v -> showOverflowMenu());

        binding.tabBar.setOnTabClickListener(index -> {
            saveCurrentEditorState();
            viewModel.setActiveTab(index);
        });

        binding.tabBar.setOnTabCloseListener(index -> {
            saveCurrentEditorState();
            handleTabClose(index);
        });
    }

    private CodeEditText getActiveCodeEditor() {
        if (activeViewer != null) {
            return activeViewer.getCodeEditor();
        }
        return null;
    }

    private void handleRunAction() {
        if (localWebServer != null && localWebServer.isRunning()) {
            localWebServer.stop();
            binding.btnRun.setImageResource(R.drawable.ic_play);
            binding.ivViewPreview.setVisibility(View.GONE);
            Toast.makeText(this, "Server stopped", Toast.LENGTH_SHORT).show();
            updateToolbarVisibility();
            return;
        }

        int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();

        if (files == null || activeIndex < 0 || activeIndex >= files.size()) {
            Toast.makeText(this, "Open a file first to run the preview.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (localWebServer == null) {
            localWebServer = new LocalWebServer(viewModel.getProjectRoot());
        }
        localWebServer.start();
        binding.btnRun.setImageResource(R.drawable.ic_stop);
        binding.ivViewPreview.setVisibility(View.VISIBLE);
        executeActiveFilePreviewIntent();
        updateToolbarVisibility();
    }

    private void toggleInlinePreview() {
        int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        if (files == null || activeIndex < 0 || activeIndex >= files.size()) return;

        EditorFile activeFile = files.get(activeIndex);
        FileType type = activeFile.getFileType();
        if (type != FileType.SVG && type != FileType.CSV && type != FileType.MARKDOWN) return;

        String relPath = activeFile.getRelativePath(viewModel.getProjectRoot());
        boolean isPreviewMode = viewModel.getPreviewState(relPath);

        viewModel.setPreviewState(relPath, !isPreviewMode);

        // This will trigger getSettingsLiveData or we can just force the update manually:
        updateActiveViewer(activeFile, !isPreviewMode);
    }

    private void updateToolbarVisibility() {
        boolean isServerRunning = localWebServer != null && localWebServer.isRunning();

        int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        boolean hasOpenFile = files != null && activeIndex >= 0 && activeIndex < files.size();
        boolean isActiveHtml = false;

        if (hasOpenFile) {
            EditorFile activeFile = files.get(activeIndex);
            if (activeFile.getFileType() == FileType.HTML) {
                isActiveHtml = true;
            }
            binding.btnUndo.setVisibility(View.VISIBLE);
            binding.btnRedo.setVisibility(View.VISIBLE);
            AppSettings settings = viewModel.getSettingsLiveData().getValue();
            boolean autoSave = settings != null && settings.autoSave;
            binding.btnSaveCurrent.setVisibility(autoSave ? View.GONE : View.VISIBLE);
        } else {
            binding.btnUndo.setVisibility(View.GONE);
            binding.btnRedo.setVisibility(View.GONE);
            binding.btnSaveCurrent.setVisibility(View.GONE);
        }

        if (isServerRunning || isActiveHtml) {
            binding.btnRun.setVisibility(View.VISIBLE);
        } else {
            binding.btnRun.setVisibility(View.GONE);
        }
    }

    private void executeActiveFilePreviewIntent() {
        int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        String path = "";
        if (files != null && activeIndex >= 0 && activeIndex < files.size()) {
            path = files.get(activeIndex).getRelativePath(viewModel.getProjectRoot());
        }

        String serverUrl = localWebServer.getUrl(path);
        AppSettings settings = viewModel.getSettingsLiveData().getValue();
        boolean openInApp = settings == null || settings.openPreviewInApp;

        if (openInApp) {
            Intent intent = new Intent(this, PreviewActivity.class);
            intent.putExtra(PreviewActivity.EXTRA_URL, serverUrl);
            if (viewModel.getProjectRoot() != null) {
                intent.putExtra(PreviewActivity.EXTRA_PROJECT_PATH, viewModel.getProjectRoot().getAbsolutePath());
            }
            startActivity(intent);
        } else {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(this, "No browser app found to open this URL.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupObservers() {
        viewModel.getSettingsLiveData().observe(this, settings -> {
            if (settings == null) return;
            int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (settings.theme == AppSettings.Theme.DARK) mode = AppCompatDelegate.MODE_NIGHT_YES;
            else if (settings.theme == AppSettings.Theme.LIGHT)
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            AppCompatDelegate.setDefaultNightMode(mode);

            // Rebind the active viewer so settings take effect
            if (activeViewer != null) {
                int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
                List<EditorFile> files = viewModel.getOpenFiles().getValue();
                if (files != null && activeIndex >= 0 && activeIndex < files.size()) {
                    activeViewer.bindFile(files.get(activeIndex), viewModel);
                }
            }
            binding.tabBar.setAutoSaveOn(settings.autoSave);
            updateToolbarVisibility();
        });

        viewModel.getIsEditorLoading().observe(this, isLoading -> {
            if (isLoading != null && isLoading) {
                binding.progressEditorLoading.setVisibility(View.VISIBLE);
                binding.viewerContainer.setVisibility(View.GONE);
                binding.layoutEmptyEditor.setVisibility(View.GONE);
            } else {
                binding.progressEditorLoading.setVisibility(View.GONE);
                List<EditorFile> files = viewModel.getOpenFiles().getValue();
                if (files != null && !files.isEmpty()) {
                    binding.viewerContainer.setVisibility(View.VISIBLE);
                    binding.layoutEmptyEditor.setVisibility(View.GONE);
                } else {
                    binding.viewerContainer.setVisibility(View.GONE);
                    binding.layoutEmptyEditor.setVisibility(View.VISIBLE);
                }
            }
        });

        viewModel.getOpenFiles().observe(this, files -> {
            int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
            boolean isLoading = viewModel.getIsEditorLoading().getValue() != null && viewModel.getIsEditorLoading().getValue();
            if (files != null && !files.isEmpty()) {
                if (!isLoading) {
                    binding.layoutEmptyEditor.setVisibility(View.GONE);
                    binding.viewerContainer.setVisibility(View.VISIBLE);
                }
                binding.tabBar.setVisibility(View.VISIBLE);
                binding.tabBar.setTabs(files, activeIndex);
                updateBreadcrumbVisibility();
            } else {
                if (!isLoading) {
                    binding.layoutEmptyEditor.setVisibility(View.VISIBLE);
                    binding.viewerContainer.setVisibility(View.GONE);
                }
                binding.tabBar.setVisibility(View.GONE);
                binding.diagnosticBar.setVisibility(View.GONE);
                updateBreadcrumbVisibility();

                // Hide keyboard
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);

                // Hide all toolbar buttons except ivViewPreview (keep visible if server is running)
                binding.ivTogglePreview.setVisibility(View.GONE);
                boolean isServerRunning = localWebServer != null && localWebServer.isRunning();
                binding.ivViewPreview.setVisibility(isServerRunning ? View.VISIBLE : View.GONE);
                updateToolbarVisibility();
            }
        });

        viewModel.getActiveTabIndex().observe(this, index -> {
            List<EditorFile> files = viewModel.getOpenFiles().getValue();
            if (files != null && index >= 0 && index < files.size()) {
                EditorFile activeFile = files.get(index);
                binding.tabBar.setActiveTab(index);

                String relPath = activeFile.getRelativePath(viewModel.getProjectRoot());
                updateBreadcrumbVisibility();

                boolean isPreview = viewModel.getPreviewState(relPath);
                // Don't default to preview mode for empty files (freshly created)
                if (isPreview && !viewModel.hasExplicitPreviewState(relPath)) {
                    String content = activeFile.getContent();
                    if (content == null || content.trim().isEmpty()) {
                        isPreview = false;
                    }
                }
                updateActiveViewer(activeFile, isPreview);
                boolean isEmpty;
                if (activeViewer != null && activeViewer.getCodeEditor() != null) {
                    isEmpty = activeViewer.getCodeEditor().length() == 0;
                } else {
                    String content = activeFile.getContent();
                    isEmpty = (content == null || content.trim().isEmpty());
                }
                boolean showDiagnostic = isFileDiagnosable(activeFile) && !isEmpty;
                binding.diagnosticBar.setVisibility(showDiagnostic ? View.VISIBLE : View.GONE);
            }
            updateToolbarVisibility();
        });

        viewModel.getActiveFileDiagnostics().observe(this, counts -> {
            List<EditorFile> files = viewModel.getOpenFiles().getValue();
            Integer idx = viewModel.getActiveTabIndex().getValue();
            if (files == null || idx == null || idx < 0 || idx >= files.size() || !isFileDiagnosable(files.get(idx))) {
                binding.diagnosticBar.setVisibility(View.GONE);
                return;
            }
            
            boolean isEmpty;
            if (activeViewer != null && activeViewer.getCodeEditor() != null) {
                isEmpty = activeViewer.getCodeEditor().length() == 0;
            } else {
                String content = files.get(idx).getContent();
                isEmpty = (content == null || content.trim().isEmpty());
            }
            
            if (isEmpty) {
                binding.diagnosticBar.setVisibility(View.GONE);
                return;
            }

            binding.diagnosticBar.setVisibility(View.VISIBLE);
            if (counts != null) {
                binding.diagnosticBar.update(counts[0], counts[1], counts[2]);
            } else {
                binding.diagnosticBar.setLoading();
            }
        });
    }

    private void updateBreadcrumbVisibility() {
        if (viewModel == null || binding == null) return;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        Integer index = viewModel.getActiveTabIndex().getValue();
        if (files != null && !files.isEmpty() && index != null && index >= 0 && index < files.size()) {
            EditorFile activeFile = files.get(index);
            if (activeFile.getFileType() == FileType.API_TESTER) {
                binding.breadcrumb.setVisibility(View.GONE);
            } else {
                binding.breadcrumb.setVisibility(View.VISIBLE);
                String relPath = activeFile.getRelativePath(viewModel.getProjectRoot());
                binding.breadcrumb.setPath(viewModel.getProjectName(), relPath);
            }
        } else {
            binding.breadcrumb.setVisibility(View.GONE);
        }
    }

    private boolean isFileDiagnosable(EditorFile file) {
        if (file == null) return false;
        switch (file.getFileType()) {
            case HTML:
            case CSS:
            case SCSS:
            case JAVASCRIPT:
            case TYPESCRIPT:
            case JSON:
                return true;
            default:
                return false;
        }
    }

    private void updateActiveViewer(EditorFile activeFile, boolean isPreview) {
        if (activeViewer != null) {
            activeViewer.onPause();
        }

        activeViewer = viewerManager.getOrCreateViewer(this, activeFile, isPreview);
        View viewerView = activeViewer.getView(this, binding.viewerContainer);

        // Ensure the view is added to the container
        if (viewerView.getParent() == null) {
            binding.viewerContainer.addView(viewerView);
        }

        // Hide all other views, show this one
        for (int i = 0; i < binding.viewerContainer.getChildCount(); i++) {
            View child = binding.viewerContainer.getChildAt(i);
            child.setVisibility(child == viewerView ? View.VISIBLE : View.GONE);
        }

        activeViewer.bindFile(activeFile, viewModel);
        activeViewer.onResume();

        applyReadOnlyState();

        // Update toggle button UI
        FileType type = activeFile.getFileType();
        if (type == FileType.SVG || type == FileType.CSV || type == FileType.MARKDOWN) {
            binding.ivTogglePreview.setVisibility(View.VISIBLE);
            if (isPreview) {
                binding.ivTogglePreview.setImageResource(R.drawable.ic_code);
            } else {
                int iconRes = R.drawable.ic_image_icon;
                if (type == FileType.CSV) iconRes = R.drawable.ic_csv_icon;
                else if (type == FileType.MARKDOWN) iconRes = R.drawable.ic_md_icon;
                binding.ivTogglePreview.setImageResource(iconRes);
            }
        } else {
            binding.ivTogglePreview.setVisibility(View.GONE);
        }

        if (binding.findReplaceBar.getVisibility() == View.VISIBLE) {
            binding.findReplaceBar.slideUp();
        }
    }

    private void handleTabClose(int index) {
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        if (files == null || index < 0 || index >= files.size()) return;

        EditorFile file = files.get(index);
        AppSettings settings = viewModel.getSettingsLiveData().getValue();
        boolean confirm = settings == null || settings.confirmOnTabClose;

        Runnable doClose = () -> {
            viewerManager.destroyViewer(file.getId());
            viewModel.closeFile(index);
        };

        if (file.isDirty() && confirm) {
            new AlertDialog.Builder(this)
                    .setTitle("Unsaved Changes")
                    .setMessage("Save changes to " + file.getFileName() + " before closing?")
                    .setPositiveButton("Save & Close", (d, w) -> viewModel.saveFile(index, doClose))
                    .setNegativeButton("Discard", (d, w) -> doClose.run())
                    .setNeutralButton("Cancel", null)
                    .show();
        } else {
            doClose.run();
        }
    }

    private void showOverflowMenu() {
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
                options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_magnifying_glass, "Find/Replace", this::showFindReplaceBar));
                options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_lock, "Read-only", true, isReadOnly, () -> {
                    isReadOnly = !isReadOnly;
                    applyReadOnlyState();
                }));
            }
            if (CodeFormatter.isFormatSupported(files.get(activeIndex).getFileType())) {
                options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_wand_magic, "Format Code", this::formatCurrentFile));
            }
            if (!isVirtual) {
                options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_arrow_right, "Go to Line", this::showGoToLineDialog));
            }
        }

        options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_star, "Snippet Manager", this::showSnippetManager));

        options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_globe, "API Tester", () -> viewModel.openApiTester()));

        options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_git, "Git", () -> navigateWithUnsavedCheck(() -> {
            Intent navToGit = new Intent(this, GitActivity.class);
            if (viewModel.getProjectRoot() != null) {
                navToGit.putExtra("project_path", viewModel.getProjectRoot().getAbsolutePath());
                navToGit.putExtra("project_name", getIntent().getStringExtra(EXTRA_PROJECT_NAME));
                AppSettings settings = viewModel.getSettingsLiveData().getValue();
                if (settings != null && settings.gitDefaultBranch != null) {
                    navToGit.putExtra("default_branch", settings.gitDefaultBranch);
                }
                startActivity(navToGit);
            } else {
                Toast.makeText(this, "Error: Project directory not loaded.", Toast.LENGTH_SHORT).show();
            }
        })));

        AppSettings settingsForMenu = viewModel.getSettingsLiveData().getValue();
        boolean autoSave = settingsForMenu != null && settingsForMenu.autoSave;
        if (hasOpenFile && !autoSave) {
            options.add(new EditorOptionsBottomSheet.Option(R.drawable.ic_floppy_disk, "Save All", () -> {
                viewModel.saveAll();
                Toast.makeText(this, "Saving all files...", Toast.LENGTH_SHORT).show();
            }));
        }

        EditorOptionsBottomSheet bottomSheet = new EditorOptionsBottomSheet();
        bottomSheet.setOptions(options);
        bottomSheet.show(getSupportFragmentManager(), "EditorOptions");
    }

    private void applyReadOnlyState() {
        CodeEditText codeEditText = getActiveCodeEditor();
        if (codeEditText != null) {
            codeEditText.setFocusable(!isReadOnly);
            codeEditText.setFocusableInTouchMode(!isReadOnly);
            codeEditText.setCursorVisible(!isReadOnly);
        }
    }

    private void showFindReplaceBar() {
        if (binding.findReplaceBar.getVisibility() == View.VISIBLE) {
            binding.findReplaceBar.slideUp();
        } else {
            CodeEditText codeEditText = getActiveCodeEditor();
            if (codeEditText != null) binding.findReplaceBar.setEditor(codeEditText);
            binding.findReplaceBar.slideDown();
        }
    }

    private void showSnippetManager() {
        SnippetsBottomSheet snippetsSheet = new SnippetsBottomSheet();
        snippetsSheet.setListener(snippet -> {
            CodeEditText codeEditText = getActiveCodeEditor();
            if (codeEditText != null && snippet.getContent() != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() ->
                        codeEditText.insertSnippet(snippet.getContent()), 250);
            }
        });
        snippetsSheet.show(getSupportFragmentManager(), "Snippets");
    }

    private void saveCurrentEditorState() {
        CodeEditText codeEditText = getActiveCodeEditor();
        if (codeEditText != null && codeEditText.getTag() != null) {
            List<EditorFile> files = viewModel.getOpenFiles().getValue();
            Integer activeIndex = viewModel.getActiveTabIndex().getValue();
            if (files != null && activeIndex != null && activeIndex >= 0 && activeIndex < files.size()) {
                EditorFile activeFile = files.get(activeIndex);
                if (!activeFile.isBinaryAsset()) {
                    viewModel.updateActiveFileState(codeEditText.getSelectionStart(), codeEditText.getScrollY());
                }
            }
        }
    }

    @Override
    public void onFileSelected(FileNode fileNode) {
        binding.drawerLayout.closeDrawer(GravityCompat.START);
        saveCurrentEditorState();
        viewModel.openFile(fileNode.getFile());
    }

    private void showGoToLineDialog() {
        CodeEditText codeEditText = getActiveCodeEditor();
        if (codeEditText == null || codeEditText.getText() == null) return;

        int maxLines = codeEditText.getLineCount();
        if (maxLines == 0) maxLines = codeEditText.getText().toString().split("\n", -1).length;

        GoToLineBottomSheet sheet = new GoToLineBottomSheet();
        sheet.setMaxLines(maxLines);
        sheet.setListener(this::jumpToLine);
        sheet.show(getSupportFragmentManager(), "GoToLineSheet");
    }

    public void jumpToLine(int line) {
        CodeEditText codeEditText = getActiveCodeEditor();
        if (codeEditText == null) return;
        // goToLine() handles clamping, cursor update, and scroll — O(log n) via Content.positionAt()
        codeEditText.goToLine(line);
    }

    private void formatCurrentFile() {
        CodeEditText codeEditText = getActiveCodeEditor();
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        Integer activeIndex = viewModel.getActiveTabIndex().getValue();

        if (files == null || activeIndex == null || activeIndex < 0 || activeIndex >= files.size() || codeEditText == null) {
            Toast.makeText(this, "No file open to format", Toast.LENGTH_SHORT).show();
            return;
        }

        EditorFile activeFile = files.get(activeIndex);
        if (activeFile.isBinaryAsset()) {
            Toast.makeText(this, "Cannot format a media asset.", Toast.LENGTH_SHORT).show();
            return;
        }

        String rawCode = java.util.Objects.requireNonNull(codeEditText.getText()).toString();
        FileType lang = activeFile.getFileType();
        int originalCursor = codeEditText.getSelectionStart();

        Toast.makeText(this, "Formatting...", Toast.LENGTH_SHORT).show();
        ExecutorProvider.getInstance().runOnIo(() -> {
            String formattedCode = CodeFormatter.format(rawCode, lang);
            ExecutorProvider.getInstance().runOnMain(() -> {
                if (!rawCode.equals(formattedCode)) {
                    codeEditText.setText(formattedCode);
                    // Restore cursor to its pre-format position so Android's cursor-visibility
                    // logic scrolls back to the right place instead of jumping to the top.
                    int safeCursor = Math.min(originalCursor, formattedCode.length());
                    codeEditText.setSelection(safeCursor);
                    Toast.makeText(this, "Formatted successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Code is already formatted", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void navigateWithUnsavedCheck(Runnable navigateAction) {
        if (viewModel.hasUnsavedFiles()) {
            new AlertDialog.Builder(this)
                    .setTitle("Unsaved Changes")
                    .setMessage("You have unsaved files. Save them before leaving?")
                    .setPositiveButton("Save All", (d, w) -> viewModel.saveAll(navigateAction))
                    .setNegativeButton("Discard", (d, w) -> navigateAction.run())
                    .setNeutralButton("Cancel", null)
                    .show();
        } else {
            navigateAction.run();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveCurrentEditorState();
        if (viewModel != null) viewModel.onStopSync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.reloadSettings();
            viewModel.refreshFileTree();
            viewModel.validateOpenFilesWithDisk();
        }
        if (activeViewer != null) {
            activeViewer.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (localWebServer != null) localWebServer.stop();
        if (viewerManager != null) viewerManager.destroyAll();
    }

    @Override
    public void reportProblems(File file, List<com.cocode.vcode.ide.data.model.Problem> problems) {
        if (viewModel != null) {
            viewModel.reportProblems(file, problems);
        }
    }
}