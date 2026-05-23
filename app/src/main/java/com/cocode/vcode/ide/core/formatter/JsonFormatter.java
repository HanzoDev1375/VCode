package com.cocode.vcode.ide.core.formatter;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Standard data structure beautifier leveraging native platform object parsers
 * to parse and convert minified JSON text data into human-readable structural formats.
 */
public class JsonFormatter extends BaseFormatter {
    @Override
    public String format(String code) {
        try {
            String trimmed = code.trim();
            // Top-level entry point is an Object block dictionary schema
            if (trimmed.startsWith("{")) {
                return new JSONObject(trimmed).toString(INDENT.length());
            }
            // Top-level entry point is a sequential value Array list schema
            else if (trimmed.startsWith("[")) {
                return new JSONArray(trimmed).toString(INDENT.length());
            }
        } catch (Exception e) {
            // Log formatting parse trace failures without throwing runtime thread crashes
            e.printStackTrace();
        }
        return code; // Revert safely back to unformatted raw payload if an exception occurs
    }
}