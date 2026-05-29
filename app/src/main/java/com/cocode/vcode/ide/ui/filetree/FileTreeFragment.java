package com.cocode.vcode.ide.ui.filetree;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.FileNode;
import com.cocode.vcode.ide.databinding.FragmentFileTreeBinding;
import com.cocode.vcode.ide.databinding.ItemCustomPopupBinding;
import com.cocode.vcode.ide.databinding.LayoutCustomPopupBinding;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.ui.sheets.DeleteBottomSheet;
import com.cocode.vcode.ide.ui.sheets.NewFileBottomSheet;
import com.cocode.vcode.ide.ui.sheets.NewFolderBottomSheet;
import com.cocode.vcode.ide.ui.sheets.RenameBottomSheet;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * FileTreeFragment displays the project's file explorer.
 * It provides tools for navigating the project structure, creating/deleting files,
 * and importing assets from the device filesystem. It also visualizes Git statuses
 * on a per-file basis.
 */
public class FileTreeFragment extends Fragment implements FileTreeAdapter.FileTreeListener {

    private FragmentFileTreeBinding binding;
    private EditorViewModel viewModel;

    /** Result launcher for importing multiple files from the system picker. */
    private final ActivityResultLauncher<String> importFilesLauncher = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    copyUrisToProject(uris);
                }
            }
    );

    /** Result launcher for importing an entire directory tree from the system picker. */
    private final ActivityResultLauncher<Uri> importFolderLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null) {
                    copyFolderToProject(uri);
                }
            }
    );

    private FileTreeAdapter adapter;
    private FileSelectionListener selectionListener;
    private File selectedImportDestination = null;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Verify that the parent activity implements the selection listener
        if (context instanceof FileSelectionListener) {
            selectionListener = (FileSelectionListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFileTreeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize the tree adapter with a standard 16dp indentation per depth level
        float density = getResources().getDisplayMetrics().density;
        adapter = new FileTreeAdapter(this, 16, density);
        binding.rvFileTree.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvFileTree.setAdapter(adapter);

        // Apply specialized UI fonts
        binding.tvFileExplorer.setTypeface(FontManager.getInstance().getUiSemiBold(requireContext()));
        binding.btnImportFiles.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));
        binding.btnImportFolder.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));

        // Bind to the Activity-scoped EditorViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(EditorViewModel.class);

        setupObservers();

        // Wire up interaction listeners
        binding.btnRefresh.setOnClickListener(v -> viewModel.refreshFileTree());
        binding.btnImportFiles.setOnClickListener(v -> showImportDestinationDialog(() -> importFilesLauncher.launch("*/*")));
        binding.btnImportFolder.setOnClickListener(v -> showImportDestinationDialog(() -> importFolderLauncher.launch(null)));
    }

    /**
     * Connects reactive data streams from the ViewModel to update the file tree.
     */
    private void setupObservers() {
        // Observe the structural file tree data
        viewModel.getFileTree().observe(getViewLifecycleOwner(), nodes -> {
            if (nodes != null && viewModel.getProjectRoot() != null) {
                adapter.setRootPath(viewModel.getProjectRoot().getAbsolutePath());
                adapter.setProjectName(viewModel.getProjectName());
                adapter.setTree(nodes);
            }
        });

        // Observe Git statuses and update the UI dots based on user preferences
        viewModel.getGitStatuses().observe(getViewLifecycleOwner(), statuses -> {
            AppSettings settings = viewModel.getSettingsLiveData().getValue();
            if (settings != null && settings.gitShowFileTreeStatus && statuses != null) {
                adapter.setGitStatuses(statuses);
            } else {
                adapter.setGitStatuses(new HashMap<>());
            }
        });

        // Monitor settings changes to toggle Git status visibility instantly
        viewModel.getSettingsLiveData().observe(getViewLifecycleOwner(), settings -> {
            if (settings != null) {
                if (settings.gitShowFileTreeStatus && viewModel.getGitStatuses().getValue() != null) {
                    adapter.setGitStatuses(viewModel.getGitStatuses().getValue());
                } else {
                    adapter.setGitStatuses(new HashMap<>());
                }
            }
        });
    }

    /**
     * Copies a list of selected system URIs into the project root directory.
     */
    private void copyUrisToProject(List<Uri> uris) {
        File root = selectedImportDestination != null ? selectedImportDestination : viewModel.getProjectRoot();
        if (root == null) return;

        Toast.makeText(getContext(), "Importing " + uris.size() + " files...", Toast.LENGTH_SHORT).show();

        ExecutorProvider.getInstance().runOnIo(() -> {
            for (Uri uri : uris) {
                String fileName = getFileNameFromUri(uri);
                if (fileName == null) fileName = "imported_file_" + System.currentTimeMillis();
                File destFile = new File(root, fileName);
                copyStreamToFile(uri, destFile);
            }

            ExecutorProvider.getInstance().runOnMain(() -> {
                Toast.makeText(getContext(), "Import complete!", Toast.LENGTH_SHORT).show();
                viewModel.refreshFileTree(); // Reload tree to show new files
            });
        });
    }

    /**
     * Copies an entire directory structure from a system SAF Uri into the project root.
     */
    private void copyFolderToProject(Uri treeUri) {
        File root = selectedImportDestination != null ? selectedImportDestination : viewModel.getProjectRoot();
        if (root == null) return;

        Toast.makeText(getContext(), "Importing folder...", Toast.LENGTH_SHORT).show();

        ExecutorProvider.getInstance().runOnIo(() -> {
            DocumentFile documentFile = DocumentFile.fromTreeUri(requireContext(), treeUri);
            if (documentFile != null) {
                String folderName = documentFile.getName() != null ? documentFile.getName() : "Imported_Folder";
                File destDir = new File(root, folderName);
                if (!destDir.exists()) destDir.mkdirs();

                copyDocumentFileTree(documentFile, destDir);
            }

            ExecutorProvider.getInstance().runOnMain(() -> {
                Toast.makeText(getContext(), "Folder imported!", Toast.LENGTH_SHORT).show();
                viewModel.refreshFileTree();
            });
        });
    }

    private void showImportDestinationDialog(Runnable onConfirmed) {
        if (viewModel.getFileTree().getValue() == null || viewModel.getProjectRoot() == null) {
            Toast.makeText(getContext(), "Project tree not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(com.cocode.vcode.ide.R.layout.dialog_import_destination, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        android.widget.TextView tvTitle = dialogView.findViewById(com.cocode.vcode.ide.R.id.tv_dialog_title);
        tvTitle.setTypeface(FontManager.getInstance().getUiSemiBold(requireContext()));

        androidx.recyclerview.widget.RecyclerView rvFolders = dialogView.findViewById(com.cocode.vcode.ide.R.id.rv_destination_folders);
        rvFolders.setLayoutManager(new LinearLayoutManager(getContext()));
        
        File[] selected = new File[]{viewModel.getProjectRoot()};

        DestinationAdapter destAdapter = new DestinationAdapter(file -> {
            selected[0] = file;
        }, 16, getResources().getDisplayMetrics().density);

        rvFolders.setAdapter(destAdapter);
        destAdapter.setTree(viewModel.getProjectRoot(), viewModel.getProjectName(), viewModel.getFileTree().getValue());

        com.google.android.material.button.MaterialButton btnCancel = dialogView.findViewById(com.cocode.vcode.ide.R.id.btn_cancel);
        com.google.android.material.button.MaterialButton btnConfirm = dialogView.findViewById(com.cocode.vcode.ide.R.id.btn_confirm);
        
        btnCancel.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));
        btnConfirm.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            selectedImportDestination = selected[0];
            dialog.dismiss();
            onConfirmed.run();
        });

        dialog.show();
    }

    /**
     * Recursively traverses a DocumentFile tree and copies it to the filesystem.
     */
    private void copyDocumentFileTree(DocumentFile sourceDoc, File destDir) {
        for (DocumentFile file : sourceDoc.listFiles()) {
            if (file.isDirectory()) {
                File newDir = new File(destDir, Objects.requireNonNull(file.getName()));
                if (!newDir.exists()) newDir.mkdirs();
                copyDocumentFileTree(file, newDir);
            } else {
                File newFile = new File(destDir, Objects.requireNonNull(file.getName()));
                copyStreamToFile(file.getUri(), newFile);
            }
        }
    }

    /**
     * Performs a low-level stream copy from a content Uri to a destination File.
     */
    private void copyStreamToFile(Uri sourceUri, File destFile) {
        try (InputStream in = requireContext().getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destFile)) {
            if (in == null) return;

            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Resolves the user-friendly filename from a system Uri.
     */
    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : 0;
            if (cut != -1) {
                result = result != null ? result.substring(cut + 1) : null;
            }
        }
        return result;
    }

    @Override
    public void onFileClick(File file) {
        if (!file.isDirectory()) {
            if (selectionListener != null) {
                // Notify the EditorActivity to load the selected file
                selectionListener.onFileSelected(new FileNode(file, 0));
            }
        }
    }

    @Override
    public void onNodeLongClick(View anchor, FileNode node) {
        File file = node.getFile();
        boolean isRoot = viewModel.getProjectRoot() != null && file.getAbsolutePath().equals(viewModel.getProjectRoot().getAbsolutePath());

        File clipboardFile = adapter.getClipboardFile();
        boolean canPaste = clipboardFile != null && clipboardFile.exists();

        if (isRoot && !canPaste) {
            return;
        }

        LayoutCustomPopupBinding popupBinding = LayoutCustomPopupBinding.inflate(getLayoutInflater());
        int width = UiUtils.dpToPx(requireContext(), 220);

        PopupWindow popupWindow = new PopupWindow(
                popupBinding.getRoot(),
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(8f);
        popupWindow.setAnimationStyle(R.style.VCodePopupMenuAnimation);

        if (!isRoot) {
            addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_pen, "Rename", () -> showRenameDialog(file));
            addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_copy, "Copy", () -> {
                adapter.setClipboardState(file, false);
                Toast.makeText(getContext(), "Copied", Toast.LENGTH_SHORT).show();
            });
            addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_scissors, "Cut", () -> {
                adapter.setClipboardState(file, true);
                Toast.makeText(getContext(), "Cut", Toast.LENGTH_SHORT).show();
            });
        }
        
        if (canPaste) {
            addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_file_plus, "Paste", () -> {
                File destDir = file.isDirectory() ? file : file.getParentFile();
                performPaste(destDir);
            });
        }
        
        if (!isRoot) {
            addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_copy, "Copy Path", () -> showCopyPathPopup(anchor, file));
            
            addDivider(popupBinding.popupContainer);
            
            View deleteItem = addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_trash, "Delete", () -> showDeleteDialog(file));
            TextView tvTitle = deleteItem.findViewById(R.id.tv_title);
            ImageView ivIcon = deleteItem.findViewById(R.id.iv_icon);
            int errorColor = ContextCompat.getColor(requireContext(), R.color.vcode_accent_error);
            if (tvTitle != null) tvTitle.setTextColor(errorColor);
            if (ivIcon != null) ivIcon.setColorFilter(errorColor);
        }

        popupWindow.showAsDropDown(anchor, anchor.getWidth() / 2, -anchor.getHeight() / 2);
    }

    private void showCopyPathPopup(View anchor, File file) {
        LayoutCustomPopupBinding popupBinding = LayoutCustomPopupBinding.inflate(getLayoutInflater());
        int width = UiUtils.dpToPx(requireContext(), 220);

        PopupWindow popupWindow = new PopupWindow(
                popupBinding.getRoot(),
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(8f);
        popupWindow.setAnimationStyle(R.style.VCodePopupMenuAnimation);

        addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_copy, "Absolute Path", () -> {
            copyToSystemClipboard("Absolute Path", file.getAbsolutePath());
        });

        addPopupItem(popupBinding.popupContainer, popupWindow, R.drawable.ic_copy, "Relative Path", () -> {
            if (viewModel.getProjectRoot() != null) {
                String relPath = file.getAbsolutePath().replace(viewModel.getProjectRoot().getAbsolutePath() + File.separator, "");
                if (relPath.startsWith(File.separator)) relPath = relPath.substring(1);
                copyToSystemClipboard("Relative Path", relPath);
            }
        });

        popupWindow.showAsDropDown(anchor, anchor.getWidth() / 2, -anchor.getHeight() / 2);
    }

    private View addPopupItem(ViewGroup container, PopupWindow popup, int iconRes, String title, Runnable action) {
        ItemCustomPopupBinding itemBinding = ItemCustomPopupBinding.inflate(getLayoutInflater(), container, false);
        itemBinding.ivIcon.setImageResource(iconRes);
        itemBinding.tvTitle.setText(title);
        itemBinding.tvTitle.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall);
        itemBinding.tvTitle.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));
        itemBinding.getRoot().setOnClickListener(v -> { popup.dismiss(); action.run(); });
        container.addView(itemBinding.getRoot());
        return itemBinding.getRoot();
    }

    private void addDivider(ViewGroup container) {
        View divider = new View(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiUtils.dpToPx(requireContext(), 1)
        );
        params.setMargins(0, UiUtils.dpToPx(requireContext(), 4), 0, UiUtils.dpToPx(requireContext(), 4));
        divider.setLayoutParams(params);
        divider.setBackgroundColor(getThemeColor(com.google.android.material.R.attr.colorOutlineVariant));
        container.addView(divider);
    }

    private int getThemeColor(int attrRes) {
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }

    private void copyToSystemClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Copied path", Toast.LENGTH_SHORT).show();
        }
    }

    private void performPaste(File destinationDir) {
        File source = adapter.getClipboardFile();
        boolean isCut = adapter.isCutAction();
        if (source == null || !source.exists() || destinationDir == null) return;

        Toast.makeText(getContext(), "Pasting...", Toast.LENGTH_SHORT).show();

        ExecutorProvider.getInstance().runOnIo(() -> {
            File target = new File(destinationDir, source.getName());
            boolean success = false;

            int counter = 1;
            String baseName = source.getName();
            String extension = "";
            int dotIndex = baseName.lastIndexOf('.');
            if (dotIndex > 0) {
                extension = baseName.substring(dotIndex);
                baseName = baseName.substring(0, dotIndex);
            }
            while (target.exists()) {
                target = new File(destinationDir, baseName + "_" + counter + extension);
                counter++;
            }

            if (isCut) {
                success = source.renameTo(target);
                if (!success) {
                    if (source.isDirectory()) {
                        success = FileUtils.copyDirectory(source, target) && FileUtils.deleteRecursive(source);
                    } else {
                        success = FileUtils.copyFile(source, target) && source.delete();
                    }
                }
                if (success) {
                    ExecutorProvider.getInstance().runOnMain(() -> adapter.setClipboardState(null, false));
                }
            } else {
                if (source.isDirectory()) {
                    success = FileUtils.copyDirectory(source, target);
                } else {
                    success = FileUtils.copyFile(source, target);
                }
            }

            if (success) {
                ExecutorProvider.getInstance().runOnMain(() -> viewModel.refreshFileTree());
            } else {
                ExecutorProvider.getInstance().runOnMain(() -> Toast.makeText(getContext(), "Paste failed", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showRenameDialog(File file) {
        RenameBottomSheet.RenameType type = file.isDirectory() ? RenameBottomSheet.RenameType.FOLDER : RenameBottomSheet.RenameType.FILE;
        RenameBottomSheet.show(
                getChildFragmentManager(),
                type,
                file.getName(),
                newName -> viewModel.renameNode(file, newName)
        );
    }

    private void showDeleteDialog(File file) {
        DeleteBottomSheet.DeleteType type = file.isDirectory() ? DeleteBottomSheet.DeleteType.FOLDER : DeleteBottomSheet.DeleteType.FILE;
        DeleteBottomSheet.show(
                getChildFragmentManager(),
                type,
                file.getName(),
                null,
                () -> viewModel.deleteNode(file)
        );
    }

    @Override
    public void onAddFileClick(File parentDir) {
        NewFileBottomSheet sheet = NewFileBottomSheet.newInstance();
        sheet.setListener((fileName, initialContent) -> viewModel.createFile(parentDir, fileName, initialContent));
        sheet.show(getChildFragmentManager(), "NewFileBottomSheet");
    }

    @Override
    public void onAddFolderClick(File parentDir) {
        NewFolderBottomSheet sheet = NewFolderBottomSheet.newInstance(parentDir);
        sheet.show(getChildFragmentManager(), "NewFolderBottomSheet");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * Interface for communicating file selections to the hosting Activity.
     */
    public interface FileSelectionListener {
        void onFileSelected(FileNode fileNode);
    }
}