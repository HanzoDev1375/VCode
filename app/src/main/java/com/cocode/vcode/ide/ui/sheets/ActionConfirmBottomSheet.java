package com.cocode.vcode.ide.ui.sheets;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetGitActionConfirmedBinding;
import com.cocode.vcode.ide.git.model.GitAction;
import com.cocode.vcode.ide.ui.git.GitViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * ActionConfirmBottomSheet provides a unified confirmation interface for advanced Git operations.
 * It dynamically adapts its UI (title, description, icons, and colors) based on the 
 * requested {@link GitAction}, such as resets, cherry-picks, and reverts.
 */
public class ActionConfirmBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_ACTION_TYPE = "arg_action_type";
    private BottomSheetGitActionConfirmedBinding binding;
    private GitViewModel viewModel;
    private GitAction action;

    /**
     * Creates a new instance of the sheet for a specific Git action.
     * @param action The Git operation to be confirmed.
     * @return A configured fragment instance.
     */
    public static ActionConfirmBottomSheet newInstance(GitAction action) {
        ActionConfirmBottomSheet fragment = new ActionConfirmBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_ACTION_TYPE, action);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Recover the action type from arguments
        if (getArguments() != null) {
            action = (GitAction) getArguments().getSerializable(ARG_ACTION_TYPE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetGitActionConfirmedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Link to the Activity-scoped ViewModel to execute the Git operation
        viewModel = new ViewModelProvider(requireActivity()).get(GitViewModel.class);

        configureUIByAction();
        setupListeners();
    }

    /**
     * Dynamically configures the visual state and textual content of the bottom sheet
     * to match the specific characteristics of the selected Git action.
     */
    private void configureUIByAction() {
        Context context = requireContext();
        binding.tilCommitRef.setHint("Commit SHA or Ref");

        // UI state management logic based on operation risk and requirements
        switch (action) {
            case SOFT_RESET:
                binding.tvConfirmTitle.setText("Confirm Soft Reset");
                binding.tvConfirmDescription.setText("Moves HEAD back to target commit. Uncommitted adjustments remain intact and fully staged.");
                binding.tilCommitRef.setHelperText("Leave empty to target HEAD~1 context");
                binding.layoutWarningBox.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_primary_container)));
                binding.ivWarningIcon.setImageResource(R.drawable.ic_rotate_left);
                binding.ivWarningIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_accent_primary)));
                binding.btnActionConfirm.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_accent_primary)));
                break;

            case HARD_RESET:
                binding.tvConfirmTitle.setText("Confirm Hard Reset");
                binding.tvConfirmDescription.setText("Warning: This action will permanently delete all uncommitted changes and cannot be undone.");
                binding.tilCommitRef.setHelperText("Leave empty to reset modifications to active HEAD state");
                binding.layoutWarningBox.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_error_container)));
                binding.ivWarningIcon.setImageResource(R.drawable.ic_minus); // Maps to alert triangle
                binding.ivWarningIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_accent_error)));
                binding.tvConfirmDescription.setTextColor(ContextCompat.getColor(context, R.color.vcode_accent_error));
                binding.btnActionConfirm.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_accent_error)));
                break;

            case MIXED_RESET:
                binding.tvConfirmTitle.setText("Confirm Mixed Reset");
                binding.tvConfirmDescription.setText("Resets index state back down tree. Working code adjustments are kept but marked completely unstaged.");
                binding.tilCommitRef.setHelperText("Leave empty to target standard HEAD default reset");
                binding.layoutWarningBox.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_bg_elevated)));
                binding.ivWarningIcon.setImageResource(R.drawable.ic_arrow_right);
                binding.ivWarningIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_text_secondary)));
                binding.btnActionConfirm.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_accent_primary)));
                break;

            case CHERRY_PICK:
                binding.tvConfirmTitle.setText("Cherry Pick Commit");
                binding.tvConfirmDescription.setText("Applies isolated configuration variations from targeted commit reference context to current active HEAD.");
                binding.tilCommitRef.setHint("Target Commit SHA-1");
                binding.tilCommitRef.setHelperText("Required: Input complete reference string target");
                binding.layoutWarningBox.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_bg_elevated)));
                binding.ivWarningIcon.setImageResource(R.drawable.ic_check);
                binding.ivWarningIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_accent_primary)));
                binding.btnActionConfirm.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_accent_primary)));
                break;

            case REVERT_COMMIT:
                binding.tvConfirmTitle.setText("Revert Commit");
                binding.tvConfirmDescription.setText("Generates a completely unique, reversed inversion offset node targeting modifications within history parameters.");
                binding.tilCommitRef.setHint("Target Reversion SHA-1");
                binding.tilCommitRef.setHelperText("Required: Specify target commit block to invert");
                binding.layoutWarningBox.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_secondary_container)));
                binding.ivWarningIcon.setImageResource(R.drawable.ic_star);
                binding.ivWarningIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_accent_warning)));
                binding.btnActionConfirm.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_accent_warning)));
                break;

            case STASH:
                binding.tvConfirmTitle.setText("Stash Changes");
                binding.tvConfirmDescription.setText("Clears uncommitted adjustments from index registry tracker and pushes items to stack frame.");
                binding.tilCommitRef.setVisibility(View.GONE);
                binding.layoutWarningBox.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_success_container)));
                binding.ivWarningIcon.setImageResource(R.drawable.ic_plus);
                binding.ivWarningIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_accent_success)));
                binding.btnActionConfirm.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_accent_success)));
                break;

            case STASH_POP:
                binding.tvConfirmTitle.setText("Stash Pop");
                binding.tvConfirmDescription.setText("Retrieves variations captured within stack tracking system and merges them back onto workspace branch.");
                binding.tilCommitRef.setVisibility(View.GONE);
                binding.layoutWarningBox.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_success_container)));
                binding.ivWarningIcon.setImageResource(R.drawable.ic_plus);
                binding.ivWarningIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_accent_success)));
                binding.btnActionConfirm.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vcode_accent_success)));
                break;
        }
    }

    /**
     * Sets up interaction listeners and reactive observers for operation state tracking.
     */
    private void setupListeners() {
        binding.btnActionCancel.setOnClickListener(v -> dismiss());
        binding.btnActionConfirm.setOnClickListener(v -> executeGitOperation());

        // Update UI based on ViewModel's loading state
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (loading != null) {
                setLoadingState(loading);
            }
        });

        // Surface errors if the operation fails
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && isAdded()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Validates input and triggers the corresponding operation in the ViewModel.
     */
    private void executeGitOperation() {
        String refInput = binding.etCommitRef.getText() != null ? binding.etCommitRef.getText().toString().trim() : "";

        // Validate mandatory input for specific actions
        if ((action == GitAction.CHERRY_PICK || action == GitAction.REVERT_COMMIT) && refInput.isEmpty()) {
            binding.tilCommitRef.setError("Operational SHA target reference missing.");
            return;
        }
        binding.tilCommitRef.setError(null);

        // Dispatch action to the ViewModel
        switch (action) {
            case SOFT_RESET:
                viewModel.softReset(refInput.isEmpty() ? "HEAD~1" : refInput);
                break;
            case HARD_RESET:
                viewModel.hardReset(refInput.isEmpty() ? "HEAD" : refInput);
                break;
            case MIXED_RESET:
                viewModel.mixedReset(refInput.isEmpty() ? "HEAD" : refInput);
                break;
            case CHERRY_PICK:
                viewModel.cherryPick(refInput);
                break;
            case REVERT_COMMIT:
                viewModel.revertCommit(refInput);
                break;
            case STASH:
                // routed via internal refresh sequence logic
                viewModel.refreshAll();
                dismiss();
                return;
            case STASH_POP:
                viewModel.refreshAll();
                dismiss();
                return;
        }
        dismiss(); // Operations are typically backgrounded; sheet closes immediately
    }

    /**
     * Toggles the loading state UI (progress bar, button text/enabled state).
     */
    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            binding.btnActionConfirm.setText("");
            binding.btnActionConfirm.setEnabled(false);
            binding.btnActionCancel.setEnabled(false);
            binding.progressLoading.setVisibility(View.VISIBLE);
        } else {
            binding.btnActionConfirm.setText("Confirm");
            binding.btnActionConfirm.setEnabled(true);
            binding.btnActionCancel.setEnabled(true);
            binding.progressLoading.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}