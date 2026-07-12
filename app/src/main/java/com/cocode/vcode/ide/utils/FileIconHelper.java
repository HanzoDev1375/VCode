package com.cocode.vcode.ide.utils;

import android.content.Context;
import android.graphics.PorterDuff;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.core.model.FileType;

/**
 * Utility class for setting file icons and their corresponding colors.
 */
public class FileIconHelper {

    /**
     * Resolves and applies the appropriate icon and color for a file based on its extension.
     *
     * @param imageView The ImageView to set the icon and color on.
     * @param fileName  The name of the file to determine the icon for.
     */
    public static void setFileIconAndColor(ImageView imageView, String fileName) {
        if (imageView == null || fileName == null) {
            return;
        }

        String ext = FileUtils.getExtension(fileName.toLowerCase());
        FileType fileType = FileType.fromExtension(ext);

        imageView.setImageResource(fileType.getIconResId());
        applyFileTypeColor(imageView, fileType);
    }

    /**
     * Applies the appropriate color for a FileType to an ImageView's current drawable.
     */
    public static void applyFileTypeColor(ImageView imageView, FileType fileType) {
        if (imageView == null || fileType == null) return;
        Context context = imageView.getContext();
        imageView.setColorFilter(
                ContextCompat.getColor(context, fileType.getColorResId()),
                PorterDuff.Mode.SRC_IN
        );
    }
}
