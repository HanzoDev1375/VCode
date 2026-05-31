package com.cocode.vcode.ide.git.model;

import java.util.Date;
import java.util.List;

/**
 * Broad metadata descriptor tracking details for a target repository revision history entry.
 * Captures comprehensive properties for authoring attributes, cryptographic branch hashes, tags,
 * parent lines tracking sequences, and comprehensive list collections tracking modifications.
 */
public class CommitInfo {

    private String fullHash;               // Full 40-character unique SHA-1 commit identifier string
    private String shortHash;              // Abbreviated 7-character visual signature string for display panels
    private String fullMessage;            // Complete multi-line raw description text typed by the committer
    private String shortMessage;           // Streamlined single row summary block matching the first line definition
    private String authorName;             // Personal name declaration metadata linked to the contributor profile
    private String authorEmail;            // Electronic communication path linked to the contributor profile
    private Date date;                     // Calendar timestamp marking transaction completion
    private String[] parentHashes;         // Collection indexing preceding ancestor node identifier handles
    private List<FileStatus> changedFiles; // Tracking matrices cataloging targeted files adjustments actions
    private List<String> tags;             // Reference labels pinned across this localized historical line node
    private List<String> branchNames;      // Active collection listing pointers pointing directly to this revision node

    /**
     * Default empty constructor used for serialization routines and data factories.
     */
    public CommitInfo() {
    }

    /**
     * Multi-argument constructor mapping fundamental structural commit attributes on generation.
     */
    public CommitInfo(String fullHash, String shortHash, String fullMessage,
                      String shortMessage, String authorName, String authorEmail,
                      Date date, String[] parentHashes) {
        this.fullHash = fullHash;
        this.shortHash = shortHash;
        this.fullMessage = fullMessage;
        this.shortMessage = shortMessage;
        this.authorName = authorName;
        this.authorEmail = authorEmail;
        this.date = date;
        this.parentHashes = parentHashes;
    }

    /**
     * Evaluates parent node index sizes to verify if this node represents a historical branch merge junction.
     *
     * @return True if the commit stems from multiple independent ancestral paths.
     */
    public boolean isMergeCommit() {
        return parentHashes != null && parentHashes.length > 1;
    }

    /**
     * Generates compressed 2-letter uppercase initials matching full author names definitions.
     * Helpful to populate visual fallback graphic text badges inside layout timeline avatars items rows.
     */
    public String getInitials() {
        if (authorName == null || authorName.isEmpty()) return "?";
        String[] parts = authorName.trim().split("\\s+");

        // Single name processing profile: extract the first two characters safely
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }

        // Multi-part processing profile: compile individual character bounds elements markers
        return (String.valueOf(parts[0].charAt(0))
                + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    public String getFullHash() {
        return fullHash;
    }

    public void setFullHash(String fullHash) {
        this.fullHash = fullHash;
    }

    public String getShortHash() {
        return shortHash;
    }

    public void setShortHash(String shortHash) {
        this.shortHash = shortHash;
    }

    public String getFullMessage() {
        return fullMessage;
    }

    public void setFullMessage(String fullMessage) {
        this.fullMessage = fullMessage;
    }

    public String getShortMessage() {
        return shortMessage;
    }

    public void setShortMessage(String shortMessage) {
        this.shortMessage = shortMessage;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String[] getParentHashes() {
        return parentHashes;
    }

    public void setParentHashes(String[] parentHashes) {
        this.parentHashes = parentHashes;
    }

    public List<FileStatus> getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(List<FileStatus> files) {
        this.changedFiles = files;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getBranchNames() {
        return branchNames;
    }

    public void setBranchNames(List<String> branches) {
        this.branchNames = branches;
    }

    /**
     * Resolves appropriate available text segments to construct display message indicators layouts labels.
     */
    public String getMessage() {
        return shortMessage != null ? shortMessage : fullMessage;
    }

    /**
     * Translates standard Date objects metrics down into simplified raw Unix epoch second integers numbers formats.
     */
    public long getTimestamp() {
        return date != null ? date.getTime() / 1000L : 0;
    }
}