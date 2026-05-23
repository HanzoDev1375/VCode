package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;
import com.cocode.vcode.ide.core.parser.HtmlTagParser;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Intelligent completion coordinator for HTML source code structures.
 * Leverages tag parsers to seamlessly offer elements, closing parameters, or legal inline attributes.
 */
public class HtmlAutoCompleteEngine extends AutoCompleteEngine {

    private final List<CompletionItem> tagItems = new ArrayList<>();
    private final HtmlTagParser tagParser = new HtmlTagParser();

    // Maps a base tag identifier (e.g. "img") directly to its applicable attribute options array
    private final java.util.Map<String, List<CompletionItem>> attrMap = new java.util.HashMap<>();

    public HtmlAutoCompleteEngine(Context context) {
        super(context);
        loadTags();
    }

    /**
     * Initializes global HTML tag structural rules and metadata specifications from storage.
     */
    private void loadTags() {
        try {
            String json = loadAssetJson("completions/html_tags.json");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String tag = obj.optString("tag");
                // Get the snippet with the pipe | perfectly intact
                String snippet = obj.optString("snippet", "<" + tag + ">|</" + tag + ">");
                String detail = obj.optString("detail", "");

                // We completely removed the manual '|' stripping logic here!
                // We just pass the raw snippet with the '|' directly to CodeEditText,
                // and it will perfectly calculate the cursor position.
                CompletionItem item = new CompletionItem(tag, snippet, detail,
                        CompletionItem.Type.TAG, 0);
                tagItems.add(item);

                JSONArray attrs = obj.optJSONArray("attributes");
                if (attrs != null) {
                    List<CompletionItem> attrList = new ArrayList<>();
                    for (int j = 0; j < attrs.length(); j++) {
                        String attr = attrs.optString(j);
                        // Also let CodeEditText handle the pipe for attributes!
                        attrList.add(new CompletionItem(attr, attr + "=\"|\"",
                                "", CompletionItem.Type.ATTRIBUTE, 0));
                    }
                    attrMap.put(tag, attrList);
                }
            }
        } catch (Exception e) {
            // Completion data not critical — proceed with empty list
        }
    }

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length()) {
            return new ArrayList<>();
        }

        String lineBefore = getLineBeforeCursor(fullText, cursorPos);
        String trimmed = lineBefore.trim();
        String word = getWordBeforeCursor(fullText, cursorPos);

        // End tags requested via '</' -> Locate open elements and suggest automatic closure
        if (trimmed.endsWith("</") || lineBefore.endsWith("</")) {
            String unclosed = tagParser.getUnclosedTagAt(fullText, cursorPos);
            if (unclosed != null && !unclosed.isEmpty()) {
                List<CompletionItem> result = new ArrayList<>();
                result.add(new CompletionItem(
                        "</" + unclosed + ">",
                        "</" + unclosed + ">",
                        "Close tag",
                        CompletionItem.Type.TAG, 0));
                return result;
            }
        }

        // Caret sits inside active tag scope bounds -> Fetch valid local attribute properties
        String currentTag = tagParser.getCurrentOpenTagName(fullText, cursorPos);
        if (currentTag != null && !currentTag.isEmpty()) {
            List<CompletionItem> attrs = attrMap.get(currentTag);
            if (attrs != null) {
                return fuzzyFilter(attrs, word);
            }
            return new ArrayList<>(); // Stop searching tags if we are definitely inside attributes
        }

        // Emmet-style plain words (e.g. "div") OR typing "<div"
        // If they type a word, or just typed a '<', immediately suggest matching tags!
        if ((word != null && !word.isEmpty()) || trimmed.endsWith("<")) {
            return fuzzyFilter(tagItems, word);
        }

        return new ArrayList<>();
    }
}