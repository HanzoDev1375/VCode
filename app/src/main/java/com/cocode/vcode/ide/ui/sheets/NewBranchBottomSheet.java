package com.cocode.vcode.ide.ui.sheets;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.databinding.BottomSheetNewBranchBinding;
import com.cocode.vcode.ide.git.model.BranchItem;
import com.cocode.vcode.ide.ui.git.GitViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * NewBranchBottomSheet provides an interface for creating a new Git branch.
 * It allows users to specify the branch name and select a base branch to branch off from.
 */
public class NewBranchBottomSheet extends BottomSheetDialogFragment {
    private BottomSheetNewBranchBinding binding;
    private GitViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = BottomSheetNewBranchBinding.inflate(i, c, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Bind to Activity-scoped ViewModel to execute the branch creation
        viewModel = new ViewModelProvider(requireActivity()).get(GitViewModel.class);

        setupDropdown();

        binding.btnCreateBranch.setOnClickListener(v -> {
            String name = Objects.requireNonNull(binding.etBranchName.getText()).toString().trim();
            String from = binding.autoCreateFrom.getText().toString();

            // Validate branch name against standard Git naming conventions (basic regex)
            if (!name.matches("^[a-zA-Z0-9._-]+$")) {
                binding.tilBranchName.setError("Invalid name format (alphanumeric, dots, dashes, underscores)");
                return;
            }

            viewModel.createBranch(name, from);
            dismiss();
        });
    }

    /**
     * Populates the "Create From" dropdown with the list of existing local branches.
     */
    private void setupDropdown() {
        viewModel.getLocalBranches().observe(getViewLifecycleOwner(), branches -> {
            if (branches == null) return;

            List<String> names = new ArrayList<>();
            for (BranchItem b : branches) names.add(b.getName());

            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_dropdown_item_1line, names);
            binding.autoCreateFrom.setAdapter(adapter);

            // Default to the first available branch (usually the current HEAD)
            if (!names.isEmpty() && binding.autoCreateFrom.getText().toString().isEmpty()) {
                binding.autoCreateFrom.setText(names.get(0), false);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}