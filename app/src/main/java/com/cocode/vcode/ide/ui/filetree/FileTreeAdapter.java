package com.cocode.vcode.ide.ui.filetree;

import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.language.Language;
import com.cocode.vcode.ide.data.model.AssetType;
import com.cocode.vcode.ide.data.model.FileNode;
import com.cocode.vcode.ide.databinding.ItemFileTreeNodeBinding;
import com.cocode.vcode.ide.git.model.FileStatus;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FileTreeAdapter manages the visual representation of the project's file hierarchy.
 * It transforms a nested {@link FileNode} structure into a flattened list for the RecyclerView,
 * supporting folder expansion/collapse, specialized file icons, and Git status indicators.
 */
public class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.FileViewHolder> {

    private final List<FileNode> flatNodes = new ArrayList<>();
    private final FileTreeListener listener;
    private final int indentWidthPx;
    private Map<String, FileStatus.Type> gitStatusMap;
    private String rootPath;
    private String projectName;

    /** Tracks the node that is currently displaying its action menu (rename/delete). */
    private FileNode activeActionNode = null;

    public FileTreeAdapter(FileTreeListener listener, int indentDp, float screenDensity) {
        this.listener = listener;
        this.indentWidthPx = (int) (indentDp * screenDensity);
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    /**
     * Updates the file tree data. Preserves the expansion state of folders across updates.
     * @param rootNodes The new set of root-level nodes.
     */
    public void setTree(List<FileNode> rootNodes) {
        // Capture currently expanded paths before clearing the list
        Set<String> expandedPaths = new HashSet<>();
        for (FileNode node : flatNodes) {
            if (node.isDirectory() && node.isExpanded()) {
                expandedPaths.add(node.getFile().getAbsolutePath());
            }
        }

        // Apply captured state to the new node tree
        restoreExpandedState(rootNodes, expandedPaths);

        flatNodes.clear();
        activeActionNode = null;
        // Transform the nested tree into a flat list for the adapter
        flatten(rootNodes, flatNodes);
        notifyDataSetChanged();
    }

    /**
     * Resets the active action state (hides rename/delete icons).
     */
    public void clearActiveAction() {
        if (activeActionNode != null) {
            int pos = flatNodes.indexOf(activeActionNode);
            activeActionNode = null;
            if (pos != -1) {
                notifyItemChanged(pos);
            }
        }
    }

    /**
     * Recursively reapplies the "expanded" flag to folders based on their absolute paths.
     */
    private void restoreExpandedState(List<FileNode> nodes, Set<String> expandedPaths) {
        if (nodes == null) return;
        for (FileNode node : nodes) {
            if (node.isDirectory()) {
                if (expandedPaths.contains(node.getFile().getAbsolutePath())) {
                    node.setExpanded(true);
                }
                restoreExpandedState(node.getChildren(), expandedPaths);
            }
        }
    }

    /**
     * Updates the Git status mapping and refreshes the UI badges.
     */
    public void setGitStatuses(Map<String, FileStatus.Type> statuses) {
        this.gitStatusMap = statuses;
        notifyDataSetChanged();
    }

    /**
     * Converts a nested tree structure into a flat list, including only children
     * of folders that are currently expanded.
     */
    private void flatten(List<FileNode> nodes, List<FileNode> out) {
        if (nodes == null) return;
        for (FileNode node : nodes) {
            out.add(node);
            if (node.isDirectory() && node.isExpanded()) {
                flatten(node.getChildren(), out);
            }
        }
    }

    /**
     * Determines if a file is supported by the internal code editor or asset viewers.
     */
    private boolean isSupportedByEditor(File file) {
        String ext = FileUtils.getExtension(file.getName()).toLowerCase();
        if (ext.isEmpty()) return true; // Generic text files without extensions are allowed

        // Check for supported media and font types
        AssetType assetType = AssetType.fromExtension(ext);
        if (assetType == AssetType.IMAGE || assetType == AssetType.GIF ||
                assetType == AssetType.ICO || assetType == AssetType.BMP ||
                assetType == AssetType.FONT) {
            return true;
        }

        // Support all generic text and code files dynamically.
        // If an extension isn't explicitly classified as a restricted binary asset (like Audio/Video/PDF),
        // we safely assume it's a readable text format and allow the editor to parse it.
        return assetType == null || assetType.isTextBased();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFileTreeNodeBinding binding = ItemFileTreeNodeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new FileViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        holder.bind(flatNodes.get(position));
    }

    @Override
    public int getItemCount() {
        return flatNodes.size();
    }

    /**
     * Listener interface for file tree interaction events.
     */
    public interface FileTreeListener {
        void onFileClick(File file);

        void onAddFileClick(File parentDir);

        void onAddFolderClick(File parentDir);

        void onRenameNodeClick(File file);

        void onDeleteNodeClick(File file);
    }

    /**
     * ViewHolder for a single node in the file tree.
     * Manages click, long-press, and expansion animations.
     */
    public class FileViewHolder extends RecyclerView.ViewHolder {

        private final ItemFileTreeNodeBinding binding;

        public FileViewHolder(@NonNull ItemFileTreeNodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            setupClickListeners();
        }

        private void setupClickListeners() {
            // "New File" or "Rename" action button
            binding.btnAddFile.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    FileNode node = flatNodes.get(pos);
                    if (activeActionNode == node) {
                        listener.onRenameNodeClick(node.getFile());
                        clearActiveAction();
                    } else if (node.isDirectory()) {
                        listener.onAddFileClick(node.getFile());
                    }
                }
            });

            // "New Folder" or "Delete" action button
            binding.btnAddFolder.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    FileNode node = flatNodes.get(pos);
                    if (activeActionNode == node) {
                        listener.onDeleteNodeClick(node.getFile());
                        clearActiveAction();
                    } else if (node.isDirectory()) {
                        listener.onAddFolderClick(node.getFile());
                    }
                }
            });

            // Standard click to open file or toggle folder
            binding.getRoot().setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                FileNode node = flatNodes.get(pos);

                // Dismiss any active context actions on tap
                if (activeActionNode != null) {
                    clearActiveAction();
                    return;
                }

                if (node.isDirectory()) {
                    handleDirectoryClick(node, pos);
                } else {
                    handleFileClick(node.getFile());
                }
            });

            // Long click to reveal rename and delete actions
            binding.getRoot().setOnLongClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    FileNode node = flatNodes.get(pos);

                    // Prevent actions on the project root folder
                    if (rootPath != null && node.getFile().getAbsolutePath().equals(rootPath)) {
                        return true;
                    }

                    if (activeActionNode == node) {
                        clearActiveAction();
                    } else {
                        clearActiveAction();
                        activeActionNode = node;

                        // Swap standard "Add" icons with "Edit" and "Delete" icons
                        binding.btnAddFile.setVisibility(View.VISIBLE);
                        binding.btnAddFile.setImageResource(R.drawable.ic_pen);
                        binding.btnAddFile.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.vcode_accent_primary), PorterDuff.Mode.SRC_IN);

                        binding.btnAddFolder.setVisibility(View.VISIBLE);
                        binding.btnAddFolder.setImageResource(R.drawable.ic_trash);
                        binding.btnAddFolder.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.vcode_accent_error), PorterDuff.Mode.SRC_IN);

                        // Animate the action buttons into view
                        binding.btnAddFile.setAlpha(0f);
                        binding.btnAddFile.setTranslationX(20f);
                        binding.btnAddFile.animate().alpha(1f).translationX(0f).setDuration(200).start();

                        binding.btnAddFolder.setAlpha(0f);
                        binding.btnAddFolder.setTranslationX(20f);
                        binding.btnAddFolder.animate().alpha(1f).translationX(0f).setStartDelay(50).setDuration(200).start();
                    }
                    return true;
                }
                return false;
            });
        }

        /**
         * Expands or collapses a directory and updates the flat list dynamically.
         */
        private void handleDirectoryClick(FileNode node, int pos) {
            node.setExpanded(!node.isExpanded());
            if (node.isExpanded()) {
                // Flatten child nodes and insert them below the parent
                List<FileNode> toAdd = new ArrayList<>();
                flatten(node.getChildren(), toAdd);
                flatNodes.addAll(pos + 1, toAdd);
                notifyItemRangeInserted(pos + 1, toAdd.size());
            } else {
                // Remove all visible descendants of the collapsed folder
                int removeCount = 0;
                int nextPos = pos + 1;
                while (nextPos < flatNodes.size() && flatNodes.get(nextPos).getDepth() > node.getDepth()) {
                    removeCount++;
                    nextPos++;
                }
                if (removeCount > 0) {
                    flatNodes.subList(pos + 1, pos + 1 + removeCount).clear();
                    notifyItemRangeRemoved(pos + 1, removeCount);
                }
            }
            notifyItemChanged(pos); // Update chevron and icon
        }

        /**
         * Handles file selection; opens internal editor or delegates to system.
         */
        private void handleFileClick(File clickedFile) {
            if (isSupportedByEditor(clickedFile)) {
                if (listener != null) listener.onFileClick(clickedFile);
            } else {
                // Hand over unsupported formats (e.g., PDF) to external system apps
                FileUtils.openFileExternally(binding.getRoot().getContext(), clickedFile);
            }
        }

        /**
         * Binds the file node data to the view components.
         */
        public void bind(FileNode node) {
            // Apply indentation based on tree depth
            ViewGroup.LayoutParams params = binding.indentSpacer.getLayoutParams();
            params.width = node.getDepth() * indentWidthPx;
            binding.indentSpacer.setLayoutParams(params);

            // Special case for root node naming
            if (node.getFile().getAbsolutePath().equals(rootPath) && projectName != null) {
                binding.tvName.setText(projectName);
            } else {
                binding.tvName.setText(node.getName());
            }

            binding.ivIcon.setImageTintList(null);

            if (node.isDirectory()) {
                bindDirectory(node);
            } else {
                bindFile(node);
            }
        }

        /**
         * Specialized binding for directory nodes.
         */
        private void bindDirectory(FileNode node) {
            binding.ivChevron.setVisibility(View.VISIBLE);
            binding.ivChevron.setRotation(node.isExpanded() ? 90f : 0f);

            binding.ivIcon.setVisibility(View.VISIBLE);
            binding.ivIcon.setImageResource(node.isExpanded() ? R.drawable.ic_folder_open : R.drawable.ic_folder);

            int folderColor = ContextCompat.getColor(itemView.getContext(), R.color.vcode_accent_primary);
            binding.ivIcon.setColorFilter(folderColor, PorterDuff.Mode.SRC_IN);

            binding.tvLangBadge.setVisibility(View.GONE);
            binding.gitStatusBadge.setVisibility(View.GONE);
            binding.tvName.setTypeface(FontManager.getInstance().getUiSemiBold(itemView.getContext()));

            // Render buttons based on whether the node is in "action mode"
            if (node == activeActionNode) {
                binding.btnAddFile.setVisibility(View.VISIBLE);
                binding.btnAddFile.setImageResource(R.drawable.ic_pen);
                binding.btnAddFile.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.vcode_accent_primary), PorterDuff.Mode.SRC_IN);
                binding.btnAddFile.setAlpha(1f);
                binding.btnAddFile.setTranslationX(0f);

                binding.btnAddFolder.setVisibility(View.VISIBLE);
                binding.btnAddFolder.setImageResource(R.drawable.ic_trash);
                binding.btnAddFolder.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.vcode_accent_error), PorterDuff.Mode.SRC_IN);
                binding.btnAddFolder.setAlpha(1f);
                binding.btnAddFolder.setTranslationX(0f);
            } else {
                binding.btnAddFile.setVisibility(View.VISIBLE);
                binding.btnAddFile.setImageResource(R.drawable.ic_file_plus);
                binding.btnAddFile.clearColorFilter();
                binding.btnAddFile.setAlpha(1f);
                binding.btnAddFile.setTranslationX(0f);

                binding.btnAddFolder.setVisibility(View.VISIBLE);
                binding.btnAddFolder.setImageResource(R.drawable.ic_folder_plus);
                binding.btnAddFolder.clearColorFilter();
                binding.btnAddFolder.setAlpha(1f);
                binding.btnAddFolder.setTranslationX(0f);
            }
        }

        /**
         * Specialized binding for file nodes.
         */
        private void bindFile(FileNode node) {
            binding.ivChevron.setVisibility(View.INVISIBLE);
            binding.tvLangBadge.setVisibility(View.GONE);
            binding.ivIcon.setVisibility(View.VISIBLE);
            binding.tvName.setTypeface(FontManager.getInstance().getUiMedium(itemView.getContext()));

            if (node == activeActionNode) {
                binding.btnAddFile.setVisibility(View.VISIBLE);
                binding.btnAddFile.setImageResource(R.drawable.ic_pen);
                binding.btnAddFile.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.vcode_accent_primary), PorterDuff.Mode.SRC_IN);
                binding.btnAddFile.setAlpha(1f);
                binding.btnAddFile.setTranslationX(0f);

                binding.btnAddFolder.setVisibility(View.VISIBLE);
                binding.btnAddFolder.setImageResource(R.drawable.ic_trash);
                binding.btnAddFolder.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.vcode_accent_error), PorterDuff.Mode.SRC_IN);
                binding.btnAddFolder.setAlpha(1f);
                binding.btnAddFolder.setTranslationX(0f);
            } else {
                binding.btnAddFile.setVisibility(View.GONE);
                binding.btnAddFolder.setVisibility(View.GONE);
            }

            String ext = FileUtils.getExtension(node.getFile().getName()).toLowerCase();
            AssetType assetType = AssetType.fromExtension(ext);

            if (assetType != null) {
                // Media asset styling
                binding.ivIcon.setImageResource(assetType.getIconResId());
                binding.ivIcon.setColorFilter(
                        ContextCompat.getColor(itemView.getContext(), assetType.getColorResId()),
                        PorterDuff.Mode.SRC_IN
                );
            } else {
                // Language-specific code icon styling
                Language lang = node.getLanguage();

                int iconResId = R.drawable.ic_file_lines;
                if (lang == Language.HTML) iconResId = R.drawable.ic_html_icon;
                else if (lang == Language.CSS) iconResId = R.drawable.ic_css_icon;
                else if (lang == Language.JAVASCRIPT) iconResId = R.drawable.ic_js_icon;
                else if (lang == Language.JSON) iconResId = R.drawable.ic_json_icon;
                else if (lang == Language.MARKDOWN) iconResId = R.drawable.ic_md_icon;

                binding.ivIcon.setImageResource(iconResId);

                // Apply language coloring to the icon
                if (lang != Language.TEXT || ext.equals("txt") || ext.isEmpty()) {
                    int fileColor = ContextCompat.getColor(itemView.getContext(), lang.getColorResId());
                    binding.ivIcon.setColorFilter(fileColor, PorterDuff.Mode.SRC_IN);
                } else {
                    binding.ivIcon.clearColorFilter();
                }
            }

            updateGitStatus(node);
        }

        /**
         * Resolves the Git status for the node and displays a colored status dot if applicable.
         */
        private void updateGitStatus(FileNode node) {
            if (gitStatusMap != null && rootPath != null) {
                String relativePath = getRelativePath(node.getFile().getAbsolutePath());
                FileStatus.Type status = gitStatusMap.get(relativePath);
                if (status != null) {
                    binding.gitStatusBadge.setVisibility(View.VISIBLE);
                    binding.gitStatusBadge.setStatus(status);
                } else {
                    binding.gitStatusBadge.setVisibility(View.GONE);
                }
            } else {
                binding.gitStatusBadge.setVisibility(View.GONE);
            }
        }

        /**
         * Converts an absolute path to a project-relative path with normalized separators.
         */
        private String getRelativePath(String absPath) {
            if (absPath.startsWith(rootPath)) {
                String rel = absPath.substring(rootPath.length());
                if (rel.startsWith(File.separator)) {
                    rel = rel.substring(1);
                }
                return rel.replace('\\', '/'); // Standardize for Git and internal maps
            }
            return absPath;
        }
    }
}