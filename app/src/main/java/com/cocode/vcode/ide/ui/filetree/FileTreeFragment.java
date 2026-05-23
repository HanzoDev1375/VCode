package com.cocode.vcode.ide.ui.filetree;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.model.FileNode;
import com.cocode.vcode.ide.databinding.FragmentFileTreeBinding;
import com.cocode.vcode.ide.ui.editor.EditorViewModel;
import com.cocode.vcode.ide.ui.sheets.DeleteBottomSheet;
import com.cocode.vcode.ide.ui.sheets.NewFileBottomSheet;
import com.cocode.vcode.ide.ui.sheets.NewFolderBottomSheet;
import com.cocode.vcode.ide.ui.sheets.RenameBottomSheet;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;

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
        binding.btnImportFiles.setOnClickListener(v -> importFilesLauncher.launch("*/*"));
        binding.btnImportFolder.setOnClickListener(v -> importFolderLauncher.launch(null));
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
        File root = viewModel.getProjectRoot();
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
        File root = viewModel.getProjectRoot();
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
    public void onRenameNodeClick(File file) {
        RenameBottomSheet.RenameType type = file.isDirectory() ? RenameBottomSheet.RenameType.FOLDER : RenameBottomSheet.RenameType.FILE;
        RenameBottomSheet.show(
                getChildFragmentManager(),
                type,
                file.getName(),
                newName -> viewModel.renameNode(file, newName)
        );
    }

    @Override
    public void onDeleteNodeClick(File file) {
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