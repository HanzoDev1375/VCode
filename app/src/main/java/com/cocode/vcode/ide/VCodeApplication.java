package com.cocode.vcode.ide;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.cocode.vcode.ide.data.model.AppSettings;
import com.cocode.vcode.ide.data.repository.SettingsRepository;

public class VCodeApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Force the theme to load the exact millisecond the app launches
        SettingsRepository repo = new SettingsRepository(this);
        AppSettings settings = repo.loadSettings();

        int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (settings.getTheme() == AppSettings.Theme.DARK) mode = AppCompatDelegate.MODE_NIGHT_YES;
        else if (settings.getTheme() == AppSettings.Theme.LIGHT) mode = AppCompatDelegate.MODE_NIGHT_NO;

        AppCompatDelegate.setDefaultNightMode(mode);
    }
}