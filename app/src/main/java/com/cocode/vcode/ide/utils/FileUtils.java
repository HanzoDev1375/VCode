package com.cocode.vcode.ide.utils;

import android.content.Context;
import android.os.Environment;

import com.cocode.vcode.ide.core.language.Language;
import com.cocode.vcode.ide.data.model.FileNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Multi-purpose file manager providing core primitives for the IDE file manager.
 * Performs nested recursive deletions, deep branch duplications, tree construction indexes,
 * and handles localized system file provider interactions.
 */
public class FileUtils {

    private static final String PROJECTS_DIR_NAME = "VCodeProjects";

    private FileUtils() {
    }

    /**
     * Reads string blocks out of targeted disk files using strict UTF-8 normalization mappings.
     */
    public static String readFile(File file) throws IOException {
        if (file == null || !file.exists()) throw new IOException("File not found: " + file);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        // Remove trailing newline added by readLine loop to match the pristine document footprint
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Persists text character logs into selected disk paths, initializing parent scopes as needed.
     */
    public static void writeFile(File file, String content) throws IOException {
        if (file == null) throw new IOException("File is null");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(content == null ? "" : content);
        }
    }

    /**
     * Extracts lowercase file extensions components from trailing dot delimiters.
     */
    public static String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase();
    }

    /**
     * Translates name configurations extensions into structured Language rulesets profile.
     */
    public static Language getLanguage(String fileName) {
        return Language.fromExtension(getExtension(fileName));
    }

    /**
     * Creates new files on the file system, failing early if an object already occupies that name.
     */
    public static File createFile(File dir, String name) throws IOException {
        if (dir == null || name == null || name.isEmpty())
            throw new IOException("Invalid directory or file name");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, name);
        if (file.exists()) throw new IOException("File already exists: " + name);
        if (!file.createNewFile()) throw new IOException("Could not create file: " + name);
        return file;
    }

    /**
     * Generates directory subfolder branches down to target directory coordinates.
     */
    public static void createFolder(File dir, String name) throws IOException {
        if (dir == null || name == null || name.isEmpty())
            throw new IOException("Invalid directory or folder name");
        File folder = new File(dir, name);
        if (folder.exists()) throw new IOException("Folder already exists: " + name);
        if (!folder.mkdirs()) throw new IOException("Could not create folder: " + name);
    }

    /**
     * Recursively steps through internal directory sub-trees to completely wipe files from storage.
     */
    public static boolean deleteRecursive(File file) {
        if (file == null || !file.exists()) return false;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    /**
     * Duplicates data content chunks between files via an intermediate buffer block allocation stream.
     */
    public static boolean copyFile(File src, File dst) {
        if (src == null || !src.exists() || dst == null) return false;
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Clones complex directory trees by systematically mapping subfolders and files.
     */
    public static boolean copyDirectory(File src, File dst) {
        if (src == null || !src.isDirectory()) return false;
        if (!dst.exists()) dst.mkdirs();
        File[] children = src.listFiles();
        if (children == null) return true;
        for (File child : children) {
            File dstChild = new File(dst, child.getName());
            if (child.isDirectory()) {
                if (!copyDirectory(child, dstChild)) return false;
            } else {
                if (!copyFile(child, dstChild)) return false;
            }
        }
        return true;
    }

    /**
     * Modifies local directory entity names within shared parent folders.
     */
    public static void renameFile(File file, String newName) throws IOException {
        if (file == null || !file.exists()) throw new IOException("File not found");
        if (newName == null || newName.isEmpty()) throw new IOException("New name is empty");
        File renamed = new File(file.getParentFile(), newName);
        if (renamed.exists())
            throw new IOException("A file named '" + newName + "' already exists");
        if (!file.renameTo(renamed)) throw new IOException("Could not rename file");
    }

    /**
     * Navigates local shared user memory scopes to configure the app workspace root dir.
     */
    public static File getProjectsDir(Context ctx) {
        File root = Environment.getExternalStorageDirectory();
        File dir = new File(root, PROJECTS_DIR_NAME);

        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Inform external media indexes to ignore internal code configurations scripts text data
        File nomedia = new File(dir, ".nomedia");
        if (!nomedia.exists()) {
            try {
                nomedia.createNewFile();
            } catch (IOException ignored) {
            }
        }

        return dir;
    }

    /**
     * Forwards a file out to external device viewers by computing file provider intent mappings.
     */
    public static void openFileExternally(Context context, java.io.File file) {
        try {
            String authority = context.getPackageName() + ".fileprovider";
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file);

            String mimeType = "*/*";
            String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (extension != null && !extension.isEmpty()) {
                String type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
                if (type != null) {
                    mimeType = type;
                }
            }

            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);

            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(android.content.Intent.createChooser(intent, "Open " + file.getName() + " with..."));

        } catch (Exception e) {
            android.widget.Toast.makeText(context, "Cannot open this file type.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Generates a fully populated navigation model representable in sidebar layout folders views.
     */
    public static List<FileNode> buildFileTree(File root) {
        FileNode rootNode = new FileNode(root, 0);
        rootNode.setExpanded(true);
        rootNode.setChildren(buildFileTreeRecursive(root, 1));

        List<FileNode> tree = new ArrayList<>();
        tree.add(rootNode);
        return tree;
    }

    /**
     * Sorts folders above plain documents, alphabetically filtering internal versioning
     * metadata elements like .git profiles or tracking sheets from presentation views.
     */
    private static List<FileNode> buildFileTreeRecursive(File dir, int depth) {
        List<FileNode> nodes = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) return nodes;
        File[] entries = dir.listFiles();
        if (entries == null) return nodes;

        Arrays.sort(entries, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File entry : entries) {
            String name = entry.getName();

            // Explicitly exclude .git, and .nomedia. Allow all other dotfiles (like .env)
            if (name.equals(".git") || name.equals(".nomedia")) {
                continue;
            }

            // Hide internal IDE state files from the UI
            if (name.equals("project_meta.json") || name.equals("session.json")) {
                continue;
            }

            FileNode node = new FileNode(entry, depth);
            if (entry.isDirectory()) {
                node.setChildren(buildFileTreeRecursive(entry, depth + 1));
            }
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * Converts a file size metric integer value into a legible text display scale descriptor.
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f));
    }

    /**
     * Walks recursive data tracks to compute overall file aggregates within a target workspace path.
     */
    public static int countFilesInDir(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;

        int count = 0;
        for (File f : files) {
            String name = f.getName();

            if (name.equals(".git") || name.equals(".gitignore") || name.equals(".nomedia")) {
                continue;
            }

            if (name.equals("project_meta.json") || name.equals("session.json")) {
                continue;
            }

            if (f.isFile()) {
                count++;
            } else if (f.isDirectory()) {
                count += countFilesInDir(f);
            }
        }
        return count;
    }
}