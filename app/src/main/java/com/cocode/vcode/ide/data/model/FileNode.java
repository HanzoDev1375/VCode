package com.cocode.vcode.ide.data.model;

import com.cocode.vcode.ide.core.model.FileType;
import com.cocode.vcode.ide.utils.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Hierarchical data node representing an item inside the sidebar project file tree view.
 * Handles directory branch expansions and maps file types to structural properties.
 */
public class FileNode {

    private File file;
    private List<FileNode> children;
    private boolean isExpanded;
    private final int depth; // Nested indentation layout layer index in the sidebar list layout

    /**
     * Instantiates a tree index item bound to a specified nesting depth level.
     */
    public FileNode(File file, int depth) {
        this.file = file;
        this.depth = depth;
        this.isExpanded = false;
        // Allocate interior array tracks if this node acts as a file directory container
        if (file != null && file.isDirectory()) {
            this.children = new ArrayList<>();
        } else {
            this.children = null; // Leaf node element mapping flat system files
        }
    }

    public boolean isDirectory() {
        return file != null && file.isDirectory();
    }

    public String getName() {
        return file != null ? file.getName() : "";
    }

    /**
     * Resolves the target programming rules map linked to this node instance.
     */
    public FileType getFileType() {
        if (isDirectory()) return FileType.TEXT;
        return FileType.fromExtension(FileUtils.getExtension(getName()));
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public List<FileNode> getChildren() {
        return children;
    }

    public void setChildren(List<FileNode> c) {
        this.children = c;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setExpanded(boolean expanded) {
        this.isExpanded = expanded;
    }

    public int getDepth() {
        return depth;
    }
}