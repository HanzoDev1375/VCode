package com.cocode.vcode.ide.core.language;

import com.cocode.vcode.ide.R;

/**
 * Defines the core set of programming and markup languages supported by the vcode workspace.
 * Each language is bound to its canonical file extension, user-facing display name,
 * and specific UI branding/syntax highlighting color resources.
 */
public enum Language {

    HTML("html", "HTML"),
    CSS("css", "CSS"),
    JAVASCRIPT("js", "JavaScript"),
    JSON("json", "JSON"),
    TEXT("txt", "Plain Text"),
    MARKDOWN("md", "Markdown");

    private final String extension;
    private final String displayName;

    /**
     * Internal constructor initialization to pair file extensions with display properties.
     */
    Language(String extension, String displayName) {
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

    /**
     * Gets the formatted, human-readable name of the language for workspace menus and file tabs.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Resolves the specialized color value resource mapped to this specific platform syntax type.
     * Useful for dynamic token styling, tab badges, or sidebar folder icons.
     * @return The Android Color resource reference ID (e.g., R.color.vcode_lang_html).
     */
    public int getColorResId() {
        switch (this) {
            case HTML:
                return R.color.vcode_lang_html;
            case CSS:
                return R.color.vcode_lang_css;
            case JAVASCRIPT:
                return R.color.vcode_lang_js;
            case JSON:
                return R.color.vcode_lang_json;
            case MARKDOWN:
                return R.color.vcode_lang_md;
            default:
                // Universal theme accent color for non-code files or unparsed flat documents
                return R.color.vcode_text_secondary;
        }
    }
}