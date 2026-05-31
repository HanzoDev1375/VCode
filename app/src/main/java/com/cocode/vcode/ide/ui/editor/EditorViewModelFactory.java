package com.cocode.vcode.ide.ui.editor;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.cocode.vcode.ide.data.repository.FileRepository;
import com.cocode.vcode.ide.data.repository.ProjectRepository;
import com.cocode.vcode.ide.data.repository.ProjectStateRepository;
import com.cocode.vcode.ide.data.repository.SettingsRepository;

/**
 * Factory class for creating instances of {@link EditorViewModel}.
 * It handles the injection of required repositories using the application context
 * to ensure ViewModels are decoupled from Activity lifecycles.
 */
public class EditorViewModelFactory implements ViewModelProvider.Factory {

    private final Context appContext;

    /**
     * Initializes the factory with a context to provide to repositories.
     *
     * @param context The context used to resolve application resources.
     */
    public EditorViewModelFactory(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        // Verify that the requested ViewModel class is indeed EditorViewModel
        if (modelClass.isAssignableFrom(EditorViewModel.class)) {
            // Manually inject dependencies into the ViewModel constructor
            return (T) new EditorViewModel(
                    new FileRepository(),
                    new ProjectStateRepository(),
                    new SettingsRepository(appContext),
                    new ProjectRepository(appContext)
            );
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}