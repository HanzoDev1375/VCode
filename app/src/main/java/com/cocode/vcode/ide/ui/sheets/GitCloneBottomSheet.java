package com.cocode.vcode.ide.ui.sheets;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
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
import com.cocode.vcode.ide.ui.git.GitCloneService;
import com.cocode.vcode.ide.ui.projects.ProjectsViewModel;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.File;
import java.util.UUID;

public class GitCloneBottomSheet extends BottomSheetDialogFragment {

    private final ProjectsViewModel projectsViewModel;

    private LinearLayout layoutForm;
    private LinearLayout layoutProgress;
    private EditText etRepoUrl;
    private EditText etProjectName;
    private MaterialButton btnExecuteClone;
    private TextView tvProgressTask;
    private TextView tvProgressDetails;
    private TextView tvProgressPercentage;
    private CircularProgressIndicator progressIndicator;
    private MaterialButton btnRunBackground;

    public GitCloneBottomSheet(ProjectsViewModel viewModel) {
        this.projectsViewModel = viewModel;
    }

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
        setCancelable(true);

        layoutForm = view.findViewById(R.id.layout_form);
        layoutProgress = view.findViewById(R.id.layout_progress);
        etRepoUrl = view.findViewById(R.id.et_repo_url);
        etProjectName = view.findViewById(R.id.et_project_name);
        btnExecuteClone = view.findViewById(R.id.btn_execute_clone);
        tvProgressTask = view.findViewById(R.id.tv_progress_task);
        tvProgressDetails = view.findViewById(R.id.tv_progress_details);
        tvProgressPercentage = view.findViewById(R.id.tv_progress_percentage);
        progressIndicator = view.findViewById(R.id.progress_indicator);
        btnRunBackground = view.findViewById(R.id.btn_run_background);

        UiUtils.setViewRounded(etRepoUrl, UiUtils.dpToPx(requireContext(), 10), ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));
        UiUtils.setViewRounded(etProjectName, UiUtils.dpToPx(requireContext(), 10), ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));

        applyTypography(view);
        setupAutoNamingFallback();

        btnExecuteClone.setOnClickListener(v -> initiateRepositoryCloneWorkflow());
        btnRunBackground.setOnClickListener(v -> dismiss());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

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
        tvProgressPercentage.setTypeface(fm.getUiSemiBold(context));
        btnRunBackground.setTypeface(fm.getUiMedium(context));
    }

    private void setupAutoNamingFallback() {
        etRepoUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String url = s.toString().trim();
                if (url.endsWith(".git")) {
                    url = url.substring(0, url.length() - 4);
                }
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

        setCancelable(false);
        layoutForm.setVisibility(View.GONE);
        layoutProgress.setVisibility(View.VISIBLE);

        Context context = requireContext().getApplicationContext();
        String projectId = UUID.randomUUID().toString();
        File rootDir = FileUtils.getProjectsDir(context);
        File targetProjectDirectory = new File(rootDir, projectId);

        GitCredentialStore store = new GitCredentialStore();
        String gitUser = store.getUsername(context);
        String gitToken;
        try {
            gitToken = store.getToken(context);
        } catch (Exception e) {
            notifyFailure("Authentication token not found. Please log in to GitHub.");
            return;
        }

        GitCloneService.setListener(new GitCloneService.CloneListener() {
            @Override
            public void onProgress(String task, int done, int total, int percentage) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (isAdded()) {
                        tvProgressTask.setText(task);
                        if (total > 0) {
                            progressIndicator.setIndeterminate(false);
                            progressIndicator.setProgressCompat(percentage, true);
                            tvProgressPercentage.setText(percentage + "%");
                            tvProgressDetails.setText(done + " / " + total + " completed.");
                        } else {
                            progressIndicator.setIndeterminate(true);
                            tvProgressPercentage.setText("0%");
                            tvProgressDetails.setText("Working...");
                        }
                    }
                });
            }

            @Override
            public void onUpdate(int completed) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (isAdded()) {
                        tvProgressDetails.setText(completed + " entities synchronized.");
                    }
                });
            }

            @Override
            public void onSuccess() {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (isAdded()) {
                        Toast.makeText(context, "Cloned successfully!", Toast.LENGTH_SHORT).show();
                        projectsViewModel.loadProjects();
                        dismiss();
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                notifyFailure(error);
            }
        });

        Intent serviceIntent = new Intent(context, GitCloneService.class);
        serviceIntent.setAction(GitCloneService.ACTION_START_CLONE);
        serviceIntent.putExtra(GitCloneService.EXTRA_REPO_URL, repoUrl);
        serviceIntent.putExtra(GitCloneService.EXTRA_PROJECT_NAME, projectName);
        serviceIntent.putExtra(GitCloneService.EXTRA_TARGET_DIR, targetProjectDirectory.getAbsolutePath());
        serviceIntent.putExtra(GitCloneService.EXTRA_GIT_USER, gitUser);
        serviceIntent.putExtra(GitCloneService.EXTRA_GIT_TOKEN, gitToken);
        serviceIntent.putExtra(GitCloneService.EXTRA_PROJECT_ID, projectId);

        ContextCompat.startForegroundService(context, serviceIntent);
    }

    private void notifyFailure(String traceMessage) {
        ExecutorProvider.getInstance().runOnMain(() -> {
            if (isAdded()) {
                setCancelable(true);
                layoutProgress.setVisibility(View.GONE);
                layoutForm.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), "Clone Failed: " + traceMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
}