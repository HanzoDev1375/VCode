package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ECMAScript/JavaScript IntelliSense engine.
 * Computes context using static namespace mapping tables, standard reserved core words,
 * and regular expression syntax tracking for symbols declared on the fly by developers.
 */
public class JsAutoCompleteEngine extends AutoCompleteEngine {

    // Regex parsing pattern aimed at capturing function definitions, variable assignments, and class structures
    private static final Pattern PAT_USER_FUNC = Pattern.compile(
            "(function|const|let|var|class)\\s+([a-zA-Z_$][\\w$]*)");

    private static final Pattern PAT_WORD = Pattern.compile("[a-zA-Z_$][\\w$]*");

    // Built-in API global namespace method dictionary maps
    private static final String[][] DOT_METHODS = {
            {"console", "log,warn,error,info,table,time,timeEnd,assert,clear,count"},
            {"Math", "floor,ceil,round,abs,max,min,random,sqrt,pow,PI,E,log,sin,cos,tan"},
            {"JSON", "parse,stringify"},
            {"Array", "from,isArray,of"},
            {"Object", "keys,values,entries,assign,create,freeze,seal,defineProperty,getOwnPropertyNames"},
            {"Promise", "all,race,resolve,reject,allSettled,any"},
            {"document", "getElementById,querySelector,querySelectorAll,createElement,createTextNode," +
                    "addEventListener,removeEventListener,body,head,title,cookie,write,readyState"},
            {"window", "location,history,navigator,alert,confirm,prompt,open,close," +
                    "setTimeout,setInterval,clearTimeout,clearInterval,scrollTo,scrollBy"},
            {"navigator", "userAgent,language,onLine,geolocation,clipboard,mediaDevices"},
            {"location", "href,pathname,search,hash,reload,replace,assign,origin"},
            {"history", "back,forward,go,pushState,replaceState,length"},
            {"localStorage", "getItem,setItem,removeItem,clear,length,key"},
            {"sessionStorage", "getItem,setItem,removeItem,clear,length,key"},
    };
    private final List<CompletionItem> builtinItems = new ArrayList<>();

    public JsAutoCompleteEngine(Context context) {
        super(context);
        loadKeywords();
    }

    /**
     * Loads system keywords, syntax constructs, and framework labels out of source configuration file.
     */
    private void loadKeywords() {
        try {
            String json = loadAssetJson("completions/js_keywords.json");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String label = obj.optString("label");
                String typeStr = obj.optString("type", "KEYWORD");
                String snippet = obj.optString("snippet", label);
                String detail = obj.optString("detail", "");

                CompletionItem.Type type;
                try {
                    type = CompletionItem.Type.valueOf(typeStr);
                } catch (Exception e) {
                    type = CompletionItem.Type.KEYWORD;
                }

                int offset = 0;
                // Evaluate explicit placement indices using the internal pipe code token '|'
                if (snippet.contains("|")) {
                    String after = snippet.substring(snippet.indexOf('|') + 1);
                    offset = -after.length();
                    snippet = snippet.replace("|", "");
                }
                builtinItems.add(new CompletionItem(label, snippet, detail, type, offset));
            }
        } catch (Exception e) {
            // Non-critical
        }
    }

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length())
            return new ArrayList<>();

        String word = getWordBeforeCursor(fullText, cursorPos);

        // Member fields requested via Dot notation '.' -> Extract parent name and match methods
        int potentialDotIdx = cursorPos - word.length() - 1;
        if (potentialDotIdx >= 0 && fullText.charAt(potentialDotIdx) == '.') {
            String beforeDot = getNonWhitespaceBeforeCursor(fullText, potentialDotIdx).trim();

            for (String[] pair : DOT_METHODS) {
                // If context prefix aligns with an object namespace definition, process member properties
                if (beforeDot.equals(pair[0]) || beforeDot.endsWith(pair[0]) || beforeDot.endsWith("." + pair[0])) {
                    String[] methods = pair[1].split(",");
                    List<CompletionItem> items = new ArrayList<>();
                    for (String m : methods) {
                        items.add(new CompletionItem(m, m, pair[0] + " method",
                                CompletionItem.Type.BUILTIN, 0));
                    }
                    return fuzzyFilter(items, word);
                }
            }
        }

        // General fallback execution -> Gather keywords and scan local user-declared tokens
        List<CompletionItem> all = new ArrayList<>(builtinItems);

        // Perform text matching sweep to dynamically index active workspace variables or functions
        Set<String> seen = new HashSet<>();
        for (CompletionItem item : all) {
            seen.add(item.getLabel());
        }

        Matcher m = PAT_USER_FUNC.matcher(fullText);
        int scanLimit = Math.min(fullText.length(), 50000); // Caps lookups to preserve memory on huge files
        while (m.find() && m.start() < scanLimit) {
            String keyword = m.group(1);
            String name = m.group(2);
            if (name != null && !name.isEmpty() && seen.add(name)) {
                CompletionItem.Type type = CompletionItem.Type.VALUE;
                String detail = "Variable";
                if ("function".equals(keyword)) {
                    type = CompletionItem.Type.FUNCTION;
                    detail = "Function";
                } else if ("class".equals(keyword)) {
                    type = CompletionItem.Type.KEYWORD;
                    detail = "Class";
                }
                all.add(new CompletionItem(name, name, detail, type, 0));
            }
        }

        // Add all other words in the document as generic suggestions
        Matcher wordMatcher = PAT_WORD.matcher(fullText);
        while (wordMatcher.find() && wordMatcher.start() < scanLimit) {
            String w = wordMatcher.group();
            if (w.length() >= 2 && seen.add(w)) {
                all.add(new CompletionItem(w, w, "Document Word", CompletionItem.Type.VALUE, 0));
            }
        }

        return fuzzyFilter(all, word);
    }
}