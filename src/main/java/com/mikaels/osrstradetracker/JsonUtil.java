package com.mikaels.osrstradetracker;

final class JsonUtil
{
    private JsonUtil() {}

    static String escape(String text)
    {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    static String q(String text)
    {
        return "\"" + escape(text) + "\"";
    }
}
