package com.pagesearch.search;

import java.util.List;

public record SearchPage(
        String query,
        String mode,
        int page,
        int pageSize,
        long totalHits,
        Long totalMatches,
        List<SearchHit> hits
) {
}
