package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ECMAScript/JavaScript IntelliSense engine — mirrors VS Code's JavaScript Language Server behaviour.
 *
 * <p>Key behaviours:
 * <ul>
 *   <li>Member items use insertText = just the method name (e.g. "floor(|)"), NOT "Math.floor(|)".
 *       The dot and namespace are already in the document — we only insert after the dot.</li>
 *   <li>Dot completion triggers when cursor is right after '.' (word = "" thanks to getWordBeforeCursor fix).</li>
 *   <li>Chained-call completion: "fetch('url')." → Promise methods; "arr.filter(...)." → Array methods.</li>
 *   <li>Import/require path completion: shows files immediately when cursor is inside quote.</li>
 *   <li>Document symbol indexing is cached — only re-scans when text changes.</li>
 * </ul>
 */
public class JsAutoCompleteEngine extends AutoCompleteEngine {

    // ─── Regex patterns ────────────────────────────────────────────────────────
    private static final Pattern PAT_USER_DECL = Pattern.compile(
            "(?:function\\s+([a-zA-Z_$][\\w$]*)\\s*\\()"           // named function
            + "|(?:class\\s+([a-zA-Z_$][\\w$]*))"                   // class
            + "|(?:(?:const|let|var)\\s+([a-zA-Z_$][\\w$]*)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[a-zA-Z_$][\\w$]*)\\s*=>)" // arrow fn
            + "|(?:(?:const|let|var)\\s+([a-zA-Z_$][\\w$]*))");     // variable

    private static final Pattern PAT_WORD = Pattern.compile("[a-zA-Z_$][\\w$]{1,}");

    /** Detects cursor inside addEventListener(' or on( string argument for event names */
    private static final Pattern PAT_EVENT_STRING = Pattern.compile(
            "(?:addEventListener|removeEventListener|on)\\s*\\(\\s*['\"]([^'\"]*?)$");

    private static final Pattern PAT_GET_ELEMENT_BY_ID = Pattern.compile("getElementById\\s*\\(\\s*['\"]([^'\"]*?)$");
    private static final Pattern PAT_QUERY_SELECTOR = Pattern.compile("querySelector(?:All)?\\s*\\(\\s*['\"]([^'\"]*?)$");
    private static final Pattern PAT_JSDOC_TYPE = Pattern.compile("@(?:type|returns?|param)\\s*\\{([^}]+)\\}");
    private static final Pattern PAT_IMPORT_STAR = Pattern.compile("import\\s+\\*\\s+as\\s+([a-zA-Z_$][\\w$]*)\\s+from\\s+['\"]([^'\"]+)['\"]");

    // ─── Static namespace → comma-separated method list ────────────────────────
    private static final String[][] DOT_METHODS = {
            {"console",        "log,warn,error,info,table,time,timeEnd,timeLog,assert,clear,count,countReset,group,groupEnd,groupCollapsed,dir,dirxml,trace,debug,profile,profileEnd"},
            {"Math",           "floor,ceil,round,abs,max,min,random,sqrt,pow,PI,E,LN2,LN10,LOG2E,SQRT2,log,log2,log10,sign,trunc,sin,cos,tan,asin,acos,atan,atan2,sinh,cosh,tanh,cbrt,hypot,clz32,imul,fround"},
            {"JSON",           "parse,stringify"},
            {"Array",          "from,isArray,of"},
            {"Object",         "keys,values,entries,assign,create,freeze,seal,isFrozen,isSealed,defineProperty,defineProperties,getOwnPropertyNames,getOwnPropertyDescriptors,getPrototypeOf,setPrototypeOf,hasOwn,fromEntries,is,groupBy"},
            {"Promise",        "all,race,resolve,reject,allSettled,any,withResolvers"},
            {"Number",         "isFinite,isInteger,isNaN,isSafeInteger,parseInt,parseFloat,MAX_VALUE,MIN_VALUE,MAX_SAFE_INTEGER,MIN_SAFE_INTEGER,EPSILON,POSITIVE_INFINITY,NEGATIVE_INFINITY,NaN"},
            {"String",         "fromCharCode,fromCodePoint,raw"},
            {"Date",           "now,parse,UTC"},
            {"Map",            "groupBy"},
            {"Set",            ""},
            {"WeakMap",        ""},
            {"WeakSet",        ""},
            {"RegExp",         ""},
            {"Symbol",         "iterator,asyncIterator,hasInstance,toPrimitive,toStringTag,for,keyFor"},
            {"Reflect",        "apply,construct,defineProperty,deleteProperty,get,getOwnPropertyDescriptor,getPrototypeOf,has,isExtensible,ownKeys,preventExtensions,set,setPrototypeOf"},
            {"Proxy",          "revocable"},
            {"document",       "getElementById,querySelector,querySelectorAll,createElement,createTextNode,createDocumentFragment,createComment,addEventListener,removeEventListener,body,head,title,cookie,write,writeln,readyState,documentElement,activeElement,forms,images,links,scripts,styleSheets,hidden,visibilityState,fullscreenElement,pointerLockElement,designMode,execCommand,getSelection,hasFocus,open,close,importNode,adoptNode,createEvent,createRange,createTreeWalker,elementsFromPoint,elementFromPoint,exitFullscreen,exitPointerLock,scrollingElement"},
            {"window",         "location,history,navigator,alert,confirm,prompt,open,close,scrollTo,scrollBy,setTimeout,setInterval,clearTimeout,clearInterval,requestAnimationFrame,cancelAnimationFrame,requestIdleCallback,cancelIdleCallback,fetch,addEventListener,removeEventListener,getComputedStyle,matchMedia,innerWidth,innerHeight,outerWidth,outerHeight,devicePixelRatio,performance,screen,crypto,indexedDB,caches,customElements,visualViewport,structuredClone,atob,btoa,queueMicrotask,reportError,postMessage,focus,blur,print,stop,getSelection"},
            {"navigator",      "userAgent,language,languages,onLine,geolocation,clipboard,mediaDevices,permissions,serviceWorker,hardwareConcurrency,cookieEnabled,platform,maxTouchPoints,connection,storage,locks,credentials,sendBeacon,vibrate,share,canShare,getBattery,getGamepads,requestMIDIAccess,wakeLock"},
            {"location",       "href,pathname,search,hash,hostname,port,protocol,host,origin,reload,replace,assign,toString"},
            {"history",        "back,forward,go,pushState,replaceState,length,state,scrollRestoration"},
            {"localStorage",   "getItem,setItem,removeItem,clear,length,key"},
            {"sessionStorage", "getItem,setItem,removeItem,clear,length,key"},
            {"performance",    "now,mark,measure,clearMarks,clearMeasures,getEntries,getEntriesByName,getEntriesByType,timeOrigin,navigation,timing"},
            {"crypto",         "subtle,getRandomValues,randomUUID"},
            {"screen",         "width,height,availWidth,availHeight,colorDepth,pixelDepth,orientation"},
            {"URL",            "createObjectURL,revokeObjectURL,canParse"},
            {"URLSearchParams","append,delete,get,getAll,has,set,sort,toString,entries,keys,values,forEach,size"},
            {"FormData",       "append,delete,get,getAll,has,set,entries,keys,values,forEach"},
            {"Headers",        "append,delete,get,has,set,entries,keys,values,forEach"},
            {"Request",        "clone,arrayBuffer,blob,formData,json,text,url,method,headers,body,mode,credentials,cache,redirect,referrer,integrity,signal"},
            {"Response",       "clone,arrayBuffer,blob,formData,json,text,ok,status,statusText,headers,url,type,redirected,error,redirect"},
            {"AbortController","abort,signal"},
            {"IntersectionObserver","observe,unobserve,disconnect,takeRecords,root,rootMargin,thresholds"},
            {"ResizeObserver",  "observe,unobserve,disconnect"},
            {"MutationObserver","observe,disconnect,takeRecords"},
            {"EventTarget",    "addEventListener,removeEventListener,dispatchEvent"},
            {"CustomEvent",    "detail"},
            {"WebSocket",      "send,close,onopen,onclose,onmessage,onerror,readyState,url,protocol,binaryType,bufferedAmount,CONNECTING,OPEN,CLOSING,CLOSED"},
            {"Worker",         "postMessage,terminate,onmessage,onerror"},
            {"BroadcastChannel","postMessage,close,onmessage,name"},
            {"Intl",           "DateTimeFormat,NumberFormat,Collator,PluralRules,RelativeTimeFormat,ListFormat,Segmenter,DisplayNames,Locale"},
            {"TextEncoder",    "encode,encodeInto,encoding"},
            {"TextDecoder",    "decode,encoding,fatal,ignoreBOM"},
            {"DOMParser",      "parseFromString"},
            {"XMLSerializer",  "serializeToString"},
    };

    // ─── Event names for addEventListener string completion ────────────────────
    private static final String[] EVENT_NAMES = {
            "click", "dblclick", "mousedown", "mouseup", "mousemove", "mouseover", "mouseout",
            "mouseenter", "mouseleave", "contextmenu", "wheel",
            "keydown", "keyup", "keypress",
            "focus", "blur", "focusin", "focusout",
            "input", "change", "submit", "reset", "invalid",
            "touchstart", "touchmove", "touchend", "touchcancel",
            "pointerdown", "pointerup", "pointermove", "pointerenter", "pointerleave",
            "pointerover", "pointerout", "pointercancel", "gotpointercapture", "lostpointercapture",
            "scroll", "scrollend", "resize",
            "load", "error", "abort", "unload", "beforeunload",
            "DOMContentLoaded", "readystatechange",
            "animationstart", "animationend", "animationiteration", "animationcancel",
            "transitionstart", "transitionend", "transitionrun", "transitioncancel",
            "drag", "dragstart", "dragend", "dragover", "dragenter", "dragleave", "drop",
            "copy", "cut", "paste",
            "play", "pause", "ended", "timeupdate", "volumechange", "seeking", "seeked",
            "canplay", "canplaythrough", "loadeddata", "loadedmetadata", "progress", "waiting", "stalled",
            "fullscreenchange", "fullscreenerror",
            "visibilitychange", "online", "offline", "storage",
            "hashchange", "popstate", "pagehide", "pageshow",
            "message", "messageerror",
            "open", "close",
            "toggle", "beforetoggle",
            "select", "selectstart", "selectionchange",
            "slotchange",
            "formdata",
    };

    /**
     * Prototype method completions for typed variables — keyed by inferred type.
     */
    private static final Map<String, String[]> PROTOTYPE_METHODS = new HashMap<>();

    /**
     * When a chained call finishes with one of these method names, the NEXT dot
     * should complete with the returned type's methods.
     * e.g. "arr.filter(...).map" → filter returns array → show array methods.
     */
    private static final Map<String, String> CHAIN_RETURN_TYPES = new HashMap<>();

    /**
     * Function names that always return a Promise (without needing a declared variable).
     */
    private static final Set<String> PROMISE_FUNCTIONS = new HashSet<>();

    static {
        PROTOTYPE_METHODS.put("array", new String[]{
                "push","pop","shift","unshift","splice","slice","concat","join","reverse","sort",
                "indexOf","lastIndexOf","includes","find","findIndex","findLast","findLastIndex",
                "filter","map","reduce","reduceRight","forEach","some","every","flat","flatMap",
                "fill","copyWithin","entries","keys","values","at","toReversed","toSorted","toSpliced",
                "with","toString","toLocaleString","length","group","groupToMap"
        });
        PROTOTYPE_METHODS.put("string", new String[]{
                "charAt","charCodeAt","codePointAt","at","concat","includes","startsWith","endsWith",
                "indexOf","lastIndexOf","search","match","matchAll","replace","replaceAll","split",
                "slice","substring","padStart","padEnd","trimStart","trimEnd","trim",
                "toUpperCase","toLowerCase","toLocaleLowerCase","toLocaleUpperCase","normalize",
                "repeat","valueOf","toString","length","isWellFormed","toWellFormed","localeCompare"
        });
        PROTOTYPE_METHODS.put("number", new String[]{"toFixed","toPrecision","toExponential","toString","valueOf","toLocaleString"});
        PROTOTYPE_METHODS.put("promise", new String[]{"then","catch","finally"});
        PROTOTYPE_METHODS.put("map",     new String[]{"set","get","has","delete","clear","forEach","keys","values","entries","size"});
        PROTOTYPE_METHODS.put("set",     new String[]{"add","has","delete","clear","forEach","values","keys","entries","size","union","intersection","difference","symmetricDifference","isSubsetOf","isSupersetOf"});
        PROTOTYPE_METHODS.put("date",    new String[]{"getTime","getFullYear","getMonth","getDate","getDay","getHours","getMinutes","getSeconds","getMilliseconds","setTime","setFullYear","setMonth","setDate","setHours","setMinutes","setSeconds","toISOString","toLocaleDateString","toLocaleTimeString","toLocaleString","toJSON","toString","valueOf","getTimezoneOffset"});
        PROTOTYPE_METHODS.put("regexp",  new String[]{"test","exec","toString","source","flags","global","ignoreCase","multiline","sticky","unicode","lastIndex"});
        PROTOTYPE_METHODS.put("element", new String[]{
                "addEventListener","removeEventListener","setAttribute","getAttribute","removeAttribute",
                "hasAttribute","toggleAttribute","closest","matches","querySelector","querySelectorAll",
                "classList","style","dataset","innerHTML","textContent","innerText","outerHTML","outerText",
                "appendChild","removeChild","insertBefore","replaceChild","replaceChildren","cloneNode",
                "contains","remove","before","after","prepend","append","insertAdjacentHTML",
                "insertAdjacentElement","insertAdjacentText","getBoundingClientRect","getClientRects",
                "scrollIntoView","scroll","scrollTo","scrollBy","focus","blur","click",
                "animate","getAnimations","requestFullscreen","attachShadow",
                "id","className","tagName","localName","parentElement","parentNode",
                "children","childNodes","childElementCount","firstChild","lastChild",
                "firstElementChild","lastElementChild","nextSibling","previousSibling",
                "nextElementSibling","previousElementSibling",
                "offsetWidth","offsetHeight","offsetTop","offsetLeft","offsetParent",
                "clientWidth","clientHeight","clientTop","clientLeft",
                "scrollWidth","scrollHeight","scrollTop","scrollLeft",
                "hidden","isConnected","slot","assignedSlot",
                "value","type","checked","disabled","readOnly","name","form",
                "min","max","step","placeholder","required","maxLength","minLength",
                "play","pause","load","currentTime","duration","paused","muted","volume","src","playbackRate"
        });
        PROTOTYPE_METHODS.put("nodelist", new String[]{"forEach","entries","keys","values","item","length"});
        PROTOTYPE_METHODS.put("response", new String[]{"json","text","blob","arrayBuffer","formData","clone","ok","status","statusText","headers","url","type","redirected"});
        PROTOTYPE_METHODS.put("event", new String[]{"preventDefault","stopPropagation","stopImmediatePropagation","target","currentTarget","type","bubbles","cancelable","composed","timeStamp","isTrusted","defaultPrevented","eventPhase"});
        PROTOTYPE_METHODS.put("classlist", new String[]{"add","remove","toggle","contains","replace","item","length","value","entries","keys","values","forEach","supports"});
        PROTOTYPE_METHODS.put("style", new String[]{"getPropertyValue","setProperty","removeProperty","cssText","length","item"});
        PROTOTYPE_METHODS.put("canvascontext", new String[]{"arc","arcTo","beginPath","bezierCurveTo","clearRect","clip","closePath","createImageData","createLinearGradient","createPattern","createRadialGradient","drawFocusIfNeeded","drawImage","ellipse","fill","fillRect","fillText","getImageData","getLineDash","isPointInPath","isPointInStroke","lineTo","measureText","moveTo","putImageData","quadraticCurveTo","rect","restore","rotate","save","scale","setLineDash","setTransform","stroke","strokeRect","strokeText","transform","translate","fillStyle","font","globalAlpha","globalCompositeOperation","imageSmoothingEnabled","lineCap","lineDashOffset","lineJoin","lineWidth","miterLimit","shadowBlur","shadowColor","shadowOffsetX","shadowOffsetY","strokeStyle","textAlign","textBaseline"});
        PROTOTYPE_METHODS.put("blob", new String[]{"size","type","arrayBuffer","slice","stream","text"});
        PROTOTYPE_METHODS.put("file", new String[]{"name","lastModified","size","type","arrayBuffer","slice","stream","text"});
        PROTOTYPE_METHODS.put("filereader", new String[]{"readAsArrayBuffer","readAsBinaryString","readAsDataURL","readAsText","abort","error","readyState","result","onload","onloadstart","onloadend","onprogress","onabort","onerror"});

        // Array methods that return arrays (chained calls)
        for (String m : new String[]{"filter","map","slice","concat","flat","flatMap","sort","reverse","toReversed","toSorted","splice","copyWithin","fill","from","of","keys","values","entries"}) {
            CHAIN_RETURN_TYPES.put(m, "array");
        }
        // String methods that return strings
        for (String m : new String[]{"trim","trimStart","trimEnd","toLowerCase","toUpperCase","replace","replaceAll","slice","substring","padStart","padEnd","repeat","normalize","concat","charAt","at","toLocaleLowerCase","toLocaleUpperCase"}) {
            CHAIN_RETURN_TYPES.put(m, "string");
        }
        // Promise-returning methods
        for (String m : new String[]{"then","catch","finally","all","race","allSettled","any","resolve","reject"}) {
            CHAIN_RETURN_TYPES.put(m, "promise");
        }
        // DOM query methods return elements
        for (String m : new String[]{"querySelector","getElementById","createElement","closest","parentElement","firstElementChild","lastElementChild","nextElementSibling","previousElementSibling","cloneNode"}) {
            CHAIN_RETURN_TYPES.put(m, "element");
        }
        // DOM query methods returning NodeList
        for (String m : new String[]{"querySelectorAll","getElementsByClassName","getElementsByTagName","childNodes","children"}) {
            CHAIN_RETURN_TYPES.put(m, "nodelist");
        }
        // Methods returning responses
        for (String m : new String[]{"clone"}) {
            // clone can return various; skip if already mapped
        }
        // classList returns ClassList
        CHAIN_RETURN_TYPES.put("classList", "classlist");
        // style returns CSSStyleDeclaration
        CHAIN_RETURN_TYPES.put("style", "style");
        // json() returns promise
        CHAIN_RETURN_TYPES.put("json", "promise");
        CHAIN_RETURN_TYPES.put("text", "promise");
        CHAIN_RETURN_TYPES.put("blob", "promise");
        CHAIN_RETURN_TYPES.put("arrayBuffer", "promise");
        CHAIN_RETURN_TYPES.put("formData", "promise");

        // Functions that always return promises
        for (String f : new String[]{"fetch","axios","axios.get","axios.post","axios.put","axios.delete"}) {
            PROMISE_FUNCTIONS.add(f);
        }
    }

    // ─── Instance state ─────────────────────────────────────────────────────────
    private final List<CompletionItem> builtinItems = new ArrayList<>();
    private int lastTextHash = 0;
    
    private static class JsSymbol {
        CompletionItem item;
        JsScopeParser.ScopeBlock scope;
        public JsSymbol(CompletionItem item, JsScopeParser.ScopeBlock scope) {
            this.item = item;
            this.scope = scope;
        }
    }

    private final List<JsSymbol> cachedUserSymbols = new ArrayList<>();
    private List<JsScopeParser.ScopeBlock> documentScopes = new ArrayList<>();
    private final Map<String, String> varTypeMap = new HashMap<>();
    private File currentFile;

    public JsAutoCompleteEngine(Context context) {
        super(context);
        loadKeywords();
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
        File projectRoot = ProjectSymbolIndex.getProjectRoot(file);
        if (projectRoot != null) {
            ProjectSymbolIndex.getInstance().buildIndex(projectRoot);
        }
    }

    // ─── Keyword loading ───────────────────────────────────────────────────────

    private void loadKeywords() {
        try {
            String json = loadAssetJson("completions/js_keywords.json");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String label   = obj.optString("label");
                String typeStr = obj.optString("type", "KEYWORD");
                String snippet = obj.optString("snippet", obj.optString("insertText", label));
                String detail  = obj.optString("detail", "");

                CompletionItem.Type type;
                try { type = CompletionItem.Type.valueOf(typeStr); }
                catch (Exception e) { type = CompletionItem.Type.KEYWORD; }

                int offset = 0;
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

    // ─── Main entry point ──────────────────────────────────────────────────────

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length())
            return new ArrayList<>();

        String word = getWordBeforeCursor(fullText, cursorPos);

        // ── 1. Import / require path completion ──────────────────────────────
        List<CompletionItem> importItems = getImportPathSuggestions(fullText, cursorPos);
        if (importItems != null) return importItems;

        // ── 1c. Import block completion ──────────────────────────────────────
        List<CompletionItem> importExport = getImportExportSuggestions(fullText, cursorPos, word);
        if (importExport != null) return importExport;

        // ── 1b. Event name string completions (addEventListener/removeEventListener) ──
        String lineBefore = getLineBeforeCursor(fullText, cursorPos);
        Matcher eventMatcher = PAT_EVENT_STRING.matcher(lineBefore);
        if (eventMatcher.find()) {
            String typedEvent = eventMatcher.group(1);
            List<CompletionItem> eventItems = new ArrayList<>();
            for (String ev : EVENT_NAMES) {
                eventItems.add(new CompletionItem(ev, ev, "DOM Event", CompletionItem.Type.VALUE, 0));
            }
            return fuzzyFilter(eventItems, typedEvent);
        }

        Matcher idMatcher = PAT_GET_ELEMENT_BY_ID.matcher(lineBefore);
        if (idMatcher.find()) {
            String typedId = idMatcher.group(1);
            List<CompletionItem> ids = ProjectSymbolIndex.getInstance().getHtmlIdItems();
            return fuzzyFilter(ids, typedId);
        }

        Matcher queryMatcher = PAT_QUERY_SELECTOR.matcher(lineBefore);
        if (queryMatcher.find()) {
            String typedQuery = queryMatcher.group(1);
            List<CompletionItem> prefixed = new ArrayList<>();
            
            for (CompletionItem ci : ProjectSymbolIndex.getInstance().getCssClassItems()) {
                CompletionItem prefixedItem = new CompletionItem("." + ci.getLabel(), "." + ci.getEffectiveInsertText(), ci.getDetail(), ci.getType(), ci.getCursorOffset());
                prefixedItem.setReplaceLength(typedQuery.length());
                prefixed.add(prefixedItem);
            }
            for (CompletionItem ci : ProjectSymbolIndex.getInstance().getHtmlIdItems()) {
                CompletionItem prefixedItem = new CompletionItem("#" + ci.getLabel(), "#" + ci.getEffectiveInsertText(), ci.getDetail(), ci.getType(), ci.getCursorOffset());
                prefixedItem.setReplaceLength(typedQuery.length());
                prefixed.add(prefixedItem);
            }
            return fuzzyFilter(prefixed, typedQuery);
        }

        if (isInsideStringLiteral(fullText, cursorPos)) {
            return new ArrayList<>();
        }

        // ── 2. Dot-member completion ─────────────────────────────────────────
        int dotCheckPos = cursorPos - word.length() - 1;
        if (dotCheckPos >= 0 && fullText.charAt(dotCheckPos) == '.') {
            List<CompletionItem> memberItems = getMemberCompletions(fullText, dotCheckPos, word);
            if (!memberItems.isEmpty()) return memberItems;
        }

        // ── 2b. Object literal key completion ────────────────────────────────
        // If we're inside an object literal (after { or ,) suggest known keys
        List<CompletionItem> objKeys = getObjectLiteralSuggestions(fullText, cursorPos, word);
        if (objKeys != null) return objKeys;

        // ── 3. General: keywords + user symbols ──────────────────────────────
        ensureDocumentIndexed(fullText);
        List<CompletionItem> all = new ArrayList<>(builtinItems);
        Set<String> added = new HashSet<>();
        for (CompletionItem item : builtinItems) added.add(item.getLabel());

        for (JsSymbol sym : cachedUserSymbols) {
            if (sym.scope == null || sym.scope.contains(cursorPos)) {
                if (added.add(sym.item.getLabel())) {
                    all.add(sym.item);
                }
            }
        }
        return fuzzyFilter(all, word);
    }

    // ─── Object literal key suggestions ────────────────────────────────────────

    /**
     * Detects if cursor is in an object literal key position and suggests known keys.
     * Returns null if not in object literal context, empty list if in context but no suggestions.
     *
     * <p>Detects these patterns:
     * <ul>
     *   <li>{@code { | }} — after opening brace</li>
     *   <li>{@code { key: value, | }} — after comma in object</li>
     *   <li>Destructuring: {@code const { | } = obj}</li>
     * </ul>
     */
    private List<CompletionItem> getObjectLiteralSuggestions(String fullText, int cursorPos, String word) {
        // Find the character that precedes the current word (skip whitespace)
        int i = cursorPos - word.length() - 1;
        while (i >= 0 && Character.isWhitespace(fullText.charAt(i))) i--;
        if (i < 0) return null;

        char preceding = fullText.charAt(i);
        // Object key position indicators: after { or after ,
        if (preceding != '{' && preceding != ',') return null;

        // Verify we're actually inside an object literal by checking brace balance
        // and ensuring this isn't a code block (function body, if block, etc.)
        // Code blocks are preceded by ) (if/for/while), else, or start a function body
        if (preceding == '{') {
            // Walk back to see if this { is a code block or an object literal
            int j = i - 1;
            while (j >= 0 && Character.isWhitespace(fullText.charAt(j))) j--;
            if (j >= 0) {
                char beforeBrace = fullText.charAt(j);
                // Code block indicators
                if (beforeBrace == ')' || beforeBrace == '>' ) return null; // arrow function body or if/for
                // Check for keywords that indicate code blocks
                String context = fullText.substring(Math.max(0, j - 10), j + 1).trim();
                if (context.endsWith("else") || context.endsWith("try") || context.endsWith("catch")
                        || context.endsWith("finally") || context.endsWith("do")) {
                    return null;
                }
            }
        }

        // We're likely in an object literal — gather keys from same object and similar objects
        ensureDocumentIndexed(fullText);

        // Collect keys already used in this object (to avoid re-suggesting them)
        java.util.Set<String> usedKeys = new java.util.HashSet<>();
        int braceStart = findMatchingBrace(fullText, cursorPos);
        if (braceStart >= 0) {
            String objContent = fullText.substring(braceStart + 1, cursorPos);
            Matcher keyMatcher = Pattern.compile("([a-zA-Z_$][\\w$]*)\\s*[,:]").matcher(objContent);
            while (keyMatcher.find()) {
                usedKeys.add(keyMatcher.group(1));
            }
        }

        // Collect common object property names from the document
        List<CompletionItem> items = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>(usedKeys);

        // Extract all object keys in the document
        Matcher objKeyMatcher = Pattern.compile("([a-zA-Z_$][\\w$]*)\\s*:(?!=)").matcher(fullText);
        int limit = Math.min(fullText.length(), 100_000);
        while (objKeyMatcher.find() && objKeyMatcher.start() < limit) {
            String key = objKeyMatcher.group(1);
            if (key.length() > 1 && seen.add(key)) {
                items.add(new CompletionItem(key, key, "Object key", CompletionItem.Type.VALUE, 0));
            }
        }

        if (items.isEmpty()) return null; // Not enough context to suggest
        return fuzzyFilter(items, word);
    }

    /**
     * Finds the position of the opening { for the object literal we're currently in.
     */
    private int findMatchingBrace(String text, int cursorPos) {
        int depth = 0;
        for (int i = cursorPos - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '}') depth++;
            else if (c == '{') {
                if (depth == 0) return i;
                depth--;
            }
        }
        return -1;
    }

    // ─── Import / require path completion ─────────────────────────────────────

    private List<CompletionItem> getImportExportSuggestions(String fullText, int cursorPos, String word) {
        String before = fullText.substring(0, cursorPos);
        Matcher mBefore = Pattern.compile("import\\s+\\{[^{}]*$").matcher(before);
        if (!mBefore.find()) return null;

        String after = fullText.substring(cursorPos);
        Matcher mAfter = Pattern.compile("^[^{}]*\\}\\s*from\\s*['\"]([^'\"]+)['\"]").matcher(after);
        if (mAfter.find()) {
            String path = mAfter.group(1);
            List<CompletionItem> exports = ProjectSymbolIndex.getInstance().getExportsForPath(currentFile, path);
            if (!exports.isEmpty()) return fuzzyFilter(exports, word);
        }
        return null;
    }

    /**
     * Returns file completions when cursor is inside an import/require path string.
     *
     * @return list of file completions, empty list if inside import but no matches,
     *         or {@code null} if NOT inside an import context at all.
     */
    private List<CompletionItem> getImportPathSuggestions(String fullText, int cursorPos) {
        if (currentFile == null) return null;

        String lineBefore = getLineBeforeCursor(fullText, cursorPos);

        // Match: import ... from '...' or require('...')
        // The group captures everything typed after the opening quote (may be empty)
        Matcher m = Pattern.compile(
                "(?:from\\s+['\"]|require\\s*\\(\\s*['\"])([^'\"]*)?$"
        ).matcher(lineBefore);

        if (!m.find()) return null; // Not inside an import path

        String typedPath = m.group(1) != null ? m.group(1) : "";
        return buildFileCompletions(typedPath);
    }

    private List<CompletionItem> buildFileCompletions(String typedPath) {
        File baseDir = currentFile.getParentFile();
        if (baseDir == null) return new ArrayList<>();

        int lastSlash = typedPath.lastIndexOf('/');
        File searchDir;
        String filterPrefix;

        if (lastSlash != -1) {
            String dirPart = typedPath.substring(0, lastSlash);
            filterPrefix = typedPath.substring(lastSlash + 1).toLowerCase();
            searchDir = dirPart.isEmpty() ? baseDir : new File(baseDir, dirPart);
        } else {
            filterPrefix = typedPath.toLowerCase();
            // For relative paths (start with . or /) search base dir; for bare names also show from base dir
            searchDir = baseDir;
        }

        if (!searchDir.exists() || !searchDir.isDirectory()) return new ArrayList<>();

        List<CompletionItem> items = new ArrayList<>();
        List<File> files = VFSManager.getInstance().listCachedFiles(searchDir);
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (name.startsWith(".")) continue;
                if (!filterPrefix.isEmpty() && !name.toLowerCase().startsWith(filterPrefix)) continue;

                if (f.isDirectory()) {
                    items.add(new CompletionItem(name + "/", name + "/",
                            "Directory", CompletionItem.Type.FOLDER, 0));
                } else if (isJsLike(name) || typedPath.isEmpty()) {
                    items.add(new CompletionItem(name, name, "File", CompletionItem.Type.FILE, 0));
                }
            }
        }

        java.util.Collections.sort(items, (a, b) -> {
            int fa = a.getType() == CompletionItem.Type.FOLDER ? 0 : 1;
            int fb = b.getType() == CompletionItem.Type.FOLDER ? 0 : 1;
            if (fa != fb) return fa - fb;
            return a.getLabel().compareToIgnoreCase(b.getLabel());
        });
        return items.size() > MAX_SUGGESTIONS ? items.subList(0, MAX_SUGGESTIONS) : items;
    }

    private boolean isJsLike(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".jsx")
                || lower.endsWith(".tsx") || lower.endsWith(".json") || lower.endsWith(".mjs");
    }

    // ─── Dot-member completion ─────────────────────────────────────────────────

    /**
     * Computes member completions for the object/expression before the dot.
     *
     * <p>Insert text is ONLY the method name (e.g. "floor(|)"), never "Math.floor(|)".
     * The namespace is already in the document — we insert only AFTER the dot.
     */
    private List<CompletionItem> getMemberCompletions(String text, int dotPos, String word) {
        String objectToken = extractObjectBeforeDot(text, dotPos);
        if (objectToken == null || objectToken.isEmpty()) return new ArrayList<>();

        // ── a. Check known static namespaces ─────────────────────────────────
        for (String[] pair : DOT_METHODS) {
            if (pair[0].equalsIgnoreCase(objectToken) || objectToken.endsWith(pair[0])) {
                return buildMemberList(pair[0], pair[1].split(","), word, CompletionItem.Type.BUILTIN);
            }
        }

        // ── b. Functions that always return Promise (e.g. fetch) ──────────────
        if (PROMISE_FUNCTIONS.contains(objectToken)) {
            return buildMemberList("Promise", PROTOTYPE_METHODS.get("promise"), word, CompletionItem.Type.FUNCTION);
        }

        // ── c. Chain return type — e.g. "arr.filter(...)" → array methods ────
        String chainType = CHAIN_RETURN_TYPES.get(objectToken);
        if (chainType != null) {
            String[] methods = PROTOTYPE_METHODS.get(chainType);
            if (methods != null) {
                return buildMemberList(chainType, methods, word, CompletionItem.Type.FUNCTION);
            }
        }

        // ── d. User-variable type inference ───────────────────────────────────
        String inferredType = varTypeMap.get(objectToken);
        if (inferredType != null) {
            if (inferredType.startsWith("module:")) {
                String path = inferredType.substring(7);
                List<CompletionItem> exports = ProjectSymbolIndex.getInstance().getExportsForPath(currentFile, path);
                if (!exports.isEmpty()) return fuzzyFilter(exports, word);
            }
            
            // First check prototype methods (array, string, etc.)
            String[] methods = PROTOTYPE_METHODS.get(inferredType);
            if (methods != null) {
                return buildMemberList(inferredType, methods, word, CompletionItem.Type.FUNCTION);
            }
            // Then check DOT_METHODS (for types like IntersectionObserver, WebSocket etc.)
            for (String[] pair : DOT_METHODS) {
                if (pair[0].equals(inferredType)) {
                    return buildMemberList(pair[0], pair[1].split(","), word, CompletionItem.Type.BUILTIN);
                }
            }
        }

        // ── e. Heuristic name-based guess ─────────────────────────────────────
        String lower = objectToken.toLowerCase();
        for (Map.Entry<String, String[]> entry : PROTOTYPE_METHODS.entrySet()) {
            String key = entry.getKey();
            if (lower.contains(key) || (key.equals("array") && (lower.contains("arr") || lower.contains("list") || lower.contains("items") || lower.endsWith("s")))
                    || (key.equals("element") && (lower.startsWith("el") || lower.contains("elem") || lower.contains("node") || lower.contains("btn") || lower.contains("div")))) {
                return buildMemberList(key, entry.getValue(), word, CompletionItem.Type.FUNCTION);
            }
        }

        return new ArrayList<>();
    }

    /**
     * Builds a member completion list.
     * InsertText is ONLY the member name (never "namespace.member") so the cursor-already-past-dot
     * insertion in {@code CodeEditText.insertCompletion} puts the right text after the dot.
     */
    private List<CompletionItem> buildMemberList(String ns, String[] methods, String word, CompletionItem.Type type) {
        if (methods == null) return new ArrayList<>();
        List<CompletionItem> items = new ArrayList<>();
        for (String m : methods) {
            m = m.trim();
            if (m.isEmpty()) continue;
            // Constants (all-caps or known names) don't get parentheses
            boolean isConstant = m.equals(m.toUpperCase()) || m.equals("length") || m.equals("size")
                    || Character.isUpperCase(m.charAt(0));
            // insertText is just the method name — the dot and namespace are already in the doc
            String insert = isConstant ? m : m + "(|)";
            items.add(new CompletionItem(m, insert, ns + " member", type, 0));
        }
        return fuzzyFilter(items, word);
    }

    /**
     * Walks backward from {@code dotPos - 1} to extract the identifier or expression
     * immediately before the dot, handling: simple identifiers, function calls {@code ()},
     * and array accesses {@code []}.
     */
    private String extractObjectBeforeDot(String text, int dotPos) {
        if (dotPos <= 0) return "";
        int i = dotPos - 1;

        // Skip spaces before dot
        while (i >= 0 && text.charAt(i) == ' ') i--;
        if (i < 0) return "";

        // Handle closing ) — walk past the entire argument list
        if (text.charAt(i) == ')') {
            int depth = 0;
            while (i >= 0) {
                char c = text.charAt(i);
                if (c == ')') depth++;
                else if (c == '(') { depth--; if (depth == 0) { i--; break; } }
                i--;
            }
            while (i >= 0 && text.charAt(i) == ' ') i--;
            if (i < 0) return "";
        }

        // Handle closing ] — walk past array access
        if (i >= 0 && text.charAt(i) == ']') {
            int depth = 0;
            while (i >= 0) {
                char c = text.charAt(i);
                if (c == ']') depth++;
                else if (c == '[') { depth--; if (depth == 0) { i--; break; } }
                i--;
            }
            while (i >= 0 && text.charAt(i) == ' ') i--;
            if (i < 0) return "";
        }

        // Collect the identifier
        if (i < 0 || !isWordChar(text.charAt(i))) return "";
        int end = i + 1;
        while (i > 0 && isWordChar(text.charAt(i - 1))) i--;
        return text.substring(i, end);
    }

    // ─── Document symbol indexing ──────────────────────────────────────────────

    private void ensureDocumentIndexed(String text) {
        int hash = text.hashCode();
        if (hash == lastTextHash) return;
        lastTextHash = hash;
        cachedUserSymbols.clear();
        varTypeMap.clear();

        Set<String> builtinNames = new HashSet<>();
        for (CompletionItem item : builtinItems) builtinNames.add(item.getLabel());

        documentScopes = JsScopeParser.buildScopes(text);
        int scanLimit = Math.min(text.length(), 100_000);

        Set<String> declNames = new HashSet<>(builtinNames);

        Matcher m = PAT_USER_DECL.matcher(text);
        while (m.find() && m.start() < scanLimit) {
            String name = firstNonNull(m.group(1), m.group(2), m.group(3), m.group(4));
            if (name == null || name.isEmpty() || builtinNames.contains(name)) continue;

            declNames.add(name);

            boolean isFunction = m.group(1) != null || m.group(3) != null;
            boolean isClass    = m.group(2) != null;

            CompletionItem.Type type;
            String detail;
            String jsDocType = extractJsDocType(text, m.start());

            if (isClass) { type = CompletionItem.Type.KEYWORD; detail = "Class"; }
            else if (isFunction) { type = CompletionItem.Type.FUNCTION; detail = "Function"; }
            else { type = CompletionItem.Type.VALUE; detail = "Variable"; }

            if (jsDocType != null) {
                varTypeMap.put(name, jsDocType.toLowerCase());
            } else if (!isFunction && !isClass) {
                inferVariableType(text, m.end(), name, scanLimit);
            }

            JsScopeParser.ScopeBlock symScope = JsScopeParser.findDeepestScope(documentScopes, m.start());
            cachedUserSymbols.add(new JsSymbol(new CompletionItem(name, name, detail, type, 0), symScope));
        }

        Matcher wordMatcher = PAT_WORD.matcher(text);
        JsScopeParser.ScopeBlock rootScope = documentScopes.isEmpty() ? null : documentScopes.get(0);
        
        Matcher mImport = PAT_IMPORT_STAR.matcher(text);
        while (mImport.find() && mImport.start() < scanLimit) {
            String name = mImport.group(1);
            String path = mImport.group(2);
            varTypeMap.put(name, "module:" + path);
            declNames.add(name);
            cachedUserSymbols.add(new JsSymbol(new CompletionItem(name, name, "Module", CompletionItem.Type.KEYWORD, 0), rootScope));
        }
        
        Set<String> wordSeen = new HashSet<>(declNames);
        while (wordMatcher.find() && wordMatcher.start() < scanLimit) {
            String w = wordMatcher.group();
            if (w.length() >= 3 && wordSeen.add(w)) {
                cachedUserSymbols.add(new JsSymbol(new CompletionItem(w, w, "Word", CompletionItem.Type.VALUE, 0), rootScope));
            }
        }
    }

    private String extractJsDocType(String text, int declStart) {
        int limit = Math.max(0, declStart - 500);
        int commentEnd = text.lastIndexOf("*/", declStart);
        if (commentEnd > limit) {
            String between = text.substring(commentEnd + 2, declStart);
            if (between.trim().isEmpty()) {
                int commentStart = text.lastIndexOf("/**", commentEnd);
                if (commentStart >= limit) {
                    String jsdoc = text.substring(commentStart, commentEnd);
                    Matcher m = PAT_JSDOC_TYPE.matcher(jsdoc);
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            }
        }
        return null;
    }

    private void inferVariableType(String text, int afterDeclEnd, String varName, int scanLimit) {
        if (afterDeclEnd >= scanLimit) return;
        int end = Math.min(afterDeclEnd + 120, scanLimit);
        String snippet = text.substring(afterDeclEnd, end);

        int stop = snippet.indexOf(';'); if (stop != -1) snippet = snippet.substring(0, stop);
        stop = snippet.indexOf('\n'); if (stop != -1) snippet = snippet.substring(0, stop);
        snippet = snippet.trim();

        if (snippet.startsWith("=")) {
            snippet = snippet.substring(1).trim();
            if (snippet.startsWith("[") || snippet.startsWith("Array.from"))   varTypeMap.put(varName, "array");
            else if (snippet.startsWith("\"") || snippet.startsWith("'") || snippet.startsWith("`")) varTypeMap.put(varName, "string");
            else if (snippet.startsWith("new Promise"))           varTypeMap.put(varName, "promise");
            else if (snippet.startsWith("fetch(") || snippet.startsWith("axios")) varTypeMap.put(varName, "promise");
            else if (snippet.startsWith("new Map"))               varTypeMap.put(varName, "map");
            else if (snippet.startsWith("new Set"))               varTypeMap.put(varName, "set");
            else if (snippet.startsWith("new Date"))              varTypeMap.put(varName, "date");
            else if (snippet.startsWith("new RegExp") || snippet.startsWith("/"))  varTypeMap.put(varName, "regexp");
            else if (snippet.contains("new IntersectionObserver")) varTypeMap.put(varName, "IntersectionObserver");
            else if (snippet.contains("new ResizeObserver"))    varTypeMap.put(varName, "ResizeObserver");
            else if (snippet.contains("new MutationObserver"))  varTypeMap.put(varName, "MutationObserver");
            else if (snippet.contains("new WebSocket"))         varTypeMap.put(varName, "WebSocket");
            else if (snippet.contains("new Worker"))            varTypeMap.put(varName, "Worker");
            else if (snippet.contains("new BroadcastChannel")) varTypeMap.put(varName, "BroadcastChannel");
            else if (snippet.contains("new AbortController"))  varTypeMap.put(varName, "AbortController");
            else if (snippet.contains("new URL("))             varTypeMap.put(varName, "URL");
            else if (snippet.contains("new URLSearchParams"))  varTypeMap.put(varName, "URLSearchParams");
            else if (snippet.contains("new FormData"))         varTypeMap.put(varName, "FormData");
            else if (snippet.contains("new Headers"))          varTypeMap.put(varName, "Headers");
            else if (snippet.contains("new FileReader"))       varTypeMap.put(varName, "filereader");
            else if (snippet.contains("new Blob"))             varTypeMap.put(varName, "blob");
            else if (snippet.contains("new File("))            varTypeMap.put(varName, "file");
            else if (snippet.contains("getContext('2d')") || snippet.contains("getContext(\"2d\")")) varTypeMap.put(varName, "canvascontext");
            else if (snippet.startsWith("document.querySelector") || snippet.startsWith("document.getElementById") || snippet.startsWith("document.createElement")) varTypeMap.put(varName, "element");
            else if (snippet.startsWith("document.querySelectorAll") || snippet.startsWith("document.getElementsBy")) varTypeMap.put(varName, "nodelist");
            else if (snippet.contains(".filter(") || snippet.contains(".map(") || snippet.contains(".slice(") || snippet.contains(".concat(") || snippet.contains(".flat(") || snippet.contains("Array.from")) varTypeMap.put(varName, "array");
            else if (snippet.contains(".then("))                  varTypeMap.put(varName, "promise");
            else if (snippet.contains(".split("))                 varTypeMap.put(varName, "array");
            else if (snippet.contains(".toString(") || snippet.contains(".trim(") || snippet.contains(".replace(")) varTypeMap.put(varName, "string");
            else if (snippet.matches("^\\d.*"))                   varTypeMap.put(varName, "number");
        }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
    }
}