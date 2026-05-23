package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
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
            "(?:function\\s+|const\\s+|let\\s+|var\\s+|class\\s+)([a-zA-Z_$][\\w$]*)");

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
                    offset = after.length();
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
        if (fullText == null || cursorPos < 0) return new ArrayList<>();
        String line = getLineBeforeCursor(fullText, cursorPos);

        // Member fields requested via Dot notation '.' -> Extract parent name and match methods
        int dotIdx = line.lastIndexOf('.');
        if (dotIdx >= 0 && dotIdx == cursorPos - (cursorPos - dotIdx)) {
            String beforeDot = getNonWhitespaceBeforeCursor(
                    fullText.substring(0, dotIdx), dotIdx).trim();
            String afterDot = getWordBeforeCursor(fullText, cursorPos);

            for (String[] pair : DOT_METHODS) {
                // If context prefix aligns with an object namespace definition, process member properties
                if (beforeDot.equals(pair[0]) || beforeDot.endsWith(pair[0])) {
                    String[] methods = pair[1].split(",");
                    List<CompletionItem> items = new ArrayList<>();
                    for (String m : methods) {
                        items.add(new CompletionItem(m, m, pair[0] + " method",
                                CompletionItem.Type.BUILTIN, 0));
                    }
                    return fuzzyFilter(items, afterDot);
                }
            }
        }

        // General fallback execution -> Gather keywords and scan local user-declared tokens
        String word = getWordBeforeCursor(fullText, cursorPos);
        List<CompletionItem> all = new ArrayList<>(builtinItems);

        // Perform text matching sweep to dynamically index active workspace variables or functions
        Matcher m = PAT_USER_FUNC.matcher(fullText);
        int scanLimit = Math.min(fullText.length(), 10000); // Caps lookups to preserve memory on huge files
        while (m.find() && m.start() < scanLimit) {
            String name = m.group(1);
            if (name != null && !name.isEmpty()) {
                all.add(new CompletionItem(name, name, "User defined",
                        CompletionItem.Type.FUNCTION, 0));
            }
        }

        return fuzzyFilter(all, word);
    }
}