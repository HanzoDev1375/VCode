package com.cocode.vcode.ide.git.model;

/**
 * Flat transaction structural tracking container representing modified files units.
 * Packages directory location metrics with short code characters text logs, feeding status
 * metrics values across changes confirmation sheets lists elements.
 */
public class GitFileItem {
    private final String path;     // Absolute or project-relative routing string pointing to the resource location on disk
    private final String fileName; // Trimmed visual name text used to represent files components entries cards layout headers
    private final String status;   // Individual single letter status codes classification characters flag markers (e.g. "M", "A")
    private final boolean staged;  // Selection indicator verifying if structural attributes live inside index storage areas

    /**
     * Complete immutable constructor mapping state properties configurations fields on generation events loops.
     */
    public GitFileItem(String path, String fileName, String status, boolean staged) {
        this.path = path;
        this.fileName = fileName;
        this.status = status;
        this.staged = staged;
    }

    public String getPath() {
        return path;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStatus() {
        return status;
    }

    public boolean isStaged() {
        return staged;
    }
}