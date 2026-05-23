package com.cocode.vcode.ide.ui.sheets;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.BottomSheetDiffViewerBinding;
import com.cocode.vcode.ide.git.model.GitFileItem;
import com.cocode.vcode.ide.git.repository.GitRepository;
import com.cocode.vcode.ide.ui.commitdetails.CommitDetailsActivity;
import com.cocode.vcode.ide.ui.commitdetails.CommitDetailsViewModel;
import com.cocode.vcode.ide.ui.git.GitViewModel;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * DiffViewerBottomSheet provides a line-by-line visual comparison of file changes.
 * it supports both workspace diffs (staged/unstaged) and historical commit diffs.
 * The UI highlights additions in green and removals in red, mimicking standard Git diff output.
 */
public class DiffViewerBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetDiffViewerBinding binding;
    private GitFileItem fileItem;
    
    /** The specific commit SHA to compare against, or null for workspace diffs. */
    private String commitSha;

    /**
     * Creates a new instance for viewing workspace changes.
     */
    public static DiffViewerBottomSheet newInstance(GitFileItem item) {
        DiffViewerBottomSheet sheet = new DiffViewerBottomSheet();
        sheet.fileItem = item;
        return sheet;
    }

    /**
     * Creates a new instance for viewing changes within a specific commit.
     */
    public static DiffViewerBottomSheet newInstance(String commitSha, GitFileItem item) {
        DiffViewerBottomSheet sheet = new DiffViewerBottomSheet();
        sheet.commitSha = commitSha;
        sheet.fileItem = item;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetDiffViewerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Setup header metadata
        binding.tvDiffFilename.setText(fileItem.getFileName());
        binding.tvDiffFilename.setTypeface(FontManager.getInstance().getUiSemiBold(requireContext()));
        binding.btnCloseDiff.setOnClickListener(v -> dismiss());

        // Asynchronously load the diff data from the repository
        loadDiff();
    }

    /**
     * Resolves the appropriate repository instance and retrieves the diff string.
     */
    private void loadDiff() {
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                GitRepository repository;
                String diff;

                // Dynamically resolve the correct repository instance by evaluating the host activity context
                if (getActivity() instanceof CommitDetailsActivity) {
                    repository = new ViewModelProvider(requireActivity()).get(CommitDetailsViewModel.class).getRepository();
                } else {
                    repository = new ViewModelProvider(requireActivity()).get(GitViewModel.class).getRepository();
                }

                if (commitSha != null && !commitSha.isEmpty()) {
                    // Fetch diff for a historical commit
                    diff = repository.getCommitFileDiff(commitSha, fileItem.getPath());
                } else {
                    // Fetch diff for current workspace modifications
                    diff = repository.getFileDiff(fileItem.getPath(), fileItem.isStaged());
                }

                // Transition back to main thread for UI rendering
                ExecutorProvider.getInstance().runOnMain(() -> renderDiff(diff));
            } catch (Exception e) {
                ExecutorProvider.getInstance().runOnMain(() -> {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Cannot load diff: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                    dismiss();
                });
            }
        });
    }

    /**
     * Parses the raw diff string and dynamically populates the UI with highlighted lines.
     * @param diff The raw JGit unified diff output.
     */
    private void renderDiff(String diff) {
        // Handle the case where no changes were found
        if (diff == null || diff.trim().isEmpty()) {
            View emptyLineView = getLayoutInflater().inflate(R.layout.item_diff_line, binding.layoutDiffLines, false);
            TextView tvContent = emptyLineView.findViewById(R.id.root_view).findViewById(R.id.tv_line_content);
            tvContent.setText("No changes detected in this tracking block index.");
            tvContent.setTextColor(ContextCompat.getColor(requireContext(), R.color.vcode_text_secondary));
            binding.layoutDiffLines.addView(emptyLineView);
            return;
        }

        // Split raw output into lines and process each for color coding
        String[] lines = diff.split("\n");
        for (String line : lines) {
            View lineView = getLayoutInflater().inflate(R.layout.item_diff_line, binding.layoutDiffLines, false);
            TextView tvContent = lineView.findViewById(R.id.root_view).findViewById(R.id.tv_line_content);
            tvContent.setText(line);

            if (line.startsWith("+")) {
                // Line addition: highlight green
                lineView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_added_bg));
                tvContent.setTextColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_added_text));
            } else if (line.startsWith("-")) {
                // Line removal: highlight red
                lineView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_removed_bg));
                tvContent.setTextColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_removed_text));
            } else if (line.startsWith("@@")) {
                // Hunk header: highlight grey/blue background
                lineView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.vcode_diff_hunk_bg));
            }

            binding.layoutDiffLines.addView(lineView);
        }
    }
}