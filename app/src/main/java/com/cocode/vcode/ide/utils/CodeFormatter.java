package com.cocode.vcode.ide.utils;

import com.cocode.vcode.ide.core.formatter.BaseFormatter;
import com.cocode.vcode.ide.core.formatter.CssFormatter;
import com.cocode.vcode.ide.core.formatter.HtmlFormatter;
import com.cocode.vcode.ide.core.formatter.JsFormatter;
import com.cocode.vcode.ide.core.formatter.JsonFormatter;
import com.cocode.vcode.ide.core.language.Language;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central routing service for source code formatting.
 * Matches specific programming languages with their corresponding
 * base formatter implementation to handle formatting requests.
 */
public class CodeFormatter {

    // An optimized map lookup structure mapped to language enum keys
    private static final Map<Language, BaseFormatter> FORMATTERS = new EnumMap<>(Language.class);

    static {
        FORMATTERS.put(Language.JSON, new JsonFormatter());
        FORMATTERS.put(Language.HTML, new HtmlFormatter());
        FORMATTERS.put(Language.CSS, new CssFormatter());
        FORMATTERS.put(Language.JAVASCRIPT, new JsFormatter());
    }

    /**
     * Formats incoming source text block based on target language style standards.
     * @param code The unformatted raw code text block.
     * @param language The targeted language configuration identifier.
     * @return The beautified structural code string.
     */
    public static String format(String code, Language language) {
        // Return original string immediately if empty or null to avoid unneeded allocation steps
        if (code == null || code.trim().isEmpty()) return code;

        try {
            BaseFormatter formatter = FORMATTERS.get(language);
            if (formatter != null) {
                return formatter.format(code);
            }
            return code; // Return unmodified string if language does not have a dedicated formatter class
        } catch (Exception e) {
            e.printStackTrace();
            return code; // Structural fallback to protect data integrity on invalid syntax states
        }
    }
}