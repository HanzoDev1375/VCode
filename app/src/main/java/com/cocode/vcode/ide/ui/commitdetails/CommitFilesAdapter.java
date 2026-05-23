package com.cocode.vcode.ide.ui.commitdetails;

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
        return new ViewHolder(binding);
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
    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemGitFileBinding binding;

        ViewHolder(ItemGitFileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
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
            badge.setCornerRadius(4 * context.getResources().getDisplayMetrics().density);
            badge.setColor(ContextCompat.getColor(context, statusColor));
            binding.tvStatusBadge.setBackground(badge);

            // Resolve and style the file icon based on extension and language
            String ext = FileUtils.getExtension(item.getFileName().toLowerCase());
            AssetType assetType = AssetType.fromExtension(ext);

            if (assetType != null) {
                binding.ivFileIcon.setImageResource(assetType.getIconResId());
                binding.ivFileIcon.setColorFilter(ContextCompat.getColor(context, assetType.getColorResId()), PorterDuff.Mode.SRC_IN);
            } else {
                Language lang = Language.fromExtension(ext);
                int iconResId = R.drawable.ic_file_lines;
                if (lang == Language.HTML) iconResId = R.drawable.ic_html_icon;
                else if (lang == Language.CSS) iconResId = R.drawable.ic_css_icon;
                else if (lang == Language.JAVASCRIPT) iconResId = R.drawable.ic_js_icon;
                else if (lang == Language.JSON) iconResId = R.drawable.ic_json_icon;
                else if (lang == Language.MARKDOWN) iconResId = R.drawable.ic_md_icon;

                binding.ivFileIcon.setImageResource(iconResId);
                // Apply language-specific coloring unless it's a generic text file
                if (lang != Language.TEXT || ext.equals("txt") || ext.isEmpty()) {
                    binding.ivFileIcon.setColorFilter(ContextCompat.getColor(context, lang.getColorResId()), PorterDuff.Mode.SRC_IN);
                } else {
                    binding.ivFileIcon.clearColorFilter();
                }
            }

            binding.getRoot().setOnClickListener(v -> listener.onFileClick(item));
        }
    }
}