package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Formatted suggestion provider optimized for JSON structure data.
 * Automates symbol insertions for strings, integers, array elements, and structural objects.
 */
public class JsonAutoCompleteEngine extends AutoCompleteEngine {

    private static final List<CompletionItem> VALUE_ITEMS;

    // Static Initialization Block: Prepare primitive structural JSON choices
    static {
        VALUE_ITEMS = new ArrayList<>();
        VALUE_ITEMS.add(new CompletionItem("\"\"", "\"\"", "String value", CompletionItem.Type.JSON_KEY, -1));
        VALUE_ITEMS.add(new CompletionItem("0", "0", "Number", CompletionItem.Type.JSON_KEY, 0));
        VALUE_ITEMS.add(new CompletionItem("true", "true", "Boolean", CompletionItem.Type.JSON_KEY, 0));
        VALUE_ITEMS.add(new CompletionItem("false", "false", "Boolean", CompletionItem.Type.JSON_KEY, 0));
        VALUE_ITEMS.add(new CompletionItem("null", "null", "Null", CompletionItem.Type.JSON_KEY, 0));
        // Multi-line templates with explicit formatting offsets to position cursor right inside the blocks
        VALUE_ITEMS.add(new CompletionItem("{}", "{\n  \n}", "Object", CompletionItem.Type.JSON_KEY, -3));
        VALUE_ITEMS.add(new CompletionItem("[]", "[\n  \n]", "Array", CompletionItem.Type.JSON_KEY, -3));
    }

    private final List<CompletionItem> snippetItems = new ArrayList<>();

    public JsonAutoCompleteEngine(Context context) {
        super(context);
        loadSnippets();
    }

    /**
     * Reads complex dictionary keys and developer-defined boilerplate schemas out of JSON assets.
     */
    private void loadSnippets() {
        try {
            String json = loadAssetJson("completions/json_snippets.json");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String label = obj.optString("label");
                String snippet = obj.optString("snippet", label);
                String detail = obj.optString("detail", "");
                int offset = 0;

                // Track cursor position marks via the pipe token character
                if (snippet.contains("|")) {
                    String after = snippet.substring(snippet.indexOf('|') + 1);
                    offset = after.length();
                    snippet = snippet.replace("|", "");
                }
                snippetItems.add(new CompletionItem(label, snippet, detail,
                        CompletionItem.Type.SNIPPET, offset));
            }
        } catch (Exception e) {
            // Non-critical
        }
    }

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0) return new ArrayList<>();
        String line = getLineBeforeCursor(fullText, cursorPos);
        String trimmed = line.trim();

        // Directly behind assignment symbol ':' -> Display primitive data type templates
        if (trimmed.endsWith(":") || trimmed.endsWith(": ")) {
            return new ArrayList<>(VALUE_ITEMS);
        }

        // Within active collection arrays or parameters -> Display value choices
        if (trimmed.endsWith("[") || trimmed.endsWith(",")) {
            return new ArrayList<>(VALUE_ITEMS);
        }

        // General structural matching -> Fall back to filtering loaded config snippets
        String word = getWordBeforeCursor(fullText, cursorPos);
        return fuzzyFilter(snippetItems, word);
    }
}