package com.cocode.vcode.ide.ui.editor;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.EditorFile;
import com.cocode.vcode.ide.data.model.FileNode;
import com.cocode.vcode.ide.data.model.ProjectState;
import com.cocode.vcode.ide.data.model.Result;
import com.cocode.vcode.ide.data.repository.FileRepository;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.data.repository.ProjectStateRepository;
import com.cocode.vcode.ide.data.repository.SettingsRepository;
import com.cocode.vcode.ide.git.model.FileStatus;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EditorViewModel serves as the centralized state manager for the EditorActivity.
 * it orchestrates file system operations, project state persistence, settings management,
 * and Git status tracking to ensure a reactive and consistent editing experience.
 */
public class EditorViewModel extends ViewModel {

    private final FileRepository fileRepo;
    private final ProjectStateRepository stateRepo;
    private final SettingsRepository settingsRepo;
    private final ProjectRepository projectRepo;

    // Reactive streams for UI components to observe
    private final MutableLiveData<List<EditorFile>> openFilesLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> activeTabIndexLiveData = new MutableLiveData<>(-1);
    private final MutableLiveData<Result<Boolean>> fileSaveResult = new MutableLiveData<>();
    private final MutableLiveData<ProjectState> projectStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<FileNode>> fileTreeLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<AppSettings> settingsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isEditorLoadingLiveData = new MutableLiveData<>(false);

    /**
     * Maps repository-relative file paths to their current Git status (e.g., Modified, Untracked).
     * This is used to provide visual feedback (colored overlays) in the File Tree.
     */
    private final MutableLiveData<Map<String, FileStatus.Type>> gitStatusesLiveData = new MutableLiveData<>(new HashMap<>());

    private File projectRoot;
    private String projectId;
    private String projectName;
    private ProjectState currentState;

    /** Background task for periodic automatic saving of all dirty files. */
    private final Runnable autoSaveRunnable = this::saveAll;

    public EditorViewModel(FileRepository fileRepo, ProjectStateRepository stateRepo, SettingsRepository settingsRepo, ProjectRepository projectRepo) {
        this.fileRepo = fileRepo;
        this.stateRepo = stateRepo;
        this.settingsRepo = settingsRepo;
        this.projectRepo = projectRepo;
        reloadSettings();
    }

    // --- Getters for reactive data streams ---
    public LiveData<List<FileNode>> getFileTree() { return fileTreeLiveData; }
    public File getProjectRoot() { return projectRoot; }
    public LiveData<List<EditorFile>> getOpenFiles() { return openFilesLiveData; }
    public LiveData<Integer> getActiveTabIndex() { return activeTabIndexLiveData; }
    public LiveData<Result<Boolean>> getFileSaveResult() { return fileSaveResult; }
    public LiveData<ProjectState> getProjectState() { return projectStateLiveData; }
    public String getProjectName() { return projectName; }
    public LiveData<AppSettings> getSettingsLiveData() { return settingsLiveData; }
    public LiveData<Map<String, FileStatus.Type>> getGitStatuses() { return gitStatusesLiveData; }
    public LiveData<Boolean> getIsEditorLoading() { return isEditorLoadingLiveData; }

    /**
     * Loads the latest application settings from the repository and updates the LiveData.
     */
    public void reloadSettings() {
        ExecutorProvider.getInstance().runOnIo(() -> {
            AppSettings freshSettings = settingsRepo.loadSettings();
            ExecutorProvider.getInstance().runOnMain(() -> settingsLiveData.setValue(freshSettings));
        });
    }

    /**
     * Initializes the ViewModel with project metadata and restores the previous session's state.
     * @param root The root directory of the project.
     * @param pId Unique identifier for the project.
     * @param pName Human-friendly name of the project.
     */
    public void initProject(File root, String pId, String pName) {
        if (this.projectRoot != null) return; // Guard against multiple initializations

        this.projectRoot = root;
        this.projectId = pId;
        this.projectName = pName;
        refreshFileTree();

        // Load project-specific state (open tabs, scroll positions) from the metadata repository
        ExecutorProvider.getInstance().runOnIo(() -> {
            ProjectState state = stateRepo.loadStateSync(projectRoot, projectId);
            ExecutorProvider.getInstance().runOnMain(() -> {
                currentState = state;
                projectStateLiveData.setValue(currentState);
                restoreTabsFromState(currentState);
            });
        });
    }

    /**
     * Restores file tabs based on the persisted project state.
     */
    private void restoreTabsFromState(ProjectState state) {
        List<String> paths = state.getOpenFilePaths();
        if (paths == null || paths.isEmpty()) {
            ExecutorProvider.getInstance().runOnIo(() -> {
                try {
                    File metaFile = new File(projectRoot, "project_meta.json");
                    if (metaFile.exists()) {
                        String metaContent = FileUtils.readFile(metaFile);
                        org.json.JSONObject metaJson = new org.json.JSONObject(metaContent);
                        String mainFileName = metaJson.optString("mainFile", "index.html");
                        File mainFile = new File(projectRoot, mainFileName);
                        if (mainFile.exists()) {
                            ExecutorProvider.getInstance().runOnMain(() -> openFile(mainFile));
                        }
                    }
                } catch (Exception ignored) { }
            });
            return;
        }

        ExecutorProvider.getInstance().runOnIo(() -> {
            List<EditorFile> restoredFiles = new ArrayList<>();
            for (String relativePath : paths) {
                File file = new File(projectRoot, relativePath);
                if (file.exists() && file.isFile()) {
                    try {
                        FileType fileType = FileType.fromExtension(FileUtils.getExtension(file.getName()));
                        EditorFile ef = new EditorFile(UUID.randomUUID().toString(), file, "", fileType);
                        ef.setCursorPosition(state.getCursorFor(relativePath));
                        ef.setScrollY(state.getScrollFor(relativePath));
                        ef.setContentLoaded(false);
                        restoredFiles.add(ef);
                    } catch (Exception ignored) { }
                }
            }
            
            int targetTab = state.getActiveTabIndex();
            if (targetTab < 0 || targetTab >= restoredFiles.size()) {
                targetTab = restoredFiles.isEmpty() ? -1 : 0;
            }

            if (targetTab >= 0) {
                EditorFile active = restoredFiles.get(targetTab);
                if (!active.isBinaryAsset()) {
                    try {
                        active.setContent(FileUtils.readFile(active.getFile()));
                        active.markSaved();
                    } catch (Exception ignored) {}
                }
                active.setContentLoaded(true);
            }

            final int finalTargetTab = targetTab;
            ExecutorProvider.getInstance().runOnMain(() -> {
                openFilesLiveData.setValue(restoredFiles);
                activeTabIndexLiveData.setValue(finalTargetTab);
                
                loadRemainingTabsAsync(restoredFiles);
            });
        });
    }

    private void loadRemainingTabsAsync(List<EditorFile> files) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            boolean updated = false;
            for (EditorFile ef : files) {
                if (!ef.isContentLoaded()) {
                    if (!ef.isBinaryAsset()) {
                        try {
                            String content = FileUtils.readFile(ef.getFile());
                            ef.setContent(content);
                            ef.markSaved();
                        } catch (Exception ignored) {}
                    }
                    ef.setContentLoaded(true);
                    updated = true;
                }
            }
            if (updated) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    List<EditorFile> currentDocs = getOpenFilesList();
                    if (!currentDocs.isEmpty()) {
                        openFilesLiveData.setValue(new ArrayList<>(currentDocs));
                    }
                });
            }
        });
    }

    /**
     * Rebuilds the file tree representation based on the current disk state.
     */
    public void refreshFileTree() {
        if (projectRoot == null) return;
        ExecutorProvider.getInstance().runOnIo(() -> {
            List<FileNode> nodes = FileUtils.buildFileTree(projectRoot);
            ExecutorProvider.getInstance().runOnMain(() -> {
                fileTreeLiveData.setValue(nodes);
                // Trigger a refresh of Git statuses to sync with the new tree
                refreshGitStatuses();
            });
        });
    }

    /**
     * Analyzes the project's Git repository to identify modified, untracked, or staged files.
     * Results are mapped by relative path for easy lookup by the UI.
     */
    public void refreshGitStatuses() {
        if (projectRoot == null) return;

        // Skip Git analysis if the project is not a valid repository
        File gitDir = new File(projectRoot, ".git");
        if (!gitDir.exists()) {
            ExecutorProvider.getInstance().runOnMain(() -> gitStatusesLiveData.setValue(new HashMap<>()));
            return;
        }

        ExecutorProvider.getInstance().runOnIo(() -> {
            try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(projectRoot)) {
                org.eclipse.jgit.api.Status workspaceStatus = git.status().call();
                Map<String, FileStatus.Type> freshMap = new HashMap<>();

                // Compile JGit's categorical status sets into our unified mapping
                for (String path : workspaceStatus.getAdded()) freshMap.put(path, FileStatus.Type.STAGED_ADDED);
                for (String path : workspaceStatus.getChanged()) freshMap.put(path, FileStatus.Type.STAGED_MODIFIED);
                for (String path : workspaceStatus.getRemoved()) freshMap.put(path, FileStatus.Type.STAGED_DELETED);
                for (String path : workspaceStatus.getModified()) freshMap.put(path, FileStatus.Type.UNSTAGED_MODIFIED);
                for (String path : workspaceStatus.getMissing()) freshMap.put(path, FileStatus.Type.UNSTAGED_DELETED);
                for (String path : workspaceStatus.getUntracked()) freshMap.put(path, FileStatus.Type.UNTRACKED);
                for (String path : workspaceStatus.getConflicting()) freshMap.put(path, FileStatus.Type.CONFLICTED);

                ExecutorProvider.getInstance().runOnMain(() -> gitStatusesLiveData.setValue(freshMap));
            } catch (Exception e) {
                // Silently handle JGit errors to prevent crashes in malformed repositories
                e.printStackTrace();
            }
        });
    }

    /**
     * Synchronizes open file content with their respective files on disk.
     * This handles scenarios where files were modified or deleted by external processes.
     */
    public void validateOpenFilesWithDisk() {
        if (projectRoot == null) return;

        ExecutorProvider.getInstance().runOnIo(() -> {
            List<EditorFile> currentDocs = getOpenFilesList();
            if (currentDocs.isEmpty()) return;

            List<EditorFile> updatedDocs = new ArrayList<>();
            boolean altered = false;
            int activeIndex = getActiveTabIndexValue();
            int newActiveIndex = activeIndex;

            for (int i = 0; i < currentDocs.size(); i++) {
                EditorFile doc = currentDocs.get(i);
                File fileOnDisk = doc.getFile();

                if (!fileOnDisk.exists()) {
                    // File no longer exists; mark as altered to trigger a UI cleanup
                    altered = true;
                    if (i <= activeIndex && newActiveIndex > 0) {
                        newActiveIndex--;
                    }
                } else {
                    try {
                        // Check if text content has diverged from disk
                        if (!doc.isBinaryAsset()) {
                            String diskContent = FileUtils.readFile(fileOnDisk);
                            if (!diskContent.equals(doc.getContent())) {
                                doc.setContent(diskContent);
                                doc.markSaved(); // Reset dirty state on external reload
                                altered = true;
                            }
                        }
                        updatedDocs.add(doc);
                    } catch (Exception ignored) {
                        updatedDocs.add(doc);
                    }
                }
            }

            // Only notify the UI if an actual change in the open file set occurred
            if (altered) {
                final List<EditorFile> finalDocs = updatedDocs;
                final int finalActiveIndex = finalDocs.isEmpty() ? -1 : Math.min(newActiveIndex, finalDocs.size() - 1);
                ExecutorProvider.getInstance().runOnMain(() -> {
                    openFilesLiveData.setValue(finalDocs);
                    activeTabIndexLiveData.setValue(finalActiveIndex);
                    updateCurrentStateObject();
                    persistStateAsync();
                });
            }
        });
    }

    /**
     * Creates a new file on disk and refreshes the tree.
     */
    public void createFile(File parentDir, String name, String content) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                File newFile = FileUtils.createFile(parentDir, name);
                FileUtils.writeFile(newFile, content);
                refreshFileTree();
                projectRepo.touchProjectById(projectId);
            } catch (Exception ignored) { }
        });
    }

    /**
     * Creates a new directory on disk and refreshes the tree.
     */
    public void createDirectory(File parentDir, String name) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                FileUtils.createFolder(parentDir, name);
                refreshFileTree();
                projectRepo.touchProjectById(projectId);
            } catch (Exception ignored) { }
        });
    }

    /**
     * Deletes a file or directory recursively and refreshes the tree.
     */
    public void deleteNode(File file) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            FileUtils.deleteRecursive(file);
            refreshFileTree();
            projectRepo.touchProjectById(projectId);
        });
    }

    /**
     * Renames an existing file or directory and refreshes the tree.
     */
    public void renameNode(File file, String newName) {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                FileUtils.renameFile(file, newName);
                refreshFileTree();
                projectRepo.touchProjectById(projectId);
            } catch (Exception ignored) { }
        });
    }

    /**
     * Opens a file in the editor, or switches to its tab if it is already open.
     * @param file The file to open.
     */
    public void openFile(File file) {
        List<EditorFile> currentDocs = getOpenFilesList();
        // Check if the file is already loaded in a tab
        for (int i = 0; i < currentDocs.size(); i++) {
            if (currentDocs.get(i).getFile().getAbsolutePath().equals(file.getAbsolutePath())) {
                activeTabIndexLiveData.setValue(i);
                return;
            }
        }

        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                FileType fileType = FileType.fromExtension(FileUtils.getExtension(file.getName()));
                String content = "";

                if (fileType == null || fileType.isTextBased()) {
                    content = FileUtils.readFile(file);
                }

                EditorFile newFile = new EditorFile(UUID.randomUUID().toString(), file, content, fileType);
                newFile.markSaved();
                newFile.setContentLoaded(true);

                // Restore previous cursor/scroll if available in the state object
                if (currentState != null) {
                    String relativePath = getRelativePath(file);
                    newFile.setCursorPosition(currentState.getCursorFor(relativePath));
                    newFile.setScrollY(currentState.getScrollFor(relativePath));
                }

                ExecutorProvider.getInstance().runOnMain(() -> {
                    // Re-check for existing tabs on the main thread to prevent race conditions
                    List<EditorFile> latestDocs = getOpenFilesList();
                    for (int i = 0; i < latestDocs.size(); i++) {
                        if (latestDocs.get(i).getFile().getAbsolutePath().equals(file.getAbsolutePath())) {
                            activeTabIndexLiveData.setValue(i);
                            return;
                        }
                    }

                    List<EditorFile> updated = new ArrayList<>(latestDocs);
                    updated.add(newFile);
                    openFilesLiveData.setValue(updated);
                    activeTabIndexLiveData.setValue(updated.size() - 1);
                    persistStateAsync();
                });
            } catch (Exception ignored) { }
        });
    }

    /**
     * Closes the file tab at the specified index and manages the active tab transition.
     */
    public void closeFile(int index) {
        List<EditorFile> currentDocs = new ArrayList<>(getOpenFilesList());
        if (index < 0 || index >= currentDocs.size()) return;

        currentDocs.remove(index);
        openFilesLiveData.setValue(currentDocs);

        int currentIndex = activeTabIndexLiveData.getValue() != null ? activeTabIndexLiveData.getValue() : -1;
        if (currentDocs.isEmpty()) {
            activeTabIndexLiveData.setValue(-1);
        } else if (index < currentIndex) {
            activeTabIndexLiveData.setValue(currentIndex - 1);
        } else if (index == currentIndex) {
            activeTabIndexLiveData.setValue(Math.min(currentIndex, currentDocs.size() - 1));
        }

        persistStateAsync();
    }

    /**
     * Closes all currently open file tabs.
     */
    public void closeAll() {
        openFilesLiveData.setValue(new ArrayList<>());
        activeTabIndexLiveData.setValue(-1);
        persistStateAsync();
    }

    /**
     * Sets a specific tab as the active (visible) editor tab.
     */
    public void setActiveTab(int index) {
        List<EditorFile> docs = getOpenFilesList();
        if (index >= 0 && index < docs.size()) {
            EditorFile target = docs.get(index);
            if (!target.isContentLoaded()) {
                isEditorLoadingLiveData.setValue(true);
                ExecutorProvider.getInstance().runOnIo(() -> {
                    if (!target.isContentLoaded()) {
                        if (!target.isBinaryAsset()) {
                            try {
                                String content = FileUtils.readFile(target.getFile());
                                target.setContent(content);
                                target.markSaved();
                            } catch (Exception ignored) {}
                        }
                        target.setContentLoaded(true);
                    }
                    ExecutorProvider.getInstance().runOnMain(() -> {
                        openFilesLiveData.setValue(new ArrayList<>(docs));
                        activeTabIndexLiveData.setValue(index);
                        isEditorLoadingLiveData.setValue(false);
                        persistStateAsync();
                    });
                });
            } else {
                activeTabIndexLiveData.setValue(index);
                persistStateAsync();
            }
        }
    }

    /**
     * Updates the content and viewport state of the active file.
     * Triggers the auto-save mechanism if configured.
     */
    public void updateActiveFileContent(String content, int cursor, int scrollY) {
        int index = getActiveTabIndexValue();
        if (index < 0) return;

        List<EditorFile> docs = getOpenFilesList();
        if (index >= docs.size()) return;

        EditorFile file = docs.get(index);

        boolean wasDirty = file.isDirty();
        file.setContent(content);
        file.setCursorPosition(cursor);
        file.setScrollY(scrollY);
        boolean isNowDirty = file.isDirty();

        // Notify UI only if the 'dirty' status changed to update save indicators
        if (wasDirty != isNowDirty) {
            openFilesLiveData.setValue(new ArrayList<>(docs));
        }

        // Handle debounced auto-save logic
        AppSettings settings = settingsLiveData.getValue();
        if (settings != null && settings.autoSave) {
            ExecutorProvider.getInstance().getMainHandler().removeCallbacks(autoSaveRunnable);
            ExecutorProvider.getInstance().getMainHandler().postDelayed(autoSaveRunnable, settings.autoSaveDelay * 20L);
        }
    }

    public void saveFile(int index) {
        saveFile(index, null);
    }

    /**
     * Saves the content of the file at the specified index to disk.
     */
    public void saveFile(int index, Runnable onComplete) {
        List<EditorFile> docs = getOpenFilesList();
        if (index < 0 || index >= docs.size()) return;

        EditorFile ef = docs.get(index);

        // Skip saving if the file isn't modified or is a binary asset
        if (!ef.isDirty() || ef.isBinaryAsset()) {
            fileSaveResult.setValue(Result.success(true));
            if (onComplete != null) onComplete.run();
            return;
        }

        fileRepo.writeFile(ef.getFile(), ef.getContent()).observeForever(result -> {
            if (result != null && result.isSuccess()) {
                ef.markSaved();
                openFilesLiveData.setValue(new ArrayList<>(docs));
                projectRepo.touchProjectById(projectId);

                // Update Git status as the file change is now committed to the filesystem
                refreshGitStatuses();

                if (onComplete != null) onComplete.run();
            }
            fileSaveResult.setValue(result);
        });
    }

    public void saveActiveFile() {
        saveFile(getActiveTabIndexValue());
    }

    /**
     * Checks if there are any open files with unsaved changes.
     */
    public boolean hasUnsavedFiles() {
        List<EditorFile> docs = getOpenFilesList();
        for (EditorFile ef : docs) {
            if (ef.isDirty() && !ef.isBinaryAsset()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bulk saves all open files that have unsaved changes.
     */
    public void saveAll() {
        List<EditorFile> docs = getOpenFilesList();
        ExecutorProvider.getInstance().runOnIo(() -> {
            boolean allSuccess = true;
            boolean anySaved = false;

            for (EditorFile ef : docs) {
                if (ef.isDirty() && !ef.isBinaryAsset()) {
                    try {
                        fileRepo.writeFileSync(ef.getFile(), ef.getContent());
                        ef.markSaved();
                        anySaved = true;
                    } catch (Exception e) {
                        allSuccess = false;
                    }
                }
            }

            boolean finalAllSuccess = allSuccess;
            boolean finalAnySaved = anySaved;

            ExecutorProvider.getInstance().runOnMain(() -> {
                if (finalAnySaved) {
                    openFilesLiveData.setValue(new ArrayList<>(docs));
                    projectRepo.touchProjectById(projectId);
                    refreshGitStatuses();
                }
                if (finalAllSuccess) {
                    fileSaveResult.setValue(Result.success(true));
                } else {
                    fileSaveResult.setValue(Result.error("Failed to save some files"));
                }
            });
        });
    }

    /**
     * Updates the UI-only state (cursor, scroll) for the active file.
     */
    public void updateActiveFileState(int cursor, int scrollY) {
        int index = getActiveTabIndexValue();
        if (index < 0) return;
        List<EditorFile> docs = getOpenFilesList();
        if (index >= docs.size()) return;

        EditorFile file = docs.get(index);
        if (cursor >= 0) file.setCursorPosition(cursor);
        if (scrollY >= 0) file.setScrollY(scrollY);
    }

    public AppSettings getSettings() {
        return settingsRepo.loadSettings();
    }

    /**
     * Triggers an asynchronous save of the project's metadata (tabs, positions).
     */
    public void persistStateAsync() {
        if (currentState == null || projectRoot == null) return;
        updateCurrentStateObject();
        stateRepo.saveState(projectRoot, currentState);
    }

    /**
     * Performs a final synchronous state sync and auto-save before the activity is destroyed.
     */
    public void onStopSync() {
        if (currentState == null || projectRoot == null || projectId == null) return;

        ExecutorProvider.getInstance().getMainHandler().removeCallbacks(autoSaveRunnable);

        AppSettings appSettings = getSettings();
        if (appSettings != null && appSettings.autoSave) {
            saveAllSync();
        }

        updateCurrentStateObject();
        stateRepo.saveStateSync(projectRoot, currentState);
    }

    /** Synchronous version of saveAll for lifecycle-critical cleanup. */
    private void saveAllSync() {
        List<EditorFile> docs = getOpenFilesList();
        boolean anySaved = false;

        for (EditorFile ef : docs) {
            if (ef.isDirty() && !ef.isBinaryAsset()) {
                try {
                    fileRepo.writeFileSync(ef.getFile(), ef.getContent());
                    ef.markSaved();
                    anySaved = true;
                } catch (Exception ignored) { }
            }
        }

        if (anySaved) {
            projectRepo.touchProjectById(projectId);
        }
    }

    /**
     * Updates the internal ProjectState model with current UI data (open paths, cursor positions).
     */
    private void updateCurrentStateObject() {
        if (currentState == null) return;
        List<EditorFile> docs = getOpenFilesList();
        int activeIdx = getActiveTabIndexValue();

        currentState.setActiveTabIndex(activeIdx);

        List<String> paths = new ArrayList<>();
        for (EditorFile doc : docs) {
            String rel = getRelativePath(doc.getFile());
            paths.add(rel);
            currentState.setCursorFor(rel, doc.getCursorPosition());
            currentState.setScrollFor(rel, doc.getScrollY());
        }
        currentState.setOpenFilePaths(paths);
    }

    private List<EditorFile> getOpenFilesList() {
        List<EditorFile> list = openFilesLiveData.getValue();
        return list != null ? list : new ArrayList<>();
    }

    private int getActiveTabIndexValue() {
        Integer val = activeTabIndexLiveData.getValue();
        return val != null ? val : -1;
    }

    /**
     * Computes the relative path of a file with respect to the project root.
     */
    public String getRelativePath(File file) {
        if (projectRoot == null) return file.getName();
        String rootPath = projectRoot.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (filePath.startsWith(rootPath)) {
            String rel = filePath.substring(rootPath.length());
            if (rel.startsWith(File.separator)) rel = rel.substring(1);
            return rel;
        }
        return file.getName();
    }

    public void setPreviewState(String relativePath, boolean isPreview) {
        if (currentState != null) {
            currentState.setPreviewStateFor(relativePath, isPreview);
            persistStateAsync();
        }
    }

    public boolean getPreviewState(String relativePath) {
        if (currentState != null) {
            return currentState.getPreviewStateFor(relativePath);
        }
        return true;
    }
}