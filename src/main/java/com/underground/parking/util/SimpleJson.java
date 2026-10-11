package com.underground.parking.util;

import java.util.*;

/**
 * Minimalist, dependency-free JSON utility tailored for Java 17+.
 * Handles JSON serialization and lightweight JSON key-value extraction.
 */
public class SimpleJson {

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Extracts a string value by key from a flat or simple JSON string.
     */
    public static String getString(String json, String key) {
        if (json == null || key == null) return null;
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colon = json.indexOf(':', idx + pattern.length());
        if (colon == -1) return null;

        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote == -1) return null;

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = firstQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == '"') sb.append('"');
                else if (c == '\\') sb.append('\\');
                else if (c == 'n') sb.append('\n');
                else if (c == 'r') sb.append('\r');
                else if (c == 't') sb.append('\t');
                else sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Extracts a boolean value by key from a simple JSON string.
     */
    public static Boolean getBoolean(String json, String key) {
        if (json == null || key == null) return null;
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colon = json.indexOf(':', idx + pattern.length());
        if (colon == -1) return null;

        String rest = json.substring(colon + 1).trim();
        if (rest.startsWith("true")) return true;
        if (rest.startsWith("false")) return false;
        return null;
    }

    /**
     * Extracts an integer or long value by key.
     */
    public static Long getLong(String json, String key) {
        if (json == null || key == null) return null;
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colon = json.indexOf(':', idx + pattern.length());
        if (colon == -1) return null;

        int end = colon + 1;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-' || Character.isWhitespace(json.charAt(end)))) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(colon + 1, end).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
