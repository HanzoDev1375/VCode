package com.cocode.vcode.ide.ui.sheets;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.cocode.vcode.ide.databinding.BottomSheetCreateGithubRepoBinding;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Objects;

public class CreateGitHubRepoBottomSheet extends BottomSheetDialogFragment {
    private BottomSheetCreateGithubRepoBinding binding;
    private CreateRepoListener listener;
    private String projectName;

    public interface CreateRepoListener {
        void onPublish(String name, String desc, boolean isPrivate);
    }

    public static void show(FragmentManager manager, String projectName, CreateRepoListener listener) {
        CreateGitHubRepoBottomSheet sheet = new CreateGitHubRepoBottomSheet();
        sheet.projectName = projectName;
        sheet.setListener(listener);
        sheet.show(manager, "CreateGitHubRepoBottomSheet");
    }

    public void setListener(CreateRepoListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetCreateGithubRepoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = requireContext();
        FontManager fm = FontManager.getInstance();
        binding.tvTitle.setTypeface(fm.getUiSemiBold(context));
        binding.tvPrivateLabel.setTypeface(fm.getUiMedium(context));
        binding.btnPublish.setTypeface(fm.getUiSemiBold(context));
        
        if (binding.etRepoName != null) {
            binding.etRepoName.setTypeface(fm.getUiMedium(context));
        }
        if (binding.etRepoDescription != null) {
            binding.etRepoDescription.setTypeface(fm.getUiMedium(context));
        }

        if (projectName != null && !projectName.isEmpty() && binding.etRepoName != null) {
            binding.etRepoName.setText(projectName);
        }

        binding.btnPublish.setOnClickListener(v -> {
            String name = binding.etRepoName != null && binding.etRepoName.getText() != null ? binding.etRepoName.getText().toString().trim() : "";
            String desc = binding.etRepoDescription != null && binding.etRepoDescription.getText() != null ? binding.etRepoDescription.getText().toString().trim() : "";
            boolean isPrivate = binding.switchPrivate.isChecked();

            if (name.isEmpty()) {
                if (binding.etRepoName != null) {
                    binding.etRepoName.setError("Repository name is required");
                    binding.etRepoName.requestFocus();
                }
                return;
            }

            if (listener != null) {
                listener.onPublish(name, desc, isPrivate);
            }
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
