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

    private final CssAutoCompleteEngine cssEngine;
    private final JsAutoCompleteEngine jsEngine;
    private java.io.File currentFile;
    private String htmlBoilerplate;

    public HtmlAutoCompleteEngine(Context context) {
        super(context);
        loadTags();
        this.cssEngine = new CssAutoCompleteEngine(context);
        this.jsEngine = new JsAutoCompleteEngine(context);
    }

    public void setCurrentFile(java.io.File file) {
        this.currentFile = file;
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

        try {
            String template = loadAssetText("templates/template_blank.html");
            if (template != null && !template.trim().isEmpty()) {
                htmlBoilerplate = template.replace("<body>\n\n", "<body>\n    |\n")
                                          .replace("<body>\r\n\r\n", "<body>\r\n    |\r\n");
                if (!htmlBoilerplate.contains("|")) {
                    htmlBoilerplate = htmlBoilerplate.replace("<body>", "<body>\n    |");
                }
            }
        } catch (Exception e) {
            // Non-critical
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

        // Check if inside embedded <style> or <script> tags
        String unclosedBlock = tagParser.getUnclosedTagAt(fullText, cursorPos);
        if ("style".equals(unclosedBlock)) {
            return cssEngine.getSuggestions(fullText, cursorPos);
        } else if ("script".equals(unclosedBlock)) {
            return jsEngine.getSuggestions(fullText, cursorPos);
        }

        // Caret sits inside active tag scope bounds -> Fetch valid local attribute properties
        String currentTag = tagParser.getCurrentOpenTagName(fullText, cursorPos);
        if (currentTag != null && !currentTag.isEmpty()) {
            // Check if we are specifically inside an inline 'style="..."' attribute
            String before = fullText.substring(0, cursorPos);
            int lastOpen = before.lastIndexOf('<');
            if (lastOpen != -1) {
                String tagText = before.substring(lastOpen);
                if (tagText.matches("(?s).*\\bstyle\\s*=\\s*\"[^\"]*$") ||
                        tagText.matches("(?s).*\\bstyle\\s*=\\s*'[^']*$")) {
                    return cssEngine.getSuggestions(fullText, cursorPos, true);
                }

                // File path attribute check for src, href, action, poster, data
                java.util.regex.Matcher pathMatcher = java.util.regex.Pattern.compile("(?s).*\\b(src|href|action|poster|data)\\s*=\\s*[\"']([^\"']*)$").matcher(tagText);
                if (pathMatcher.matches()) {
                    String typedPath = pathMatcher.group(2);
                    return getFileSuggestions(typedPath);
                }
            }

            List<CompletionItem> attrs = attrMap.get(currentTag);
            if (attrs != null) {
                return fuzzyFilter(attrs, word);
            }
            return new ArrayList<>(); // Stop searching tags if we are definitely inside attributes
        }

        // Emmet support
        String emmetAbbr = getEmmetAbbreviationBeforeCursor(fullText, cursorPos);
        List<CompletionItem> emmetResults = new ArrayList<>();
        if (emmetAbbr != null && !emmetAbbr.isEmpty() && !emmetAbbr.contains("<")) {
            String expanded = EmmetParser.expandHtml(emmetAbbr, htmlBoilerplate);
            if (expanded != null) {
                if (emmetAbbr.contains(".") || emmetAbbr.contains("#") || emmetAbbr.contains(">") || emmetAbbr.contains("*") || emmetAbbr.contains("+") || emmetAbbr.equals("!")) {
                    List<CompletionItem> res = new ArrayList<>();
                    CompletionItem emmetItem = new CompletionItem(emmetAbbr, expanded, "Emmet Abbreviation", CompletionItem.Type.SNIPPET, 0);
                    emmetItem.setReplaceLength(emmetAbbr.length());
                    res.add(emmetItem);
                    return res;
                } else {
                    CompletionItem emmetItem = new CompletionItem(emmetAbbr, expanded, "Emmet Abbreviation", CompletionItem.Type.SNIPPET, 0);
                    emmetItem.setReplaceLength(emmetAbbr.length());
                    emmetResults.add(emmetItem);
                }
            }
        }

        // Emmet-style plain words (e.g. "div") OR typing "<div"
        // If they type a word, or just typed a '<', immediately suggest matching tags!
        if ((word != null && !word.isEmpty()) || trimmed.endsWith("<")) {
            List<CompletionItem> finalResults = new ArrayList<>(emmetResults);
            finalResults.addAll(fuzzyFilter(tagItems, word));
            return finalResults;
        }

        return emmetResults.isEmpty() ? new ArrayList<>() : emmetResults;
    }

    private java.io.File getProjectRoot(java.io.File file) {
        if (file == null) return null;
        java.io.File dir = file.isDirectory() ? file : file.getParentFile();
        while (dir != null) {
            if (new java.io.File(dir, "project_meta.json").exists()) {
                return dir;
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    private String getRelativeHtmlPath(java.io.File baseDir, java.io.File target) {
        String[] basePath = baseDir.getAbsolutePath().split("/");
        String[] targetPath = target.getAbsolutePath().split("/");

        int common = 0;
        while (common < basePath.length && common < targetPath.length && basePath[common].equals(targetPath[common])) {
            common++;
        }

        StringBuilder rel = new StringBuilder();
        for (int i = common; i < basePath.length; i++) {
            rel.append("../");
        }
        for (int i = common; i < targetPath.length; i++) {
            rel.append(targetPath[i]);
            if (i < targetPath.length - 1) rel.append("/");
        }
        if (target.isDirectory() && rel.length() > 0 && rel.charAt(rel.length() - 1) != '/') {
            rel.append("/");
        }
        if (rel.length() == 0) return "./";
        return rel.toString();
    }

    private void findFilesRecursively(java.io.File dir, String query, List<java.io.File> results, int limit) {
        if (results.size() >= limit) return;
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        for (java.io.File f : files) {
            if (f.getName().startsWith(".")) continue; // Skip hidden files/folders

            if (f.getName().toLowerCase().startsWith(query)) {
                results.add(f);
                if (results.size() >= limit) return;
            }
            if (f.isDirectory()) {
                findFilesRecursively(f, query, results, limit);
            }
        }
    }

    private List<CompletionItem> getFileSuggestions(String typedPath) {
        if (currentFile == null) return new ArrayList<>();
        java.io.File currentDir = currentFile.getParentFile();
        if (currentDir == null) return new ArrayList<>();

        List<CompletionItem> items = new ArrayList<>();
        int lastSlash = typedPath.lastIndexOf('/');

        if (lastSlash != -1) {
            String dirPart = typedPath.substring(0, lastSlash);
            String filterPrefix = typedPath.substring(lastSlash + 1);
            java.io.File searchDir = currentDir;
            if (!dirPart.isEmpty()) {
                searchDir = new java.io.File(currentDir, dirPart);
            }
            if (searchDir.exists() && searchDir.isDirectory()) {
                java.io.File[] files = searchDir.listFiles();
                if (files != null) {
                    for (java.io.File f : files) {
                        String name = f.getName();
                        if (name.toLowerCase().startsWith(filterPrefix.toLowerCase()) || filterPrefix.isEmpty()) {
                            String completion = name + (f.isDirectory() ? "/" : "");
                            items.add(new CompletionItem(completion, completion,
                                    f.isDirectory() ? "Directory" : "File",
                                    f.isDirectory() ? CompletionItem.Type.FOLDER : CompletionItem.Type.FILE, 0));
                        }
                    }
                }
            }
            return fuzzyFilter(items, filterPrefix);
        } else {
            java.io.File projectRoot = getProjectRoot(currentFile);
            if (projectRoot == null) projectRoot = currentDir;

            List<java.io.File> allMatching = new ArrayList<>();
            findFilesRecursively(projectRoot, typedPath.toLowerCase(), allMatching, 50);

            for (java.io.File f : allMatching) {
                String relPath = getRelativeHtmlPath(currentDir, f);
                String label = f.getName() + (f.isDirectory() ? "/" : "");

                items.add(new CompletionItem(label, relPath,
                        relPath,
                        f.isDirectory() ? CompletionItem.Type.FOLDER : CompletionItem.Type.FILE, 0));
            }

            return items; // No fuzzy filtering needed, already prefix filtered
        }
    }
}