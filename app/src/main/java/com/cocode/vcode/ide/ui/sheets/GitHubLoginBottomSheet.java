package com.cocode.vcode.ide.ui.sheets;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * GitHubLoginBottomSheet provides a secure interface for linking a GitHub account via Personal Access Token.
 * It features a dual-state UI: a login form for unauthenticated users, and a profile card
 * for users who have already connected their account.
 */
public class GitHubLoginBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetGithubLoginBinding binding;
    private GitHubLoginListener listener;

    /**
     * Static helper to instantiate and display the GitHub login sheet.
     */
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
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        designUI();
        // Determine which UI state (Login vs Profile) to display based on existing credentials
        refreshUIState();
        setupListeners();
    }

    /**
     * Toggles the visibility of UI components based on the current authentication status.
     * Displays the user's profile card if logged in, otherwise shows the PAT entry form.
     */
    private void refreshUIState() {
        GitCredentialStore store = new GitCredentialStore();

        if (store.hasCredentials(requireContext())) {
            // User IS authenticated: Show profile details and logout option
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
            // User is NOT authenticated: Show PAT input form and instructions
            binding.cardGithubLoggedIn.setVisibility(View.GONE);

            binding.imgGithub.setVisibility(View.VISIBLE);
            binding.tvConnectYourGithub.setVisibility(View.VISIBLE);
            binding.tvHowToGetGithubToken.setVisibility(View.VISIBLE);
            binding.tvPat.setVisibility(View.VISIBLE);
            binding.etPat.setVisibility(View.VISIBLE);
            binding.btnsContainer.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Initializes listeners for token creation navigation, account connection, and disconnection.
     */
    private void setupListeners() {
        // Navigate the user to the GitHub PAT creation page with pre-defined scopes
        binding.btnVisitTokenPage.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new?scopes=repo,workflow"));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "No browser app found to open this URL.", Toast.LENGTH_SHORT).show();
            }
        });

        // Handle the login attempt with the provided token
        binding.btnConnectGithub.setOnClickListener(v -> {
            String token = binding.etPat.getText() != null ? binding.etPat.getText().toString().trim() : "";

            if (token.isEmpty()) {
                binding.etPat.setError("Token is required");
                binding.etPat.requestFocus();
                return;
            }
            binding.etPat.setError(null);

            // Display loading state during the validation request
            setLoadingState(true);

            if (listener != null) {
                listener.onLogin(token, (success, errorMsg) -> {
                    // Update UI state based on the asynchronous validation result
                    if (getView() != null) {
                        getView().post(() -> {
                            setLoadingState(false);

                            if (success) {
                                // Switch to profile card UI on successful login
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

        // Handle account disconnection (logout)
        binding.btnDisconnectGithub.setOnClickListener(v -> {
            GitCredentialStore store = new GitCredentialStore();
            try {
                store.clearCredentials(requireContext());
                Toast.makeText(requireContext(), "Disconnected from GitHub.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Failed to disconnect. Please try again.", Toast.LENGTH_SHORT).show();
            }

            // Revert back to the login form UI
            refreshUIState();
        });
    }

    /**
     * Applies the branding fonts and rounded styling to the sheet's components.
     */
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

        UiUtils.setViewRounded(binding.etPat, UiUtils.dpToPx(ctx, 10), ContextCompat.getColor(ctx, R.color.vcode_bg_elevated));
    }

    /**
     * Manages the button text and enabled state during an active login request.
     */
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

    /**
     * Interface for communicating the login token to the parent component.
     */
    public interface GitHubLoginListener {
        void onLogin(String token, GitHubLoginUpdater updater);
    }

    /**
     * Interface for reporting the result of the GitHub account validation.
     */
    public interface GitHubLoginUpdater {
        void onResult(boolean success, String errorMsg);
    }
}