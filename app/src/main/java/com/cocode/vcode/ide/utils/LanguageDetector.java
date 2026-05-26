package com.cocode.vcode.ide.utils;

import com.cocode.vcode.ide.core.model.FileType;

import java.util.regex.Pattern;

/**
 * Heuristic code text analyser designed to infer context models for unlabelled inputs.
 * Evaluates source signatures via regex to map snippets into their likely language families.
 */
public class LanguageDetector {

    // Grammar matching vectors checking tag structures, script blocks, and style properties
    private static final Pattern HTML_PATTERN = Pattern.compile("(?is).*<(!DOCTYPE|html|head|body|div|p|script|style|link|a|img|ul|li|span|br|h[1-6]).*>.*");
    private static final Pattern JS_KEYWORDS = Pattern.compile("(?s).*\\b(const|let|var|function|async|await|import|export|return|if|for|while|console\\.|document\\.|window\\.)\\b.*");
    private static final Pattern CSS_BLOCK = Pattern.compile("(?s).*[^{]+\\s*\\{\\s*[a-zA-Z-]+\\s*:\\s*[^;]+;?\\s*\\}.*");

    /**
     * Analyzes the textual content shapes to resolve the applicable system language type.
     * @param code The unmapped raw script text sequence.
     * @return The inferred destination Language enum configuration target.
     */
    public static FileType detect(String code) {
        if (code == null || code.trim().isEmpty()) return FileType.TEXT;

        String content = code.trim();

        // 1. Check for HTML elements signatures
        if (HTML_PATTERN.matcher(content).matches()) {
            return FileType.HTML;
        }

        // 2. Look for open and close braces configuration shapes descriptive of JSON dictionaries data fields
        if ((content.startsWith("{") && content.endsWith("}")) || (content.startsWith("[") && content.endsWith("]"))) {
            return FileType.JSON;
        }

        // 3. Look for standard CSS rule blocks assignments markers
        if (CSS_BLOCK.matcher(content).matches() || content.contains("@media")) {
            return FileType.CSS;
        }

        // 4. Trace programming keywords or functional arrow symbols common to script scopes
        if (JS_KEYWORDS.matcher(content).matches() || content.contains("=>")) {
            return FileType.JAVASCRIPT;
        }

        return FileType.TEXT; // Catch-all choice default fallback mode
    }
}