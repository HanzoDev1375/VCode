package com.cocode.vcode.ide.git.adapters;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.language.Language;
import com.cocode.vcode.ide.data.model.AssetType;
import com.cocode.vcode.ide.databinding.ItemGitFileBinding;
import com.cocode.vcode.ide.git.model.GitFileItem;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;

/**
 * Work-tree modifications change tracking file listing controller.
 * Groups status codes (Added, Modified, Untracked, Deleted) to apply contextual color backgrounds,
 * formats source path trees relative to active root setups, and handles multi-language icon mappings.
 */
public class GitFilesAdapter extends ListAdapter<GitFileItem, GitFilesAdapter.ViewHolder> {

    private final GitFileListener listener;
    private String projectName = "";

    public GitFilesAdapter(GitFileListener listener) {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull GitFileItem oldItem, @NonNull GitFileItem newItem) {
                return oldItem.getPath().equals(newItem.getPath());
            }

            @Override
            public boolean areContentsTheSame(@NonNull GitFileItem oldItem, @NonNull GitFileItem newItem) {
                return oldItem.getStatus().equals(newItem.getStatus()) &&
                        oldItem.isStaged() == newItem.isStaged();
            }
        });
        this.listener = listener;
    }

    /**
     * Sets the active project name configuration to format root path layouts beautifully.
     */
    public void setProjectName(String projectName) {
        this.projectName = projectName != null ? projectName.trim() : "";
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGitFileBinding binding = ItemGitFileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public interface GitFileListener {
        void onFileClick(GitFileItem item);

        void onActionClick(GitFileItem item);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemGitFileBinding binding;

        ViewHolder(ItemGitFileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(GitFileItem item) {
            Context context = itemView.getContext();

            binding.tvFileName.setTypeface(FontManager.getInstance().getUiSemiBold(context));
            binding.tvFilePath.setTypeface(FontManager.getInstance().getUiMedium(context));
            binding.tvStatusBadge.setTypeface(FontManager.getInstance().getUiSemiBold(context));

            binding.tvFileName.setText(item.getFileName());
            binding.tvStatusBadge.setText(item.getStatus());

            // Core path styling layout adjustment: if the path has no directory separators,
            // append the project name token directly to establish standard workspace visibility structures.
            String pristinePath = item.getPath();
            if (!projectName.isEmpty() && !pristinePath.contains("/")) {
                binding.tvFilePath.setText(projectName.concat("/").concat(pristinePath));
            } else {
                binding.tvFilePath.setText(pristinePath);
            }

            // Assign localized branding identifiers corresponding to the specific Git action code character
            int statusColor;
            switch (item.getStatus()) {
                case "A": // Staged Addition
                    statusColor = R.color.vcode_git_staged_color;
                    break;
                case "D": // Deleted item context
                    statusColor = R.color.vcode_git_deleted_color;
                    break;
                case "?": // Untracked workspace component
                    statusColor = R.color.vcode_git_untracked_color;
                    break;
                default:  // Modified text rule block
                    statusColor = R.color.vcode_git_modified_color;
                    break;
            }

            GradientDrawable badge = new GradientDrawable();
            badge.setCornerRadius(4 * context.getResources().getDisplayMetrics().density);
            badge.setColor(ContextCompat.getColor(context, statusColor));
            binding.tvStatusBadge.setBackground(badge);

            // Handle staging action icon shapes and color theme updates
            binding.btnAction.setImageResource(item.isStaged() ? R.drawable.ic_minus : R.drawable.ic_plus);
            binding.btnAction.setColorFilter(ContextCompat.getColor(context,
                    item.isStaged() ? R.color.vcode_accent_error : R.color.vcode_accent_primary));

            String ext = FileUtils.getExtension(item.getFileName().toLowerCase());
            AssetType assetType = AssetType.fromExtension(ext);

            // Select graphic layout vectors depending on file type metrics
            if (assetType != null) {
                // Media / Asset category path definitions
                binding.ivFileIcon.setImageResource(assetType.getIconResId());
                binding.ivFileIcon.setColorFilter(
                        ContextCompat.getColor(itemView.getContext(), assetType.getColorResId()),
                        PorterDuff.Mode.SRC_IN
                );
            } else {
                // Code / Structural language category paths
                Language lang = Language.fromExtension(ext);
                binding.ivFileIcon.setImageResource(lang.getIconResId());

                int fileColor = ContextCompat.getColor(itemView.getContext(), lang.getColorResId());
                binding.ivFileIcon.setColorFilter(fileColor, PorterDuff.Mode.SRC_IN);
            }

            binding.getRoot().setOnClickListener(v -> listener.onFileClick(item));
            binding.btnAction.setOnClickListener(v -> listener.onActionClick(item));
        }
    }
}