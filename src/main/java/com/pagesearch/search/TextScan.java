package com.pagesearch.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class TextScan {

    private static final int CONTEXT = 80;
    private static final int MAX_SPANS = 100_000;

    private TextScan() {
    }

    record Found(boolean matched, List<TextSpan> titleSpans, FormattedSnippet snippet, List<String> matchedTerms, int matchCount) {
    }

    static Found inDocument(String title, String body, String query, SearchMode mode, boolean matchCase, boolean regex) {
        if (regex) {
            return regex(title, body, query, matchCase);
        }
        if (mode == SearchMode.PHRASE) {
            return phrase(title, body, query.strip(), matchCase);
        }
        return words(title, body, query, mode, matchCase);
    }

    private static Found regex(String title, String body, String query, boolean matchCase) {
        Pattern pattern = compile(query, matchCase);
        List<TextSpan> titleSpans = regexSpans(title, pattern);
        List<TextSpan> bodySpans = regexSpans(body, pattern);
        if (titleSpans.isEmpty() && bodySpans.isEmpty()) {
            return miss();
        }
        return found(title, body, titleSpans, bodySpans, List.of());
    }

    private static Found phrase(String title, String body, String phrase, boolean matchCase) {
        List<TextSpan> titleSpans = literalSpans(title, phrase, matchCase);
        List<TextSpan> bodySpans = literalSpans(body, phrase, matchCase);
        if (titleSpans.isEmpty() && bodySpans.isEmpty()) {
            return miss();
        }
        return found(title, body, titleSpans, bodySpans, List.of());
    }

    private static Found words(String title, String body, String query, SearchMode mode, boolean matchCase) {
        List<String> words = words(query);
        List<String> matched = new ArrayList<>();
        List<TextSpan> titleSpans = new ArrayList<>();
        List<TextSpan> bodySpans = new ArrayList<>();
        for (String word : words) {
            List<TextSpan> inTitle = literalSpans(title, word, matchCase);
            List<TextSpan> inBody = literalSpans(body, word, matchCase);
            if (inTitle.isEmpty() && inBody.isEmpty()) {
                continue;
            }
            matched.add(word);
            titleSpans.addAll(inTitle);
            bodySpans.addAll(inBody);
        }
        boolean ok = mode == SearchMode.ANY ? !matched.isEmpty() : matched.size() == words.size();
        if (!ok) {
            return miss();
        }
        return found(title, body, titleSpans, bodySpans, List.copyOf(matched));
    }

    private static Found found(
            String title,
            String body,
            List<TextSpan> titleSpans,
            List<TextSpan> bodySpans,
            List<String> matchedTerms
    ) {
        titleSpans = sorted(titleSpans);
        bodySpans = sorted(bodySpans);
        FormattedSnippet snippet = bodySpans.isEmpty()
                ? new FormattedSnippet("", List.of())
                : window(body, bodySpans.get(0));
        return new Found(true, titleSpans, snippet, matchedTerms, titleSpans.size() + bodySpans.size());
    }

    private static Found miss() {
        return new Found(false, List.of(), new FormattedSnippet("", List.of()), List.of(), 0);
    }

    static FormattedSnippet window(String text, TextSpan span) {
        int from = Math.max(0, span.start() - CONTEXT);
        int to = Math.min(text.length(), span.end() + CONTEXT);
        return new FormattedSnippet(
                text.substring(from, to),
                List.of(new TextSpan(span.start() - from, span.end() - from))
        );
    }

    private static List<TextSpan> literalSpans(String text, String needle, boolean matchCase) {
        if (needle.isEmpty() || text.isEmpty()) {
            return List.of();
        }
        String haystack = matchCase ? text : text.toLowerCase(Locale.ROOT);
        String term = matchCase ? needle : needle.toLowerCase(Locale.ROOT);
        List<TextSpan> spans = new ArrayList<>();
        int from = 0;
        while (from < haystack.length() && spans.size() < MAX_SPANS) {
            int at = haystack.indexOf(term, from);
            if (at < 0) {
                break;
            }
            spans.add(new TextSpan(at, at + term.length()));
            from = at + term.length();
        }
        return spans;
    }

    private static List<TextSpan> regexSpans(String text, Pattern pattern) {
        if (text.isEmpty()) {
            return List.of();
        }
        List<TextSpan> spans = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find() && spans.size() < MAX_SPANS) {
            if (matcher.start() == matcher.end()) {
                if (matcher.end() >= text.length()) {
                    break;
                }
                matcher.region(matcher.end() + 1, text.length());
                continue;
            }
            spans.add(new TextSpan(matcher.start(), matcher.end()));
        }
        return spans;
    }

    private static Pattern compile(String query, boolean matchCase) {
        int flags = Pattern.MULTILINE;
        if (!matchCase) {
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        try {
            return Pattern.compile(query, flags);
        } catch (PatternSyntaxException e) {
            throw new InvalidSearchException("Invalid regular expression: " + e.getDescription());
        }
    }

    private static List<String> words(String query) {
        String trimmed = query.strip();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return List.of(trimmed.split("\\s+"));
    }

    private static List<TextSpan> sorted(List<TextSpan> spans) {
        return spans.stream().sorted(Comparator.comparingInt(TextSpan::start)).toList();
    }
}
