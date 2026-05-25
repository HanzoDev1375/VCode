package com.cocode.vcode.ide.utils;

import android.content.Context;
import android.graphics.PorterDuff;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.cocode.vcode.ide.core.language.Language;
import com.cocode.vcode.ide.data.model.AssetType;

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

        Context context = imageView.getContext();
        String ext = FileUtils.getExtension(fileName.toLowerCase());
        AssetType assetType = AssetType.fromExtension(ext);

        if (assetType != null) {
            imageView.setImageResource(assetType.getIconResId());
            imageView.setColorFilter(
                    ContextCompat.getColor(context, assetType.getColorResId()),
                    PorterDuff.Mode.SRC_IN
            );
        } else {
            Language lang = Language.fromExtension(ext);
            imageView.setImageResource(lang.getIconResId());
            imageView.setColorFilter(
                    ContextCompat.getColor(context, lang.getColorResId()),
                    PorterDuff.Mode.SRC_IN
            );
        }
    }
}
