package com.pagesearch.search;

import java.util.Locale;

public enum SearchMode {
    ALL,
    ANY,
    PHRASE,
    REGEX;

    public static SearchMode parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "all" -> ALL;
            case "any" -> ANY;
            case "phrase" -> PHRASE;
            case "regex" -> REGEX;
            default -> throw new InvalidSearchException("mode must be all, any, phrase, or regex");
        };
    }

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
