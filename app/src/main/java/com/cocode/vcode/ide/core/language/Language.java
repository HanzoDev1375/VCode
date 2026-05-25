package com.cocode.vcode.ide.core.language;

import com.cocode.vcode.ide.R;

/**
 * Defines the core set of programming and markup languages supported by the vcode workspace.
 * Each language is bound to its canonical file extension, user-facing display name,
 * and specific UI branding/syntax highlighting color resources.
 */
public enum Language {

    HTML(R.drawable.ic_html_icon, R.color.vcode_lang_html,"html", "HTML"),
    CSS(R.drawable.ic_css_icon, R.color.vcode_lang_css, "css", "CSS"),
    JAVASCRIPT(R.drawable.ic_js_icon, R.color.vcode_lang_js, "js", "JavaScript"),
    JSON(R.drawable.ic_json_icon, R.color.vcode_lang_json, "json", "JSON"),
    TEXT(R.drawable.ic_file_lines, R.color.vcode_accent_primary, "txt", "Plain Text"),
    MARKDOWN(R.drawable.ic_md_icon, R.color.vcode_lang_md, "md", "Markdown");

    private final String extension;
    private final String displayName;
    private final int iconResId;
    private final int colorResId;

    /**
     * Internal constructor initialization to map file icon, extensions and display properties.
     */
    Language(int iconResId, int colorResId, String extension, String displayName) {
        this.iconResId = iconResId;
        this.colorResId = colorResId;
        this.extension = extension;
        this.displayName = displayName;
    }

    /**
     * Utility lookup factory that safely resolves an incoming raw file extension
     * string into its corresponding structured Language profile.
     * @param ext The raw file extension component (e.g., "js", "HTML").
     * @return The matching Language enum type, defaulting to TEXT if unmatched or empty.
     */
    public static Language fromExtension(String ext) {
        // Fallback gracefully to plain text context if no valid extension string is detected
        if (ext == null || ext.isEmpty()) return TEXT;
        ext = ext.toLowerCase();

        // Treat mjs and cjs exactly like standard JS for styling and editing
        if (ext.equals("js") || ext.equals("mjs") || ext.equals("cjs")) {
            return JAVASCRIPT;
        }

        // Search through the registered language dictionary for exact extension configurations
        for (Language l : values()) {
            if (l.extension.equalsIgnoreCase(ext)) return l;
        }

        // Default catch-all for unknown formats or unmapped file variants
        return TEXT;
    }

    public int getIconResId() {
        return iconResId;
    }

    public int getColorResId() {
        return colorResId;
    }

    /**
     * Gets the formatted, human-readable name of the language for workspace menus and file tabs.
     */
    public String getDisplayName() {
        return displayName;
    }

}