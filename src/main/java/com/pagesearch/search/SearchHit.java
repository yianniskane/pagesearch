package com.pagesearch.search;

import java.util.List;

public record SearchHit(
        String id,
        String title,
        List<TextSpan> titleHighlights,
        String path,
        float score,
        List<String> matchedTerms,
        String snippet,
        List<TextSpan> snippetHighlights,
        Integer matchCount
) {
}
