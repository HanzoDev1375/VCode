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
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetGithubLoginBinding;
import com.cocode.vcode.ide.git.core.GitCredentialStore;
import com.cocode.vcode.ide.git.github.GitHubDeviceFlowClient;
import com.cocode.vcode.ide.git.github.GitHubApiClient;
import com.cocode.vcode.ide.ui.git.GitCloneService;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class GitHubLoginBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetGithubLoginBinding binding;
    private GitHubLoginListener listener;
    private GitCloneService.CloneListener cloneListener;
    private GitHubDeviceFlowClient deviceFlowClient;
    private AtomicBoolean isPollingCancelled = new AtomicBoolean(false);

    public static void show(FragmentManager manager, GitHubLoginListener listener) {
        GitHubLoginBottomSheet sheet = new GitHubLoginBottomSheet();
        sheet.setListener(listener);
        sheet.show(manager, "GitHubLoginBottomSheet");
    }

    public void setListener(GitHubLoginListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetGithubLoginBinding.inflate(inflater, container, false);
        deviceFlowClient = new GitHubDeviceFlowClient();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        designUI();
        refreshUIState();
        setupListeners();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        isPollingCancelled.set(true);
    }

    private void refreshUIState() {
        GitCredentialStore store = new GitCredentialStore();

        if (store.hasCredentials(requireContext())) {
            binding.cardGithubLoggedIn.setVisibility(View.VISIBLE);

            binding.imgGithub.setVisibility(View.GONE);
            binding.tvConnectYourGithub.setVisibility(View.GONE);
            binding.tvDeviceFlowInstructions.setVisibility(View.GONE);
            binding.layoutDeviceCode.setVisibility(View.GONE);
            binding.btnsContainer.setVisibility(View.GONE);

            String username = store.getUsername(requireContext());
            binding.tvAccountUsername.setText(username != null ? username : "Connected");

        } else {
            binding.cardGithubLoggedIn.setVisibility(View.GONE);

            binding.imgGithub.setVisibility(View.VISIBLE);
            binding.tvConnectYourGithub.setVisibility(View.VISIBLE);
            binding.tvDeviceFlowInstructions.setVisibility(View.VISIBLE);
            binding.layoutDeviceCode.setVisibility(View.GONE);
            binding.btnsContainer.setVisibility(View.VISIBLE);
            
            binding.btnConnectGithub.setVisibility(View.VISIBLE);
        }
    }

    private void setupListeners() {
        binding.btnConnectGithub.setOnClickListener(v -> {
            setLoadingState(true);
            isPollingCancelled.set(false);

            deviceFlowClient.requestDeviceCode(new GitHubDeviceFlowClient.DeviceCodeCallback() {
                @Override
                public void onSuccess(GitHubDeviceFlowClient.DeviceCodeResponse response) {
                    if (getView() == null) return;
                    
                    binding.btnConnectGithub.setVisibility(View.GONE);
                    binding.layoutDeviceCode.setVisibility(View.VISIBLE);
                    binding.tvUserCode.setText(response.userCode);
                    binding.tvAuthStatus.setText("Waiting for authorization...");
                    binding.authProgress.setVisibility(View.VISIBLE);

                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(response.verificationUriComplete));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "No browser app found to open GitHub.", Toast.LENGTH_SHORT).show();
                    }

                    deviceFlowClient.pollForToken(response.deviceCode, response.intervalSeconds, isPollingCancelled, new GitHubDeviceFlowClient.TokenPollListener() {
                        @Override
                        public void onSuccess(String accessToken) {
                            if (getView() == null) return;
                            fetchIdentityAndFinish(accessToken);
                        }

                        @Override
                        public void onExpired() {
                            if (getView() == null) return;
                            binding.authProgress.setVisibility(View.GONE);
                            binding.tvAuthStatus.setText("Code expired, try again.");
                            binding.btnConnectGithub.setVisibility(View.VISIBLE);
                            setLoadingState(false);
                        }

                        @Override
                        public void onDenied() {
                            if (getView() == null) return;
                            binding.authProgress.setVisibility(View.GONE);
                            binding.tvAuthStatus.setText("Authorization cancelled.");
                            binding.btnConnectGithub.setVisibility(View.VISIBLE);
                            setLoadingState(false);
                        }

                        @Override
                        public void onError(String error) {
                            if (getView() == null) return;
                            binding.authProgress.setVisibility(View.GONE);
                            binding.tvAuthStatus.setText("Error: " + error);
                            binding.btnConnectGithub.setVisibility(View.VISIBLE);
                            setLoadingState(false);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    if (getView() == null) return;
                    setLoadingState(false);
                    Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_LONG).show();
                }
            });
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
    
    private void fetchIdentityAndFinish(String token) {
        binding.tvAuthStatus.setText("Fetching profile...");
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                GitHubApiClient client = new GitHubApiClient(token);
                GitHubApiClient.GitHubUser user = client.validateToken();
                
                GitCredentialStore store = new GitCredentialStore();
                store.saveToken(requireContext(), token);
                store.saveUsername(requireContext(), user.getLogin());
                
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (getView() == null) return;
                    
                    if (listener != null) {
                        listener.onLogin(token, (success, errorMsg) -> {
                            if (getView() != null) {
                                getView().post(() -> {
                                    setLoadingState(false);
                                    if (success) {
                                        refreshUIState();
                                    } else {
                                        Toast.makeText(requireContext(), errorMsg != null ? errorMsg : "Authentication failed", Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        });
                    } else {
                        dismiss();
                    }
                });
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (getView() == null) return;
                    binding.authProgress.setVisibility(View.GONE);
                    binding.tvAuthStatus.setText("Failed to fetch profile.");
                    binding.btnConnectGithub.setVisibility(View.VISIBLE);
                    setLoadingState(false);
                });
            }
        });
    }

    private void designUI() {
        FontManager fm = FontManager.getInstance();
        Context ctx = requireContext();

        binding.tvConnectYourGithub.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvDeviceFlowInstructions.setTypeface(fm.getUiMedium(ctx));
        binding.btnConnectGithub.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvGithubAccount.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvAccountUsername.setTypeface(fm.getUiSemiBold(ctx));
        
        binding.tvUserCode.setTypeface(fm.getUiSemiBold(ctx));
        binding.tvAuthStatus.setTypeface(fm.getUiMedium(ctx));

        UiUtils.setViewRounded(binding.layoutDeviceCode, UiUtils.dpToPx(ctx, 10), ContextCompat.getColor(ctx, R.color.vcode_bg_elevated));
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            binding.btnConnectGithub.setEnabled(false);
            binding.btnConnectGithub.setText(R.string.vcode_connecting);
        } else {
            binding.btnConnectGithub.setEnabled(true);
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