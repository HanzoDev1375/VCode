package com.cocode.vcode.ide.git.model;

/**
 * Operational workflow state tracker monitoring single file modifications paths inside active repositories.
 * Evaluates branch boundaries rules to verify if modifications reside inside index regions or local workspaces zones.
 */
public class FileStatus {

    private String relativePath; // Root path tracking layout running out from base working directories paths blocks
    private Type type;           // Grammatical category code character defining nature of file alteration state

    public FileStatus() {
    }

    public FileStatus(String relativePath, Type type) {
        this.relativePath = relativePath;
        this.type = type;
    }

    /**
     * Grouping check checking if item metrics indicate changes have been recorded inside index staging files pools.
     */
    public boolean isStaged() {
        return type == Type.STAGED_ADDED
                || type == Type.STAGED_MODIFIED
                || type == Type.STAGED_DELETED;
    }

    /**
     * Grouping check checking if modifications reside exclusively on workspace disks lines awaiting indexing runs.
     */
    public boolean isUnstaged() {
        return type == Type.UNSTAGED_MODIFIED
                || type == Type.UNSTAGED_DELETED
                || type == Type.UNTRACKED;
    }

    /**
     * Translates high level internal types declarations states flags into standard version command indicators character markers symbols.
     * Useful to format tracking markers characters text lines inside sidebar trees files view blocks.
     */
    public String getStatusLabel() {
        if (type == null) return "?";
        switch (type) {
            case STAGED_ADDED:
                return "A";
            case STAGED_MODIFIED:
            case UNSTAGED_MODIFIED:
                return "M";
            case STAGED_DELETED:
            case UNSTAGED_DELETED:
                return "D";
            case CONFLICTED:
                return "!";
            default:
                return "?";
        }
    }

    /**
     * Extracts localized flat visual filename structures by cleaning out trailing path separator structures.
     */
    public String getFileName() {
        if (relativePath == null) return "";
        int sep = relativePath.lastIndexOf('/');
        if (sep < 0)
            sep = relativePath.lastIndexOf('\\'); // Fallback check evaluating alternative platform layouts marks
        return sep >= 0 ? relativePath.substring(sep + 1) : relativePath;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String path) {
        this.relativePath = path;
    }

    public String getPath() {
        return relativePath;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Explicit operational code types categorizing file lifecycle steps parameters rules.
     */
    public enum Type {
        STAGED_ADDED,       // Newly created item recorded inside the index staging cache area
        STAGED_MODIFIED,    // Revised pre-existing component recorded inside the index staging cache area
        STAGED_DELETED,     // Purged component whose removal record is verified inside the index staging cache area
        UNSTAGED_MODIFIED,  // Altered component text lines residing only on local disk file spaces fields
        UNSTAGED_DELETED,   // Deleted disk item whose removal record has not been checked into index files maps
        UNTRACKED,          // Newly authored document unknown to the active repository branch definition rules
        CONFLICTED,         // Incompatible merge states tracking lines blockages demanding developer reconciliation actions
        IGNORED             // Blacklisted system paths profiles mapped out of scanning operations via ignore templates scripts
    }
}