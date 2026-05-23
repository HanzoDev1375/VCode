package com.cocode.vcode.ide.git.model;

/**
 * Read-only data container model optimized for lean interface adapters tracking systems.
 * Provides flat string fields definitions to speed up list bindings on historical lists displays feeds.
 */
public class CommitItem {
    private final String sha;         // Full uniquely tracking branch identification key value hash
    private final String shortSha;    // Trimmed subset string segment targeted to format card sub-headers details
    private final String message;     // Documented descriptions detail summary row saved against current edits pass
    private final String author;      // Contributor label reference identifying change tracking origins entries
    private final String timestamp;   // Pre-formatted localized date string narrative representation (e.g. "2 mins ago")

    /**
     * Complete configuration constructor establishing values mappings across final immutable variables tracks.
     */
    public CommitItem(String sha, String shortSha, String message, String author, String timestamp) {
        this.sha = sha;
        this.shortSha = shortSha;
        this.message = message;
        this.author = author;
        this.timestamp = timestamp;
    }

    public String getSha() {
        return sha;
    }

    public String getShortSha() {
        return shortSha;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthor() {
        return author;
    }

    public String getTimestamp() {
        return timestamp;
    }
}