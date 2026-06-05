package com.cocode.vcode.ide.ui.commitdetails;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.ItemGitFileBinding;
import com.cocode.vcode.ide.git.model.GitFileItem;
import com.cocode.vcode.ide.utils.FileIconHelper;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * CommitFilesAdapter manages the display of files modified within a specific commit.
 * It visualizes file names, paths, and Git status badges (Added, Modified, Deleted).
 * It also dynamically resolves file icons and colors based on file extensions.
 */
public class CommitFilesAdapter extends ListAdapter<GitFileItem, CommitFilesAdapter.ViewHolder> {

    private final OnFileSelectedListener listener;

    /**
     * Initializes the adapter with a DiffUtil callback for efficient list updates.
     */
    public CommitFilesAdapter(OnFileSelectedListener listener) {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull GitFileItem oldItem, @NonNull GitFileItem newItem) {
                // Identity check based on the file path
                return oldItem.getPath().equals(newItem.getPath());
            }

            @Override
            public boolean areContentsTheSame(@NonNull GitFileItem oldItem, @NonNull GitFileItem newItem) {
                // Content check based on the Git status character
                return oldItem.getStatus().equals(newItem.getStatus());
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemGitFileBinding binding = ItemGitFileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding, this);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    /**
     * Callback interface for handling clicks on commit file items.
     */
    public interface OnFileSelectedListener {
        void onFileClick(GitFileItem item);
    }

    /**
     * ViewHolder for representing a single modified file in a commit.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemGitFileBinding binding;
        private final CommitFilesAdapter adapter;

        ViewHolder(ItemGitFileBinding binding, CommitFilesAdapter adapter) {
            super(binding.getRoot());
            this.binding = binding;
            this.adapter = adapter;
        }

        /**
         * Binds a GitFileItem to the view components.
         */
        void bind(GitFileItem item) {
            Context context = itemView.getContext();

            // Apply consistent typography
            binding.tvFileName.setTypeface(FontManager.getInstance().getUiSemiBold(context));
            binding.tvFilePath.setTypeface(FontManager.getInstance().getUiMedium(context));
            binding.tvStatusBadge.setTypeface(FontManager.getInstance().getUiSemiBold(context));

            binding.tvFileName.setText(item.getFileName());
            binding.tvFilePath.setText(item.getPath());
            binding.tvStatusBadge.setText(item.getStatus());

            // Hide the workspace action button (Stage/Unstage) since this is a historical view
            binding.btnAction.setVisibility(ViewGroup.GONE);

            // Determine badge color based on Git status
            int statusColor;
            switch (item.getStatus()) {
                case "A": // Added
                    statusColor = R.color.vcode_git_staged_color;
                    break;
                case "D": // Deleted
                    statusColor = R.color.vcode_git_deleted_color;
                    break;
                default: // Modified
                    statusColor = R.color.vcode_git_modified_color;
                    break;
            }

            // Apply a rounded background to the status badge
            GradientDrawable badge = new GradientDrawable();
            badge.setCornerRadius(UiUtils.dpToPx(context, 4));
            badge.setColor(ContextCompat.getColor(context, statusColor));
            binding.tvStatusBadge.setBackground(badge);

            // Resolve and style the file icon based on extension and language
            FileIconHelper.setFileIconAndColor(binding.ivFileIcon, item.getFileName());

            binding.getRoot().setOnClickListener(v -> adapter.listener.onFileClick(item));
        }
    }
}