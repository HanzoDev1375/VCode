package com.cocode.vcode.ide.ui.base;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.repository.SettingsRepository;

/**
 * BaseActivity serves as the foundational activity for all screens in the VCode IDE.
 * It handles global configuration such as theme application and settings synchronization
 * to ensure a consistent user experience across the entire application.
 */
public abstract class BaseActivity extends AppCompatActivity {

    /**
     * Repository for accessing and persisting user settings.
     */
    protected SettingsRepository settingsRepo;

    /**
     * The current settings applied to this activity instance.
     */
    protected AppSettings currentSettings;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Initialize the settings repository to load user preferences
        settingsRepo = new SettingsRepository(this);

        // We apply the theme here, before the layout inflates, to avoid visual flickering
        applyGlobalSettings();

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-apply settings on resume in case the user changed them in the SettingsActivity
        // and then returned to this screen via the back button.
        applyGlobalSettings();
    }

    /**
     * Loads the latest settings and applies global UI configurations,
     * such as the application-wide theme (Dark/Light/System).
     */
    private void applyGlobalSettings() {
        currentSettings = settingsRepo.loadSettings();

        // Configure the Night Mode based on the user's theme preference
        int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (currentSettings.getTheme() == AppSettings.Theme.DARK) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        } else if (currentSettings.getTheme() == AppSettings.Theme.LIGHT) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        }
        AppCompatDelegate.setDefaultNightMode(mode);

        // Notify the child activity that settings have been applied so it can perform
        // its own specific UI updates if necessary.
        onSettingsApplied();
    }

    /**
     * Hook method called after global settings (like theme) have been applied.
     * Child activities can override this to perform specific UI updates,
     * such as updating font sizes or line wrapping in an editor.
     */
    protected void onSettingsApplied() {
        // Default implementation is empty; intended for overrides.
    }
}