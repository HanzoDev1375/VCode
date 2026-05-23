package com.cocode.vcode.ide.git.model;

/**
 * Data representation model for an individual Git branch node within the workspace.
 * Holds state attributes defining localized checkout tracking and reference descriptors.
 */
public class BranchItem {
    private final String name;       // The human-readable name of the branch (e.g., "main", "feature/login")
    private final boolean isActive;  // True if this branch is the one currently checked out in the workspace
    private final boolean isRemote;  // True if this branch belongs to a tracked remote connection endpoint
    private final String lastCommit; // The commit message summary associated with the tip of this branch branch pointer

    /**
     * Complete initialization constructor providing baseline property mappings.
     */
    public BranchItem(String name, boolean isActive, boolean isRemote, String lastCommit) {
        this.name = name;
        this.isActive = isActive;
        this.isRemote = isRemote;
        this.lastCommit = lastCommit;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isRemote() {
        return isRemote;
    }

    public String getLastCommit() {
        return lastCommit;
    }
}