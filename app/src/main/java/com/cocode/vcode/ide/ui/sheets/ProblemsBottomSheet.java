package com.cocode.vcode.ide.ui.sheets;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.model.Problem;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.ui.editor.EditorViewModelFactory;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

import com.cocode.vcode.ide.databinding.VcodeBottomSheetProblemsBinding;
import com.cocode.vcode.ide.utils.FontManager;

public class ProblemsBottomSheet extends BottomSheetDialogFragment {

    private ProblemsAdapter adapter;
    private ProblemListener listener;
    private EditorViewModel viewModel;
    private VcodeBottomSheetProblemsBinding binding;

    public interface ProblemListener {
        void onProblemSelected(int lineNumber);
    }

    public void setListener(ProblemListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = VcodeBottomSheetProblemsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.tvTitle.setTypeface(FontManager.getInstance().getUiSemiBold(requireContext()));

        binding.rvProblems.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ProblemsAdapter();
        binding.rvProblems.setAdapter(adapter);

        EditorViewModelFactory factory = new EditorViewModelFactory(requireContext());
        viewModel = new ViewModelProvider(requireActivity(), factory).get(EditorViewModel.class);

        viewModel.getProblems().observe(getViewLifecycleOwner(), problems -> {
            if (problems != null) {
                adapter.setProblems(problems, viewModel.getProjectRoot() != null ? viewModel.getProjectRoot().getAbsolutePath() : "");
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private class ProblemsAdapter extends RecyclerView.Adapter<ProblemsAdapter.ViewHolder> {
        private List<Problem> items = new ArrayList<>();
        private String projectRoot = "";

        @SuppressLint("NotifyDataSetChanged")
        void setProblems(List<Problem> newItems, String root) {
            this.items = newItems;
            this.projectRoot = root;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            com.cocode.vcode.ide.databinding.VcodeItemProblemBinding itemBinding = 
                com.cocode.vcode.ide.databinding.VcodeItemProblemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Problem item = items.get(position);

            holder.binding.tvMessage.setText(item.getMessage());
            holder.binding.tvMessage.setTypeface(FontManager.getInstance().getUiMedium(holder.itemView.getContext()));

            String relPath = item.getFile().getAbsolutePath().replace(projectRoot + java.io.File.separator, "");
            holder.binding.tvFilePath.setText(relPath);
            holder.binding.tvFilePath.setTypeface(FontManager.getInstance().getUiMedium(holder.itemView.getContext()));

            holder.binding.tvLineNumber.setText(String.valueOf(item.getLine()));
            holder.binding.tvLineNumber.setTypeface(FontManager.getInstance().getUiMedium(holder.itemView.getContext()));

            // Severity icon and color
            android.content.Context ctx = holder.itemView.getContext();
            int iconRes;
            int colorRes;
            switch (item.getSeverity()) {
                case ERROR:
                    iconRes = com.cocode.vcode.ide.R.drawable.ic_error;
                    colorRes = com.cocode.vcode.ide.R.color.vcode_accent_error;
                    break;
                case WARNING:
                    iconRes = R.drawable.ic_triangle_exclamation;
                    colorRes = com.cocode.vcode.ide.R.color.vcode_accent_warning;
                    break;
                default: // INFO
                    iconRes = com.cocode.vcode.ide.R.drawable.ic_info;
                    colorRes = com.cocode.vcode.ide.R.color.vcode_accent_primary;
                    break;
            }
            holder.binding.ivSeverity.setImageResource(iconRes);
            holder.binding.ivSeverity.setColorFilter(
                    androidx.core.content.ContextCompat.getColor(ctx, colorRes));

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProblemSelected(item.getLine());
                }
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            com.cocode.vcode.ide.databinding.VcodeItemProblemBinding binding;

            ViewHolder(@NonNull com.cocode.vcode.ide.databinding.VcodeItemProblemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
