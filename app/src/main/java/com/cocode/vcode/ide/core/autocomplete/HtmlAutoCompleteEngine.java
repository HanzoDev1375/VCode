package com.cocode.vcode.ide.core.autocomplete;

import android.content.Context;

import com.cocode.vcode.ide.core.parser.HtmlTagParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent completion coordinator for HTML source code — mirrors VS Code's HTML language server.
 *
 * <p>Feature highlights:
 * <ul>
 *   <li>VS Code-style fuzzy scoring via {@link AutoCompleteEngine#fuzzyFilter}</li>
 *   <li>Tag completions with self-closing awareness (void elements get {@code />})</li>
 *   <li>Attribute completions per tag (loaded from JSON) + global HTML attributes</li>
 *   <li>Attribute-value enumerations (e.g., {@code type="…"} → text, email, checkbox…)</li>
 *   <li>Inline {@code style="…"} delegates to {@link CssAutoCompleteEngine}</li>
 *   <li>Inline {@code on*="…"} event-handler attributes delegate to {@link JsAutoCompleteEngine}</li>
 *   <li>Embedded {@code <style>} / {@code <script>} block delegation</li>
 *   <li>Smart file-path completions for {@code src}, {@code href}, {@code action}, {@code data} attributes</li>
 *   <li>Full Emmet expansion (unchanged — keeps pipe-based cursor positioning)</li>
 *   <li>Closing-tag auto-suggestion on {@code </}</li>
 * </ul>
 */
public class HtmlAutoCompleteEngine extends AutoCompleteEngine {

    // Patterns removed in favor of high-performance State Machine parser

    // ─── Global attributes present on every HTML element ───────────────────────
    private static final List<CompletionItem> GLOBAL_ATTRS = new ArrayList<>();

    // ─── Event handler attributes ──────────────────────────────────────────────
    private static final List<CompletionItem> EVENT_ATTRS = new ArrayList<>();

    // ─── DOCTYPE / entity completions ──────────────────────────────────────────
    private static final List<CompletionItem> DOCTYPE_ITEMS = new ArrayList<>();
    private static final List<CompletionItem> ENTITY_ITEMS = new ArrayList<>();
    
    // Fast prefix lookups
    private static final FastTrie TAG_TRIE = new FastTrie();

    static {
        String[][] globals = {
                {"class",           "class=\"|\"",          "CSS class names"},
                {"id",              "id=\"|\"",             "Unique element ID"},
                {"style",           "style=\"|\"",          "Inline CSS styles"},
                {"title",           "title=\"|\"",          "Tooltip text"},
                {"lang",            "lang=\"|\"",           "Language code"},
                {"dir",             "dir=\"|\"",            "Text direction"},
                {"tabindex",        "tabindex=\"|\"",       "Tab order"},
                {"hidden",          "hidden",               "Hide element"},
                {"aria-label",      "aria-label=\"|\"",     "Accessible label"},
                {"aria-hidden",     "aria-hidden=\"true\"", "Hide from assistive tech"},
                {"aria-describedby","aria-describedby=\"|\"","Accessible description"},
                {"aria-labelledby", "aria-labelledby=\"|\"","Accessible label (element)"},
                {"aria-live",       "aria-live=\"|\"",      "Live region"},
                {"aria-expanded",   "aria-expanded=\"|\"",  "Expanded state"},
                {"aria-controls",   "aria-controls=\"|\"",  "Controls element"},
                {"aria-current",    "aria-current=\"|\"",   "Current item indicator"},
                {"aria-disabled",   "aria-disabled=\"|\"",  "Disabled state"},
                {"aria-required",   "aria-required=\"|\"",  "Required field"},
                {"aria-invalid",    "aria-invalid=\"|\"",   "Validation state"},
                {"aria-haspopup",   "aria-haspopup=\"|\"",  "Has popup"},
                {"aria-selected",   "aria-selected=\"|\"",  "Selected state"},
                {"aria-checked",    "aria-checked=\"|\"",   "Checked state"},
                {"aria-valuemin",   "aria-valuemin=\"|\"",  "Minimum value"},
                {"aria-valuemax",   "aria-valuemax=\"|\"",  "Maximum value"},
                {"aria-valuenow",   "aria-valuenow=\"|\"",  "Current value"},
                {"role",            "role=\"|\"",           "ARIA role"},
                {"data-",           "data-|=\"\"",          "Custom data attribute"},
                {"draggable",       "draggable=\"|\"",      "Enable dragging"},
                {"contenteditable", "contenteditable=\"|\"","Editable content"},
                {"spellcheck",      "spellcheck=\"|\"",     "Spell check"},
                {"translate",       "translate=\"|\"",      "Translation hint"},
                {"accesskey",       "accesskey=\"|\"",      "Keyboard shortcut"},
                {"autocapitalize",  "autocapitalize=\"|\"", "Auto-capitalise"},
                {"enterkeyhint",    "enterkeyhint=\"|\"",   "Enter key label"},
                {"inputmode",       "inputmode=\"|\"",      "Virtual keyboard type"},
                {"is",              "is=\"|\"",             "Custom element name"},
                {"part",            "part=\"|\"",           "CSS shadow part"},
                {"slot",            "slot=\"|\"",           "Named slot target"},
                {"popover",         "popover",              "Popover element"},
                {"autofocus",       "autofocus",            "Auto focus on load"},
                {"inert",           "inert",                "Non-interactive subtree"},
                {"nonce",           "nonce=\"|\"",          "CSP nonce"},
        };
        for (String[] g : globals) {
            GLOBAL_ATTRS.add(new CompletionItem(g[0], g[1], g[2], CompletionItem.Type.ATTRIBUTE, 0));
        }

        // Event handler attributes — complete list matching MDN
        String[][] events = {
                {"onclick",       "onclick=\"|\"",       "Mouse click"},
                {"ondblclick",    "ondblclick=\"|\"",    "Double click"},
                {"onmousedown",   "onmousedown=\"|\"",   "Mouse button pressed"},
                {"onmouseup",     "onmouseup=\"|\"",     "Mouse button released"},
                {"onmouseover",   "onmouseover=\"|\"",   "Mouse enters element"},
                {"onmouseout",    "onmouseout=\"|\"",    "Mouse leaves element"},
                {"onmousemove",   "onmousemove=\"|\"",   "Mouse moves over element"},
                {"onmouseenter",  "onmouseenter=\"|\"",  "Mouse enters (no bubble)"},
                {"onmouseleave",  "onmouseleave=\"|\"",  "Mouse leaves (no bubble)"},
                {"onkeydown",     "onkeydown=\"|\"",     "Key pressed"},
                {"onkeyup",       "onkeyup=\"|\"",       "Key released"},
                {"onkeypress",    "onkeypress=\"|\"",    "Key press (deprecated)"},
                {"onfocus",       "onfocus=\"|\"",       "Element gains focus"},
                {"onblur",        "onblur=\"|\"",        "Element loses focus"},
                {"onfocusin",     "onfocusin=\"|\"",     "Focus (bubbles)"},
                {"onfocusout",    "onfocusout=\"|\"",    "Blur (bubbles)"},
                {"onchange",      "onchange=\"|\"",      "Value changed"},
                {"oninput",       "oninput=\"|\"",       "Input value changes"},
                {"onsubmit",      "onsubmit=\"|\"",      "Form submitted"},
                {"onreset",       "onreset=\"|\"",       "Form reset"},
                {"oninvalid",     "oninvalid=\"|\"",     "Input validation fails"},
                {"onselect",      "onselect=\"|\"",      "Text selected"},
                {"onload",        "onload=\"|\"",        "Resource loaded"},
                {"onerror",       "onerror=\"|\"",       "Error occurred"},
                {"onresize",      "onresize=\"|\"",      "Element resized"},
                {"onscroll",      "onscroll=\"|\"",      "Element scrolled"},
                {"onwheel",       "onwheel=\"|\"",       "Wheel rotated"},
                {"oncontextmenu", "oncontextmenu=\"|\"", "Context menu opened"},
                {"ondrag",        "ondrag=\"|\"",        "Dragging"},
                {"ondragstart",   "ondragstart=\"|\"",   "Drag started"},
                {"ondragend",     "ondragend=\"|\"",     "Drag ended"},
                {"ondragover",    "ondragover=\"|\"",    "Dragged over target"},
                {"ondragenter",   "ondragenter=\"|\"",   "Enters drop target"},
                {"ondragleave",   "ondragleave=\"|\"",   "Leaves drop target"},
                {"ondrop",        "ondrop=\"|\"",        "Dropped on target"},
                {"ontouchstart",  "ontouchstart=\"|\"",  "Touch started"},
                {"ontouchmove",   "ontouchmove=\"|\"",   "Touch moved"},
                {"ontouchend",    "ontouchend=\"|\"",    "Touch ended"},
                {"ontouchcancel", "ontouchcancel=\"|\"", "Touch cancelled"},
                {"onpointerdown", "onpointerdown=\"|\"", "Pointer pressed"},
                {"onpointerup",   "onpointerup=\"|\"",   "Pointer released"},
                {"onpointermove", "onpointermove=\"|\"", "Pointer moved"},
                {"onpointerenter","onpointerenter=\"|\"","Pointer enters"},
                {"onpointerleave","onpointerleave=\"|\"","Pointer leaves"},
                {"onpointerover", "onpointerover=\"|\"", "Pointer over"},
                {"onpointerout",  "onpointerout=\"|\"",  "Pointer out"},
                {"onpointercancel","onpointercancel=\"|\"","Pointer cancelled"},
                {"onanimationstart","onanimationstart=\"|\"","CSS animation starts"},
                {"onanimationend", "onanimationend=\"|\"","CSS animation ends"},
                {"onanimationiteration","onanimationiteration=\"|\"","CSS animation repeats"},
                {"ontransitionend","ontransitionend=\"|\"","CSS transition ends"},
                {"oncopy",        "oncopy=\"|\"",        "Content copied"},
                {"oncut",         "oncut=\"|\"",         "Content cut"},
                {"onpaste",       "onpaste=\"|\"",       "Content pasted"},
                {"onplay",        "onplay=\"|\"",        "Media playback starts"},
                {"onpause",       "onpause=\"|\"",       "Media playback paused"},
                {"onended",       "onended=\"|\"",       "Media playback ended"},
                {"ontimeupdate",  "ontimeupdate=\"|\"",  "Media time changed"},
                {"onvolumechange","onvolumechange=\"|\"","Media volume changed"},
                {"oncanplay",     "oncanplay=\"|\"",     "Media can start"},
                {"ontoggle",      "ontoggle=\"|\"",      "Details toggled"},
        };
        for (String[] ev : events) {
            EVENT_ATTRS.add(new CompletionItem(ev[0], ev[1], ev[2], CompletionItem.Type.ATTRIBUTE, 0));
        }
        GLOBAL_ATTRS.addAll(EVENT_ATTRS);

        // DOCTYPE completions
        DOCTYPE_ITEMS.add(new CompletionItem("<!DOCTYPE html>", "<!DOCTYPE html>\n", "HTML5 DOCTYPE", CompletionItem.Type.SNIPPET, 0));
        DOCTYPE_ITEMS.add(new CompletionItem("<!-- -->", "<!-- | -->", "Comment", CompletionItem.Type.SNIPPET, 0));

        // HTML entity completions
        String[][] entities = {
                {"&amp;",    "&amp;",    "& ampersand"},
                {"&lt;",     "&lt;",     "< less-than"},
                {"&gt;",     "&gt;",     "> greater-than"},
                {"&quot;",   "&quot;",   "\" quotation mark"},
                {"&apos;",   "&apos;",   "' apostrophe"},
                {"&nbsp;",   "&nbsp;",   "Non-breaking space"},
                {"&copy;",   "&copy;",   "\u00A9 copyright"},
                {"&reg;",    "&reg;",    "\u00AE registered"},
                {"&trade;",  "&trade;",  "\u2122 trademark"},
                {"&mdash;",  "&mdash;",  "\u2014 em dash"},
                {"&ndash;",  "&ndash;",  "\u2013 en dash"},
                {"&laquo;",  "&laquo;",  "\u00AB left guillemet"},
                {"&raquo;",  "&raquo;",  "\u00BB right guillemet"},
                {"&bull;",   "&bull;",   "\u2022 bullet"},
                {"&hellip;", "&hellip;", "\u2026 horizontal ellipsis"},
                {"&larr;",   "&larr;",   "\u2190 left arrow"},
                {"&rarr;",   "&rarr;",   "\u2192 right arrow"},
                {"&uarr;",   "&uarr;",   "\u2191 up arrow"},
                {"&darr;",   "&darr;",   "\u2193 down arrow"},
                {"&times;",  "&times;",  "\u00D7 multiplication"},
                {"&divide;", "&divide;", "\u00F7 division"},
                {"&plusmn;", "&plusmn;", "\u00B1 plus-minus"},
                {"&deg;",    "&deg;",    "\u00B0 degree"},
                {"&infin;",  "&infin;",  "\u221E infinity"},
                {"&lsquo;",  "&lsquo;",  "\u2018 left single quote"},
                {"&rsquo;",  "&rsquo;",  "\u2019 right single quote"},
                {"&ldquo;",  "&ldquo;",  "\u201C left double quote"},
                {"&rdquo;",  "&rdquo;",  "\u201D right double quote"},
                {"&euro;",   "&euro;",   "\u20AC euro sign"},
                {"&pound;",  "&pound;",  "\u00A3 pound sign"},
                {"&yen;",    "&yen;",    "\u00A5 yen sign"},
                {"&cent;",   "&cent;",   "\u00A2 cent sign"},
                {"&check;",  "&check;",  "\u2713 check mark"},
                {"&hearts;", "&hearts;", "\u2665 heart"},
                {"&star;",   "&star;",   "\u2606 star"},
        };
        for (String[] e : entities) {
            ENTITY_ITEMS.add(new CompletionItem(e[0], e[1], e[2], CompletionItem.Type.VALUE, 0));
        }
    }

    // ─── Attribute value enumerations ────────────────────────────────────────────
    /** Maps attribute name → allowed values shown in VS Code dropdowns. */
    private static final Map<String, String[]> ATTR_VALUES = new HashMap<>();

    static {
        ATTR_VALUES.put("type", new String[]{
                "text","password","email","number","tel","url","search","date","time","datetime-local",
                "month","week","color","range","checkbox","radio","file","hidden","submit","reset","button","image"
        });
        ATTR_VALUES.put("method",     new String[]{"get", "post", "dialog"});
        ATTR_VALUES.put("enctype",    new String[]{"application/x-www-form-urlencoded", "multipart/form-data", "text/plain"});
        ATTR_VALUES.put("target",     new String[]{"_blank", "_self", "_parent", "_top"});
        ATTR_VALUES.put("rel",        new String[]{"noopener", "noreferrer", "nofollow", "stylesheet", "icon", "preload", "prefetch", "canonical", "alternate", "author", "license", "manifest", "dns-prefetch", "preconnect", "modulepreload", "prev", "next", "help", "search"});
        ATTR_VALUES.put("loading",    new String[]{"lazy", "eager"});
        ATTR_VALUES.put("decoding",   new String[]{"async", "sync", "auto"});
        ATTR_VALUES.put("fetchpriority", new String[]{"high", "low", "auto"});
        ATTR_VALUES.put("crossorigin",new String[]{"anonymous", "use-credentials"});
        ATTR_VALUES.put("referrerpolicy", new String[]{"no-referrer","no-referrer-when-downgrade","origin","origin-when-cross-origin","same-origin","strict-origin","strict-origin-when-cross-origin","unsafe-url"});
        ATTR_VALUES.put("autocomplete", new String[]{"on","off","name","email","username","current-password","new-password","one-time-code","postal-code","country","tel","address-line1","address-line2","city","state","zip"});
        ATTR_VALUES.put("dir",        new String[]{"ltr", "rtl", "auto"});
        ATTR_VALUES.put("draggable",  new String[]{"true", "false"});
        ATTR_VALUES.put("contenteditable", new String[]{"true", "false", "plaintext-only"});
        ATTR_VALUES.put("spellcheck", new String[]{"true", "false"});
        ATTR_VALUES.put("translate",  new String[]{"yes", "no"});
        ATTR_VALUES.put("scope",      new String[]{"col", "row", "colgroup", "rowgroup"});
        ATTR_VALUES.put("wrap",       new String[]{"soft", "hard"});
        ATTR_VALUES.put("preload",    new String[]{"none", "metadata", "auto"});
        ATTR_VALUES.put("kind",       new String[]{"subtitles", "captions", "descriptions", "chapters", "metadata"});
        ATTR_VALUES.put("inputmode",  new String[]{"none","text","decimal","numeric","tel","search","email","url"});
        ATTR_VALUES.put("enterkeyhint", new String[]{"enter","done","go","next","previous","search","send"});
        ATTR_VALUES.put("autocapitalize", new String[]{"off","none","on","sentences","words","characters"});
        ATTR_VALUES.put("sandbox",    new String[]{"allow-forms","allow-modals","allow-popups","allow-same-origin","allow-scripts","allow-top-navigation"});
        ATTR_VALUES.put("aria-live",  new String[]{"off", "polite", "assertive"});
        ATTR_VALUES.put("aria-expanded", new String[]{"true", "false"});
        ATTR_VALUES.put("aria-haspopup", new String[]{"true","false","menu","listbox","tree","grid","dialog"});
        ATTR_VALUES.put("aria-current", new String[]{"page","step","location","date","time","true","false"});
        ATTR_VALUES.put("aria-invalid", new String[]{"false","true","grammar","spelling"});
        ATTR_VALUES.put("popover",    new String[]{"auto", "manual"});
        ATTR_VALUES.put("shape",      new String[]{"rect", "circle", "poly", "default"});
        ATTR_VALUES.put("http-equiv", new String[]{"content-type","default-style","refresh","x-ua-compatible","content-security-policy"});
        ATTR_VALUES.put("name",       new String[]{"viewport","description","keywords","author","robots","theme-color","color-scheme","generator","application-name"});
        ATTR_VALUES.put("property",   new String[]{"og:title","og:description","og:image","og:url","og:type","og:site_name","og:locale"});
        ATTR_VALUES.put("role",       new String[]{"alert","alertdialog","application","article","banner","button","cell","checkbox","columnheader","combobox","complementary","contentinfo","definition","dialog","directory","document","feed","figure","form","grid","gridcell","group","heading","img","link","list","listbox","listitem","log","main","marquee","math","menu","menubar","menuitem","menuitemcheckbox","menuitemradio","navigation","none","note","option","presentation","progressbar","radio","radiogroup","region","row","rowgroup","rowheader","scrollbar","search","searchbox","separator","slider","spinbutton","status","switch","tab","table","tablist","tabpanel","term","textbox","timer","toolbar","tooltip","tree","treegrid","treeitem"});
    }

    // ─── Instance state ──────────────────────────────────────────────────────────
    private final List<CompletionItem> tagItems = new ArrayList<>();
    private final HtmlTagParser tagParser       = new HtmlTagParser();
    private final Map<String, List<CompletionItem>> attrMap = new HashMap<>();

    private final CssAutoCompleteEngine cssEngine;
    private final JsAutoCompleteEngine  jsEngine;
    private File currentFile;
    private String htmlBoilerplate;

    public HtmlAutoCompleteEngine(Context context) {
        super(context);
        loadTags();
        this.cssEngine = new CssAutoCompleteEngine(context);
        this.jsEngine  = new JsAutoCompleteEngine(context);
    }

    public void setCurrentFile(File file) {
        this.currentFile = file;
        jsEngine.setCurrentFile(file);
    }

    // ─── Tag loading ────────────────────────────────────────────────────────────

    /**
     * Initialises HTML tag completions and per-tag attribute lists from the JSON asset.
     */
    private void loadTags() {
        try {
            String json = loadAssetJson("completions/html_tags.json");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String tag     = obj.optString("tag");
                String snippet = obj.optString("snippet", "<" + tag + ">|</" + tag + ">");
                String detail  = obj.optString("detail", "");

                CompletionItem item = new CompletionItem(tag, snippet, detail,
                        CompletionItem.Type.TAG, 0);
                tagItems.add(item);
                TAG_TRIE.insert(item);

                // Build per-tag attribute list (tag-specific + global HTML attributes)
                JSONArray attrs = obj.optJSONArray("attributes");
                List<CompletionItem> attrList = new ArrayList<>(GLOBAL_ATTRS);
                if (attrs != null) {
                    for (int j = 0; j < attrs.length(); j++) {
                        String attr = attrs.optString(j);
                        attrList.add(0, new CompletionItem(attr, attr + "=\"|\"",
                                detail.isEmpty() ? tag : detail, CompletionItem.Type.ATTRIBUTE, 0));
                    }
                }
                attrMap.put(tag, attrList);
            }
        } catch (Exception e) {
            // Completion data not critical — proceed with empty list
        }

        // Load the ! boilerplate (Emmet)
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

    // ─── Main entry point ───────────────────────────────────────────────────────

    @Override
    public List<CompletionItem> getSuggestions(String fullText, int cursorPos) {
        if (fullText == null || cursorPos < 0 || cursorPos > fullText.length()) {
            return new ArrayList<>();
        }

        String lineBefore = getLineBeforeCursor(fullText, cursorPos);
        String trimmed    = lineBefore.trim();
        String word       = getWordBeforeCursor(fullText, cursorPos);

        // ── 1. DOCTYPE / comment completions (when typing "<!" or "<!D") ─────────
        if (trimmed.equals("<!") || trimmed.startsWith("<!D") || trimmed.startsWith("<!d")) {
            String filter = trimmed.startsWith("<!") ? trimmed.substring(2) : "";
            return fuzzyFilter(DOCTYPE_ITEMS, filter);
        }

        // ── 1b. Entity completions (when typing "&" followed by letters) ──────
        if (lineBefore.length() > 0) {
            int ampIdx = lineBefore.lastIndexOf('&');
            if (ampIdx >= 0) {
                String afterAmp = lineBefore.substring(ampIdx + 1);
                // Only trigger if no semicolon yet and chars are entity-like
                if (!afterAmp.contains(";") && !afterAmp.contains(" ") && afterAmp.length() <= 10) {
                    String entityFilter = "&" + afterAmp;
                    List<CompletionItem> entityResults = fuzzyFilter(ENTITY_ITEMS, entityFilter);
                    if (!entityResults.isEmpty()) return entityResults;
                }
            }
        }

        HtmlTagParser.HtmlContext ctx = tagParser.parseContext(fullText, cursorPos);

        // ── 3. Closing-tag suggestion on "</" ─────────────────────────────────
        if (trimmed.endsWith("</") || lineBefore.endsWith("</")) {
            if (ctx.unclosedTag != null && !ctx.unclosedTag.isEmpty()) {
                List<CompletionItem> result = new ArrayList<>();
                result.add(new CompletionItem(
                        "</" + ctx.unclosedTag + ">",
                        "</" + ctx.unclosedTag + ">",
                        "Close tag",
                        CompletionItem.Type.TAG, 0));
                return result;
            }
        }

        // ── 4. Embedded <style> / <script> block delegation ───────────────────
        if ("style".equals(ctx.unclosedTag)) {
            // Extract only the CSS content between <style> and cursor
            int styleStart = findBlockContentStart(fullText, cursorPos, "style");
            if (styleStart >= 0) {
                String cssContent = fullText.substring(styleStart, cursorPos);
                int cssCursor = cursorPos - styleStart;
                return cssEngine.getSuggestions(cssContent, cssCursor);
            }
            return cssEngine.getSuggestions(fullText, cursorPos);
        } else if ("script".equals(ctx.unclosedTag)) {
            // Extract only the JS content between <script> and cursor
            int scriptStart = findBlockContentStart(fullText, cursorPos, "script");
            if (scriptStart >= 0) {
                String jsContent = fullText.substring(scriptStart, cursorPos);
                int jsCursor = cursorPos - scriptStart;
                return jsEngine.getSuggestions(jsContent, jsCursor);
            }
            return jsEngine.getSuggestions(fullText, cursorPos);
        }

        // ── 5. Inside an open tag — attribute / attribute-value completions ───
        if (ctx.isInsideOpenTag && !ctx.isTypingTagName && ctx.currentTagName != null) {
            if (ctx.isInsideAttributeValue && ctx.currentAttributeName != null) {
                String attrName = ctx.currentAttributeName;
                String typedValue = ctx.currentAttributeValue != null ? ctx.currentAttributeValue : "";

                // 5a. Inside style="…" → CSS
                if ("style".equals(attrName)) {
                    return cssEngine.getSuggestions(typedValue, typedValue.length(), true);
                }

                // 5b. Inside on*="…" → JS
                if (attrName.startsWith("on")) {
                    return jsEngine.getSuggestions(typedValue, typedValue.length());
                }

                // 5c. Inside file-path attribute → file suggestions
                if (attrName.equals("src") || attrName.equals("href") || attrName.equals("action") || 
                    attrName.equals("formaction") || attrName.equals("poster") || attrName.equals("data") || 
                    attrName.equals("cite") || attrName.equals("manifest") || attrName.equals("srcset")) {
                    return getFileSuggestions(typedValue, ctx.currentTagName, attrName);
                }

                // 5d. Inside a generic attribute value (e.g. class="…", id="…", dir="…")
                String[] values = ATTR_VALUES.get(attrName);
                if (values != null) {
                    List<CompletionItem> valItems = new ArrayList<>();
                    for (String v : values) {
                        valItems.add(new CompletionItem(v, v, attrName + " value",
                                CompletionItem.Type.VALUE, 0));
                    }
                    return fuzzyFilter(valItems, typedValue);
                }
                
                // We are inside quotes for an attribute, but we don't have specific completions.
                // Return empty list so we don't fall through and suggest attribute names.
                return new ArrayList<>();
            }

            // 5e. Attribute name completions for the current tag
            List<CompletionItem> attrs = attrMap.get(ctx.currentTagName);
            if (attrs == null) attrs = new ArrayList<>(GLOBAL_ATTRS);
            return fuzzyFilter(attrs, word);
        }

        // ── 6. Emmet expansion ────────────────────────────────────────────────
        String emmetAbbr = getEmmetAbbreviationBeforeCursor(fullText, cursorPos);
        List<CompletionItem> emmetResults = new ArrayList<>();
        if (emmetAbbr != null && !emmetAbbr.isEmpty() && !emmetAbbr.contains("<")) {
            String expanded = EmmetParser.expandHtml(emmetAbbr, htmlBoilerplate);
            if (expanded != null) {
                boolean isComplex = emmetAbbr.contains(".") || emmetAbbr.contains("#")
                        || emmetAbbr.contains(">") || emmetAbbr.contains("*")
                        || emmetAbbr.contains("+") || emmetAbbr.contains("^")
                        || emmetAbbr.contains("(") || emmetAbbr.contains("{")
                        || emmetAbbr.equals("!");
                CompletionItem emmetItem = new CompletionItem(emmetAbbr, expanded,
                        "Emmet Abbreviation", CompletionItem.Type.SNIPPET, 0);
                emmetItem.setReplaceLength(emmetAbbr.length());
                if (isComplex) {
                    // Complex Emmet abbreviation → only show this one item
                    List<CompletionItem> res = new ArrayList<>();
                    res.add(emmetItem);
                    return res;
                }
                emmetResults.add(emmetItem);
            }
        }

        // ── 7. Tag name completions (when typing "div", "<div", etc.) ─────────
        // Suppress tag suggestions when cursor is inside Emmet text braces {}
        if ((word != null && !word.isEmpty()) || trimmed.endsWith("<")) {
            if (isInsideEmmetBraces(lineBefore)) {
                return emmetResults.isEmpty() ? new ArrayList<>() : emmetResults;
            }
            List<CompletionItem> finalResults = new ArrayList<>(emmetResults);
            
            // Get O(L) fast prefix matches via Trie
            List<CompletionItem> prefixMatches = TAG_TRIE.getCompletions(word, MAX_SUGGESTIONS);
            if (!prefixMatches.isEmpty()) {
                finalResults.addAll(prefixMatches);
            } else {
                // Fallback to fuzzy filtering if no strict prefix matched
                finalResults.addAll(fuzzyFilter(tagItems, word));
            }
            return finalResults;
        }

        return emmetResults.isEmpty() ? new ArrayList<>() : emmetResults;
    }

    // ─── Emmet brace detection ─────────────────────────────────────────────────

    /**
     * Returns true if the cursor is inside unmatched curly braces on the current line.
     * This indicates the user is typing Emmet text content like {@code a{Click me|}}
     * and we should NOT show HTML tag suggestions for the words inside.
     */
    private boolean isInsideEmmetBraces(String lineBefore) {
        int depth = 0;
        for (int i = 0; i < lineBefore.length(); i++) {
            char c = lineBefore.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
        }
        return depth > 0;
    }

    // ─── Embedded block content extraction ─────────────────────────────────────

    /**
     * Finds the content start position of the last unclosed &lt;style&gt; or &lt;script&gt; block
     * before the cursor. Returns the position right after the closing '>' of the opening tag.
     *
     * @param tag "style" or "script"
     * @return index of first char of content, or -1 if not found
     */
    private int findBlockContentStart(String text, int cursorPos, String tag) {
        String searchText = text.substring(0, Math.min(cursorPos, text.length()));
        // Find last opening <style...> or <script...> tag
        String openPattern = "<" + tag;
        int lastOpen = -1;
        int pos = 0;
        while (true) {
            int idx = searchText.indexOf(openPattern, pos);
            if (idx < 0) break;
            // Verify it's a proper tag (not e.g. <styled>)
            int afterTag = idx + openPattern.length();
            if (afterTag < searchText.length()) {
                char next = searchText.charAt(afterTag);
                if (next == '>' || next == ' ' || next == '\n' || next == '\r' || next == '\t') {
                    // Find the closing > of this opening tag
                    int closeAngle = searchText.indexOf('>', afterTag);
                    if (closeAngle >= 0) {
                        // Make sure there isn't a </style> or </script> between this open and cursor
                        String closeTag = "</" + tag;
                        int closeIdx = searchText.indexOf(closeTag, closeAngle);
                        if (closeIdx < 0) {
                            // No closing tag found before cursor — this is the active block
                            lastOpen = closeAngle + 1;
                        }
                    }
                }
            }
            pos = idx + 1;
        }
        return lastOpen;
    }

    // ─── File / folder path suggestions ────────────────────────────────────────

    /**
     * Provides VS Code-style file/folder path completions for path-bearing attributes
     * (src, href, action…). Shows the immediate directory contents when a slash is
     * present; otherwise does a recursive fuzzy-prefix search from the project root.
     */
    private List<CompletionItem> getFileSuggestions(String typedPath, String tagName, String attrName) {
        if (currentFile == null) return new ArrayList<>();
        File currentDir = currentFile.getParentFile();
        if (currentDir == null) return new ArrayList<>();

        List<CompletionItem> items = new ArrayList<>();
        int lastSlash = typedPath.lastIndexOf('/');

        if (lastSlash != -1 || typedPath.isEmpty()) {
            // User typed a path with a directory component OR it's empty — list that directory
            String dirPart      = lastSlash != -1 ? typedPath.substring(0, lastSlash) : "";
            String filterPrefix = lastSlash != -1 ? typedPath.substring(lastSlash + 1).toLowerCase() : typedPath.toLowerCase();
            File   searchDir    = dirPart.isEmpty() ? currentDir : new File(currentDir, dirPart);

            if (searchDir.exists() && searchDir.isDirectory()) {
                List<File> files = VFSManager.getInstance().listCachedFiles(searchDir);
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().startsWith(".")) continue;
                        if (!isFileAllowed(f, tagName, attrName)) continue;
                        String name = f.getName();
                        if (!filterPrefix.isEmpty() && !name.toLowerCase().startsWith(filterPrefix)) continue;
                        String completion = name + (f.isDirectory() ? "/" : "");
                        items.add(new CompletionItem(completion, completion,
                                f.isDirectory() ? "Directory" : getFileSizeHint(f),
                                f.isDirectory() ? CompletionItem.Type.FOLDER : CompletionItem.Type.FILE, 0));
                    }
                }
            }
            sortFileItems(items);
            return items.size() > MAX_SUGGESTIONS ? items.subList(0, MAX_SUGGESTIONS) : items;
        }

        // No slash — search recursively from the project root
        File projectRoot = getProjectRoot(currentFile);
        if (projectRoot == null) projectRoot = currentDir;

        List<File> allMatching = new ArrayList<>();
        findFilesRecursively(projectRoot, typedPath.toLowerCase(), allMatching, 50, tagName, attrName);

        for (File f : allMatching) {
            String relPath = getRelativeHtmlPath(currentDir, f);
            String label   = f.getName() + (f.isDirectory() ? "/" : "");
            items.add(new CompletionItem(label, relPath,
                    f.isDirectory() ? "Directory" : relPath,
                    f.isDirectory() ? CompletionItem.Type.FOLDER : CompletionItem.Type.FILE, 0));
        }
        sortFileItems(items);
        return items;
    }

    private void sortFileItems(List<CompletionItem> items) {
        // Folders first, then files alphabetically
        Collections.sort(items, (a, b) -> {
            int fa = a.getType() == CompletionItem.Type.FOLDER ? 0 : 1;
            int fb = b.getType() == CompletionItem.Type.FOLDER ? 0 : 1;
            if (fa != fb) return fa - fb;
            return a.getLabel().compareToIgnoreCase(b.getLabel());
        });
    }

    private String getFileSizeHint(File f) {
        long size = f.length();
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return (size / (1024 * 1024)) + " MB";
    }

    // ─── Path helpers ───────────────────────────────────────────────────────────

    private File getProjectRoot(File file) {
        if (file == null) return null;
        File dir = file.isDirectory() ? file : file.getParentFile();
        while (dir != null) {
            if (new File(dir, "project_meta.json").exists()) return dir;
            dir = dir.getParentFile();
        }
        return null;
    }

    private String getRelativeHtmlPath(File baseDir, File target) {
        String[] basePath   = baseDir.getAbsolutePath().split("/");
        String[] targetPath = target.getAbsolutePath().split("/");

        int common = 0;
        while (common < basePath.length && common < targetPath.length
                && basePath[common].equals(targetPath[common])) {
            common++;
        }

        StringBuilder rel = new StringBuilder();
        for (int i = common; i < basePath.length; i++) rel.append("../");
        for (int i = common; i < targetPath.length; i++) {
            rel.append(targetPath[i]);
            if (i < targetPath.length - 1) rel.append("/");
        }
        if (target.isDirectory() && rel.length() > 0 && rel.charAt(rel.length() - 1) != '/') {
            rel.append("/");
        }
        return rel.length() == 0 ? "./" : rel.toString();
    }

    private void findFilesRecursively(File dir, String query, List<File> results, int limit, String tagName, String attrName) {
        if (results.size() >= limit) return;
        List<File> files = VFSManager.getInstance().listCachedFiles(dir);
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (!isFileAllowed(f, tagName, attrName)) continue;
            
            if (f.getName().toLowerCase().startsWith(query)) {
                results.add(f);
                if (results.size() >= limit) return;
            }
            if (f.isDirectory()) {
                findFilesRecursively(f, query, results, limit, tagName, attrName);
            }
        }
    }

    private boolean isFileAllowed(File f, String tagName, String attrName) {
        if (f.isDirectory()) return true; // Always allow traversing directories
        String name = f.getName().toLowerCase();
        
        if ("img".equals(tagName) || "poster".equals(attrName) || "srcset".equals(attrName)) {
            return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") 
                || name.endsWith(".gif") || name.endsWith(".svg") || name.endsWith(".webp") || name.endsWith(".ico");
        }
        if ("script".equals(tagName)) {
            return name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".jsx") 
                || name.endsWith(".tsx") || name.endsWith(".mjs") || name.endsWith(".cjs") || name.endsWith(".vue");
        }
        if ("link".equals(tagName) && "href".equals(attrName)) {
            return name.endsWith(".css") || name.endsWith(".png") || name.endsWith(".ico") || name.endsWith(".svg");
        }
        if ("audio".equals(tagName)) {
            return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg");
        }
        if ("video".equals(tagName) && "src".equals(attrName)) {
            return name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".ogg");
        }
        if ("source".equals(tagName)) {
            return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") 
                || name.endsWith(".gif") || name.endsWith(".svg") || name.endsWith(".webp")
                || name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg")
                || name.endsWith(".mp4") || name.endsWith(".webm");
        }
        if ("html".equals(tagName) && "manifest".equals(attrName)) {
            return name.endsWith(".json") || name.endsWith(".webmanifest");
        }
        if ("form".equals(tagName) || "action".equals(attrName) || "formaction".equals(attrName)) {
            return name.endsWith(".php") || name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".js");
        }
        // For other generic tags (like <a>, <iframe>), allow everything
        return true; 
    }
}