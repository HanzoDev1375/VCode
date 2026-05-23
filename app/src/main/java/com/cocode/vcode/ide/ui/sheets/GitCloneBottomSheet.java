package com.cocode.vcode.ide.ui.sheets;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.git.core.GitCredentialStore;
import com.cocode.vcode.ide.git.core.GitManager;
import com.cocode.vcode.ide.ui.projects.ProjectsViewModel;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * GitCloneBottomSheet manages the workflow for cloning a remote repository.
 * It provides a form for URL and project name input, then switches to a progress
 * view to report real-time JGit cloning status. Upon completion, it automatically
 * generates the required project metadata.
 */
public class GitCloneBottomSheet extends BottomSheetDialogFragment {

    private final ProjectsViewModel projectsViewModel;

    private LinearLayout layoutForm;
    private LinearLayout layoutProgress;
    private EditText etRepoUrl;
    private EditText etProjectName;
    private MaterialButton btnExecuteClone;
    private TextView tvProgressTask;
    private TextView tvProgressDetails;

    public GitCloneBottomSheet(ProjectsViewModel viewModel) {
        this.projectsViewModel = viewModel;
    }

    /**
     * Static helper to instantiate and display the cloning bottom sheet.
     */
    public static void show(androidx.fragment.app.FragmentManager manager, ProjectsViewModel viewModel) {
        new GitCloneBottomSheet(viewModel).show(manager, "GitCloneBottomSheet");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_git_clone, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setCancelable(true); // Allow dismissal while the form is visible

        layoutForm = view.findViewById(R.id.layout_form);
        layoutProgress = view.findViewById(R.id.layout_progress);
        etRepoUrl = view.findViewById(R.id.et_repo_url);
        etProjectName = view.findViewById(R.id.et_project_name);
        btnExecuteClone = view.findViewById(R.id.btn_execute_clone);
        tvProgressTask = view.findViewById(R.id.tv_progress_task);
        tvProgressDetails = view.findViewById(R.id.tv_progress_details);

        // Apply visual styling to input fields
        UiUtils.setViewRounded(etRepoUrl, UiUtils.dpToPx(requireContext(), 10), ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));
        UiUtils.setViewRounded(etProjectName, UiUtils.dpToPx(requireContext(), 10), ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));

        applyTypography(view);
        setupAutoNamingFallback();

        btnExecuteClone.setOnClickListener(v -> initiateRepositoryCloneWorkflow());
    }

    /**
     * Applies the branding fonts to all textual components in the sheet.
     */
    private void applyTypography(View view) {
        Context context = requireContext();
        FontManager fm = FontManager.getInstance();

        ((TextView) view.findViewById(R.id.tv_clone_title)).setTypeface(fm.getUiSemiBold(context));
        ((TextView) view.findViewById(R.id.tv_clone_subtitle)).setTypeface(fm.getUiMedium(context));
        ((TextView) view.findViewById(R.id.tv_repo_url_label)).setTypeface(fm.getUiSemiBold(context));
        ((TextView) view.findViewById(R.id.tv_project_name_label)).setTypeface(fm.getUiSemiBold(context));

        etRepoUrl.setTypeface(fm.getUiMedium(context));
        etProjectName.setTypeface(fm.getUiMedium(context));
        btnExecuteClone.setTypeface(fm.getUiSemiBold(context));
        tvProgressTask.setTypeface(fm.getUiSemiBold(context));
        tvProgressDetails.setTypeface(fm.getUiMedium(context));
    }

    /**
     * Attaches a listener to the URL field to automatically suggest a project name
     * extracted from the repository URL if the name field is empty.
     */
    private void setupAutoNamingFallback() {
        etRepoUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String url = s.toString().trim();
                // Clean up .git suffix
                if (url.endsWith(".git")) {
                    url = url.substring(0, url.length() - 4);
                }
                // Extract the last path segment as the project name
                int lastSlash = url.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < url.length() - 1) {
                    String candidateName = url.substring(lastSlash + 1);
                    if (etProjectName.getText().toString().trim().isEmpty()) {
                        etProjectName.setText(candidateName);
                    }
                }
            }
        });
    }

    /**
     * Validates input and triggers the background JGit clone operation.
     * Manages UI state transitions between form and progress indicator.
     */
    private void initiateRepositoryCloneWorkflow() {
        String repoUrl = etRepoUrl.getText().toString().trim();
        String projectName = etProjectName.getText().toString().trim();

        if (repoUrl.isEmpty()) {
            Toast.makeText(getContext(), "Repository URL cannot be empty.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (projectName.isEmpty()) {
            Toast.makeText(getContext(), "Please specify a project name.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Lock UI to prevent premature dismissal or concurrent clone attempts
        setCancelable(false);
        layoutForm.setVisibility(View.GONE);
        layoutProgress.setVisibility(View.VISIBLE);

        Context context = requireContext().getApplicationContext();
        String projectId = UUID.randomUUID().toString();
        File rootDir = FileUtils.getProjectsDir(context);
        File targetProjectDirectory = new File(rootDir, projectId);

        // Fetch authenticated workspace profile metrics for the JGit operation
        GitCredentialStore store = new GitCredentialStore();
        String gitUser = store.getUsername(context);
        String gitToken;
        try {
            gitToken = store.getToken(context);
        } catch (Exception e) {
            // Logically critical: Handle missing PAT by failing clone attempt
            notifyFailure("Authentication token not found. Please log in to GitHub.");
            return;
        }

        String finalGitToken = gitToken;
        ExecutorProvider.getInstance().runOnIo(() -> {
            // Execute JGit clone with real-time progress callbacks
            var result = GitManager.cloneRepo(repoUrl, targetProjectDirectory, gitUser, finalGitToken, new GitManager.CloneProgressCallback() {
                @Override
                public void onProgress(String task, int done, int total) {
                    ExecutorProvider.getInstance().runOnMain(() -> {
                        if (isAdded()) {
                            tvProgressTask.setText(task);
                            tvProgressDetails.setText(done + " / " + total + " operations completed.");
                        }
                    });
                }

                @Override
                public void onUpdate(int completed) {
                    ExecutorProvider.getInstance().runOnMain(() -> {
                        if (isAdded()) {
                            tvProgressDetails.setText(completed + " structural entities synchronized.");
                        }
                    });
                }

                @Override
                public void onTaskDone() {
                    ExecutorProvider.getInstance().runOnMain(() -> {
                        if (isAdded()) {
                            tvProgressDetails.setText("Task partition successfully complete.");
                        }
                    });
                }
            });

            if (result.isSuccess()) {
                try {
                    // Assemble the project metadata layer post-clone
                    long timestamp = System.currentTimeMillis();
                    JSONObject metadata = new JSONObject();
                    metadata.put("id", projectId);
                    metadata.put("name", projectName);
                    metadata.put("createdAt", timestamp);
                    metadata.put("lastModifiedAt", timestamp);

                    // Auto-detect the primary entry point file
                    String mainFile = getMainFile(targetProjectDirectory);
                    metadata.put("mainFile", mainFile);
                    metadata.put("fileCount", FileUtils.countFilesInDir(targetProjectDirectory));

                    // Persist metadata to disk
                    File metaFile = new File(targetProjectDirectory, "project_meta.json");
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(metaFile), StandardCharsets.UTF_8))) {
                        writer.write(metadata.toString(2));
                    }

                    // Cache the remote URL for future sync operations
                    context.getSharedPreferences("vcode_git_remote_credentials", Context.MODE_PRIVATE)
                            .edit()
                            .putString(targetProjectDirectory.getAbsolutePath() + "_url", repoUrl)
                            .apply();

                    // Notify success and refresh the main project list
                    ExecutorProvider.getInstance().runOnMain(() -> {
                        if (isAdded()) {
                            Toast.makeText(context, "Workspace cloned successfully!", Toast.LENGTH_SHORT).show();
                            projectsViewModel.loadProjects();
                            dismiss();
                        }
                    });

                } catch (Exception e) {
                    // Handle post-clone assembly errors cleanly by wiping the corrupt directory
                    FileUtils.deleteRecursive(targetProjectDirectory);
                    notifyFailure(e.getMessage());
                }
            } else {
                // Wipe data block traces to prevent dirty workspace generation splits on failed clone
                FileUtils.deleteRecursive(targetProjectDirectory);
                notifyFailure(result.getMessage());
            }
        });
    }

    /**
     * Scans the cloned directory for a suitable "main" file (e.g., index.html).
     * @param targetProjectDirectory The cloned repository root.
     * @return The filename of the detected main file.
     */
    @NonNull
    private String getMainFile(File targetProjectDirectory) {
        String mainFile = "index.html";
        if (!new File(targetProjectDirectory, mainFile).exists()) {
            File[] trackingCollection = targetProjectDirectory.listFiles();
            if (trackingCollection != null) {
                for (File innerFile : trackingCollection) {
                    if (innerFile.isFile() && !innerFile.getName().startsWith(".")) {
                        mainFile = innerFile.getName();
                        break;
                    }
                }
            }
        }
        return mainFile;
    }

    /**
     * Resets the UI to the form state and displays a failure notification.
     */
    private void notifyFailure(String traceMessage) {
        ExecutorProvider.getInstance().runOnMain(() -> {
            if (isAdded()) {
                setCancelable(true);
                layoutProgress.setVisibility(View.GONE);
                layoutForm.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), "Clone Transaction Aborted: " + traceMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
}