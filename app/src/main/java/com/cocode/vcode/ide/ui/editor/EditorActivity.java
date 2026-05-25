package com.cocode.vcode.ide.ui.editor;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.language.Language;
import com.cocode.vcode.ide.core.parser.json.JsonError;
import com.cocode.vcode.ide.core.parser.json.JsonValidator;
import com.cocode.vcode.ide.core.parser.json.ValidationReport;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.AssetType;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.data.model.FileNode;
import com.cocode.vcode.ide.databinding.ActivityEditorBinding;
import com.cocode.vcode.ide.databinding.ItemCustomPopupBinding;
import com.cocode.vcode.ide.databinding.LayoutCustomPopupBinding;
import com.cocode.vcode.ide.ui.base.BaseActivity;
import com.cocode.vcode.ide.ui.filetree.FileTreeFragment;
import com.cocode.vcode.ide.ui.git.GitActivity;
import com.cocode.vcode.ide.ui.preview.PreviewActivity;
import com.cocode.vcode.ide.ui.settings.SettingsActivity;
import com.cocode.vcode.ide.ui.sheets.GoToLineBottomSheet;
import com.cocode.vcode.ide.ui.sheets.SnippetsBottomSheet;
import com.cocode.vcode.ide.utils.CodeFormatter;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.LocalWebServer;
import com.cocode.vcode.ide.utils.ProjectFileRecovery;
import com.cocode.vcode.ide.utils.UiUtils;
import com.cocode.vcode.ide.views.CodeEditText;

import java.io.File;
import java.util.List;

/**
 * EditorActivity is the core workspace of the VCode IDE.
 * It manages the code editor, file tabs, project structure, local preview server,
 * and integration with various tools like Git, Search, and Snippets.
 * This activity is always scoped to a single project.
 */
public class EditorActivity extends BaseActivity implements FileTreeFragment.FileSelectionListener {

    /** Intent extra for the absolute path to the project directory. */
    public static final String EXTRA_PROJECT_PATH = "extra_project_path";
    /** Intent extra for the unique project identifier. */
    public static final String EXTRA_PROJECT_ID = "extra_project_id";
    /** Intent extra for the user-friendly project name. */
    public static final String EXTRA_PROJECT_NAME = "extra_project_name";

    private final android.os.Handler jsonValidationHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private ActivityEditorBinding binding;
    private LocalWebServer localWebServer;
    private EditorViewModel viewModel;
    private CodeEditText codeEditText;

    /**
     * Watches for text changes in the editor to sync content with the ViewModel
     * and trigger real-time features like JSON validation.
     */
    private final TextWatcher editorTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            // Guard against programmatic changes to avoid infinite loops
            if (codeEditText != null && codeEditText.getTag() != null) {
                String content = s.toString();
                // Persist the current state to the ViewModel
                viewModel.updateActiveFileContent(content, codeEditText.getSelectionStart(), codeEditText.getScrollY());
                // Trigger validation if the file is a JSON file
                validateJsonIfRequired(content);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Configure system UI insets for a true edge-to-edge experience
        UiUtils.applySystemBarInsets(binding.drawerLayout, binding.mainContent, binding.drawerContainer);

        // Extract project details from the launch intent
        String projectPath = getIntent().getStringExtra(EXTRA_PROJECT_PATH);
        String projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        String projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);

        if (projectPath == null) {
            Toast.makeText(this, "No project path provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Fallback logic for project metadata if partial data is provided
        if (projectId == null) projectId = projectPath.substring(projectPath.lastIndexOf("/") + 1);
        if (projectName == null) projectName = "Project";

        // Initialize ViewModel with its factory for dependency injection
        EditorViewModelFactory factory = new EditorViewModelFactory(this);
        viewModel = new ViewModelProvider(this, factory).get(EditorViewModel.class);

        // Cache the reference to the internal code editor view
        codeEditText = binding.editorLayout.getCodeEditText();

        // Apply specialized UI fonts
        binding.tvProjectName.setText(projectName);
        binding.tvProjectName.setTypeface(FontManager.getInstance().getUiSemiBold(this));
        binding.tvOpenFileFromTree.setTypeface(FontManager.getInstance().getUiMedium(this));

        // Ensure project directory and metadata are initialized
        File projectDirectory = new File(projectPath);
        ProjectFileRecovery.ensureProjectFilesExist(projectDirectory);
        viewModel.initProject(projectDirectory, projectId, projectName);

        // Initialize UI components and reactive streams
        setupFragments();
        setupFloatingPreviewStyles();
        setupListeners();
        setupObservers();

        // Custom back press logic to handle drawer and search bar dismissal before exiting
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
                finish();
            }
        });
    }

    /**
     * Injects the FileTreeFragment into the navigation drawer if not already present.
     */
    private void setupFragments() {
        if (getSupportFragmentManager().findFragmentById(binding.drawerContainer.getId()) == null) {
            FileTreeFragment fileTreeFragment = new FileTreeFragment();
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(binding.drawerContainer.getId(), fileTreeFragment);
            ft.commit();
        }
    }

    /**
     * Configures the visual style of the floating preview button, including
     * dynamic color resolution and transparency for a glass-morphism effect.
     */
    private void setupFloatingPreviewStyles() {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true);
        int baseColor = value.data;

        // Apply a subtle 85% opacity to the primary accent color
        int glassAccentColor = (baseColor & 0x00FFFFFF) | 0xD9000000;

        GradientDrawable ovalDrawable = new GradientDrawable();
        ovalDrawable.setShape(GradientDrawable.OVAL);
        ovalDrawable.setColor(glassAccentColor);
        binding.ivViewPreview.setBackground(ovalDrawable);
    }

    /**
     * Attaches click and interaction listeners to the main UI components.
     */
    private void setupListeners() {
        binding.btnMenu.setOnClickListener(v -> {
            UiUtils.hideKeyboard(this);
            if (codeEditText != null) {
                codeEditText.clearFocus();
            }
            binding.drawerLayout.openDrawer(GravityCompat.START);
        });

        binding.btnUndo.setOnClickListener(v -> {
            if (codeEditText != null && codeEditText.canUndo()) codeEditText.undo();
        });

        binding.btnRedo.setOnClickListener(v -> {
            if (codeEditText != null && codeEditText.canRedo()) codeEditText.redo();
        });

        binding.btnRun.setOnClickListener(v -> handleRunAction());

        binding.ivViewPreview.setOnClickListener(v -> executeActiveFilePreviewIntent());

        binding.btnSaveCurrent.setOnClickListener(v -> {
            Integer activeIndex = viewModel.getActiveTabIndex().getValue();
            if (activeIndex != null && activeIndex >= 0) {
                viewModel.saveActiveFile();
                Toast.makeText(this, "File saved", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnOverflow.setOnClickListener(this::showOverflowMenu);

        binding.tabBar.setOnTabClickListener(index -> {
            saveCurrentEditorState();
            viewModel.setActiveTab(index);
        });

        binding.tabBar.setOnTabCloseListener(index -> {
            saveCurrentEditorState();
            handleTabClose(index);
        });

        if (codeEditText != null) {
            codeEditText.addTextChangedListener(editorTextWatcher);
        }
    }

    /**
     * Orchestrates the local server lifecycle for project previews.
     * Starts the server for HTML files and manages the run/stop state UI.
     */
    private void handleRunAction() {
        // Toggle server state if currently running
        if (localWebServer != null && localWebServer.isRunning()) {
            localWebServer.stop();
            binding.btnRun.setImageResource(R.drawable.ic_play);
            binding.ivViewPreview.setVisibility(View.GONE);
            Toast.makeText(this, "Server stopped", Toast.LENGTH_SHORT).show();
            return;
        }

        int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();

        if (files == null || activeIndex < 0 || activeIndex >= files.size()) {
            Toast.makeText(this, "No file open to preview", Toast.LENGTH_SHORT).show();
            return;
        }

        EditorFile activeFile = files.get(activeIndex);
        Language lang = activeFile.getLanguage();

        if (lang == Language.HTML) {
            // Initialize and start the local web server scoped to the project root
            if (localWebServer == null) {
                localWebServer = new LocalWebServer(viewModel.getProjectRoot());
            }
            localWebServer.start();
            binding.btnRun.setImageResource(R.drawable.ic_stop);
            binding.ivViewPreview.setVisibility(View.VISIBLE);

            // Automatically trigger the preview view
            executeActiveFilePreviewIntent();
        } else if (lang == Language.CSS || lang == Language.JAVASCRIPT) {
            // Inform the user that non-HTML files cannot be previewed directly
            new AlertDialog.Builder(this)
                    .setTitle("Preview Required")
                    .setMessage("CSS and JS files need an HTML file to preview. Open an HTML file in a tab and tap Run from there.")
                    .setPositiveButton("Open HTML File", (dialog, which) -> binding.drawerLayout.openDrawer(GravityCompat.START))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            Toast.makeText(this, "No preview available for this file type.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Dispatches an intent to launch the preview interface, either in-app
     * or via an external browser, depending on user preferences.
     */
    private void executeActiveFilePreviewIntent() {
        int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
        List<EditorFile> files = viewModel.getOpenFiles().getValue();

        if (files == null || activeIndex < 0 || activeIndex >= files.size()) {
            Toast.makeText(this, "No file open to preview", Toast.LENGTH_SHORT).show();
            return;
        }

        EditorFile activeFile = files.get(activeIndex);
        Language lang = activeFile.getLanguage();

        if (lang == Language.HTML) {
            // Map the absolute file path to a relative URL for the local server
            String relativePath = activeFile.getFile().getAbsolutePath()
                    .replace(viewModel.getProjectRoot().getAbsolutePath() + "/", "");
            String serverUrl = localWebServer.getUrl(relativePath);

            AppSettings settings = viewModel.getSettingsLiveData().getValue();
            boolean openInApp = settings == null || settings.openPreviewInApp;

            if (openInApp) {
                Intent intent = new Intent(this, PreviewActivity.class);
                intent.putExtra(PreviewActivity.EXTRA_URL, serverUrl);
                startActivity(intent);
            } else {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(this, "Could not open external browser.", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(this, "The active operational tab must display an HTML asset to generate web views.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Initializes LiveData observers to react to data and state changes in the ViewModel.
     */
    private void setupObservers() {
        // Observe settings changes and update editor configuration accordingly
        viewModel.getSettingsLiveData().observe(this, settings -> {
            if (settings == null) return;

            // Apply global theme
            int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (settings.theme == AppSettings.Theme.DARK) mode = AppCompatDelegate.MODE_NIGHT_YES;
            else if (settings.theme == AppSettings.Theme.LIGHT)
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            AppCompatDelegate.setDefaultNightMode(mode);

            // Configure editor view properties
            if (codeEditText != null) {
                codeEditText.setTextSize(settings.getFontSize());
                codeEditText.setAutoCloseBrackets(settings.isAutoCloseBrackets());
                codeEditText.setAutoIndent(settings.autoIndent);
                binding.editorLayout.setShowLineNumbers(settings.isShowLineNumbers());
            }
            // Trigger a re-validation of JSON if the setting changed
            validateJsonIfRequired(codeEditText != null && codeEditText.getText() != null ? codeEditText.getText().toString() : "");
        });

        // Sync the tab bar and empty state UI with the list of open files
        viewModel.getOpenFiles().observe(this, files -> {
            int activeIndex = viewModel.getActiveTabIndex().getValue() != null ? viewModel.getActiveTabIndex().getValue() : -1;
            if (files != null && !files.isEmpty()) {
                binding.layoutEmptyEditor.setVisibility(View.GONE);
                binding.editorContentContainer.setVisibility(View.VISIBLE);
                binding.tabBar.setVisibility(View.VISIBLE);
                binding.breadcrumb.setVisibility(View.VISIBLE);
                binding.tabBar.setTabs(files, activeIndex);
            } else {
                binding.layoutEmptyEditor.setVisibility(View.VISIBLE);
                binding.editorContentContainer.setVisibility(View.GONE);
                binding.tabBar.setVisibility(View.GONE);
                binding.breadcrumb.setVisibility(View.GONE);
                binding.jsonStatusBar.setVisibility(View.GONE);
            }
        });

        // Handle tab switching and asset visualization logic
        viewModel.getActiveTabIndex().observe(this, index -> {
            List<EditorFile> files = viewModel.getOpenFiles().getValue();
            if (files != null && index >= 0 && index < files.size()) {
                EditorFile activeFile = files.get(index);
                binding.tabBar.setActiveTab(index);

                // Update breadcrumbs for navigation context
                String relPath = activeFile.getRelativePath(viewModel.getProjectRoot());
                binding.breadcrumb.setPath(viewModel.getProjectName(), relPath);

                // Determine if we should show the code editor or a specialized asset viewer
                if (activeFile.isBinaryAsset()) {
                    binding.editorLayout.setVisibility(View.GONE);
                    if (binding.findReplaceBar.getVisibility() == View.VISIBLE)
                        binding.findReplaceBar.slideUp();
                    binding.jsonStatusBar.setVisibility(View.GONE);

                    AssetType type = activeFile.getAssetType();
                    if (type == AssetType.IMAGE || type == AssetType.GIF || type == AssetType.ICO || type == AssetType.BMP) {
                        binding.layoutFontViewer.setVisibility(View.GONE);
                        binding.ivImageViewer.setVisibility(View.VISIBLE);

                        if (type == AssetType.GIF) {
                            Glide.with(EditorActivity.this).asGif().load(activeFile.getFile()).into(binding.ivImageViewer);
                        } else {
                            binding.ivImageViewer.setImageURI(Uri.fromFile(activeFile.getFile()));
                        }
                    } else if (type == AssetType.FONT) {
                        binding.ivImageViewer.setVisibility(View.GONE);
                        binding.layoutFontViewer.setVisibility(View.VISIBLE);
                        binding.tvFontName.setText(activeFile.getFile().getName());

                        String ext = FileUtils.getExtension(activeFile.getFile().getName()).toLowerCase();
                        if (ext.equals("ttf") || ext.equals("otf")) {
                            binding.webviewFontPreview.setVisibility(View.GONE);
                            binding.etFontPreview.setVisibility(View.VISIBLE);
                            try {
                                android.graphics.Typeface tf = android.graphics.Typeface.createFromFile(activeFile.getFile());
                                binding.etFontPreview.setTypeface(tf);
                            } catch (Exception e) {
                                Toast.makeText(this, "Could not load font", Toast.LENGTH_SHORT).show();
                            }
                        } else if (ext.equals("woff") || ext.equals("woff2") || ext.equals("eot")) {
                            binding.etFontPreview.setVisibility(View.GONE);
                            binding.webviewFontPreview.setVisibility(View.VISIBLE);
                            loadWebFontPreview(activeFile.getFile(), ext);
                        }
                    }
                } else {
                    // Show standard code editor for text-based files
                    binding.ivImageViewer.setVisibility(View.GONE);
                    binding.layoutFontViewer.setVisibility(View.GONE);
                    binding.editorLayout.setVisibility(View.VISIBLE);

                    if (codeEditText != null) {
                        String currentFileId = (String) codeEditText.getTag();
                        String currentEditorText = codeEditText.getText() != null ? codeEditText.getText().toString() : "";

                        // Only reload the editor if we've switched to a different file or content has changed externally
                        if (!activeFile.getId().equals(currentFileId) || !activeFile.getContent().equals(currentEditorText)) {
                            if (binding.findReplaceBar.getVisibility() == View.VISIBLE) {
                                binding.findReplaceBar.slideUp();
                            }

                            // Avoid triggering the TextWatcher while loading new file content
                            codeEditText.removeTextChangedListener(editorTextWatcher);
                            codeEditText.setTag(activeFile.getId());
                            codeEditText.setLanguage(activeFile.getLanguage());
                            codeEditText.setText(activeFile.getContent());

                            // Restore cursor and scroll position
                            int cursor = activeFile.getCursorPosition();
                            if (cursor >= 0 && cursor <= codeEditText.length()) {
                                codeEditText.setSelection(cursor);
                            }
                            codeEditText.scrollTo(0, activeFile.getScrollY());
                            codeEditText.addTextChangedListener(editorTextWatcher);
                            validateJsonIfRequired(activeFile.getContent());
                        }
                    }
                }
            }
        });
    }

    /**
     * Generates a web-based preview for font assets (WOFF/EOT) using a temporary
     * HTML wrapper with @font-face and base64 encoding.
     */
    private void loadWebFontPreview(java.io.File fontFile, String ext) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                // Read font file bytes into memory
                byte[] bytes = new byte[(int) fontFile.length()];
                try (java.io.FileInputStream fis = new java.io.FileInputStream(fontFile)) {
                    fis.read(bytes);
                }

                String base64Font = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                String format = ext.equals("woff2") ? "woff2" : (ext.equals("eot") ? "embedded-opentype" : "woff");
                String mime = ext.equals("woff2") ? "font/woff2" : "font/woff";

                // Resolve theme-aware text color for the preview
                int colorInt = androidx.core.content.ContextCompat.getColor(this, R.color.vcode_text_primary);
                String hexColor = String.format("#%06X", (0xFFFFFF & colorInt));

                // Construct HTML content with embedded font
                String html = "<!DOCTYPE html><html><head><style>" +
                        "@font-face { font-family: 'Preview'; src: url(data:" + mime + ";charset=utf-8;base64," + base64Font + ") format('" + format + "'); }" +
                        "body { font-family: 'Preview', sans-serif; color: " + hexColor + "; font-size: 24px; text-align: center; display: flex; align-items: center; justify-content: center; margin: 0; background: transparent; }" +
                        "div { outline: none; border: none; width: 100%; margin-top: 24px; }" +
                        "</style></head><body>" +
                        "<div contenteditable=\"true\" spellcheck=\"false\">The quick brown fox jumps over the lazy dog<br>0123456789</div>" +
                        "</body></html>";

                android.os.Handler mainHandler = ExecutorProvider.getInstance().getMainHandler();
                mainHandler.post(() -> {
                    binding.webviewFontPreview.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    binding.webviewFontPreview.getSettings().setJavaScriptEnabled(false);
                    binding.webviewFontPreview.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
                });
            } catch (Exception e) {
                android.os.Handler mainHandler = ExecutorProvider.getInstance().getMainHandler();
                mainHandler.post(() -> Toast.makeText(this, "Could not load font", Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * Performs debounced JSON validation on the background thread and updates the status bar.
     * Only executes if the active file is JSON and real-time validation is enabled.
     */
    private void validateJsonIfRequired(String text) {
        AppSettings settings = viewModel.getSettingsLiveData().getValue();
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        Integer activeIdx = viewModel.getActiveTabIndex().getValue();

        if (settings != null && settings.jsonValidateRealtime && files != null && activeIdx != null && activeIdx >= 0 && activeIdx < files.size()) {
            EditorFile file = files.get(activeIdx);
            if (file.getLanguage() == Language.JSON) {
                binding.jsonStatusBar.setVisibility(View.VISIBLE);
                binding.jsonStatusBar.showValidating();
                jsonValidationHandler.removeCallbacksAndMessages(null);

                Runnable jsonValidationRunnable = () -> ExecutorProvider.getInstance().runOnIo(() -> {
                    JsonValidator validator = new JsonValidator();
                    ValidationReport report = validator.validate(text);

                    ExecutorProvider.getInstance().runOnMain(() -> {
                        if (report.isValid()) {
                            binding.jsonStatusBar.showValid();
                        } else {
                            JsonError firstError = report.getErrors().get(0);
                            String formattedError = firstError.message + " (Line " + firstError.line + ", Col " + firstError.column + ")";
                            binding.jsonStatusBar.showInvalid(formattedError);
                        }
                    });
                });
                // Debounce to avoid constant CPU usage while typing
                jsonValidationHandler.postDelayed(jsonValidationRunnable, 500);
            } else {
                binding.jsonStatusBar.setVisibility(View.GONE);
            }
        } else {
            binding.jsonStatusBar.setVisibility(View.GONE);
        }
    }

    /**
     * Handles the workflow of closing a file tab, including unsaved changes confirmations.
     * @param index The index of the tab to close.
     */
    private void handleTabClose(int index) {
        List<EditorFile> files = viewModel.getOpenFiles().getValue();
        if (files == null || index < 0 || index >= files.size()) return;

        EditorFile file = files.get(index);
        AppSettings settings = viewModel.getSettings();
        boolean confirm = settings == null || settings.confirmOnTabClose;

        if (file.isDirty() && confirm) {
            new AlertDialog.Builder(this)
                    .setTitle("Unsaved Changes")
                    .setMessage("Save changes to " + file.getFileName() + " before closing?")
                    .setPositiveButton("Save & Close", (d, w) -> viewModel.saveFile(index, () -> viewModel.closeFile(index)))
                    .setNegativeButton("Discard", (d, w) -> viewModel.closeFile(index))
                    .setNeutralButton("Cancel", null)
                    .show();
        } else {
            viewModel.closeFile(index);
        }
    }

    /**
     * Displays a custom context menu for additional editor actions like Git, Settings, and Formatting.
     * @param anchorView The view to anchor the popup window to.
     */
    private void showOverflowMenu(View anchorView) {
        LayoutCustomPopupBinding popupBinding = LayoutCustomPopupBinding.inflate(getLayoutInflater());
        int width = UiUtils.dpToPx(this, 220);

        PopupWindow popupWindow = new PopupWindow(popupBinding.getRoot(), width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(UiUtils.dpToPx(this, 8));

        // Add menu items with icons and actions
        addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_magnifying_glass, "Find/Replace", this::showFindReplaceBar);
        addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_wand_magic, "Format Code", this::formatCurrentFile);
        addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_arrow_right, "Go to Line", this::showGoToLineDialog);
        addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_star, "Snippet Manager", this::showSnippetManager);
        addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_git, "Git", () -> {
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
        });
        addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_gear, "Settings", () -> startActivity(new Intent(this, SettingsActivity.class)));
        addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_floppy_disk, "Save All", () -> {
            viewModel.saveAll();
            Toast.makeText(this, "Saving all files...", Toast.LENGTH_SHORT).show();
        });

        popupWindow.showAsDropDown(anchorView, 0, UiUtils.dpToPx(this, 4));
    }

    /**
     * Helper to create and add an item to the custom overflow popup.
     */
    private void addPopupItem(LinearLayout container, PopupWindow popup, int iconRes, String title, Runnable action) {
        ItemCustomPopupBinding itemBinding = ItemCustomPopupBinding.inflate(getLayoutInflater(), container, false);
        itemBinding.ivIcon.setImageResource(iconRes);
        itemBinding.tvTitle.setText(title);
        itemBinding.tvTitle.setTypeface(FontManager.getInstance().getUiMedium(this));

        itemBinding.getRoot().setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        container.addView(itemBinding.getRoot());
    }

    /**
     * Toggles the visibility of the Find and Replace bar.
     */
    private void showFindReplaceBar() {
        if (binding.findReplaceBar.getVisibility() == View.VISIBLE) {
            binding.findReplaceBar.slideUp();
        } else {
            if (codeEditText != null) binding.findReplaceBar.setEditor(codeEditText);
            binding.findReplaceBar.slideDown();
        }
    }

    /**
     * Launches the Snippet Manager bottom sheet for inserting pre-defined code blocks.
     */
    private void showSnippetManager() {
        SnippetsBottomSheet snippetsSheet = new SnippetsBottomSheet();
        snippetsSheet.setListener(snippet -> {
            if (codeEditText != null && snippet.getContent() != null) {
                // Delay insertion slightly to ensure sheet dismissal doesn't steal focus
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() ->
                        codeEditText.insertSnippet(snippet.getContent()), 250);
            }
        });
        snippetsSheet.show(getSupportFragmentManager(), "Snippets");
    }

    /**
     * Captures the current cursor and scroll position of the editor 
     * and saves it to the active file's state in the ViewModel.
     */
    private void saveCurrentEditorState() {
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

    /**
     * Displays a dialog to navigate to a specific line number in the current file.
     */
    private void showGoToLineDialog() {
        if (codeEditText == null || codeEditText.getText() == null) return;

        int maxLines = codeEditText.getLineCount();
        if (maxLines == 0) maxLines = codeEditText.getText().toString().split("\n", -1).length;

        GoToLineBottomSheet sheet = new GoToLineBottomSheet();
        sheet.setMaxLines(maxLines);
        sheet.setListener(line -> {
            int targetLineIndex = line - 1;
            Layout layout = codeEditText.getLayout();

            if (layout != null) {
                int offset = layout.getLineStart(targetLineIndex);
                codeEditText.setSelection(offset);
                int y = layout.getLineTop(targetLineIndex);
                codeEditText.scrollTo(0, Math.max(0, y - codeEditText.getPaddingTop()));
            } else {
                // Fallback for when layout is not yet computed
                String text = codeEditText.getText().toString();
                int currentLine = 0;
                int offset = 0;
                for (int i = 0; i < text.length(); i++) {
                    if (currentLine == targetLineIndex) {
                        offset = i;
                        break;
                    }
                    if (text.charAt(i) == '\n') currentLine++;
                }
                codeEditText.setSelection(offset);
            }
        });
        sheet.show(getSupportFragmentManager(), "GoToLineSheet");
    }

    /**
     * Triggers the appropriate code formatter for the active file's language
     * on a background thread.
     */
    private void formatCurrentFile() {
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
        Language lang = activeFile.getLanguage();

        Toast.makeText(this, "Formatting...", Toast.LENGTH_SHORT).show();
        ExecutorProvider.getInstance().runOnIo(() -> {
            String formattedCode = CodeFormatter.format(rawCode, lang);

            ExecutorProvider.getInstance().runOnMain(() -> {
                if (!rawCode.equals(formattedCode)) {
                    codeEditText.setText(formattedCode);
                    codeEditText.setSelection(0);
                    codeEditText.scrollTo(0, 0);
                    Toast.makeText(this, "Formatted successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Code is already formatted", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Force a state sync to disk when the activity is stopped
        saveCurrentEditorState();
        if (viewModel != null) viewModel.onStopSync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            // Refresh environment and check for external file changes
            viewModel.reloadSettings();
            viewModel.refreshFileTree();
            viewModel.validateOpenFilesWithDisk();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up resources to prevent memory leaks
        if (localWebServer != null) localWebServer.stop();
        jsonValidationHandler.removeCallbacksAndMessages(null);
    }
}