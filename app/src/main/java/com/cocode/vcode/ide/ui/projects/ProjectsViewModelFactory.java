package com.cocode.vcode.ide.ui.projects;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.data.repository.SettingsRepository;

/**
 * Factory class for creating instances of {@link ProjectsViewModel}.
 * It manages the dependency injection of the ProjectRepository and SettingsRepository.
 */
public class ProjectsViewModelFactory implements ViewModelProvider.Factory {

    private final Context appContext;

    /**
     * Initializes the factory with the application context.
     *
     * @param context The context used for repository initialization.
     */
    public ProjectsViewModelFactory(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        // Instantiate ProjectsViewModel with the required data repositories
        if (modelClass.isAssignableFrom(ProjectsViewModel.class)) {
            return (T) new ProjectsViewModel(
                    new ProjectRepository(appContext),
                    new SettingsRepository(appContext));
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}