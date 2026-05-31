package com.cocode.vcode.ide.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cocode.vcode.ide.data.model.Result;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FileUtils;

import java.io.File;

/**
 * Low-level data repository coordinating disk I/O file operations.
 * Handles reading, writing, and deleting local workspace documents asynchronously
 * off the main application thread using managed LiveData wrappers.
 */
public class FileRepository {

    /**
     * Instantiates the file repository component.
     */
    public FileRepository() {
    }

    // --- Section: Write ---

    /**
     * Asynchronously writes content to a target file on the disk storage system.
     * Shifts execution to an I/O thread pool and returns feedback to the active subscriber loop.
     *
     * @param file    The local target file descriptor block to modify.
     * @param content The string character sequence data payload to push to disk.
     * @return A LiveData notification channel containing operation result states.
     */
    public LiveData<Result<Boolean>> writeFile(File file, String content) {
        MutableLiveData<Result<Boolean>> liveData = new MutableLiveData<>();
        if (file == null) {
            liveData.setValue(Result.error("File is null"));
            return liveData;
        }

        // Dispatch disk writing operation to a dedicated background I/O worker thread
        ExecutorProvider.getInstance().runOnIo(() -> {
            try {
                FileUtils.writeFile(file, content != null ? content : "");
                // Safely post a successful status state back onto the user interface thread
                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.success(true)));
            } catch (Exception e) {
                // Route failures gracefully back to active UI components
                ExecutorProvider.getInstance()
                        .runOnMain(() -> liveData.setValue(Result.error("Failed to write file: " + e.getMessage())));
            }
        });
        return liveData;
    }

    /**
     * Synchronous write — call only from a background thread. Returns success flag.
     * Leverages raw synchronous calls for quick saving routines within thread limits.
     */
    public void writeFileSync(File file, String content) {
        if (file == null)
            return;
        try {
            FileUtils.writeFile(file, content != null ? content : "");
        } catch (Exception ignored) {
            // Fails silently; optimized for fast-paced ambient background sync calls
        }
    }

    // --- Section: Delete ---

    /**
     * Recursively targets and destroys a specified file or workspace directory branch.
     *
     * @param file The file or folder node targeted for removal.
     * @return A LiveData channel delivering the final destruction outcome status.
     */
    public LiveData<Result<Boolean>> delete(File file) {
        MutableLiveData<Result<Boolean>> liveData = new MutableLiveData<>();
        if (file == null) {
            liveData.setValue(Result.error("File is null"));
            return liveData;
        }

        ExecutorProvider.getInstance().runOnIo(() -> {
            boolean deleted = FileUtils.deleteRecursive(file);
            if (deleted) {
                ExecutorProvider.getInstance().runOnMain(() -> liveData.setValue(Result.success(true)));
            } else {
                ExecutorProvider.getInstance()
                        .runOnMain(() -> liveData.setValue(Result.error("Failed to delete: " + file.getName())));
            }
        });
        return liveData;
    }
}