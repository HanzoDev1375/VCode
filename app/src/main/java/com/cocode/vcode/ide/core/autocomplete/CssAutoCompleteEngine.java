package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
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

    static {
        // Format: { label (displayed), insertText (what is typed after ":"), detail }
        // insertText does NOT include the leading ":" — the user already typed it.
        String[][] pseudos = {
                {":hover", "hover", "Pseudo-class — mouse hover"},
                {":focus", "focus", "Pseudo-class — keyboard focus"},
                {":focus-within", "focus-within", "Pseudo-class — child has focus"},
                {":focus-visible", "focus-visible", "Pseudo-class — keyboard focus ring"},
                {":active", "active", "Pseudo-class — being clicked"},
                {":visited", "visited", "Pseudo-class — visited link"},
                {":link", "link", "Pseudo-class — unvisited link"},
                {":checked", "checked", "Pseudo-class — checked input"},
                {":disabled", "disabled", "Pseudo-class — disabled element"},
                {":enabled", "enabled", "Pseudo-class — enabled element"},
                {":placeholder-shown", "placeholder-shown", "Pseudo-class — placeholder visible"},
                {":required", "required", "Pseudo-class — required input"},
                {":optional", "optional", "Pseudo-class — optional input"},
                {":valid", "valid", "Pseudo-class — valid input value"},
                {":invalid", "invalid", "Pseudo-class — invalid input value"},
                {":read-only", "read-only", "Pseudo-class — read-only element"},
                {":read-write", "read-write", "Pseudo-class — editable element"},
                {":empty", "empty", "Pseudo-class — no children/text"},
                {":root", "root", "Pseudo-class — document root element"},
                {":first-child", "first-child", "Pseudo-class — first child"},
                {":last-child", "last-child", "Pseudo-class — last child"},
                {":first-of-type", "first-of-type", "Pseudo-class — first of its type"},
                {":last-of-type", "last-of-type", "Pseudo-class — last of its type"},
                {":only-child", "only-child", "Pseudo-class — only child"},
                {":only-of-type", "only-of-type", "Pseudo-class — only of its type"},
                {":nth-child()", "nth-child(|)", "Pseudo-class — nth child"},
                {":nth-last-child()", "nth-last-child(|)", "Pseudo-class — nth last child"},
                {":nth-of-type()", "nth-of-type(|)", "Pseudo-class — nth of type"},
                {":nth-last-of-type()", "nth-last-of-type(|)", "Pseudo-class — nth last of type"},
                {":not()", "not(|)", "Pseudo-class — negation selector"},
                {":is()", "is(|)", "Pseudo-class — matches any selector"},
                {":where()", "where(|)", "Pseudo-class — zero-specificity match"},
                {":has()", "has(|)", "Pseudo-class — relational (parent) selector"},
                // Pseudo-elements — label uses "::" but insertText is ":" + name so only one extra colon needed
                // We detect "::" context separately via the double-colon check
                {"::before", ":before", "Pseudo-element — generated before content"},
                {"::after", ":after", "Pseudo-element — generated after content"},
                {"::placeholder", ":placeholder", "Pseudo-element — input placeholder"},
                {"::selection", ":selection", "Pseudo-element — user text selection"},
                {"::first-line", ":first-line", "Pseudo-element — first line of block"},
                {"::first-letter", ":first-letter", "Pseudo-element — first letter of block"},
                {"::marker", ":marker", "Pseudo-element — list item marker"},
                {"::backdrop", ":backdrop", "Pseudo-element — fullscreen backdrop"},
        };
        PSEUDO_ITEMS = new ArrayList<>();
        for (String[] p : pseudos) {
            String label = p[0];
            String insertText = p[1]; // Without leading ":" — user typed it already
            String detail = p[2];
            // Cursor inside () for parameterised pseudos (handled by pipe in insertText)
            PSEUDO_ITEMS.add(new CompletionItem(label, insertText, detail, CompletionItem.Type.CSS_VALUE, 0));
        }

        // At-rules — shown when line starts with "@" and we're NOT inside @media()
        String[][] atRules = {
                {"@media", "@media "},
                {"@keyframes", "@keyframes "},
                {"@import", "@import '"},
                {"@font-face", "@font-face "},
                {"@supports", "@supports "},
                {"@charset", "@charset '"},
                {"@layer", "@layer "},
                {"@container", "@container "},
                {"@property", "@property --"},
                {"@counter-style", "@counter-style "},
        };
        AT_RULE_ITEMS = new ArrayList<>();
        for (String[] r : atRules) {
            // Label = "@media" so fuzzy match works; insertText = "@media " with trailing space
            AT_RULE_ITEMS.add(new CompletionItem(r[0], r[1], "At-rule", CompletionItem.Type.CSS_VALUE, 0));
        }
    }

    // ─── Instance state ─────────────────────────────────────────────────────────
    private final List<CompletionItem> propertyItems = new ArrayList<>();
    private final Map<String, List<String>> valueMap = new HashMap<>();
    private final List<CompletionItem> htmlTagItems = new ArrayList<>();
    private final List<CompletionItem> colorItems = new ArrayList<>();
    private final List<CompletionItem> globalValueItems = new ArrayList<>();
    private final List<CompletionItem> cachedCustomProps = new ArrayList<>();
    private final FastTrie propertyTrie = new FastTrie();
    private int lastTextHash = 0;

    public CssAutoCompleteEngine(Context context) {
        super(context);
        loadProperties();
        loadHtmlTags();
        loadColors();
    }

    public void setCurrentFile(File file) {
        File projectRoot = ProjectSymbolIndex.getProjectRoot(file);
        if (projectRoot != null) {
            ProjectSymbolIndex.getInstance().buildIndex(projectRoot);
        }
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
                String detail = obj.optString("detail", "");
                JSONArray values = obj.optJSONArray("values");

                // Cursor between colon and semicolon after selection
                CompletionItem item = new CompletionItem(property, property + ": |;",
                        detail, CompletionItem.Type.CSS_PROPERTY, 0);
                propertyItems.add(item);
                propertyTrie.insert(item);

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

        String line = getLineBeforeCursor(fullText, cursorPos);
        String trimmed = line.trim();
        String word = getWordBeforeCursor(fullText, cursorPos);

        // Prevent showing all suggestions immediately after typing { or }
        // We only want to show suggestions when the user hits Enter (trimmed will be empty)
        // or actually starts typing a word.
        if (word.isEmpty()) {
            if (trimmed.endsWith("{") || trimmed.endsWith("}")) {
                return new ArrayList<>();
            }
        }

        // ── 1. @media/@supports/@container CONDITION completions ───────────────
        // Only triggers when writing the condition BEFORE the opening brace.
        if (isInsideAtRuleCondition(fullText, cursorPos)) {
            return getMediaSuggestions(fullText, cursorPos, word);
        }

        // ── 2. At-rule completions — triggered when line starts with "@" ───────
        // Only at top-level (depth 0) or inside an at-rule body (depth 1 from @media etc.)
        if (trimmed.startsWith("@")) {
            String atWord = "@" + word;
            return fuzzyFilter(AT_RULE_ITEMS, atWord);
        }

        // ── 3. Pseudo-class / pseudo-element completions ─────────────────────
        int wordStartPos = cursorPos - word.length();
        boolean directlyAfterColon = wordStartPos > 0 && fullText.charAt(wordStartPos - 1) == ':';
        boolean cursorRightAfterColon = word.isEmpty() && cursorPos > 0
                && fullText.charAt(cursorPos - 1) == ':';

        if (directlyAfterColon || cursorRightAfterColon) {
            return fuzzyFilter(PSEUDO_ITEMS, word);
        }

        // ── 4. Intelligent context detection with nesting awareness ────────────
        CssContext ctx = detectContext(fullText, cursorPos, isInlineStyle);

        switch (ctx.zone) {
            case VALUE:
                return getValueSuggestions(ctx.propertyName, word, fullText);
            case PROPERTY:
                return getPropertySuggestions(word, fullText, cursorPos);
            case NESTED_SELECTOR:
                // Inside a CSS nesting block — show BOTH selectors and properties
                return getNestedSuggestions(word, trimmed, fullText, cursorPos);
            case SELECTOR:
            default:
                return getSelectorSuggestions(word, trimmed);
        }
    }

    // ─── Context detection ─────────────────────────────────────────────────────

    /**
     * Detects context with full nesting depth awareness.
     *
     * <p>Depth semantics:
     * <ul>
     *   <li>depth 0 → top-level (selectors only)</li>
     *   <li>depth 1 → inside first rule block OR inside @media body (selectors + properties)</li>
     *   <li>depth 2+ → nested CSS rule OR rule inside @media (selectors + properties)</li>
     * </ul>
     *
     * <p>Inside an at-rule body (@media, @supports, @container, @layer), depth 1 means
     * "inside the at-rule but not inside a selector block" → show selectors.
     * Depth 2 means "inside a selector block within the at-rule" → show properties.
     */
    private CssContext detectContext(String text, int cursorPos, boolean isInlineStyle) {
        CssContext ctx = new CssContext();

        if (isInlineStyle) {
            ctx.zone = detectDeclarationZone(text, cursorPos);
            if (ctx.zone == Zone.VALUE) {
                ctx.propertyName = extractPropertyBeforeColon(getLineBeforeCursor(text, cursorPos));
            }
            return ctx;
        }

        // Compute nesting depth and track at-rule context
        int depth = 0;
        boolean insideAtRuleBody = false;
        int atRuleDepth = -1; // the depth at which the at-rule was opened

        for (int i = 0; i < cursorPos && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                // Check if this brace opens an at-rule body
                if (!insideAtRuleBody && isAtRuleBraceAt(text, i)) {
                    insideAtRuleBody = true;
                    atRuleDepth = depth;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth < 0) depth = 0;
                // If we've closed back to the at-rule level, we're no longer in it
                if (insideAtRuleBody && depth <= atRuleDepth) {
                    insideAtRuleBody = false;
                    atRuleDepth = -1;
                }
            }
        }

        if (depth == 0) {
            ctx.zone = Zone.SELECTOR;
            return ctx;
        }

        // Check if on the current line we're writing a value (has colon after last separator)
        Zone declZone = detectDeclarationZone(text, cursorPos);
        if (declZone == Zone.VALUE) {
            ctx.zone = Zone.VALUE;
            ctx.propertyName = extractPropertyBeforeColon(getLineBeforeCursor(text, cursorPos));
            return ctx;
        }

        // Determine what zone based on depth and at-rule context
        if (insideAtRuleBody && depth == atRuleDepth + 1) {
            // Inside at-rule body but NOT inside a selector block → show selectors
            // e.g. @media (max-width: 768px) { HERE }
            ctx.zone = Zone.SELECTOR;
        } else if (depth == 1 && !insideAtRuleBody) {
            // Inside a regular rule block at depth 1 (e.g. .class { HERE })
            // Modern CSS allows nesting, so show both
            ctx.zone = Zone.NESTED_SELECTOR;
        } else if (depth >= 2) {
            // Deeper nesting — could be nested rule or property
            ctx.zone = Zone.NESTED_SELECTOR;
        } else {
            ctx.zone = Zone.PROPERTY;
        }

        return ctx;
    }

    /**
     * Checks whether the opening brace at position {@code bracePos} belongs to an at-rule
     * (@media, @supports, @container, @layer, @keyframes, @font-face).
     */
    private boolean isAtRuleBraceAt(String text, int bracePos) {
        // Walk backward from the brace to find the most recent @ symbol
        int searchStart = Math.max(0, bracePos - 200);
        String before = text.substring(searchStart, bracePos);
        // Find last unmatched at-rule (no { between it and our brace)
        int lastAt = before.lastIndexOf('@');
        if (lastAt < 0) return false;
        // Check there's no other { between the @rule and this brace
        String between = before.substring(lastAt);
        if (between.indexOf('{') >= 0) return false;
        // Verify it's actually an at-rule keyword
        Matcher m = Pattern.compile("@(media|supports|container|layer|keyframes|font-face|property|counter-style)\\b")
                .matcher(between);
        return m.find();
    }

    /**
     * Within a declaration block, determines if cursor is in property-name or value zone.
     */
    private Zone detectDeclarationZone(String text, int cursorPos) {
        // Look back from cursor to find the start of the current declaration
        int i = cursorPos - 1;
        while (i >= 0) {
            char c = text.charAt(i);
            if (c == ';' || c == '{' || c == '}') break;
            i--;
        }
        // Check if there's a colon in the current declaration segment
        String currentDecl = text.substring(i + 1, cursorPos);
        // Ignore colons inside url() or other function calls
        int colonIdx = -1;
        int parenDepth = 0;
        for (int j = 0; j < currentDecl.length(); j++) {
            char c = currentDecl.charAt(j);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (c == ':' && parenDepth == 0) {
                colonIdx = j;
                break;
            }
        }
        return colonIdx >= 0 ? Zone.VALUE : Zone.PROPERTY;
    }

    private String extractPropertyBeforeColon(String line) {
        // Walk backward from cursor on the line to find colon, ignoring function parens
        int blockStart = -1;
        for (int i = line.length() - 1; i >= 0; i--) {
            char c = line.charAt(i);
            if (c == ';' || c == '{') {
                blockStart = i;
                break;
            }
        }
        String decl = blockStart >= 0 ? line.substring(blockStart + 1) : line;
        int colon = -1;
        int parenDepth = 0;
        for (int i = 0; i < decl.length(); i++) {
            char c = decl.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (c == ':' && parenDepth == 0) {
                colon = i;
                break;
            }
        }
        return colon < 0 ? "" : decl.substring(0, colon).trim();
    }

    private List<CompletionItem> getValueSuggestions(String propertyName, String word, String fullText) {
        // Emmet CSS shorthand in value position
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
        String expanded = EmmetParser.expandCss(emmetAbbr);
        if (expanded != null) {
            List<CompletionItem> res = new ArrayList<>();
            CompletionItem emmetItem = new CompletionItem(emmetAbbr, expanded, "Emmet", CompletionItem.Type.SNIPPET, 0);
            emmetItem.setReplaceLength(emmetAbbr.length());
            res.add(emmetItem);
            return res;
        }

        List<CompletionItem> all = new ArrayList<>();
        List<CompletionItem> prefixMatches = propertyTrie.getCompletions(word, MAX_SUGGESTIONS);
        if (!prefixMatches.isEmpty()) {
            all.addAll(prefixMatches);
            all.addAll(fuzzyFilter(cachedCustomProps, word));
            return all;
        } else {
            List<CompletionItem> fallback = new ArrayList<>(propertyItems);
            fallback.addAll(cachedCustomProps);
            return fuzzyFilter(fallback, word);
        }
    }

    // ─── Zone-specific suggestion builders ────────────────────────────────────

    /**
     * Suggestions for nested selector context (inside a rule block with CSS nesting support).
     * Shows selectors (HTML tags, & prefix, class/id), properties, and Emmet at once —
     * matching how VS Code behaves inside nested CSS.
     */
    private List<CompletionItem> getNestedSuggestions(String word, String trimmed, String fullText, int cursorPos) {
        // Emmet CSS shorthand takes priority
        String emmetAbbr = getEmmetAbbreviationBeforeCursor(fullText, cursorPos);
        String expanded = EmmetParser.expandCss(emmetAbbr);
        if (expanded != null) {
            List<CompletionItem> res = new ArrayList<>();
            CompletionItem emmetItem = new CompletionItem(emmetAbbr, expanded, "Emmet", CompletionItem.Type.SNIPPET, 0);
            emmetItem.setReplaceLength(emmetAbbr.length());
            res.add(emmetItem);
            return res;
        }

        // Combine properties + selectors (properties listed first for relevance)
        List<CompletionItem> all = new ArrayList<>(propertyItems);
        all.addAll(cachedCustomProps);
        all.addAll(htmlTagItems);
        if (word.startsWith(".") || word.startsWith("#")) {
            addCrossFileSelectors(all);
        }
        return fuzzyFilter(all, word);
    }

    private void addCrossFileSelectors(List<CompletionItem> list) {
        List<CompletionItem> classes = ProjectSymbolIndex.getInstance().getCssClassItems();
        for (CompletionItem ci : classes) {
            list.add(new CompletionItem("." + ci.getLabel(), "." + ci.getEffectiveInsertText(), "CSS Class", CompletionItem.Type.CSS_VALUE, 0));
        }
        List<CompletionItem> htmlIds = ProjectSymbolIndex.getInstance().getHtmlIdItems();
        for (CompletionItem ci : htmlIds) {
            list.add(new CompletionItem("#" + ci.getLabel(), "#" + ci.getEffectiveInsertText(), "ID", CompletionItem.Type.CSS_VALUE, 0));
        }
    }

    private List<CompletionItem> getSelectorSuggestions(String word, String trimmed) {
        if (word.startsWith(":")) {
            return fuzzyFilter(PSEUDO_ITEMS, word.substring(1));
        }
        List<CompletionItem> all = new ArrayList<>(htmlTagItems);
        if (word.startsWith(".") || word.startsWith("#") || word.isEmpty()) {
            addCrossFileSelectors(all);
        }
        return fuzzyFilter(all, word);
    }

    private boolean isColorProperty(String prop) {
        if (prop == null) return false;
        String p = prop.toLowerCase();
        return p.endsWith("color") || p.equals("background") || p.equals("fill")
                || p.equals("stroke") || p.equals("outline") || p.equals("border")
                || p.endsWith("shadow") || p.equals("caret-color") || p.equals("accent-color");
    }

    private List<CompletionItem> getMediaSuggestions(String fullText, int cursorPos, String word) {
        String line = getLineBeforeCursor(fullText, cursorPos);

        // Determine if inside parentheses of the media condition
        int open = 0, close = 0;
        // Find the at-rule start on this line or nearby
        int searchStart = Math.max(0, cursorPos - 300);
        String snippet = fullText.substring(searchStart, cursorPos);
        int atIdx = snippet.lastIndexOf("@media");
        if (atIdx < 0) atIdx = snippet.lastIndexOf("@supports");
        if (atIdx < 0) atIdx = snippet.lastIndexOf("@container");
        if (atIdx >= 0) {
            String afterAt = snippet.substring(atIdx);
            for (char c : afterAt.toCharArray()) {
                if (c == '(') open++;
                else if (c == ')') close++;
            }
        }
        boolean inParens = open > close;

        List<CompletionItem> items = new ArrayList<>();

        if (!inParens) {
            String[] keywords = {"screen", "print", "all", "and", "not", "only"};
            for (String kw : keywords) {
                items.add(new CompletionItem(kw, kw + " ", "Media type/keyword", CompletionItem.Type.KEYWORD, 0));
            }
        }

        String[][] features = {
                {"max-width", "max-width: |"},
                {"min-width", "min-width: |"},
                {"max-height", "max-height: |"},
                {"min-height", "min-height: |"},
                {"width", "width: |"},
                {"height", "height: |"},
                {"orientation: portrait", "orientation: portrait"},
                {"orientation: landscape", "orientation: landscape"},
                {"prefers-color-scheme: dark", "prefers-color-scheme: dark"},
                {"prefers-color-scheme: light", "prefers-color-scheme: light"},
                {"prefers-reduced-motion", "prefers-reduced-motion: reduce"},
                {"hover: hover", "hover: hover"},
                {"hover: none", "hover: none"},
                {"pointer: fine", "pointer: fine"},
                {"pointer: coarse", "pointer: coarse"},
                {"display-mode: standalone", "display-mode: standalone"},
                {"aspect-ratio", "aspect-ratio: |"},
                {"resolution", "resolution: |"}
        };

        for (String[] f : features) {
            String label = f[0];
            String insertText = f[1];

            if (!inParens) {
                insertText = "(" + insertText + ")";
            }

            int offset = 0;
            if (insertText.contains("|")) {
                String after = insertText.substring(insertText.indexOf('|') + 1);
                offset = -after.length();
                insertText = insertText.replace("|", "");
            }
            items.add(new CompletionItem(label, insertText, "Media feature", CompletionItem.Type.CSS_VALUE, offset));
        }

        return fuzzyFilter(items, word);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Determines if the cursor is inside an at-rule CONDITION (before the opening brace).
     * e.g. "@media screen and (|)" or "@supports (display: grid|)"
     * Returns false once the { is opened (we're in the body, not the condition).
     */
    private boolean isInsideAtRuleCondition(String text, int cursorPos) {
        int start = Math.max(0, cursorPos - 500);
        String snippet = text.substring(start, cursorPos);

        // Find the last @media/@supports/@container
        int mediaIdx = -1;
        for (String rule : new String[]{"@media", "@supports", "@container"}) {
            int idx = snippet.lastIndexOf(rule);
            if (idx > mediaIdx) mediaIdx = idx;
        }
        if (mediaIdx < 0) return false;

        String afterRule = snippet.substring(mediaIdx);
        // If there's an opening brace, we're past the condition and inside the body
        return afterRule.indexOf('{') < 0;
    }

    private boolean isInsideRuleBlock(String text, int pos) {
        int depth = 0;
        for (int i = 0; i < pos && i < text.length(); i++) {
            if (text.charAt(i) == '{') depth++;
            if (text.charAt(i) == '}') depth--;
        }
        return depth > 0;
    }

    private enum Zone {SELECTOR, PROPERTY, VALUE, NESTED_SELECTOR}

    private static class CssContext {
        Zone zone = Zone.SELECTOR;
        String propertyName = "";
    }
}