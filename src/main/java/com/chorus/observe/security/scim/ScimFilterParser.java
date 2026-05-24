package com.chorus.observe.security.scim;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ScimFilterParser {

    public record Filter(@NonNull String attribute, @NonNull String operator, @NonNull String value) {}

    public static @NonNull List<Filter> parse(@NonNull String filter) {
        List<Filter> result = new ArrayList<>();
        String[] parts = filter.split("\\s+and\\s+", -1);
        for (String part : parts) {
            Filter f = parseSingle(part.trim());
            if (f != null) {
                result.add(f);
            }
        }
        return result;
    }

    private static @Nullable Filter parseSingle(@NonNull String expr) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "^(\\w+)\\s+(eq|sw)\\s+\"?([^\"]+)\"?$");
        java.util.regex.Matcher matcher = pattern.matcher(expr);
        if (matcher.matches()) {
            return new Filter(matcher.group(1), matcher.group(2), matcher.group(3));
        }
        return null;
    }
}
