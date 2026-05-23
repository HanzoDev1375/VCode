package com.cocode.vcode.ide.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.prefs.PreferenceKeys;
import com.cocode.vcode.ide.git.core.GitCredentialStore;

/**
 * Access provider interface interacting with local SharedPreferences.
 * Synchronizes identity profiles, handles system fallback evaluations,
 * and ensures global configuration modifications map up dynamically into Git credential stores.
 */
public class SettingsRepository {

    private static final String PREFS_NAME = "vcode_settings";
    private final SharedPreferences prefs;
    private final Context context;

    public SettingsRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Section: Load ---

    /**
     * Hydrates an AppSettings entity block by reading serialized preferences from local partitions.
     */
    public AppSettings loadSettings() {
        AppSettings s = new AppSettings();

        // Editor layout preferences
        s.fontSize = prefs.getInt(PreferenceKeys.FONT_SIZE, s.fontSize);
        s.tabSize = prefs.getInt(PreferenceKeys.TAB_SIZE, s.tabSize);
        s.showLineNumbers = prefs.getBoolean(PreferenceKeys.SHOW_LINE_NUMBERS, s.showLineNumbers);
        s.autoCloseBrackets = prefs.getBoolean(PreferenceKeys.AUTO_CLOSE_BRACKETS, s.autoCloseBrackets);
        s.autoCloseHtmlTags = prefs.getBoolean(PreferenceKeys.AUTO_CLOSE_HTML_TAGS, s.autoCloseHtmlTags);
        s.highlightCurrentLine = prefs.getBoolean(PreferenceKeys.HIGHLIGHT_CURRENT_LINE, s.highlightCurrentLine);
        s.autoIndent = prefs.getBoolean(PreferenceKeys.AUTO_INDENT, s.autoIndent);
        s.matchBrackets = prefs.getBoolean(PreferenceKeys.MATCH_BRACKETS, s.matchBrackets);

        // Language syntax preferences
        s.jsonFormatOnSave = prefs.getBoolean(PreferenceKeys.JSON_FORMAT_ON_SAVE, s.jsonFormatOnSave);
        s.jsonValidateRealtime = prefs.getBoolean(PreferenceKeys.JSON_VALIDATE_REALTIME, s.jsonValidateRealtime);

        // Version control mapping: Pull security settings from secure credential managers first
        GitCredentialStore credentialStore = new GitCredentialStore();
        s.gitAuthorName = credentialStore.getLocalAuthorName(context);
        s.gitAuthorEmail = credentialStore.getLocalAuthorEmail(context);

        // Fallback approach: If credential keys are missing, extract baseline properties from flat storage layers
        if (s.gitAuthorName == null || s.gitAuthorName.isEmpty()) {
            s.gitAuthorName = prefs.getString(PreferenceKeys.GIT_AUTHOR_NAME, "");
        }
        if (s.gitAuthorEmail == null || s.gitAuthorEmail.isEmpty()) {
            s.gitAuthorEmail = prefs.getString(PreferenceKeys.GIT_AUTHOR_EMAIL, "");
        }

        s.gitDefaultBranch = prefs.getString(PreferenceKeys.GIT_DEFAULT_BRANCH, s.gitDefaultBranch);
        s.gitAutoFetch = prefs.getBoolean(PreferenceKeys.GIT_AUTO_FETCH, s.gitAutoFetch);
        s.gitConfirmPush = prefs.getBoolean(PreferenceKeys.GIT_CONFIRM_PUSH, s.gitConfirmPush);
        s.gitConfirmHardReset = prefs.getBoolean(PreferenceKeys.GIT_CONFIRM_HARD_RESET, s.gitConfirmHardReset);
        s.gitShowFileTreeStatus = prefs.getBoolean(PreferenceKeys.GIT_SHOW_FILE_STATUS, s.gitShowFileTreeStatus);
        s.gitDefaultRemote = prefs.getString(PreferenceKeys.GIT_DEFAULT_REMOTE, s.gitDefaultRemote);

        // Visual Presentation modes
        String themeStr = prefs.getString(PreferenceKeys.THEME, s.theme.name());
        try {
            s.theme = AppSettings.Theme.valueOf(themeStr);
        } catch (Exception e) {
            s.theme = AppSettings.Theme.SYSTEM;
        }

        // Preview rendering options
        s.openPreviewInApp = prefs.getBoolean(PreferenceKeys.OPEN_PREVIEW_IN_APP, s.openPreviewInApp);
        s.autoRefreshPreview = prefs.getBoolean(PreferenceKeys.AUTO_REFRESH_PREVIEW, s.autoRefreshPreview);

        // Automation intervals
        s.autoSave = prefs.getBoolean(PreferenceKeys.AUTO_SAVE, s.autoSave);
        s.autoSaveDelay = prefs.getInt(PreferenceKeys.AUTO_SAVE_INTERVAL, s.autoSaveDelay);

        // Safety checkpoint parameters
        s.confirmOnTabClose = prefs.getBoolean(PreferenceKeys.CONFIRM_ON_TAB_CLOSE, s.confirmOnTabClose);
        s.confirmOnProjectDelete = prefs.getBoolean(PreferenceKeys.CONFIRM_ON_PROJECT_DEL, s.confirmOnProjectDelete);

        return s;
    }

    // --- Section: Save ---

    /**
     * Commits configuration values to local workspace preference maps.
     * Forwards identity declarations directly into version control secure storage blocks.
     */
    public void saveSettings(AppSettings s) {
        if (s == null) return;
        SharedPreferences.Editor ed = prefs.edit();

        ed.putInt(PreferenceKeys.FONT_SIZE, s.fontSize);
        ed.putInt(PreferenceKeys.TAB_SIZE, s.tabSize);
        ed.putBoolean(PreferenceKeys.SHOW_LINE_NUMBERS, s.showLineNumbers);
        ed.putBoolean(PreferenceKeys.AUTO_CLOSE_BRACKETS, s.autoCloseBrackets);
        ed.putBoolean(PreferenceKeys.AUTO_CLOSE_HTML_TAGS, s.autoCloseHtmlTags);
        ed.putBoolean(PreferenceKeys.HIGHLIGHT_CURRENT_LINE, s.highlightCurrentLine);
        ed.putBoolean(PreferenceKeys.AUTO_INDENT, s.autoIndent);
        ed.putBoolean(PreferenceKeys.MATCH_BRACKETS, s.matchBrackets);

        ed.putBoolean(PreferenceKeys.JSON_FORMAT_ON_SAVE, s.jsonFormatOnSave);
        ed.putBoolean(PreferenceKeys.JSON_VALIDATE_REALTIME, s.jsonValidateRealtime);

        ed.putString(PreferenceKeys.GIT_AUTHOR_NAME, s.gitAuthorName != null ? s.gitAuthorName : "");
        ed.putString(PreferenceKeys.GIT_AUTHOR_EMAIL, s.gitAuthorEmail != null ? s.gitAuthorEmail : "");
        ed.putString(PreferenceKeys.GIT_DEFAULT_BRANCH, s.gitDefaultBranch != null ? s.gitDefaultBranch : "main");
        ed.putBoolean(PreferenceKeys.GIT_AUTO_FETCH, s.gitAutoFetch);
        ed.putBoolean(PreferenceKeys.GIT_CONFIRM_PUSH, s.gitConfirmPush);
        ed.putBoolean(PreferenceKeys.GIT_CONFIRM_HARD_RESET, s.gitConfirmHardReset);
        ed.putBoolean(PreferenceKeys.GIT_SHOW_FILE_STATUS, s.gitShowFileTreeStatus);
        ed.putString(PreferenceKeys.GIT_DEFAULT_REMOTE, s.gitDefaultRemote != null ? s.gitDefaultRemote : "origin");

        ed.putString(PreferenceKeys.THEME, s.theme != null ? s.theme.name() : AppSettings.Theme.SYSTEM.name());

        ed.putBoolean(PreferenceKeys.OPEN_PREVIEW_IN_APP, s.openPreviewInApp);
        ed.putBoolean(PreferenceKeys.AUTO_REFRESH_PREVIEW, s.autoRefreshPreview);

        ed.putBoolean(PreferenceKeys.AUTO_SAVE, s.autoSave);
        ed.putInt(PreferenceKeys.AUTO_SAVE_INTERVAL, s.autoSaveDelay);

        ed.putBoolean(PreferenceKeys.CONFIRM_ON_TAB_CLOSE, s.confirmOnTabClose);
        ed.putBoolean(PreferenceKeys.CONFIRM_ON_PROJECT_DEL, s.confirmOnProjectDelete);

        ed.apply();

        // Synchronize author configurations directly into secure version control credential stores
        GitCredentialStore credentialStore = new GitCredentialStore();
        credentialStore.saveLocalAuthor(
                context,
                s.gitAuthorName != null ? s.gitAuthorName : "",
                s.gitAuthorEmail != null ? s.gitAuthorEmail : ""
        );
    }

    // --- Section: Last Project ---

    /**
     * Caches the identifier of the project most recently active in the developer workspace.
     */
    public void saveLastProjectId(String projectId) {
        prefs.edit().putString(PreferenceKeys.LAST_PROJECT_ID, projectId != null ? projectId : "").apply();
    }

    /**
     * Resolves the historical workspace tracking anchor to resume previous workspace sessions on launch.
     */
    public String loadLastProjectId() {
        return prefs.getString(PreferenceKeys.LAST_PROJECT_ID, "");
    }
}