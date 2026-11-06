package com.pagesearch.search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SnippetHighlighter extends UnifiedHighlighter {

    SnippetHighlighter(IndexSearcher searcher, Analyzer analyzer) {
        super(searcher, analyzer);
        setFormatter(SnippetFormatter.INSTANCE);
        setHighlightPhrasesStrictly(true);
        setMaxLength(4_000_000);
    }

    List<FormattedSnippet> snippets(String field, Query query, int[] docIds) throws IOException {
        if (docIds.length == 0) {
            return List.of();
        }
        Map<String, Object[]> formatted = highlightFieldsAsObjects(
                new String[] {field}, query, docIds, new int[] {1});
        Object[] values = formatted.get(field);
        List<FormattedSnippet> snippets = new ArrayList<>(docIds.length);
        for (int i = 0; i < docIds.length; i++) {
            Object value = values == null ? null : values[i];
            snippets.add(value instanceof FormattedSnippet snippet ? snippet : FormattedSnippet.EMPTY);
        }
        return snippets;
    }
}
