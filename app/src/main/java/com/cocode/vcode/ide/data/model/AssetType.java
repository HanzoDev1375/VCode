package com.cocode.vcode.ide.data.model;

import com.cocode.vcode.ide.R;
import java.util.Arrays;
import java.util.List;

/**
 * Registers non-compilable project components and binary files.
 * Maps file extension signatures to dedicated icon representations, contextual theme color accents,
 * and handles routing decisions between text-based code workspaces and rich binary preview viewers.
 */
public enum AssetType {
    IMAGE(R.drawable.ic_image_icon, R.color.vcode_file_img, "png", "jpg", "jpeg", "webp"),
    GIF(R.drawable.ic_image_icon, R.color.vcode_file_gif, "gif"),
    SVG(R.drawable.ic_bezier_curve, R.color.vcode_file_svg, "svg"),
    ICO(R.drawable.ic_image_icon, R.color.vcode_file_ico, "ico"),
    BMP(R.drawable.ic_image_icon, R.color.vcode_file_bmp, "bmp"),
    FONT(R.drawable.ic_font_icon, R.color.vcode_file_font, "woff", "woff2", "ttf", "otf", "eot"),
    AUDIO(R.drawable.ic_audio_icon, R.color.vcode_file_music, "mp3", "wav", "ogg"),
    VIDEO(R.drawable.ic_video_icon, R.color.vcode_file_video, "mp4", "webm", "mov", "avi"),
    CSV(R.drawable.ic_csv_icon, R.color.vcode_file_csv, "csv"),
    MANIFEST(R.drawable.ic_gear, R.color.vcode_file_web_manifest, "webmanifest"),
    ENV(R.drawable.ic_env_icon, R.color.vcode_file_env, "env", "local"),
    FIREBASE(R.drawable.ic_firebase_icon, R.color.vcode_file_firebase, "firebaserc", "rules"),
    PDF(R.drawable.ic_pdf_icon, R.color.vcode_file_pdf, "pdf"),
    LOG(R.drawable.ic_log_icon, R.color.vcode_file_log, "log"),
    BAK(R.drawable.ic_clock_rotate, R.color.vcode_file_bak, "bak");

    private final int iconResId;
    private final int colorResId;
    private final List<String> extensions;

    /**
     * Internal constructor setting up resource associations with extension maps.
     */
    AssetType(int iconResId, int colorResId, String... extensions) {
        this.iconResId = iconResId;
        this.colorResId = colorResId;
        this.extensions = Arrays.asList(extensions);
    }

    /**
     * Inspects a raw file extension suffix string to identify its asset classification profile.
     * @param ext The raw extension string pulled from the file name.
     * @return The matching AssetType configuration enum, or null if it falls under code/text scopes instead.
     */
    public static AssetType fromExtension(String ext) {
        if (ext == null || ext.isEmpty()) return null;
        ext = ext.toLowerCase();
        for (AssetType type : values()) {
            if (type.extensions.contains(ext)) {
                return type;
            }
        }
        return null; // Not an asset; signals the loader to treat it as a candidate for programming languages
    }

    public int getIconResId() {
        return iconResId;
    }

    public int getColorResId() {
        return colorResId;
    }

    /**
     * Flags whether an asset is composed of editable plain text data streams rather than raw binary data bytes.
     * Decides if the file can be opened inside a text field instead of launching specialized graphic views.
     */
    public boolean isTextBased() {
        return this == CSV || this == ENV || this == LOG || this == BAK || this == MANIFEST || this == SVG || this == FIREBASE;
    }
}