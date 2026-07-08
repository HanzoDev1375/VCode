package com.cocode.vcode.ide.ui.sheets;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetGithubLoginBinding;
import com.cocode.vcode.ide.git.core.GitCredentialStore;
import com.cocode.vcode.ide.ui.git.GitCloneService;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.util.UUID;

public class GitHubLoginBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetGithubLoginBinding binding;
    private GitHubLoginListener listener;
    private Runnable onCloneSuccess;
    private GitCloneService.CloneListener cloneListener;

    public static void show(FragmentManager manager, @Nullable Runnable onCloneSuccess, GitHubLoginListener listener) {
        GitHubLoginBottomSheet sheet = new GitHubLoginBottomSheet();
        sheet.setListener(listener);
        sheet.setOnCloneSuccess(onCloneSuccess);
        sheet.show(manager, "GitHubLoginBottomSheet");
    }

    public void setOnCloneSuccess(Runnable onCloneSuccess) {
        this.onCloneSuccess = onCloneSuccess;
    }

    public void setListener(GitHubLoginListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetGithubLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        designUI();
        refreshUIState();
        setupListeners();
        setupCloneLogic();

        if (onCloneSuccess == null) {
            binding.tabGroup.setVisibility(View.GONE);
            binding.layoutLoginContainer.setVisibility(View.VISIBLE);
            binding.layoutCloneContainer.setVisibility(View.GONE);
        } else {
            binding.tabGroup.setVisibility(View.VISIBLE);
            binding.tabGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    if (checkedId == R.id.tab_login) {
                        binding.layoutLoginContainer.setVisibility(View.VISIBLE);
                        binding.layoutCloneContainer.setVisibility(View.GONE);
                    } else if (checkedId == R.id.tab_clone) {
                        binding.layoutLoginContainer.setVisibility(View.GONE);
                        binding.layoutCloneContainer.setVisibility(View.VISIBLE);
                        // Make sure permission is handled in Activity? Or here. 
                        // Actually, projectsActivity was handling permission.
                        // Let's assume permission is granted or handle it.
                    }
                }
            });
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

    private void refreshUIState() {
        GitCredentialStore store = new GitCredentialStore();

        if (store.hasCredentials(requireContext())) {
            binding.cardGithubLoggedIn.setVisibility(View.VISIBLE);

            binding.imgGithub.setVisibility(View.GONE);
            binding.tvConnectYourGithub.setVisibility(View.GONE);
            binding.tvHowToGetGithubToken.setVisibility(View.GONE);
            binding.tvPat.setVisibility(View.GONE);
            binding.etPat.setVisibility(View.GONE);
            binding.btnsContainer.setVisibility(View.GONE);

            String username = store.getUsername(requireContext());
            binding.tvAccountUsername.setText(username != null ? username : "Connected");

        } else {
            binding.cardGithubLoggedIn.setVisibility(View.GONE);

            binding.imgGithub.setVisibility(View.VISIBLE);
            binding.tvConnectYourGithub.setVisibility(View.VISIBLE);
            binding.tvHowToGetGithubToken.setVisibility(View.VISIBLE);
            binding.tvPat.setVisibility(View.VISIBLE);
            binding.etPat.setVisibility(View.VISIBLE);
            binding.btnsContainer.setVisibility(View.VISIBLE);
        }
    }

    private void setupListeners() {
        binding.btnVisitTokenPage.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new?scopes=repo,workflow"));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "No browser app found to open this URL.", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnConnectGithub.setOnClickListener(v -> {
            String token = binding.etPat.getText() != null ? binding.etPat.getText().toString().trim() : "";

            if (token.isEmpty()) {
                binding.etPat.setError("Token is required");
                binding.etPat.requestFocus();
                return;
            }
            binding.etPat.setError(null);
            setLoadingState(true);

            if (listener != null) {
                listener.onLogin(token, (success, errorMsg) -> {
                    if (getView() != null) {
                        getView().post(() -> {
                            setLoadingState(false);
                            if (success) {
                                refreshUIState();
                            } else {
                                binding.etPat.setError(errorMsg != null ? errorMsg : "Invalid token");
                                binding.etPat.requestFocus();
                            }
                        });
                    }
                });
            } else {
                dismiss();
            }
        });

        binding.btnDisconnectGithub.setOnClickListener(v -> {
            GitCredentialStore store = new GitCredentialStore();
            try {
                store.clearCredentials(requireContext());
                Toast.makeText(requireContext(), "Disconnected from GitHub.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Failed to disconnect. Please try again.", Toast.LENGTH_SHORT).show();
            }
            refreshUIState();
        });
    }

    private void setupCloneLogic() {
        binding.etRepoUrl.addTextChangedListener(new TextWatcher() {
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
                    if (binding.etProjectName.getText().toString().trim().isEmpty()) {
                        binding.etProjectName.setText(candidateName);
                    }
                }
            }
        });

        binding.btnExecuteClone.setOnClickListener(v -> initiateRepositoryCloneWorkflow());
        binding.btnRunBackground.setOnClickListener(v -> dismiss());
    }

    private void initiateRepositoryCloneWorkflow() {
        String repoUrl = binding.etRepoUrl.getText().toString().trim();
        String projectName = binding.etProjectName.getText().toString().trim();

        if (repoUrl.isEmpty()) {
            Toast.makeText(getContext(), "Repository URL is required.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (projectName.isEmpty()) {
            Toast.makeText(getContext(), "Project name is required.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
             Toast.makeText(getContext(), "Storage permission is required to clone repositories.", Toast.LENGTH_SHORT).show();
             return; // Or request permissions if needed.
        }

        setCancelable(false);
        binding.layoutForm.setVisibility(View.GONE);
        binding.layoutProgress.setVisibility(View.VISIBLE);
        // also hide tabs
        binding.tabGroup.setVisibility(View.GONE);

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

        cloneListener = new GitCloneService.CloneListener() {
            @Override
            public void onProgress(String task, int done, int total, int percentage) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (isAdded()) {
                        binding.tvProgressTask.setText(task);
                        if (total > 0) {
                            binding.progressIndicator.setIndeterminate(false);
                            binding.progressIndicator.setProgressCompat(percentage, true);
                            binding.tvProgressPercentage.setText(percentage + "%");
                            binding.tvProgressDetails.setText(done + " / " + total + " completed.");
                        } else {
                            binding.progressIndicator.setIndeterminate(true);
                            binding.tvProgressPercentage.setText("0%");
                            binding.tvProgressDetails.setText("Working...");
                        }
                    }
                });
            }

            @Override
            public void onUpdate(int completed) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (isAdded()) {
                        binding.tvProgressDetails.setText(completed + " entities synchronized.");
                    }
                });
            }

            @Override
            public void onSuccess() {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (isAdded()) {
                        if (onCloneSuccess != null) {
                            onCloneSuccess.run();
                        }
                        dismissAllowingStateLoss();
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                notifyFailure(error);
            }
        };
        GitCloneService.setListener(cloneListener);

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
                binding.layoutProgress.setVisibility(View.GONE);
                binding.layoutForm.setVisibility(View.VISIBLE);
                binding.tabGroup.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), "Clone failed: " + traceMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void designUI() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.tvConnectYourGithub.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvHowToGetGithubToken.setTypeface(fm.getUiMedium(ctx));
        binding.tvPat.setTypeface(fm.getUiMedium(ctx));
        binding.etPat.setTypeface(fm.getUiMedium(ctx));
        binding.btnConnectGithub.setTypeface(fm.getUiSemiBold(ctx));
        binding.btnVisitTokenPage.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvGithubAccount.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvAccountUsername.setTypeface(fm.getUiSemiBold(ctx));

        binding.tabLogin.setTypeface(fm.getUiSemiBold(ctx));
        binding.tabClone.setTypeface(fm.getUiSemiBold(ctx));

        binding.tvCloneTitle.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvCloneSubtitle.setTypeface(fm.getUiMedium(ctx));
        binding.tvRepoUrlLabel.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvProjectNameLabel.setTypeface(fm.getUiSemiBold(ctx));

        binding.etRepoUrl.setTypeface(fm.getUiMedium(ctx));
        binding.etProjectName.setTypeface(fm.getUiMedium(ctx));
        binding.btnExecuteClone.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvProgressTask.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvProgressDetails.setTypeface(fm.getUiMedium(ctx));
        binding.tvProgressPercentage.setTypeface(fm.getUiSemiBold(ctx));
        binding.btnRunBackground.setTypeface(fm.getUiMedium(ctx));

        UiUtils.setViewRounded(binding.etPat, UiUtils.dpToPx(ctx, 10), ContextCompat.getColor(ctx, R.color.vcode_bg_elevated));
        UiUtils.setViewRounded(binding.etRepoUrl, UiUtils.dpToPx(ctx, 10), ContextCompat.getColor(ctx, R.color.vcode_bg_elevated));
        UiUtils.setViewRounded(binding.etProjectName, UiUtils.dpToPx(ctx, 10), ContextCompat.getColor(ctx, R.color.vcode_bg_elevated));
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            binding.btnConnectGithub.setEnabled(false);
            binding.etPat.setEnabled(false);
            binding.btnConnectGithub.setText(R.string.vcode_connecting);
        } else {
            binding.btnConnectGithub.setEnabled(true);
            binding.etPat.setEnabled(true);
            binding.btnConnectGithub.setText(R.string.vcode_connect);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public interface GitHubLoginListener {
        void onLogin(String token, GitHubLoginUpdater updater);
    }

    public interface GitHubLoginUpdater {
        void onResult(boolean success, String errorMsg);
    }
}