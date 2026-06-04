package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contextual suggestion engine for CSS — mirrors VS Code's CSS language server.
 *
 * <p>Key fixes vs previous version:
 * <ul>
 *   <li>{@code isInsideMediaQuery} is checked BEFORE the {@code @} rule check, so
 *       "@media (" triggers feature completions instead of at-rule completions.</li>
 *   <li>Pseudo-class items use {@code insertText} WITHOUT the leading {@code :} character.
 *       When user has already typed {@code p:}, inserting "hover" gives "p:hover" (not "p::hover").</li>
 *   <li>Pseudo-class detection checks if the char BEFORE the typed word is {@code :}
 *       so "p:hov" correctly filters to ":hover".</li>
 *   <li>The colon-preceded check also works for {@code ::} (pseudo-elements like {@code ::before}).</li>
 * </ul>
 */
public class CssAutoCompleteEngine extends AutoCompleteEngine {

    // ─── Pseudo-classes / pseudo-elements ─────────────────────────────────────
    // Label shows the full ":hover" syntax; insertText is WITHOUT the leading ":"
    // so clicking ":hover" after "a:" inserts "hover" → "a:hover" (not "a::hover").
    private static final List<CompletionItem> PSEUDO_ITEMS;

    // ─── At-rules ─────────────────────────────────────────────────────────────
    private static final List<CompletionItem> AT_RULE_ITEMS;

    // ─── @media features ──────────────────────────────────────────────────────
    private static final List<CompletionItem> MEDIA_FEATURE_ITEMS;

    static {
        // Format: { label (displayed), insertText (what is typed after ":"), detail }
        // insertText does NOT include the leading ":" — the user already typed it.
        String[][] pseudos = {
                {":hover",              "hover",              "Pseudo-class — mouse hover"},
                {":focus",              "focus",              "Pseudo-class — keyboard focus"},
                {":focus-within",       "focus-within",       "Pseudo-class — child has focus"},
                {":focus-visible",      "focus-visible",      "Pseudo-class — keyboard focus ring"},
                {":active",             "active",             "Pseudo-class — being clicked"},
                {":visited",            "visited",            "Pseudo-class — visited link"},
                {":link",               "link",               "Pseudo-class — unvisited link"},
                {":checked",            "checked",            "Pseudo-class — checked input"},
                {":disabled",           "disabled",           "Pseudo-class — disabled element"},
                {":enabled",            "enabled",            "Pseudo-class — enabled element"},
                {":placeholder-shown",  "placeholder-shown",  "Pseudo-class — placeholder visible"},
                {":required",           "required",           "Pseudo-class — required input"},
                {":optional",           "optional",           "Pseudo-class — optional input"},
                {":valid",              "valid",              "Pseudo-class — valid input value"},
                {":invalid",            "invalid",            "Pseudo-class — invalid input value"},
                {":read-only",          "read-only",          "Pseudo-class — read-only element"},
                {":read-write",         "read-write",         "Pseudo-class — editable element"},
                {":empty",              "empty",              "Pseudo-class — no children/text"},
                {":root",               "root",               "Pseudo-class — document root element"},
                {":first-child",        "first-child",        "Pseudo-class — first child"},
                {":last-child",         "last-child",         "Pseudo-class — last child"},
                {":first-of-type",      "first-of-type",      "Pseudo-class — first of its type"},
                {":last-of-type",       "last-of-type",       "Pseudo-class — last of its type"},
                {":only-child",         "only-child",         "Pseudo-class — only child"},
                {":only-of-type",       "only-of-type",       "Pseudo-class — only of its type"},
                {":nth-child()",        "nth-child(|)",       "Pseudo-class — nth child"},
                {":nth-last-child()",   "nth-last-child(|)",  "Pseudo-class — nth last child"},
                {":nth-of-type()",      "nth-of-type(|)",     "Pseudo-class — nth of type"},
                {":nth-last-of-type()", "nth-last-of-type(|)","Pseudo-class — nth last of type"},
                {":not()",              "not(|)",             "Pseudo-class — negation selector"},
                {":is()",               "is(|)",              "Pseudo-class — matches any selector"},
                {":where()",            "where(|)",           "Pseudo-class — zero-specificity match"},
                {":has()",              "has(|)",             "Pseudo-class — relational (parent) selector"},
                // Pseudo-elements — label uses "::" but insertText is ":" + name so only one extra colon needed
                // We detect "::" context separately via the double-colon check
                {"::before",            ":before",            "Pseudo-element — generated before content"},
                {"::after",             ":after",             "Pseudo-element — generated after content"},
                {"::placeholder",       ":placeholder",       "Pseudo-element — input placeholder"},
                {"::selection",         ":selection",         "Pseudo-element — user text selection"},
                {"::first-line",        ":first-line",        "Pseudo-element — first line of block"},
                {"::first-letter",      ":first-letter",      "Pseudo-element — first letter of block"},
                {"::marker",            ":marker",            "Pseudo-element — list item marker"},
                {"::backdrop",          ":backdrop",          "Pseudo-element — fullscreen backdrop"},
        };
        PSEUDO_ITEMS = new ArrayList<>();
        for (String[] p : pseudos) {
            String label      = p[0];
            String insertText = p[1]; // Without leading ":" — user typed it already
            String detail     = p[2];
            // Cursor inside () for parameterised pseudos (handled by pipe in insertText)
            PSEUDO_ITEMS.add(new CompletionItem(label, insertText, detail, CompletionItem.Type.CSS_VALUE, 0));
        }

        // At-rules — shown when line starts with "@" and we're NOT inside @media()
        String[][] atRules = {
                {"@media",        "@media "},
                {"@keyframes",    "@keyframes "},
                {"@import",       "@import '"},
                {"@font-face",    "@font-face "},
                {"@supports",     "@supports "},
                {"@charset",      "@charset '"},
                {"@layer",        "@layer "},
                {"@container",    "@container "},
                {"@property",     "@property --"},
                {"@counter-style","@counter-style "},
        };
        AT_RULE_ITEMS = new ArrayList<>();
        for (String[] r : atRules) {
            // Label = "@media" so fuzzy match works; insertText = "@media " with trailing space
            AT_RULE_ITEMS.add(new CompletionItem(r[0], r[1], "At-rule", CompletionItem.Type.CSS_VALUE, 0));
        }

        // @media features — shown inside @media ( … )
        String[][] mediaFeatures = {
                {"max-width",                 "max-width: |"},
                {"min-width",                 "min-width: |"},
                {"max-height",                "max-height: |"},
                {"min-height",                "min-height: |"},
                {"width",                     "width: |"},
                {"height",                    "height: |"},
                {"orientation: portrait",     "orientation: portrait"},
                {"orientation: landscape",    "orientation: landscape"},
                {"prefers-color-scheme: dark","prefers-color-scheme: dark"},
                {"prefers-color-scheme: light","prefers-color-scheme: light"},
                {"prefers-reduced-motion",    "prefers-reduced-motion: reduce"},
                {"hover: hover",              "hover: hover"},
                {"hover: none",               "hover: none"},
                {"pointer: fine",             "pointer: fine"},
                {"pointer: coarse",           "pointer: coarse"},
                {"display-mode: standalone",  "display-mode: standalone"},
                {"aspect-ratio",              "aspect-ratio: |"},
                {"resolution",                "resolution: |"},
        };
        MEDIA_FEATURE_ITEMS = new ArrayList<>();
        for (String[] mf : mediaFeatures) {
            MEDIA_FEATURE_ITEMS.add(new CompletionItem(mf[0], mf[1], "Media feature",
                    CompletionItem.Type.CSS_VALUE, 0));
        }
    }

    // ─── Instance state ─────────────────────────────────────────────────────────
    private final List<CompletionItem> propertyItems   = new ArrayList<>();
    private final Map<String, List<String>> valueMap   = new HashMap<>();
    private final List<CompletionItem> htmlTagItems     = new ArrayList<>();
    private final List<CompletionItem> colorItems       = new ArrayList<>();
    private final List<CompletionItem> globalValueItems = new ArrayList<>();

    private int lastTextHash = 0;
    private final List<CompletionItem> cachedCustomProps = new ArrayList<>();

    public CssAutoCompleteEngine(Context context) {
        super(context);
        loadProperties();
        loadHtmlTags();
        loadColors();
    }

    // ─── Asset loading ─────────────────────────────────────────────────────────

    private void loadColors() {
        try {
            String json = loadAssetJson("completions/css_colors.json");
            JSONObject obj = new JSONObject(json);
            JSONArray colors = obj.optJSONArray("colors");
            if (colors != null) {
                for (int i = 0; i < colors.length(); i++) {
                    String c = colors.optString(i);
                    colorItems.add(new CompletionItem(c, c, "Named color", CompletionItem.Type.CSS_VALUE, 0));
                }
            }
            JSONArray colorFuncs = obj.optJSONArray("color_functions");
            if (colorFuncs != null) {
                for (int i = 0; i < colorFuncs.length(); i++) {
                    String f = colorFuncs.optString(i);
                    colorItems.add(new CompletionItem(f, f, "Color function", CompletionItem.Type.CSS_VALUE, 0));
                }
            }
            JSONArray globalFuncs = obj.optJSONArray("global_functions");
            if (globalFuncs != null) {
                for (int i = 0; i < globalFuncs.length(); i++) {
                    String f = globalFuncs.optString(i);
                    globalValueItems.add(new CompletionItem(f, f, "Function", CompletionItem.Type.CSS_VALUE, 0));
                }
            }
        } catch (Exception e) { /* Non-critical */ }
    }

    private void loadHtmlTags() {
        try {
            String json = loadAssetJson("completions/html_tags.json");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String tag = obj.optString("tag");
                htmlTagItems.add(new CompletionItem(tag, tag + " ", obj.optString("detail", "HTML Element"),
                        CompletionItem.Type.TAG, 0));
            }
        } catch (Exception e) { /* Non-critical */ }
    }

    private void loadProperties() {
        try {
            String json = loadAssetJson("completions/css_properties.json");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String property = obj.optString("property");
                String detail   = obj.optString("detail", "");
                JSONArray values = obj.optJSONArray("values");

                // Cursor between colon and semicolon after selection
                propertyItems.add(new CompletionItem(property, property + ": |;",
                        detail, CompletionItem.Type.CSS_PROPERTY, 0));

                if (values != null) {
                    List<String> vals = new ArrayList<>();
                    for (int j = 0; j < values.length(); j++) vals.add(values.optString(j));
                    valueMap.put(property, vals);
                }
            }
        } catch (Exception e) { /* Non-critical */ }
    }

    // ─── Custom property scanning ──────────────────────────────────────────────

    private void ensureCustomPropsIndexed(String text) {
        int hash = text.hashCode();
        if (hash == lastTextHash) return;
        lastTextHash = hash;
        cachedCustomProps.clear();

        Matcher m = Pattern.compile("(--[a-zA-Z][\\w-]*)\\s*:").matcher(text);
        int limit = Math.min(text.length(), 200_000);
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (m.find() && m.start() < limit) {
            String prop = m.group(1);
            if (seen.add(prop)) {
                cachedCustomProps.add(new CompletionItem(prop, prop, "Custom property",
                        CompletionItem.Type.CSS_PROPERTY, 0));
            }
        }
    }

    // ─── Main entry points ─────────────────────────────────────────────────────

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        return getSuggestions(fullText, cursorPos, false);
    }

    public List<CompletionItem> getSuggestions(String fullText, int cursorPos, boolean isInlineStyle) {
        if (fullText == null || cursorPos < 0) return new ArrayList<>();

        if (isInsideStringLiteral(fullText, cursorPos)) {
            return new ArrayList<>();
        }

        ensureCustomPropsIndexed(fullText);

        String line    = getLineBeforeCursor(fullText, cursorPos);
        String trimmed = line.trim();
        String word    = getWordBeforeCursor(fullText, cursorPos);

        // ── 1. @media feature completions — MUST come BEFORE the "@" check ────
        // Without this ordering, "@media (" would be caught by the "@" check and
        // show at-rule completions instead of media features.
        if (isInsideMediaQuery(fullText, cursorPos)) {
            return fuzzyFilter(MEDIA_FEATURE_ITEMS, word);
        }

        // ── 2. At-rule completions — triggered when line starts with "@" ───────
        if (trimmed.startsWith("@")) {
            // Strip the "@" for matching (AT_RULE_ITEMS labels start with "@")
            String atWord = trimmed.startsWith("@") ? "@" + word : word;
            return fuzzyFilter(AT_RULE_ITEMS, atWord);
        }

        // ── 3. Pseudo-class / pseudo-element completions ─────────────────────
        // Triggered when:
        //   a) The typed word is preceded immediately by ":" (e.g. user typed "p:hov")
        //   b) The cursor is right after ":" (word is empty, but last char is ":")
        //
        // INSERTION FIX: PSEUDO_ITEMS use insertText WITHOUT the leading ":" so that
        // clicking ":hover" after "a:" inserts "hover" → "a:hover" (not "a::hover").
        int wordStartPos = cursorPos - word.length();
        boolean directlyAfterColon = wordStartPos > 0 && fullText.charAt(wordStartPos - 1) == ':';
        boolean cursorRightAfterColon = word.isEmpty() && cursorPos > 0
                && fullText.charAt(cursorPos - 1) == ':';

        if (directlyAfterColon || cursorRightAfterColon) {
            // Only show pseudo-classes in selector zone (outside rule blocks), UNLESS
            // the block contains nested rules (e.g. SASS/modern CSS nesting)
            return fuzzyFilter(PSEUDO_ITEMS, word);
        }

        // ── 4. State-machine context detection ─────────────────────────────────
        CssContext ctx = detectContext(fullText, cursorPos, isInlineStyle);

        switch (ctx.zone) {
            case VALUE:    return getValueSuggestions(ctx.propertyName, word, fullText);
            case PROPERTY: return getPropertySuggestions(word, fullText, cursorPos);
            case SELECTOR:
            default:       return getSelectorSuggestions(word, trimmed);
        }
    }

    // ─── Context detection ─────────────────────────────────────────────────────

    private enum Zone { SELECTOR, PROPERTY, VALUE }

    private static class CssContext {
        Zone zone = Zone.SELECTOR;
        String propertyName = "";
    }

    private CssContext detectContext(String text, int cursorPos, boolean isInlineStyle) {
        CssContext ctx = new CssContext();

        if (isInlineStyle) {
            ctx.zone = detectDeclarationZone(text, cursorPos);
            if (ctx.zone == Zone.VALUE) {
                ctx.propertyName = extractPropertyBeforeColon(getLineBeforeCursor(text, cursorPos));
            }
            return ctx;
        }

        // Count braces up to cursor
        int open = 0, close = 0;
        for (int i = 0; i < cursorPos && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') open++;
            else if (c == '}') close++;
        }

        if (open <= close) {
            ctx.zone = Zone.SELECTOR;
            return ctx;
        }

        ctx.zone = detectDeclarationZone(text, cursorPos);
        if (ctx.zone == Zone.VALUE) {
            ctx.propertyName = extractPropertyBeforeColon(getLineBeforeCursor(text, cursorPos));
        }
        return ctx;
    }

    /**
     * Within a declaration block, determines if cursor is in property-name or value zone.
     * Finds the last meaningful separator (;, {, or } ) then checks if there's a colon after it.
     */
    private Zone detectDeclarationZone(String text, int cursorPos) {
        String line = getLineBeforeCursor(text, cursorPos);
        // Isolate the current declaration (after last ; or {)
        int blockStart = Math.max(line.lastIndexOf(';'), line.lastIndexOf('{'));
        String currentDecl = blockStart >= 0 ? line.substring(blockStart + 1) : line;
        // Also handle inline styles with multiple properties: split on ';'
        int semiInDecl = currentDecl.lastIndexOf(';');
        if (semiInDecl >= 0) currentDecl = currentDecl.substring(semiInDecl + 1);

        return currentDecl.contains(":") ? Zone.VALUE : Zone.PROPERTY;
    }

    private String extractPropertyBeforeColon(String line) {
        int blockStart = Math.max(line.lastIndexOf(';'), line.lastIndexOf('{'));
        String decl = blockStart >= 0 ? line.substring(blockStart + 1) : line;
        int colon = decl.lastIndexOf(':');
        return colon < 0 ? "" : decl.substring(0, colon).trim();
    }

    // ─── Zone-specific suggestion builders ────────────────────────────────────

    private List<CompletionItem> getValueSuggestions(String propertyName, String word, String fullText) {
        // Emmet CSS shorthand in value position (e.g. "bgc" → "background-color: #|;")
        String expanded = EmmetParser.expandCss(word);
        if (expanded != null) {
            List<CompletionItem> res = new ArrayList<>();
            CompletionItem emmetItem = new CompletionItem(word, expanded, "Emmet", CompletionItem.Type.SNIPPET, 0);
            emmetItem.setReplaceLength(word.length());
            res.add(emmetItem);
            return res;
        }

        List<CompletionItem> items = new ArrayList<>();

        // var(--…) completions
        if (word.startsWith("--") || word.equals("var")) {
            items.addAll(cachedCustomProps);
        }

        // Property-specific enumerated values
        List<String> vals = valueMap.get(propertyName);
        if (vals != null) {
            for (String v : vals) {
                items.add(new CompletionItem(v, v, propertyName, CompletionItem.Type.CSS_VALUE, 0));
            }
        }

        items.addAll(globalValueItems);

        if (isColorProperty(propertyName)) {
            items.addAll(colorItems);
        }

        return fuzzyFilter(items, word);
    }

    private List<CompletionItem> getPropertySuggestions(String word, String fullText, int cursorPos) {
        // Emmet CSS shorthand (e.g. "m10" → "margin: 10px;")
        String emmetAbbr = getEmmetAbbreviationBeforeCursor(fullText, cursorPos);
        String expanded  = EmmetParser.expandCss(emmetAbbr);
        if (expanded != null) {
            List<CompletionItem> res = new ArrayList<>();
            CompletionItem emmetItem = new CompletionItem(emmetAbbr, expanded, "Emmet", CompletionItem.Type.SNIPPET, 0);
            emmetItem.setReplaceLength(emmetAbbr.length());
            res.add(emmetItem);
            return res;
        }

        List<CompletionItem> all = new ArrayList<>(propertyItems);
        all.addAll(cachedCustomProps);
        return fuzzyFilter(all, word);
    }

    private List<CompletionItem> getSelectorSuggestions(String word, String trimmed) {
        if (word.startsWith(":")) {
            return fuzzyFilter(PSEUDO_ITEMS, word.substring(1)); // strip ":" since insertTexts don't have it
        }
        return fuzzyFilter(htmlTagItems, word);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private boolean isColorProperty(String prop) {
        if (prop == null) return false;
        String p = prop.toLowerCase();
        return p.endsWith("color") || p.equals("background") || p.equals("fill")
                || p.equals("stroke") || p.equals("outline") || p.equals("border")
                || p.endsWith("shadow") || p.equals("caret-color") || p.equals("accent-color");
    }

    /**
     * Returns true if the cursor is inside an unclosed @media ( … ) feature list.
     * Scans backward up to 300 chars for "@media" then counts unbalanced parentheses.
     */
    private boolean isInsideMediaQuery(String text, int cursorPos) {
        int start = Math.max(0, cursorPos - 300);
        String snippet = text.substring(start, cursorPos);
        int mediaIdx = snippet.lastIndexOf("@media");
        if (mediaIdx < 0) return false;
        String afterMedia = snippet.substring(mediaIdx);
        // Check for unclosed ( after @media
        int open = 0, close = 0;
        for (char c : afterMedia.toCharArray()) {
            if (c == '(') open++;
            else if (c == ')') close++;
        }
        return open > close;
    }

    private boolean isInsideRuleBlock(String text, int pos) {
        int open = 0, close = 0;
        for (int i = 0; i < pos && i < text.length(); i++) {
            if (text.charAt(i) == '{') open++;
            if (text.charAt(i) == '}') close++;
        }
        return open > close;
    }
}