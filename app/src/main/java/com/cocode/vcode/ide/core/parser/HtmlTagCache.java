package com.cocode.vcode.ide.core.parser;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Central dictionary data cache for HTML element properties.
 * Identifies void elements (self-closing tags) and block items to coordinate
 * real-time auto-indentation layout boundaries and completion logic.
 */
public class HtmlTagCache {

    private static final Set<String> VOID_ELEMENTS = new HashSet<>();
    private static final Set<String> BLOCK_ELEMENTS = new HashSet<>();
    private static boolean isLoaded = false;

    // Hardcode a tiny fallback list just in case the JSON file fails to load
    static {
        VOID_ELEMENTS.add("img");
        VOID_ELEMENTS.add("br");
        VOID_ELEMENTS.add("hr");
        VOID_ELEMENTS.add("input");
        VOID_ELEMENTS.add("meta");
        VOID_ELEMENTS.add("link");
    }

    /**
     * Reads and parses tag properties from asset configuration files.
     * Synchronized block prevents concurrent read state collisions on application startup.
     */
    public static synchronized void load(Context context) {
        if (isLoaded) return; // Prevent parsing multiple times if already cached
        try {
            // Assumes html_tags.json is placed in your app's assets folder
            InputStream is = context.getAssets().open("html_tags.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String jsonStr = new String(buffer, StandardCharsets.UTF_8);

            JSONArray tags = new JSONArray(jsonStr);
            VOID_ELEMENTS.clear();  // Wipe the hardcoded bootstrap records
            BLOCK_ELEMENTS.clear();

            for (int i = 0; i < tags.length(); i++) {
                JSONObject tagObj = tags.getJSONObject(i);
                String tagName = tagObj.getString("tag").toLowerCase();
                boolean isSelfClosing = tagObj.getBoolean("selfClosing");

                if (isSelfClosing) {
                    VOID_ELEMENTS.add(tagName);
                } else {
                    // If it is not self-closing and not inline, we treat it as a block tag for indentation
                    String detail = tagObj.optString("detail", "").toLowerCase();
                    if (!detail.contains("inline") && !detail.contains("text")) {
                        BLOCK_ELEMENTS.add(tagName);
                    }
                }
            }
            isLoaded = true;
        } catch (Exception e) {
            // Log issues safely; structural fallbacks ensure basic layout features continue running
            e.printStackTrace();
        }
    }

    /**
     * Determines if a tag is a self-closing void element that cannot contain internal children.
     */
    public static boolean isVoidElement(String tag) {
        return tag != null && VOID_ELEMENTS.contains(tag.toLowerCase());
    }

    /**
     * Determines if a tag behaves as structural block-level markup demanding dedicated indentation lines.
     */
    public static boolean isBlockElement(String tag) {
        return tag != null && BLOCK_ELEMENTS.contains(tag.toLowerCase());
    }
}