package com.cocode.vcode.ide.data.model;

/**
 * Global configuration model managing user settings across the entire IDE workspace.
 * Organizes preferences for editor behavior, code validation engines, Git version
 * control parameters, theme appearance styles, and automated file-saving actions.
 */
public class AppSettings {

    // --- Editor Configurations ---
    public int fontSize = 14;
    public int tabSize = 2;
    public boolean showLineNumbers = true;
    public boolean autoCloseBrackets = true;
    public boolean autoCloseHtmlTags = true;
    public boolean highlightCurrentLine = true;
    public boolean autoIndent = true;
    public boolean matchBrackets = true;

    // --- JSON Validation & Formatting ---
    public int jsonIndentSize = 2;
    public boolean jsonFormatOnSave = false;
    public boolean jsonValidateRealtime = true;

    // --- Git Integration Settings ---
    public String gitAuthorName = "";
    public String gitAuthorEmail = "";
    public String gitDefaultBranch = "main";
    public boolean gitAutoFetch = false;
    public boolean gitConfirmPush = true;
    public boolean gitConfirmHardReset = true;
    public boolean gitShowFileTreeStatus = true;
    public String gitDefaultRemote = "origin";

    // --- Appearance Settings ---
    public Theme theme = Theme.SYSTEM;

    // --- Live Preview Preferences ---
    public boolean openPreviewInApp = true;
    public boolean autoRefreshPreview = false;

    // --- Auto-Save Routines ---
    public boolean autoSave = false;
    public int autoSaveDelay = 2; // Expressed in seconds

    // --- Safety and Operational Prompts ---
    public boolean confirmOnTabClose = true;
    public boolean confirmOnProjectDelete = true;

    /**
     * Default constructor for instantiation and serialization engines.
     */
    public AppSettings() {
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int size) {
        this.fontSize = size;
    }

    public boolean isShowLineNumbers() {
        return showLineNumbers;
    }

    public void setShowLineNumbers(boolean show) {
        this.showLineNumbers = show;
    }

    public boolean isAutoCloseBrackets() {
        return autoCloseBrackets;
    }

    public void setAutoCloseBrackets(boolean auto) {
        this.autoCloseBrackets = auto;
    }

    /**
     * Resolves the configured branch destination metadata rule.
     */
    public String getDefaultBranch() {
        return gitDefaultRemote;
    }

    /**
     * Updates the default target remote branch destination assignment marker.
     */
    public void setDefaultBranch(String branch) {
        this.gitDefaultRemote = branch;
    }

    /**
     * Checks verification rules for deletion and reset confirmations.
     */
    public boolean isConfirmHardReset() {
        return confirmOnProjectDelete;
    }

    /**
     * Configures the enforcement prompt checks before permanent item teardowns.
     */
    public void setConfirmHardReset(boolean confirm) {
        this.confirmOnProjectDelete = confirm;
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    /**
     * Resolves the preferred baseline layout step block by stacking horizontal tab keys.
     * Used by formatter and auto-indent engines to create baseline padding lines.
     */
    public String getIndent() {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < tabSize; i++) {
            indent.append(" ");
        }
        return indent.toString();
    }

    /**
     * Available UI visual style modes supported by the workspace presentation layer.
     */
    public enum Theme {DARK, LIGHT, SYSTEM}
}