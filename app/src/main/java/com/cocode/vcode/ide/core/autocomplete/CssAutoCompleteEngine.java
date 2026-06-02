package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contextual suggestion engine optimized for Cascading Style Sheets (.css syntax).
 * Intelligently shifts context depending on whether the caret resides within selector zones,
 * property declarations, or assignment fields.
 */
public class CssAutoCompleteEngine extends AutoCompleteEngine {

    private static final List<CompletionItem> PSEUDO_ITEMS;
    private static final List<CompletionItem> AT_RULE_ITEMS;

    // Static Initialization Block: Set up standard language definitions once on class load
    static {
        String[] pseudos = {":hover", ":focus", ":active", ":visited", ":first-child",
                ":last-child", ":nth-child()", "::before", "::after", ":not()",
                ":root", ":checked", ":disabled", ":enabled", ":placeholder"};
        PSEUDO_ITEMS = new ArrayList<>();
        for (String p : pseudos) {
            // If the selector contains parameter boundaries (), offset the cursor back 1 position inside the parentheses
            PSEUDO_ITEMS.add(new CompletionItem(p, p, "Pseudo-class/element",
                    CompletionItem.Type.CSS_VALUE, p.endsWith("()") ? 1 : 0));
        }

        String[] atRules = {"@media", "@keyframes", "@import", "@font-face",
                "@supports", "@charset", "@layer", "@container"};
        AT_RULE_ITEMS = new ArrayList<>();
        for (String r : atRules) {
            // Include space formatting to automatically guide block structure definitions
            AT_RULE_ITEMS.add(new CompletionItem(r, r + " ", "At-rule",
                    CompletionItem.Type.CSS_VALUE, 0));
        }
    }

    private final List<CompletionItem> propertyItems = new ArrayList<>();
    private final Map<String, List<String>> valueMap = new HashMap<>();
    private final List<CompletionItem> htmlTagItems = new ArrayList<>();
    private final List<CompletionItem> colorItems = new ArrayList<>();
    private final List<CompletionItem> globalValueItems = new ArrayList<>();

    public CssAutoCompleteEngine(Context context) {
        super(context);
        loadProperties();
        loadHtmlTags();
        loadColors();
    }

    private void loadColors() {
        try {
            String json = loadAssetJson("completions/css_colors.json");
            JSONObject obj = new JSONObject(json);
            JSONArray colors = obj.optJSONArray("colors");
            if (colors != null) {
                for (int i = 0; i < colors.length(); i++) {
                    String c = colors.optString(i);
                    colorItems.add(new CompletionItem(c, c, "Color", CompletionItem.Type.CSS_VALUE, 0));
                }
            }
            JSONArray colorFuncs = obj.optJSONArray("color_functions");
            if (colorFuncs != null) {
                for (int i = 0; i < colorFuncs.length(); i++) {
                    String f = colorFuncs.optString(i);
                    colorItems.add(new CompletionItem(f, f, "Function", CompletionItem.Type.CSS_VALUE, -1));
                }
            }
            JSONArray globalFuncs = obj.optJSONArray("global_functions");
            if (globalFuncs != null) {
                for (int i = 0; i < globalFuncs.length(); i++) {
                    String f = globalFuncs.optString(i);
                    globalValueItems.add(new CompletionItem(f, f, "Function", CompletionItem.Type.CSS_VALUE, -1));
                }
            }
        } catch (Exception e) {
            // Non-critical
        }
    }

    private void loadHtmlTags() {
        try {
            String json = loadAssetJson("completions/html_tags.json");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String tag = obj.optString("tag");
                String detail = obj.optString("detail", "HTML Element");
                htmlTagItems.add(new CompletionItem(tag, tag + " ", detail, CompletionItem.Type.TAG, 0));
            }
        } catch (Exception e) {
            // Non-critical
        }
    }

    /**
     * Parses the dictionary of standard CSS properties and localized valid field values from asset JSON.
     */
    private void loadProperties() {
        try {
            String json = loadAssetJson("completions/css_properties.json");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String property = obj.optString("property");
                String detail = obj.optString("detail", "");
                JSONArray values = obj.optJSONArray("values");

                // Injects colon delimiter and trailing semicolon format, offsetting the cursor to the value field
                CompletionItem item = new CompletionItem(property, property + ": |;",
                        detail, CompletionItem.Type.CSS_PROPERTY, 1);
                propertyItems.add(item);

                if (values != null) {
                    List<String> vals = new ArrayList<>();
                    for (int j = 0; j < values.length(); j++) vals.add(values.optString(j));
                    valueMap.put(property, vals);
                }
            }
        } catch (Exception e) {
            // Non-critical failures degrade gracefully to empty list states without breaking compilation
        }
    }

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        return getSuggestions(fullText, cursorPos, false);
    }

    public List<CompletionItem> getSuggestions(String fullText, int cursorPos, boolean isInlineStyle) {
        if (fullText == null || cursorPos < 0) return new ArrayList<>();
        String line = getLineBeforeCursor(fullText, cursorPos);

        // Typing directives after an '@' symbol -> Match structural At-Rules
        if (line.trim().startsWith("@")) {
            String word = getWordBeforeCursor(fullText, cursorPos);
            return fuzzyFilter(AT_RULE_ITEMS, word.replace("@", ""));
        }

        int colonIdx = line.lastIndexOf(':');
        int braceIdx = line.lastIndexOf('{');
        int semiIdxLine = line.lastIndexOf(';');

        if (colonIdx > braceIdx && colonIdx > semiIdxLine) {
            String propertyPart = line.substring(braceIdx >= 0 ? braceIdx + 1 : 0, colonIdx).trim();

            // Clean up propertyPart for inline HTML styles and multiple properties on one line
            int semiIdx = propertyPart.lastIndexOf(';');
            int quoteIdx = Math.max(propertyPart.lastIndexOf('"'), propertyPart.lastIndexOf('\''));
            int startIdx = Math.max(semiIdx, quoteIdx);
            if (startIdx >= 0) {
                propertyPart = propertyPart.substring(startIdx + 1).trim();
            }

            List<String> vals = valueMap.get(propertyPart);
            List<CompletionItem> items = new ArrayList<>();
            if (vals != null) {
                for (String v : vals) {
                    items.add(new CompletionItem(v, v, "", CompletionItem.Type.CSS_VALUE, 0));
                }
            }

            items.addAll(globalValueItems);

            if (propertyPart.endsWith("color") || propertyPart.equals("background") || 
                propertyPart.equals("border") || propertyPart.endsWith("shadow") || 
                propertyPart.equals("fill") || propertyPart.equals("stroke") || 
                propertyPart.equals("outline")) {
                items.addAll(colorItems);
            }

            if (!items.isEmpty()) {
                String word = getWordBeforeCursor(fullText, cursorPos);
                return fuzzyFilter(items, word);
            }
            return new ArrayList<>();
        }

        // Active scope within curly braces OR inline style attribute -> Suggest core CSS properties
        if (isInlineStyle || braceIdx >= 0 || isInsideRuleBlock(fullText, cursorPos)) {
            String emmetAbbr = getEmmetAbbreviationBeforeCursor(fullText, cursorPos);
            String expanded = EmmetParser.expandCss(emmetAbbr);
            if (expanded != null) {
                List<CompletionItem> res = new ArrayList<>();
                CompletionItem emmetItem = new CompletionItem(emmetAbbr, expanded, "Emmet Abbreviation", CompletionItem.Type.SNIPPET, 0);
                emmetItem.setReplaceLength(emmetAbbr.length());
                res.add(emmetItem);
                return res;
            }
            String word = getWordBeforeCursor(fullText, cursorPos);
            return fuzzyFilter(propertyItems, word);
        }

        // Structural selector tracking -> Match relevant pseudo element identifiers
        String word = getWordBeforeCursor(fullText, cursorPos);
        if (word.startsWith(":")) {
            return fuzzyFilter(PSEUDO_ITEMS, word);
        }

        // Outside of blocks, suggest HTML elements for selectors
        return fuzzyFilter(htmlTagItems, word);
    }

    /**
     * Evaluates brace counts tracking back from current cursor to confirm
     * if the editing session is currently active inside a CSS declaration block.
     */
    private boolean isInsideRuleBlock(String text, int pos) {
        int open = 0;
        int close = 0;
        for (int i = 0; i < pos && i < text.length(); i++) {
            if (text.charAt(i) == '{') open++;
            if (text.charAt(i) == '}') close++;
        }
        return open > close; // True when there are open styling brackets left unresolved
    }
}